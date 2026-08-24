package com.dadcoach.memory.retrieval;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.dto.MemoryDto;
import com.dadcoach.memory.dto.RetrievalResultDto;
import com.dadcoach.memory.mapper.MemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for retrieving memories with composite scoring and ranking.
 *
 * <p>This component implements the retrieval logic defined in SPEC-004 Requirement 16,
 * retrieving candidate memories and ranking them by a composite score that balances:
 * <ul>
 *   <li>Importance (50% weight) - how critical the memory is for coaching</li>
 *   <li>Recency (30% weight) - how recently the memory was accessed</li>
 *   <li>Relevance (20% weight) - semantic similarity to the query topic</li>
 * </ul>
 *
 * <h3>Retrieval Process</h3>
 * <ol>
 *   <li>Generate embedding for the query topic (if embedding service available)</li>
 *   <li>Query candidate memories from repository (ACTIVE/CONFIRMED, confidence >= 0.3)</li>
 *   <li>Calculate composite score for each memory</li>
 *   <li>Sort by descending composite score</li>
 *   <li>Apply diversity filter (max 5 per category)</li>
 *   <li>Limit to maxCount results</li>
 *   <li>Update access tracking (access_count, last_accessed_at)</li>
 *   <li>Return rich RetrievalResultDto with metadata</li>
 * </ol>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>When no embedding is available, relevance defaults to 0.5 (neutral)</li>
 *   <li>Child filtering is optional (null childId returns all memories for father)</li>
 *   <li>Access tracking happens within the same transaction for consistency</li>
 *   <li>Diversity filter ensures no single category dominates results</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 16.2, 16.3, 16.4</b> - Composite scoring, descending order, and diversity
 *
 * @see CompositeScoreCalculator
 * @see RetrievalMetadata
 * @see RetrievalResultDto
 */
