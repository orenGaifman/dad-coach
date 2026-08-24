package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for consolidating similar memories within the same father+category.
 *
 * <p>From SPEC-004 Requirement 8 (Memory Consolidation and Merging):
 * <ul>
 *   <li>Runs weekly during maintenance window (Sunday)</li>
 *   <li>Phase 3: Identify and merge duplicate memories across all tiers (Semantic_Similarity > 0.85)</li>
 *   <li>For consolidation candidates: similarity >= 0.9 within same father+category</li>
 * </ul>
 *
 * <p>The consolidation process:
 * <ol>
 *   <li>Identifies all distinct fathers with active memories</li>
 *   <li>For each father, finds memories with high similarity within same category</li>
 *   <li>Groups similar memories as consolidation candidates</li>
 *   <li>Higher confidence memory absorbs lower confidence one via SUPERSEDED link</li>
 * </ol>
 *
 * <p>Design considerations:
 * <ul>
 *   <li>Processes fathers in batches to avoid lock contention</li>
 *   <li>Race condition protection: skips memories that changed state since job start</li>
 *   <li>Uses pgvector cosine similarity for efficient vector search</li>
 *   <li>Never consolidates: IDENTITY, MILESTONE, or EVENT memories with future dates</li>
 * </ul>
 *
 * @see Memory
 * @see MemoryRepository
 */