@Service
public class MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetriever.class);

    /**
     * Minimum confidence score for retrievable memories (Requirement 5).
     */
    private static final BigDecimal MIN_CONFIDENCE_SCORE = new BigDecimal("0.30");

    /**
     * Default relevance score when embedding similarity is not available.
     */
    private static final float DEFAULT_RELEVANCE_SCORE = 0.5f;

    /**
     * Multiplier for max candidates to fetch before scoring.
     * We fetch more candidates than needed to allow for post-filtering.
     */
    private static final int CANDIDATE_MULTIPLIER = 3;

    /**
     * Maximum number of candidates to consider (performance guard).
     */
    private static final int MAX_CANDIDATES = 100;

    /**
     * Maximum number of memories allowed per category in retrieval results.
     * This ensures diversity and prevents any single category from dominating results.
     */
    static final int MAX_PER_CATEGORY = 5;

    private final MemoryRepository memoryRepository;
    private final CompositeScoreCalculator scoreCalculator;
    private final MemoryMapper memoryMapper;

    /**
     * Creates a new MemoryRetriever with required dependencies.
     *
     * @param memoryRepository the repository for memory queries
     * @param scoreCalculator  the composite score calculator
     * @param memoryMapper     the mapper for entity-DTO conversion
     */
    public MemoryRetriever(
            @Qualifier("specMemoryRepository") MemoryRepository memoryRepository,
            CompositeScoreCalculator scoreCalculator,
            MemoryMapper memoryMapper) {
        this.memoryRepository = memoryRepository;
        this.scoreCalculator = scoreCalculator;
        this.memoryMapper = memoryMapper;
    }

    /**
     * Retrieves memories for a father, ranked by composite score in descending order.
     *
     * <p>This method implements the retrieval flow from SPEC-004:
     * <ol>
     *   <li>Query candidate memories matching father (and optionally child)</li>
     *   <li>Calculate composite score for each memory</li>
     *   <li>Sort by descending composite score</li>
     *   <li>Limit to maxCount results</li>
     *   <li>Update access tracking</li>
     * </ol>
     *
     * <p>Note: Semantic relevance scoring requires an EmbeddingService (Task 9).
     * Until that's implemented, relevance defaults to 0.5 for all memories.
     *
     * @param fatherId the father's ID (required)
     * @param topic    the topic/query for relevance scoring (optional, currently unused)
     * @param childId  filter to memories about a specific child (optional, null for all)
     * @param maxCount maximum number of results to return
     * @return list of RetrievalResultDto ordered by descending composite score
     * @throws IllegalArgumentException if fatherId is null or maxCount is less than 1
     */
    @Transactional
    public List<RetrievalResultDto> retrieveRanked(UUID fatherId, String topic, UUID childId, int maxCount) {
        validateInputs(fatherId, maxCount);

        log.debug("Retrieving ranked memories for father={}, topic='{}', childId={}, maxCount={}",
                fatherId, topic, childId, maxCount);

        // Step 1: Fetch candidate memories
        List<Memory> candidates = fetchCandidates(fatherId, childId);

        if (candidates.isEmpty()) {
            log.debug("No candidate memories found for father={}", fatherId);
            return Collections.emptyList();
        }

        log.debug("Found {} candidate memories", candidates.size());

        // Step 2: Calculate composite scores and create scored results
        // Note: Relevance scoring via embeddings will be added when EmbeddingService (Task 9) is implemented
        List<ScoredMemory> scoredMemories = candidates.stream()
                .map(memory -> scoreMemory(memory, DEFAULT_RELEVANCE_SCORE))
                .collect(Collectors.toList());

        // Step 3: Sort by descending composite score
        scoredMemories.sort((a, b) -> Double.compare(b.compositeScore(), a.compositeScore()));

        // Step 4: Apply diversity filter (max 5 per category)
        List<ScoredMemory> diverseMemories = applyDiversityFilter(scoredMemories);

        // Step 5: Limit to maxCount
        List<ScoredMemory> topMemories = diverseMemories.stream()
                .limit(maxCount)
                .toList();

        // Step 6: Update access tracking for retrieved memories
        updateAccessTracking(topMemories.stream().map(ScoredMemory::memory).toList());

        // Step 7: Convert to DTOs with metadata
        List<RetrievalResultDto> results = topMemories.stream()
                .map(this::toRetrievalResultDto)
                .toList();

        log.debug("Returning {} memories, top score={}", results.size(),
                results.isEmpty() ? "N/A" : String.format("%.4f", results.get(0).getCompositeScore()));

        return results;
    }

    /**
     * Retrieves memories using vector similarity search for relevance scoring.
     *
     * <p>This method uses pgvector's cosine similarity for semantic relevance.
     * It requires a pre-computed embedding for the query topic.
     *
     * @param fatherId       the father's ID
     * @param queryEmbedding the embedding vector for the query (1536 dimensions)
     * @param childId        filter to a specific child (optional)
     * @param maxCount       maximum number of results
     * @return list of RetrievalResultDto ordered by descending composite score
     */
    @Transactional
    public List<RetrievalResultDto> retrieveRankedWithEmbedding(
            UUID fatherId, float[] queryEmbedding, UUID childId, int maxCount) {
        validateInputs(fatherId, maxCount);

        if (queryEmbedding == null || queryEmbedding.length != Memory.EMBEDDING_DIMENSION) {
            log.warn("Invalid query embedding, falling back to default relevance scoring");
            return retrieveRanked(fatherId, null, childId, maxCount);
        }

        log.debug("Retrieving ranked memories with embedding search for father={}", fatherId);

        // Use pgvector similarity search
        int candidateLimit = Math.min(maxCount * CANDIDATE_MULTIPLIER, MAX_CANDIDATES);
        String embeddingString = arrayToVectorString(queryEmbedding);
        List<String> states = List.of(MemoryState.ACTIVE.name(), MemoryState.CONFIRMED.name());

        List<Object[]> similarResults = memoryRepository.findBySimilarity(
                fatherId, states, MIN_CONFIDENCE_SCORE, embeddingString, candidateLimit);

        if (similarResults.isEmpty()) {
            log.debug("No similar memories found for father={}", fatherId);
            return Collections.emptyList();
        }

        // Map results to scored memories
        List<ScoredMemory> scoredMemories = new ArrayList<>();
        for (Object[] row : similarResults) {
            Memory memory = mapRowToMemory(row);
            float cosineSimilarity = ((Number) row[row.length - 1]).floatValue();

            // Apply child filter if specified
            if (childId != null && !childId.equals(memory.getChildId())) {
                continue;
            }

            ScoredMemory scored = scoreMemory(memory, cosineSimilarity);
            scoredMemories.add(scored);
        }

        // Sort by descending composite score
        scoredMemories.sort((a, b) -> Double.compare(b.compositeScore(), a.compositeScore()));

        // Apply diversity filter (max 5 per category)
        List<ScoredMemory> diverseMemories = applyDiversityFilter(scoredMemories);

        // Limit and convert
        List<ScoredMemory> topMemories = diverseMemories.stream()
                .limit(maxCount)
                .toList();

        updateAccessTracking(topMemories.stream().map(ScoredMemory::memory).toList());

        return topMemories.stream()
                .map(this::toRetrievalResultDto)
                .toList();
    }

    /**
     * Validates input parameters.
     */
    private void validateInputs(UUID fatherId, int maxCount) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }
        if (maxCount < 1) {
            throw new IllegalArgumentException("maxCount must be at least 1");
        }
    }

    /**
     * Fetches candidate memories from the repository.
     */
    private List<Memory> fetchCandidates(UUID fatherId, UUID childId) {
        Collection<MemoryState> retrievableStates = EnumSet.of(MemoryState.ACTIVE, MemoryState.CONFIRMED);

        if (childId != null) {
            // Filter to specific child
            return memoryRepository.findByFatherIdAndChildIdAndStateIn(fatherId, childId, retrievableStates)
                    .stream()
                    .filter(m -> m.getConfidenceScore().compareTo(MIN_CONFIDENCE_SCORE) >= 0)
                    .collect(Collectors.toList());
        } else {
            // All memories for father with sufficient confidence
            return memoryRepository.findRetrievableMemories(fatherId, retrievableStates, MIN_CONFIDENCE_SCORE);
        }
    }

    /**
     * Scores a memory using the composite score calculator.
     */
    private ScoredMemory scoreMemory(Memory memory, float relevanceScore) {
        double compositeScore = scoreCalculator.calculate(memory, relevanceScore);
        double recencyFactor = scoreCalculator.calculateRecencyFactor(memory);

        return new ScoredMemory(
                memory,
                compositeScore,
                memory.getImportanceScore(),
                memory.getConfidenceScore().doubleValue(),
                recencyFactor,
                relevanceScore
        );
    }

    /**
     * Updates access tracking for retrieved memories.
     *
     * <p><b>IMPORTANT DESIGN DECISION (SPEC-004 Requirement 5 Criteria 2):</b>
     * This method updates ONLY access-related fields (access_count, last_accessed_at).
     * It MUST NOT modify confidence_score.
     *
     * <p>Confidence score can ONLY increase through explicit user evidence:
     * <ul>
     *   <li>User confirmation via {@link com.dadcoach.memory.lifecycle.MemoryLifecycleService#confirmMemory(UUID)}</li>
     *   <li>User correction via {@link com.dadcoach.memory.lifecycle.MemoryLifecycleService#supersedeMemory(UUID, String, java.math.BigDecimal)}</li>
     *   <li>Father repeats information in a later conversation (new user evidence)</li>
     *   <li>Deterministic domain event validation (e.g., mission completion)</li>
     * </ul>
     *
     * <p>System usage (retrieval, prompt injection, access counts) NEVER increases confidence.
     * This ensures confidence reflects actual certainty about accuracy, not popularity.
     *
     * @param memories the memories that were retrieved and should have access tracking updated
     */
    private void updateAccessTracking(List<Memory> memories) {
        for (Memory memory : memories) {
            // recordAccess() only updates access_count and last_accessed_at
            // It does NOT modify confidence_score (by design per SPEC-004 Req 5)
            memory.recordAccess();
        }
        memoryRepository.saveAll(memories);
    }

    /**
     * Converts a scored memory to a RetrievalResultDto.
     */
    private RetrievalResultDto toRetrievalResultDto(ScoredMemory scored) {
        MemoryDto dto = memoryMapper.toDto(scored.memory());

        RetrievalMetadata metadata = new RetrievalMetadata(
                scored.compositeScore(),
                scored.importanceScore(),
                scored.confidenceScore(),
                scored.recencyFactor(),
                scored.relevanceScore()
        );

        return new RetrievalResultDto(dto, metadata);
    }

    /**
     * Converts a float array to pgvector string format: [v1,v2,...,vn].
     */
    private String arrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Maps a native query result row to a Memory entity.
     * This handles the Object[] result from pgvector similarity queries.
     */
    @SuppressWarnings("unchecked")
    private Memory mapRowToMemory(Object[] row) {
        // The native query returns Memory entity fields plus cosine_similarity
        // JPA/Hibernate should handle this mapping, but we need to extract the Memory
        // In practice, if using @SqlResultSetMapping or entity manager, the first element would be the entity
        // For simplicity, assuming the row[0] contains the mapped Memory entity when using JPA native query with entity
        if (row[0] instanceof Memory) {
            return (Memory) row[0];
        }
        // Fallback: if row contains raw values, we'd need to manually construct
        // This shouldn't happen with proper JPA mapping
        throw new IllegalStateException("Expected Memory entity in query result but got: " + row[0].getClass());
    }

    /**
     * Applies diversity filter: max {@link #MAX_PER_CATEGORY} memories per category.
     *
     * <p>This method ensures that no single memory category dominates the retrieval results.
     * It iterates through the already-sorted list (by descending composite score) and
     * includes each memory only if its category hasn't reached the limit yet.
     *
     * <p>The order by composite score is preserved since we iterate in score order
     * and skip memories beyond their category limit.
     *
     * <p><b>Validates: Requirements 16.4</b> - Diversity constraint (max 5 per category)
     *
     * @param scoredMemories list of memories sorted by descending composite score
     * @return filtered list maintaining descending composite score order with max 5 per category
     */
    List<ScoredMemory> applyDiversityFilter(List<ScoredMemory> scoredMemories) {
        Map<MemoryCategory, Integer> categoryCount = new EnumMap<>(MemoryCategory.class);
        List<ScoredMemory> filtered = new ArrayList<>();

        for (ScoredMemory scored : scoredMemories) {
            MemoryCategory category = scored.memory().getCategory();
            int count = categoryCount.getOrDefault(category, 0);

            if (count < MAX_PER_CATEGORY) {
                filtered.add(scored);
                categoryCount.put(category, count + 1);
            }
            // else: skip this memory as its category has reached the limit
        }

        if (log.isDebugEnabled()) {
            int skipped = scoredMemories.size() - filtered.size();
            if (skipped > 0) {
                log.debug("Diversity filter: skipped {} memories that exceeded category limits", skipped);
            }
        }

        return filtered;
    }

    /**
     * Internal record to hold a memory with its calculated scores.
     */
    record ScoredMemory(
            Memory memory,
            double compositeScore,
            int importanceScore,
            double confidenceScore,
            double recencyFactor,
            double relevanceScore
    ) {}
}