@Service
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    /**
     * Similarity threshold for consolidation candidates (>= 0.9).
     * Memories with cosine similarity above this threshold within the same
     * father+category are considered candidates for merging.
     */
    public static final double CONSOLIDATION_SIMILARITY_THRESHOLD = 0.90;

    /**
     * States to include in consolidation: ACTIVE and CONFIRMED.
     */
    private static final List<String> ACTIVE_STATES = List.of(
            MemoryState.ACTIVE.name(),
            MemoryState.CONFIRMED.name()
    );

    /**
     * Collection of MemoryState enums for active states.
     */
    private static final Collection<MemoryState> ACTIVE_STATE_ENUMS = List.of(
            MemoryState.ACTIVE,
            MemoryState.CONFIRMED
    );

    /**
     * Minimum confidence score for memories to be considered in consolidation.
     */
    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.30");

    /**
     * Maximum number of similar memories to retrieve per memory.
     */
    private static final int MAX_SIMILAR_CANDIDATES = 10;

    /**
     * Categories that should NEVER be consolidated (each fact must remain distinct).
     */
    private static final Set<MemoryCategory> NON_CONSOLIDATABLE_CATEGORIES = Set.of(
            MemoryCategory.IDENTITY,
            MemoryCategory.MILESTONE
    );

    /**
     * Source type indicator for weekly consolidation summaries (stored in content prefix).
     */
    public static final String WEEKLY_SUMMARY_INDICATOR = "[WEEKLY_SUMMARY]";

    /**
     * Source type indicator for monthly consolidation summaries (stored in content prefix).
     */
    public static final String MONTHLY_SUMMARY_INDICATOR = "[MONTHLY_SUMMARY]";

    /**
     * Importance score for weekly consolidation summaries (Requirement 14 criteria 3).
     */
    public static final int WEEKLY_SUMMARY_IMPORTANCE = 4;

    /**
     * Importance score for monthly consolidation summaries (Requirement 14 criteria 4).
     */
    public static final int MONTHLY_SUMMARY_IMPORTANCE = 5;

    /**
     * Fixed confidence score for consolidation summaries.
     */
    public static final BigDecimal SUMMARY_CONFIDENCE = new BigDecimal("0.90");

    /**
     * Age threshold for conversation summaries eligible for weekly consolidation (30 days).
     */
    private static final int WEEKLY_CONSOLIDATION_AGE_DAYS = 30;

    /**
     * Age threshold for weekly summaries eligible for monthly consolidation (60 days).
     */
    private static final int MONTHLY_CONSOLIDATION_AGE_DAYS = 60;

    /**
     * Maximum number of active weekly summaries per father.
     */
    public static final int MAX_WEEKLY_SUMMARIES = 4;

    /**
     * Maximum number of active monthly summaries per father.
     */
    public static final int MAX_MONTHLY_SUMMARIES = 6;

    private final MemoryRepository memoryRepository;

    /**
     * Batch size for processing fathers to avoid lock contention.
     */
    @Value("${dadcoach.memory.consolidation.batch-size:50}")
    private int batchSize;

    /**
     * Creates a MemoryConsolidationService with the required repository.
     *
     * @param memoryRepository the repository for memory persistence and queries
     */
    public MemoryConsolidationService(@Qualifier("specMemoryRepository") MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * Scheduled job that runs weekly to consolidate similar memories.
     *
     * <p>Runs at 3:00 AM UTC every Sunday during the maintenance window.
     * This runs before the cleanup job to ensure consolidation happens first.
     *
     * <p>The consolidation workflow:
     * <ol>
     *   <li>Phase 1: Identify memories with high similarity (>=0.9) within same father+category</li>
     *   <li>Phase 2: Merge identified candidates - anchor absorbs others via SUPERSEDED state</li>
     *   <li>Phase 3: Create weekly/monthly consolidation summaries (SPEC-004 Req 14)</li>
     * </ol>
     *
     * <p>Cron expression: "0 0 3 * * SUN" = 3:00 AM UTC every Sunday
     */
    @Scheduled(cron = "${dadcoach.memory.consolidation.cron:0 0 3 * * SUN}")
    public void runWeeklyConsolidation() {
        log.info("MemoryConsolidationService: Starting weekly consolidation job");
        Instant jobStartTime = Instant.now();

        try {
            // Phase 1: Identify consolidation candidates
            ConsolidationResult identificationResult = identifyConsolidationCandidates(jobStartTime);

            // Phase 2: Merge the identified candidates
            MergeResult mergeResult = mergeConsolidationCandidates(identificationResult.candidateGroups());

            // Phase 3: Create weekly/monthly consolidation summaries
            SummaryCreationResult summaryResult = createSummaryMemories(jobStartTime);

            long durationMs = ChronoUnit.MILLIS.between(jobStartTime, Instant.now());

            log.info("MemoryConsolidationService: Weekly consolidation job completed. " +
                            "fathersProcessed={}, memoriesAnalyzed={}, candidateGroupsFound={}, " +
                            "groupsMerged={}, memoriesMerged={}, " +
                            "weeklySummariesCreated={}, monthlySummariesCreated={}, memoriesArchived={}, " +
                            "errors={}, durationMs={}",
                    identificationResult.fathersProcessed(), identificationResult.memoriesAnalyzed(),
                    identificationResult.candidateGroupsFound(),
                    mergeResult.groupsProcessed(), mergeResult.memoriesMerged(),
                    summaryResult.weeklySummariesCreated(), summaryResult.monthlySummariesCreated(),
                    summaryResult.memoriesArchived(),
                    identificationResult.errors() + mergeResult.errors() + summaryResult.errors(), durationMs);

        } catch (Exception e) {
            log.error("MemoryConsolidationService: Weekly consolidation job failed. error={}", 
                    e.getMessage(), e);
        }
    }

    /**
     * Identifies memories with high similarity (>= 0.9) within the same father+category.
     *
     * <p>This is Phase 3 of the consolidation job as defined in SPEC-004 Requirement 8:
     * "Identify and merge duplicate memories across all tiers (Semantic_Similarity > 0.85)"
     *
     * <p>For this task, we use a stricter threshold of 0.9 for consolidation candidates,
     * as memories with such high similarity should definitely be merged.
     *
     * @param jobStartTime the time the consolidation job started (for race condition protection)
     * @return the consolidation result containing identified candidate groups
     */
    public ConsolidationResult identifyConsolidationCandidates(Instant jobStartTime) {
        log.debug("MemoryConsolidationService: Identifying consolidation candidates with similarity >= {}",
                CONSOLIDATION_SIMILARITY_THRESHOLD);

        int fathersProcessed = 0;
        int memoriesAnalyzed = 0;
        int candidateGroupsFound = 0;
        int totalCandidateMemories = 0;
        int errors = 0;

        List<ConsolidationCandidateGroup> allCandidateGroups = new ArrayList<>();

        // Get all distinct father IDs with active memories
        List<UUID> fatherIds = memoryRepository.findDistinctFatherIdsByStateIn(ACTIVE_STATE_ENUMS);
        log.debug("MemoryConsolidationService: Found {} fathers with active memories", fatherIds.size());

        // Process fathers in batches
        for (int i = 0; i < fatherIds.size(); i += batchSize) {
            List<UUID> batchFatherIds = fatherIds.subList(
                    i, Math.min(i + batchSize, fatherIds.size()));

            for (UUID fatherId : batchFatherIds) {
                try {
                    FatherConsolidationResult fatherResult = 
                            identifyConsolidationCandidatesForFather(fatherId, jobStartTime);
                    
                    fathersProcessed++;
                    memoriesAnalyzed += fatherResult.memoriesAnalyzed();
                    candidateGroupsFound += fatherResult.candidateGroups().size();
                    totalCandidateMemories += fatherResult.candidateGroups().stream()
                            .mapToInt(g -> g.memoryIds().size())
                            .sum();
                    allCandidateGroups.addAll(fatherResult.candidateGroups());

                } catch (Exception e) {
                    errors++;
                    log.error("MemoryConsolidationService: Error processing father {}. error={}",
                            fatherId, e.getMessage(), e);
                }
            }
        }

        log.debug("MemoryConsolidationService: Consolidation candidate identification complete. " +
                        "fathersProcessed={}, memoriesAnalyzed={}, candidateGroupsFound={}",
                fathersProcessed, memoriesAnalyzed, candidateGroupsFound);

        return new ConsolidationResult(
                fathersProcessed,
                memoriesAnalyzed,
                candidateGroupsFound,
                totalCandidateMemories,
                errors,
                allCandidateGroups
        );
    }

    /**
     * Identifies consolidation candidates for a single father.
     *
     * <p>Processes each category separately to find memories with high similarity
     * within the same category. Uses pgvector cosine similarity for efficient comparison.
     *
     * @param fatherId     the father's ID
     * @param jobStartTime the time the job started (for race condition protection)
     * @return the result containing candidate groups for this father
     */
    public FatherConsolidationResult identifyConsolidationCandidatesForFather(
            UUID fatherId, Instant jobStartTime) {
        
        log.debug("MemoryConsolidationService: Processing father {}", fatherId);

        List<ConsolidationCandidateGroup> candidateGroups = new ArrayList<>();
        int memoriesAnalyzed = 0;

        // Get all retrievable memories for this father
        List<Memory> memories = memoryRepository.findRetrievableMemories(
                fatherId, ACTIVE_STATE_ENUMS, MIN_CONFIDENCE);

        // Filter out memories that:
        // 1. Changed state since job started (race condition protection)
        // 2. Don't have embeddings (can't compute similarity)
        // 3. Are in non-consolidatable categories
        List<Memory> eligibleMemories = memories.stream()
                .filter(m -> !isMemoryModifiedSinceJobStart(m, jobStartTime))
                .filter(Memory::hasEmbedding)
                .filter(m -> !NON_CONSOLIDATABLE_CATEGORIES.contains(m.getCategory()))
                .filter(m -> !isEventWithFutureDate(m))
                .toList();

        log.debug("MemoryConsolidationService: Father {} has {} eligible memories out of {} total",
                fatherId, eligibleMemories.size(), memories.size());

        // Group memories by category
        Map<MemoryCategory, List<Memory>> memoriesByCategory = new HashMap<>();
        for (Memory memory : eligibleMemories) {
            memoriesByCategory.computeIfAbsent(memory.getCategory(), k -> new ArrayList<>())
                    .add(memory);
        }

        // For each category, find similar memory groups
        for (Map.Entry<MemoryCategory, List<Memory>> entry : memoriesByCategory.entrySet()) {
            MemoryCategory category = entry.getKey();
            List<Memory> categoryMemories = entry.getValue();
            memoriesAnalyzed += categoryMemories.size();

            if (categoryMemories.size() < 2) {
                // Need at least 2 memories to find similarities
                continue;
            }

            List<ConsolidationCandidateGroup> categoryGroups = 
                    findSimilarMemoryGroups(fatherId, category, categoryMemories);
            candidateGroups.addAll(categoryGroups);
        }

        log.debug("MemoryConsolidationService: Father {} - found {} candidate groups",
                fatherId, candidateGroups.size());

        return new FatherConsolidationResult(memoriesAnalyzed, candidateGroups);
    }

    /**
     * Finds groups of similar memories within a category using pgvector similarity.
     *
     * <p>Uses a union-find approach to group memories:
     * <ol>
     *   <li>For each memory, find all other memories with similarity >= 0.9</li>
     *   <li>Group these into consolidation candidate groups</li>
     *   <li>Avoid duplicate groups by tracking already-processed memories</li>
     * </ol>
     *
     * @param fatherId         the father's ID
     * @param category         the memory category
     * @param categoryMemories the memories in this category
     * @return list of consolidation candidate groups
     */
    private List<ConsolidationCandidateGroup> findSimilarMemoryGroups(
            UUID fatherId, MemoryCategory category, List<Memory> categoryMemories) {

        List<ConsolidationCandidateGroup> groups = new ArrayList<>();
        Set<UUID> processedMemoryIds = new HashSet<>();

        for (Memory memory : categoryMemories) {
            // Skip if already part of a group
            if (processedMemoryIds.contains(memory.getId())) {
                continue;
            }

            // Find similar memories using pgvector
            List<SimilarMemory> similarMemories = findSimilarMemoriesInCategory(
                    fatherId, category, memory);

            // Filter to only include memories meeting the consolidation threshold
            List<SimilarMemory> consolidationCandidates = similarMemories.stream()
                    .filter(sm -> sm.similarity() >= CONSOLIDATION_SIMILARITY_THRESHOLD)
                    .filter(sm -> !processedMemoryIds.contains(sm.memoryId()))
                    .filter(sm -> !sm.memoryId().equals(memory.getId()))
                    .toList();

            if (!consolidationCandidates.isEmpty()) {
                // Create a consolidation group
                List<UUID> groupMemoryIds = new ArrayList<>();
                groupMemoryIds.add(memory.getId());
                
                Map<UUID, Double> similarityScores = new HashMap<>();
                similarityScores.put(memory.getId(), 1.0); // Self-similarity
                
                for (SimilarMemory sm : consolidationCandidates) {
                    groupMemoryIds.add(sm.memoryId());
                    similarityScores.put(sm.memoryId(), sm.similarity());
                }

                // Determine the anchor (highest confidence memory)
                UUID anchorMemoryId = determineAnchorMemory(groupMemoryIds, categoryMemories);

                ConsolidationCandidateGroup group = new ConsolidationCandidateGroup(
                        fatherId,
                        category,
                        anchorMemoryId,
                        groupMemoryIds,
                        similarityScores
                );

                groups.add(group);

                // Mark all memories in this group as processed
                processedMemoryIds.addAll(groupMemoryIds);

                log.debug("MemoryConsolidationService: Found consolidation group. " +
                                "fatherId={}, category={}, anchorId={}, groupSize={}",
                        fatherId, category, anchorMemoryId, groupMemoryIds.size());
            } else {
                // Mark this memory as processed even if no group found
                processedMemoryIds.add(memory.getId());
            }
        }

        return groups;
    }

    /**
     * Finds memories similar to the given memory within the same category.
     *
     * <p>Uses pgvector's cosine similarity operator to efficiently search for
     * memories with high semantic similarity.
     *
     * @param fatherId the father's ID
     * @param category the memory category
     * @param memory   the memory to find similar memories for
     * @return list of similar memories with their similarity scores
     */
    private List<SimilarMemory> findSimilarMemoriesInCategory(
            UUID fatherId, MemoryCategory category, Memory memory) {

        if (!memory.hasEmbedding()) {
            return Collections.emptyList();
        }

        String embeddingString = formatEmbeddingForPgvector(memory.getEmbedding());

        try {
            // Query for similar memories using pgvector
            // Use the existing findBySimilarity method, but filter by category in application layer
            List<Object[]> results = memoryRepository.findBySimilarity(
                    fatherId,
                    ACTIVE_STATES,
                    MIN_CONFIDENCE,
                    embeddingString,
                    MAX_SIMILAR_CANDIDATES + 1  // +1 to account for the memory itself
            );

            return results.stream()
                    .map(row -> new SimilarMemory(
                            extractMemoryId(row),
                            extractCategory(row),
                            extractSimilarity(row)
                    ))
                    .filter(sm -> sm.category() == category)  // Filter to same category
                    .filter(sm -> sm.similarity() >= CONSOLIDATION_SIMILARITY_THRESHOLD)
                    .toList();

        } catch (Exception e) {
            log.warn("MemoryConsolidationService: Error finding similar memories. " +
                    "memoryId={}, error={}", memory.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Determines the anchor memory (the one that absorbs others).
     * The anchor is the memory with the highest confidence score.
     * If confidence is equal, the older memory (earlier createdAt) is preferred.
     *
     * @param memoryIds        the IDs of memories in the group
     * @param categoryMemories all memories in the category (for lookup)
     * @return the ID of the anchor memory
     */
    private UUID determineAnchorMemory(List<UUID> memoryIds, List<Memory> categoryMemories) {
        Map<UUID, Memory> memoryMap = new HashMap<>();
        for (Memory m : categoryMemories) {
            memoryMap.put(m.getId(), m);
        }

        return memoryIds.stream()
                .map(memoryMap::get)
                .filter(Objects::nonNull)
                .max(Comparator
                        .comparing(Memory::getConfidenceScore)
                        .thenComparing(Comparator.comparing(Memory::getCreatedAt).reversed()))
                .map(Memory::getId)
                .orElse(memoryIds.get(0));
    }

    /**
     * Checks if a memory was modified after the job started (race condition protection).
     */
    private boolean isMemoryModifiedSinceJobStart(Memory memory, Instant jobStartTime) {
        return memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime);
    }

    /**
     * Checks if a memory is an EVENT with a future date (should not be consolidated).
     */
    private boolean isEventWithFutureDate(Memory memory) {
        if (memory.getCategory() != MemoryCategory.EVENT) {
            return false;
        }
        if (memory.getEventDate() == null) {
            return false;
        }
        return memory.getEventDate().isAfter(java.time.LocalDate.now());
    }

    /**
     * Formats a float array embedding into pgvector's string format.
     */
    private String formatEmbeddingForPgvector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Extracts the memory ID from the native query result row.
     */
    private UUID extractMemoryId(Object[] row) {
        Object idValue = row[0];
        if (idValue instanceof UUID) {
            return (UUID) idValue;
        }
        if (idValue instanceof String) {
            return UUID.fromString((String) idValue);
        }
        throw new IllegalStateException("Cannot extract memory ID from query result: " + idValue);
    }

    /**
     * Extracts the category from the native query result row.
     */
    private MemoryCategory extractCategory(Object[] row) {
        // Category is typically at index 3 in the memories table
        // But this depends on the query. We need to find the right index.
        // Looking at the native query: SELECT m.*, ...
        // The category column is after father_id (index 1), child_id (index 2)
        // Let's find it safely
        for (Object value : row) {
            if (value instanceof String) {
                try {
                    return MemoryCategory.valueOf((String) value);
                } catch (IllegalArgumentException ignored) {
                    // Not a category value
                }
            }
        }
        // Fallback: return null and filter out in caller
        return null;
    }

    /**
     * Extracts the cosine similarity score from the native query result row.
     */
    private double extractSimilarity(Object[] row) {
        Object similarityValue = row[row.length - 1];
        if (similarityValue instanceof Number) {
            return ((Number) similarityValue).doubleValue();
        }
        throw new IllegalStateException("Cannot extract similarity from query result: " + similarityValue);
    }

    // ─── Merge Consolidation Candidates ────────────────────────────────────

    /**
     * Merges consolidation candidates identified by {@link #identifyConsolidationCandidates}.
     *
     * <p>For each candidate group:
     * <ol>
     *   <li>The anchor memory (highest confidence) absorbs other memories</li>
     *   <li>Absorbed memories transition to SUPERSEDED state</li>
     *   <li>superseded_by field links absorbed memories to the anchor</li>
     * </ol>
     *
     * <p>From SPEC-004 Requirement 8:
     * <ul>
     *   <li>Merging preserves the anchor's content and scores</li>
     *   <li>Absorbed memories are not deleted, just superseded</li>
     *   <li>Race condition protection: skips memories that changed state during processing</li>
     * </ul>
     *
     * @param candidateGroups the groups of similar memories to merge
     * @return the merge result containing counts of merged memories
     */
    public MergeResult mergeConsolidationCandidates(List<ConsolidationCandidateGroup> candidateGroups) {
        log.info("MemoryConsolidationService: Starting merge of {} candidate groups", candidateGroups.size());

        int groupsProcessed = 0;
        int memoriesMerged = 0;
        int errors = 0;
        List<MergedGroup> mergedGroups = new ArrayList<>();

        for (ConsolidationCandidateGroup group : candidateGroups) {
            try {
                MergedGroup mergedGroup = mergeGroup(group);
                if (mergedGroup != null) {
                    groupsProcessed++;
                    memoriesMerged += mergedGroup.memoriesAbsorbed();
                    mergedGroups.add(mergedGroup);
                }
            } catch (Exception e) {
                errors++;
                log.error("MemoryConsolidationService: Error merging group. " +
                                "fatherId={}, anchorId={}, error={}",
                        group.fatherId(), group.anchorMemoryId(), e.getMessage(), e);
            }
        }

        log.info("MemoryConsolidationService: Merge complete. " +
                        "groupsProcessed={}, memoriesMerged={}, errors={}",
                groupsProcessed, memoriesMerged, errors);

        return new MergeResult(groupsProcessed, memoriesMerged, errors, mergedGroups);
    }

    /**
     * Merges a single consolidation candidate group.
     *
     * <p>The anchor memory (highest confidence) absorbs all other memories in the group.
     * Absorbed memories are transitioned to SUPERSEDED state with a link to the anchor.
     *
     * @param group the candidate group to merge
     * @return the merge result for this group, or null if merge was skipped
     */
    private MergedGroup mergeGroup(ConsolidationCandidateGroup group) {
        UUID anchorId = group.anchorMemoryId();
        List<UUID> memoryIds = group.memoryIds();

        // Fetch the anchor memory
        Memory anchorMemory = memoryRepository.findById(anchorId).orElse(null);
        if (anchorMemory == null) {
            log.warn("MemoryConsolidationService: Anchor memory not found. anchorId={}", anchorId);
            return null;
        }

        // Verify anchor is still in a valid state for consolidation
        if (!anchorMemory.isRetrievable()) {
            log.debug("MemoryConsolidationService: Anchor memory no longer retrievable. " +
                    "anchorId={}, state={}", anchorId, anchorMemory.getState());
            return null;
        }

        int memoriesAbsorbed = 0;
        List<UUID> absorbedMemoryIds = new ArrayList<>();

        // Process each memory in the group (except the anchor)
        for (UUID memoryId : memoryIds) {
            if (memoryId.equals(anchorId)) {
                continue; // Skip the anchor itself
            }

            try {
                boolean absorbed = absorbMemory(memoryId, anchorId);
                if (absorbed) {
                    memoriesAbsorbed++;
                    absorbedMemoryIds.add(memoryId);
                }
            } catch (Exception e) {
                log.warn("MemoryConsolidationService: Error absorbing memory. " +
                                "memoryId={}, anchorId={}, error={}",
                        memoryId, anchorId, e.getMessage());
            }
        }

        if (memoriesAbsorbed > 0) {
            log.debug("MemoryConsolidationService: Merged group. " +
                            "fatherId={}, category={}, anchorId={}, memoriesAbsorbed={}",
                    group.fatherId(), group.category(), anchorId, memoriesAbsorbed);

            return new MergedGroup(
                    group.fatherId(),
                    group.category(),
                    anchorId,
                    memoriesAbsorbed,
                    absorbedMemoryIds
            );
        }

        return null;
    }

    /**
     * Absorbs a memory into the anchor memory by transitioning it to SUPERSEDED state.
     *
     * <p>Per SPEC-004 Requirement 2 (Memory Lifecycle States):
     * <ul>
     *   <li>ACTIVE/CONFIRMED → SUPERSEDED when absorbed by higher confidence memory</li>
     *   <li>Sets superseded_by to point to the anchor memory</li>
     * </ul>
     *
     * @param memoryId the ID of the memory to absorb
     * @param anchorId the ID of the anchor memory that absorbs this one
     * @return true if the memory was successfully absorbed, false if skipped
     */
    private boolean absorbMemory(UUID memoryId, UUID anchorId) {
        Memory memory = memoryRepository.findById(memoryId).orElse(null);
        if (memory == null) {
            log.debug("MemoryConsolidationService: Memory not found for absorption. memoryId={}", memoryId);
            return false;
        }

        // Verify memory is still in a valid state for absorption
        if (!memory.isRetrievable()) {
            log.debug("MemoryConsolidationService: Memory no longer retrievable. " +
                    "memoryId={}, state={}", memoryId, memory.getState());
            return false;
        }

        // Check if state transition is valid
        if (!memory.getState().canTransitionTo(MemoryState.SUPERSEDED)) {
            log.debug("MemoryConsolidationService: Invalid state transition. " +
                    "memoryId={}, currentState={}", memoryId, memory.getState());
            return false;
        }

        // Transition to SUPERSEDED and link to anchor
        memory.markSuperseded(anchorId);
        memoryRepository.save(memory);

        log.trace("MemoryConsolidationService: Memory absorbed. " +
                "memoryId={}, anchorId={}", memoryId, anchorId);

        return true;
    }

    // ─── Weekly/Monthly Summary Creation ─────────────────────────────────────

    /**
     * Creates weekly and monthly consolidation summaries for all fathers.
     *
     * <p>From SPEC-004 Requirement 14 (Conversation Summaries and Long-term Summaries):
     * <ul>
     *   <li>Weekly Consolidation Summary: consolidates CONVERSATION_SUMMARY memories older than 30 days</li>
     *   <li>Monthly Consolidation Summary: consolidates Weekly Consolidation Summaries older than 60 days</li>
     * </ul>
     *
     * @param jobStartTime the time the consolidation job started (for race condition protection)
     * @return the summary creation result
     */
    public SummaryCreationResult createSummaryMemories(Instant jobStartTime) {
        log.info("MemoryConsolidationService: Creating weekly/monthly summary memories");

        int fathersProcessed = 0;
        int weeklySummariesCreated = 0;
        int monthlySummariesCreated = 0;
        int memoriesArchived = 0;
        int errors = 0;
        List<CreatedSummary> createdSummaries = new ArrayList<>();

        // Get all distinct father IDs with active memories
        List<UUID> fatherIds = memoryRepository.findDistinctFatherIdsByStateIn(ACTIVE_STATE_ENUMS);

        for (int i = 0; i < fatherIds.size(); i += batchSize) {
            List<UUID> batchFatherIds = fatherIds.subList(
                    i, Math.min(i + batchSize, fatherIds.size()));

            for (UUID fatherId : batchFatherIds) {
                try {
                    FatherSummaryResult fatherResult = createSummaryMemoriesForFather(fatherId, jobStartTime);
                    
                    fathersProcessed++;
                    weeklySummariesCreated += fatherResult.weeklySummariesCreated();
                    monthlySummariesCreated += fatherResult.monthlySummariesCreated();
                    memoriesArchived += fatherResult.memoriesArchived();
                    createdSummaries.addAll(fatherResult.createdSummaries());

                } catch (Exception e) {
                    errors++;
                    log.error("MemoryConsolidationService: Error creating summaries for father {}. error={}",
                            fatherId, e.getMessage(), e);
                }
            }
        }

        log.info("MemoryConsolidationService: Summary creation complete. " +
                        "fathersProcessed={}, weeklySummariesCreated={}, monthlySummariesCreated={}, " +
                        "memoriesArchived={}, errors={}",
                fathersProcessed, weeklySummariesCreated, monthlySummariesCreated, memoriesArchived, errors);

        return new SummaryCreationResult(
                fathersProcessed,
                weeklySummariesCreated,
                monthlySummariesCreated,
                memoriesArchived,
                errors,
                createdSummaries
        );
    }

    /**
     * Creates weekly and monthly consolidation summaries for a specific father.
     *
     * <p>Process:
     * <ol>
     *   <li>Find conversation summaries older than 30 days → create weekly summaries</li>
     *   <li>Find weekly summaries older than 60 days → create monthly summaries</li>
     *   <li>Archive source memories after creating summaries</li>
     *   <li>Enforce limits: max 4 weekly, max 6 monthly summaries</li>
     * </ol>
     *
     * @param fatherId     the father's ID
     * @param jobStartTime the time the job started (for race condition protection)
     * @return the summary creation result for this father
     */
    @Transactional
    public FatherSummaryResult createSummaryMemoriesForFather(UUID fatherId, Instant jobStartTime) {
        log.debug("MemoryConsolidationService: Creating summaries for father {}", fatherId);

        List<CreatedSummary> createdSummaries = new ArrayList<>();
        int weeklySummariesCreated = 0;
        int monthlySummariesCreated = 0;
        int memoriesArchived = 0;

        // Step 1: Create weekly summaries from conversation summaries older than 30 days
        WeeklyConsolidationResult weeklyResult = createWeeklySummaries(fatherId, jobStartTime);
        weeklySummariesCreated = weeklyResult.summariesCreated();
        memoriesArchived += weeklyResult.memoriesArchived();
        createdSummaries.addAll(weeklyResult.createdSummaries());

        // Step 2: Create monthly summaries from weekly summaries older than 60 days
        MonthlyConsolidationResult monthlyResult = createMonthlySummaries(fatherId, jobStartTime);
        monthlySummariesCreated = monthlyResult.summariesCreated();
        memoriesArchived += monthlyResult.memoriesArchived();
        createdSummaries.addAll(monthlyResult.createdSummaries());

        // Step 3: Enforce summary limits (max 4 weekly, max 6 monthly)
        int archivedFromLimits = enforceSummaryLimits(fatherId);
        memoriesArchived += archivedFromLimits;

        log.debug("MemoryConsolidationService: Father {} - weeklySummaries={}, monthlySummaries={}, archived={}",
                fatherId, weeklySummariesCreated, monthlySummariesCreated, memoriesArchived);

        return new FatherSummaryResult(
                weeklySummariesCreated,
                monthlySummariesCreated,
                memoriesArchived,
                createdSummaries
        );
    }

    /**
     * Creates weekly consolidation summaries from conversation summaries older than 30 days.
     *
     * <p>From SPEC-004 Requirement 14 criteria 3:
     * <ul>
     *   <li>Groups conversation summaries by calendar week (Monday-Sunday)</li>
     *   <li>Creates one weekly summary per week with key themes and patterns</li>
     *   <li>Archives the source conversation summaries</li>
     * </ul>
     *
     * @param fatherId     the father's ID
     * @param jobStartTime the time the job started
     * @return the weekly consolidation result
     */
    private WeeklyConsolidationResult createWeeklySummaries(UUID fatherId, Instant jobStartTime) {
        Instant ageThreshold = jobStartTime.minus(WEEKLY_CONSOLIDATION_AGE_DAYS, ChronoUnit.DAYS);

        // Find conversation summaries eligible for weekly consolidation
        List<Memory> conversationSummaries = memoryRepository.findConversationSummariesForConsolidation(
                fatherId,
                MemoryCategory.CONVERSATION_SUMMARY,
                ACTIVE_STATE_ENUMS,
                ageThreshold
        );

        // Filter out existing weekly/monthly summaries and memories modified since job start
        List<Memory> eligibleSummaries = conversationSummaries.stream()
                .filter(m -> !isWeeklySummary(m) && !isMonthlySummary(m))
                .filter(m -> !isMemoryModifiedSinceJobStart(m, jobStartTime))
                .toList();

        if (eligibleSummaries.isEmpty()) {
            return new WeeklyConsolidationResult(0, 0, Collections.emptyList());
        }

        // Group by calendar week
        Map<LocalDate, List<Memory>> memoriesByWeek = groupByCalendarWeek(eligibleSummaries);

        List<CreatedSummary> createdSummaries = new ArrayList<>();
        int summariesCreated = 0;
        int memoriesArchived = 0;

        for (Map.Entry<LocalDate, List<Memory>> entry : memoriesByWeek.entrySet()) {
            LocalDate weekStart = entry.getKey();
            List<Memory> weekMemories = entry.getValue();

            // Create weekly summary
            Memory weeklySummary = createWeeklySummaryMemory(fatherId, weekStart, weekMemories);
            if (weeklySummary != null) {
                memoryRepository.save(weeklySummary);
                summariesCreated++;
                createdSummaries.add(new CreatedSummary(
                        weeklySummary.getId(),
                        SummaryType.WEEKLY,
                        weekMemories.size(),
                        weekStart
                ));

                // Archive source memories
                for (Memory sourceMem : weekMemories) {
                    if (sourceMem.getState().canTransitionTo(MemoryState.ARCHIVED)) {
                        sourceMem.archive();
                        memoryRepository.save(sourceMem);
                        memoriesArchived++;
                    }
                }

                log.debug("MemoryConsolidationService: Created weekly summary for father {}. " +
                                "weekStart={}, sourceCount={}, summaryId={}",
                        fatherId, weekStart, weekMemories.size(), weeklySummary.getId());
            }
        }

        return new WeeklyConsolidationResult(summariesCreated, memoriesArchived, createdSummaries);
    }

    /**
     * Creates monthly consolidation summaries from weekly summaries older than 60 days.
     *
     * <p>From SPEC-004 Requirement 14 criteria 4:
     * <ul>
     *   <li>Groups weekly summaries by calendar month</li>
     *   <li>Creates one monthly summary per month with engagement trends and patterns</li>
     *   <li>Archives the source weekly summaries</li>
     * </ul>
     *
     * @param fatherId     the father's ID
     * @param jobStartTime the time the job started
     * @return the monthly consolidation result
     */
    private MonthlyConsolidationResult createMonthlySummaries(UUID fatherId, Instant jobStartTime) {
        Instant ageThreshold = jobStartTime.minus(MONTHLY_CONSOLIDATION_AGE_DAYS, ChronoUnit.DAYS);

        // Find all conversation summaries (which includes weekly summaries)
        List<Memory> allSummaries = memoryRepository.findConversationSummariesForConsolidation(
                fatherId,
                MemoryCategory.CONVERSATION_SUMMARY,
                ACTIVE_STATE_ENUMS,
                ageThreshold
        );

        // Filter to only weekly summaries that haven't been modified since job start
        List<Memory> eligibleWeeklySummaries = allSummaries.stream()
                .filter(this::isWeeklySummary)
                .filter(m -> !isMemoryModifiedSinceJobStart(m, jobStartTime))
                .toList();

        if (eligibleWeeklySummaries.isEmpty()) {
            return new MonthlyConsolidationResult(0, 0, Collections.emptyList());
        }

        // Group by calendar month
        Map<LocalDate, List<Memory>> memoriesByMonth = groupByCalendarMonth(eligibleWeeklySummaries);

        List<CreatedSummary> createdSummaries = new ArrayList<>();
        int summariesCreated = 0;
        int memoriesArchived = 0;

        for (Map.Entry<LocalDate, List<Memory>> entry : memoriesByMonth.entrySet()) {
            LocalDate monthStart = entry.getKey();
            List<Memory> monthMemories = entry.getValue();

            // Create monthly summary
            Memory monthlySummary = createMonthlySummaryMemory(fatherId, monthStart, monthMemories);
            if (monthlySummary != null) {
                memoryRepository.save(monthlySummary);
                summariesCreated++;
                createdSummaries.add(new CreatedSummary(
                        monthlySummary.getId(),
                        SummaryType.MONTHLY,
                        monthMemories.size(),
                        monthStart
                ));

                // Archive source weekly summaries
                for (Memory sourceMem : monthMemories) {
                    if (sourceMem.getState().canTransitionTo(MemoryState.ARCHIVED)) {
                        sourceMem.archive();
                        memoryRepository.save(sourceMem);
                        memoriesArchived++;
                    }
                }

                log.debug("MemoryConsolidationService: Created monthly summary for father {}. " +
                                "monthStart={}, sourceCount={}, summaryId={}",
                        fatherId, monthStart, monthMemories.size(), monthlySummary.getId());
            }
        }

        return new MonthlyConsolidationResult(summariesCreated, memoriesArchived, createdSummaries);
    }

    /**
     * Creates a weekly consolidation summary memory.
     *
     * <p>Content format (Requirement 14 criteria 3):
     * <ul>
     *   <li>Total conversations count for that week</li>
     *   <li>Key themes discussed</li>
     *   <li>Overall emotional trend</li>
     * </ul>
     *
     * @param fatherId    the father's ID
     * @param weekStart   the Monday of the week
     * @param sourceMemories the source conversation summaries
     * @return the created weekly summary memory
     */
    private Memory createWeeklySummaryMemory(UUID fatherId, LocalDate weekStart, List<Memory> sourceMemories) {
        if (sourceMemories.isEmpty()) {
            return null;
        }

        // Extract key themes from source memories
        String themes = extractKeyThemes(sourceMemories);
        
        // Build summary content
        LocalDate weekEnd = weekStart.plusDays(6);
        String content = String.format(
                "%s Week of %s to %s: %d conversations. %s",
                WEEKLY_SUMMARY_INDICATOR,
                weekStart,
                weekEnd,
                sourceMemories.size(),
                themes
        );

        // Truncate content if needed (max 500 chars)
        if (content.length() > Memory.MAX_CONTENT_LENGTH) {
            content = content.substring(0, Memory.MAX_CONTENT_LENGTH - 3) + "...";
        }

        // Find oldest source memory for created_at (preserving temporal context)
        Instant oldestCreatedAt = sourceMemories.stream()
                .map(Memory::getCreatedAt)
                .min(Instant::compareTo)
                .orElse(Instant.now());

        Memory summary = new Memory(
                fatherId,
                MemoryCategory.CONVERSATION_SUMMARY,
                MemorySubjectType.FATHER,
                content,
                WEEKLY_SUMMARY_IMPORTANCE,
                SUMMARY_CONFIDENCE,
                MemorySourceType.SYSTEM_GENERATED
        );
        summary.setCreatedAt(oldestCreatedAt);

        return summary;
    }

    /**
     * Creates a monthly consolidation summary memory.
     *
     * <p>Content format (Requirement 14 criteria 4):
     * <ul>
     *   <li>Total conversations and engagement trend for that month</li>
     *   <li>Coaching phase progress</li>
     *   <li>Key relationship insights</li>
     *   <li>Behavioral patterns observed</li>
     * </ul>
     *
     * @param fatherId     the father's ID
     * @param monthStart   the first day of the month
     * @param sourceMemories the source weekly summaries
     * @return the created monthly summary memory
     */
    private Memory createMonthlySummaryMemory(UUID fatherId, LocalDate monthStart, List<Memory> sourceMemories) {
        if (sourceMemories.isEmpty()) {
            return null;
        }

        // Extract patterns from weekly summaries
        String patterns = extractMonthlyPatterns(sourceMemories);
        
        // Build summary content
        String monthName = monthStart.getMonth().toString();
        int year = monthStart.getYear();
        String content = String.format(
                "%s %s %d: %d weekly summaries consolidated. %s",
                MONTHLY_SUMMARY_INDICATOR,
                monthName,
                year,
                sourceMemories.size(),
                patterns
        );

        // Truncate content if needed (max 500 chars)
        if (content.length() > Memory.MAX_CONTENT_LENGTH) {
            content = content.substring(0, Memory.MAX_CONTENT_LENGTH - 3) + "...";
        }

        // Find oldest source memory for created_at (preserving temporal context)
        Instant oldestCreatedAt = sourceMemories.stream()
                .map(Memory::getCreatedAt)
                .min(Instant::compareTo)
                .orElse(Instant.now());

        Memory summary = new Memory(
                fatherId,
                MemoryCategory.CONVERSATION_SUMMARY,
                MemorySubjectType.FATHER,
                content,
                MONTHLY_SUMMARY_IMPORTANCE,
                SUMMARY_CONFIDENCE,
                MemorySourceType.SYSTEM_GENERATED
        );
        summary.setCreatedAt(oldestCreatedAt);

        return summary;
    }

    /**
     * Extracts key themes from a collection of conversation summaries.
     *
     * <p>This is a simplified extraction. In a full implementation, this would use
     * AI-based summarization per Requirement 14 criteria 9.
     *
     * @param memories the source memories
     * @return a string describing key themes
     */
    private String extractKeyThemes(List<Memory> memories) {
        if (memories.isEmpty()) {
            return "No themes identified.";
        }

        // Simple extraction: take snippets from source content
        List<String> contentSnippets = memories.stream()
                .map(Memory::getContent)
                .filter(Objects::nonNull)
                .map(c -> c.length() > 50 ? c.substring(0, 50) : c)
                .limit(3)
                .toList();

        if (contentSnippets.isEmpty()) {
            return "No themes identified.";
        }

        return "Key themes: " + String.join("; ", contentSnippets);
    }

    /**
     * Extracts monthly patterns from weekly summaries.
     *
     * <p>This is a simplified extraction. In a full implementation, this would use
     * AI-based summarization per Requirement 14 criteria 9.
     *
     * @param weeklySummaries the source weekly summaries
     * @return a string describing monthly patterns
     */
    private String extractMonthlyPatterns(List<Memory> weeklySummaries) {
        if (weeklySummaries.isEmpty()) {
            return "No patterns identified.";
        }

        int totalConversations = 0;
        // Try to extract conversation counts from weekly summary content
        for (Memory summary : weeklySummaries) {
            String content = summary.getContent();
            if (content != null && content.contains("conversations")) {
                // Simple parsing - look for number before "conversations"
                try {
                    String[] parts = content.split("\\s+");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i + 1].startsWith("conversation")) {
                            totalConversations += Integer.parseInt(parts[i]);
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Fallback: count weekly summaries as proxy
                    totalConversations++;
                }
            }
        }

        if (totalConversations == 0) {
            totalConversations = weeklySummaries.size(); // Fallback
        }

        return String.format("Engagement: approximately %d total conversations across the month.", 
                totalConversations);
    }

    /**
     * Groups memories by calendar week (Monday-Sunday).
     *
     * @param memories the memories to group
     * @return map of week start date (Monday) to memories in that week
     */
    private Map<LocalDate, List<Memory>> groupByCalendarWeek(List<Memory> memories) {
        Map<LocalDate, List<Memory>> result = new HashMap<>();
        
        for (Memory memory : memories) {
            LocalDate createdDate = memory.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate weekStart = createdDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            
            result.computeIfAbsent(weekStart, k -> new ArrayList<>()).add(memory);
        }
        
        return result;
    }

    /**
     * Groups memories by calendar month.
     *
     * @param memories the memories to group
     * @return map of month start date (1st of month) to memories in that month
     */
    private Map<LocalDate, List<Memory>> groupByCalendarMonth(List<Memory> memories) {
        Map<LocalDate, List<Memory>> result = new HashMap<>();
        
        for (Memory memory : memories) {
            LocalDate createdDate = memory.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate monthStart = createdDate.withDayOfMonth(1);
            
            result.computeIfAbsent(monthStart, k -> new ArrayList<>()).add(memory);
        }
        
        return result;
    }

    /**
     * Checks if a memory is a weekly consolidation summary.
     *
     * @param memory the memory to check
     * @return true if this is a weekly summary
     */
    public boolean isWeeklySummary(Memory memory) {
        return memory.getCategory() == MemoryCategory.CONVERSATION_SUMMARY
                && memory.getContent() != null
                && memory.getContent().startsWith(WEEKLY_SUMMARY_INDICATOR);
    }

    /**
     * Checks if a memory is a monthly consolidation summary.
     *
     * @param memory the memory to check
     * @return true if this is a monthly summary
     */
    public boolean isMonthlySummary(Memory memory) {
        return memory.getCategory() == MemoryCategory.CONVERSATION_SUMMARY
                && memory.getContent() != null
                && memory.getContent().startsWith(MONTHLY_SUMMARY_INDICATOR);
    }

    /**
     * Enforces summary limits: max 4 weekly and max 6 monthly summaries per father.
     *
     * <p>From SPEC-004 Requirement 14 criteria 7:
     * Older summaries beyond the limit are transitioned to ARCHIVED.
     *
     * @param fatherId the father's ID
     * @return the number of summaries archived
     */
    private int enforceSummaryLimits(UUID fatherId) {
        int archivedCount = 0;

        // Get all active conversation summaries
        List<Memory> allSummaries = memoryRepository.findRetrievableByCategory(
                fatherId,
                MemoryCategory.CONVERSATION_SUMMARY,
                ACTIVE_STATE_ENUMS,
                new BigDecimal("0.0")
        );

        // Separate weekly and monthly summaries
        List<Memory> weeklySummaries = allSummaries.stream()
                .filter(this::isWeeklySummary)
                .sorted(Comparator.comparing(Memory::getCreatedAt).reversed())
                .collect(Collectors.toList());

        List<Memory> monthlySummaries = allSummaries.stream()
                .filter(this::isMonthlySummary)
                .sorted(Comparator.comparing(Memory::getCreatedAt).reversed())
                .collect(Collectors.toList());

        // Archive excess weekly summaries (keep newest MAX_WEEKLY_SUMMARIES)
        if (weeklySummaries.size() > MAX_WEEKLY_SUMMARIES) {
            List<Memory> toArchive = weeklySummaries.subList(MAX_WEEKLY_SUMMARIES, weeklySummaries.size());
            for (Memory summary : toArchive) {
                if (summary.getState().canTransitionTo(MemoryState.ARCHIVED)) {
                    summary.archive();
                    memoryRepository.save(summary);
                    archivedCount++;
                    log.debug("MemoryConsolidationService: Archived excess weekly summary. " +
                            "fatherId={}, summaryId={}", fatherId, summary.getId());
                }
            }
        }

        // Archive excess monthly summaries (keep newest MAX_MONTHLY_SUMMARIES)
        if (monthlySummaries.size() > MAX_MONTHLY_SUMMARIES) {
            List<Memory> toArchive = monthlySummaries.subList(MAX_MONTHLY_SUMMARIES, monthlySummaries.size());
            for (Memory summary : toArchive) {
                if (summary.getState().canTransitionTo(MemoryState.ARCHIVED)) {
                    summary.archive();
                    memoryRepository.save(summary);
                    archivedCount++;
                    log.debug("MemoryConsolidationService: Archived excess monthly summary. " +
                            "fatherId={}, summaryId={}", fatherId, summary.getId());
                }
            }
        }

        return archivedCount;
    }

    /**
     * Manually triggers summary creation for all fathers.
     *
     * @return the summary creation result
     */
    public SummaryCreationResult triggerSummaryCreation() {
        log.info("MemoryConsolidationService: Manually triggering summary creation");
        return createSummaryMemories(Instant.now());
    }

    /**
     * Manually triggers summary creation for a specific father.
     *
     * @param fatherId the father's ID
     * @return the summary creation result for that father
     */
    public FatherSummaryResult triggerSummaryCreationForFather(UUID fatherId) {
        log.info("MemoryConsolidationService: Manually triggering summary creation for father {}", fatherId);
        return createSummaryMemoriesForFather(fatherId, Instant.now());
    }

    // ─── Manual Execution (for testing/admin) ────────────────────────────────

    /**
     * Manually triggers the full consolidation workflow:
     * 1. Identify consolidation candidates
     * 2. Merge the identified candidates
     *
     * @return the consolidation result including merge results
     */
    public FullConsolidationResult triggerFullConsolidation() {
        log.info("MemoryConsolidationService: Manually triggering full consolidation");
        Instant jobStartTime = Instant.now();

        ConsolidationResult identificationResult = identifyConsolidationCandidates(jobStartTime);
        MergeResult mergeResult = mergeConsolidationCandidates(identificationResult.candidateGroups());

        return new FullConsolidationResult(identificationResult, mergeResult);
    }

    /**
     * Manually triggers the consolidation candidate identification.
     *
     * @return the consolidation result
     */
    public ConsolidationResult triggerConsolidation() {
        log.info("MemoryConsolidationService: Manually triggering consolidation");
        return identifyConsolidationCandidates(Instant.now());
    }

    /**
     * Manually triggers consolidation for a specific father.
     *
     * @param fatherId the father's ID
     * @return the consolidation result for that father
     */
    public FatherConsolidationResult triggerConsolidationForFather(UUID fatherId) {
        log.info("MemoryConsolidationService: Manually triggering consolidation for father {}", fatherId);
        return identifyConsolidationCandidatesForFather(fatherId, Instant.now());
    }

    // ─── Result Records ──────────────────────────────────────────────────────

    /**
     * Result record for the overall consolidation process.
     *
     * @param fathersProcessed        number of fathers processed
     * @param memoriesAnalyzed        total memories analyzed
     * @param candidateGroupsFound    number of consolidation candidate groups
     * @param totalCandidateMemories  total memories in all candidate groups
     * @param errors                  number of processing errors
     * @param candidateGroups         the identified candidate groups
     */
    public record ConsolidationResult(
            int fathersProcessed,
            int memoriesAnalyzed,
            int candidateGroupsFound,
            int totalCandidateMemories,
            int errors,
            List<ConsolidationCandidateGroup> candidateGroups
    ) {}

    /**
     * Result record for a single father's consolidation processing.
     *
     * @param memoriesAnalyzed number of memories analyzed
     * @param candidateGroups  the identified candidate groups
     */
    public record FatherConsolidationResult(
            int memoriesAnalyzed,
            List<ConsolidationCandidateGroup> candidateGroups
    ) {}

    /**
     * Represents a group of memories that are candidates for consolidation.
     *
     * @param fatherId         the father these memories belong to
     * @param category         the category of the memories
     * @param anchorMemoryId   the memory that should absorb the others (highest confidence)
     * @param memoryIds        all memory IDs in the group (including anchor)
     * @param similarityScores similarity scores for each memory to the group
     */
    public record ConsolidationCandidateGroup(
            UUID fatherId,
            MemoryCategory category,
            UUID anchorMemoryId,
            List<UUID> memoryIds,
            Map<UUID, Double> similarityScores
    ) {}

    /**
     * Represents a similar memory found during similarity search.
     *
     * @param memoryId   the memory ID
     * @param category   the memory category
     * @param similarity the cosine similarity score
     */
    private record SimilarMemory(UUID memoryId, MemoryCategory category, double similarity) {}

    /**
     * Result record for the merge operation.
     *
     * @param groupsProcessed  number of candidate groups processed
     * @param memoriesMerged   total number of memories absorbed (transitioned to SUPERSEDED)
     * @param errors           number of processing errors
     * @param mergedGroups     details of each merged group
     */
    public record MergeResult(
            int groupsProcessed,
            int memoriesMerged,
            int errors,
            List<MergedGroup> mergedGroups
    ) {}

    /**
     * Result record for a single merged group.
     *
     * @param fatherId           the father these memories belong to
     * @param category           the category of the memories
     * @param anchorMemoryId     the memory that absorbed the others
     * @param memoriesAbsorbed   number of memories absorbed by the anchor
     * @param absorbedMemoryIds  IDs of the absorbed memories
     */
    public record MergedGroup(
            UUID fatherId,
            MemoryCategory category,
            UUID anchorMemoryId,
            int memoriesAbsorbed,
            List<UUID> absorbedMemoryIds
    ) {}

    /**
     * Result record for the full consolidation workflow (identification + merge).
     *
     * @param identificationResult the result of identifying consolidation candidates
     * @param mergeResult          the result of merging the candidates
     */
    public record FullConsolidationResult(
            ConsolidationResult identificationResult,
            MergeResult mergeResult
    ) {}

    // ─── Summary Creation Result Records ─────────────────────────────────────

    /**
     * Type of consolidation summary.
     */
    public enum SummaryType {
        WEEKLY,
        MONTHLY
    }

    /**
     * Result record for the overall summary creation process.
     *
     * @param fathersProcessed        number of fathers processed
     * @param weeklySummariesCreated  number of weekly summaries created
     * @param monthlySummariesCreated number of monthly summaries created
     * @param memoriesArchived        number of source memories archived
     * @param errors                  number of processing errors
     * @param createdSummaries        list of created summaries
     */
    public record SummaryCreationResult(
            int fathersProcessed,
            int weeklySummariesCreated,
            int monthlySummariesCreated,
            int memoriesArchived,
            int errors,
            List<CreatedSummary> createdSummaries
    ) {}

    /**
     * Result record for a single father's summary creation.
     *
     * @param weeklySummariesCreated  number of weekly summaries created
     * @param monthlySummariesCreated number of monthly summaries created
     * @param memoriesArchived        number of source memories archived
     * @param createdSummaries        list of created summaries
     */
    public record FatherSummaryResult(
            int weeklySummariesCreated,
            int monthlySummariesCreated,
            int memoriesArchived,
            List<CreatedSummary> createdSummaries
    ) {}

    /**
     * Result record for weekly consolidation.
     *
     * @param summariesCreated number of weekly summaries created
     * @param memoriesArchived number of source memories archived
     * @param createdSummaries list of created summaries
     */
    private record WeeklyConsolidationResult(
            int summariesCreated,
            int memoriesArchived,
            List<CreatedSummary> createdSummaries
    ) {}

    /**
     * Result record for monthly consolidation.
     *
     * @param summariesCreated number of monthly summaries created
     * @param memoriesArchived number of source memories archived
     * @param createdSummaries list of created summaries
     */
    private record MonthlyConsolidationResult(
            int summariesCreated,
            int memoriesArchived,
            List<CreatedSummary> createdSummaries
    ) {}

    /**
     * Details of a created summary memory.
     *
     * @param summaryId        the ID of the created summary
     * @param type             the type of summary (WEEKLY or MONTHLY)
     * @param sourceCount      number of source memories consolidated
     * @param periodStart      the start date of the period covered
     */
    public record CreatedSummary(
            UUID summaryId,
            SummaryType type,
            int sourceCount,
            LocalDate periodStart
    ) {}
}
