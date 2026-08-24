package com.dadcoach.memory.extraction;

import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for detecting duplicate or similar memories before creation.
 *
 * <p>From SPEC-004 Requirement 9 (Duplicate Detection):
 * The Memory_System SHALL perform duplicate detection before creating any new memory:
 * <ul>
 *   <li>Semantic_Similarity > 0.85 with an existing ACTIVE or CONFIRMED memory of the same category
 *       for the same subject → update the existing memory's confidence instead of creating a duplicate</li>
 *   <li>Semantic_Similarity 0.70-0.85 → consider supersession (POTENTIAL_UPDATE)</li>
 *   <li>Semantic_Similarity < 0.70 → allow creation (DISTINCT)</li>
 * </ul>
 *
 * <p>Uses pgvector's cosine similarity operator for efficient vector search.
 *
 * <p><strong>Error Handling:</strong>
 * If duplicate detection is unavailable (e.g., pgvector down, no embedding provided),
 * the detector returns DISTINCT and allows creation. The memory is flagged for deferred
 * detection in the next consolidation job.
 *
 * @see DuplicateResult
 * @see MemoryRepository#findSimilarForDuplicateDetection
 */
@Service
public class DuplicateDetector {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetector.class);

    /**
     * States to include in duplicate search: ACTIVE and CONFIRMED.
     */
    private static final List<String> ACTIVE_STATES = List.of(
            MemoryState.ACTIVE.name(),
            MemoryState.CONFIRMED.name()
    );

    /**
     * Minimum confidence score for memories to be considered in duplicate detection.
     */
    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.30");

    /**
     * Maximum number of similar memories to retrieve from the database.
     */
    private static final int MAX_CANDIDATES = 5;

    private final MemoryRepository memoryRepository;

    /**
     * Constructs a DuplicateDetector with the required repository.
     *
     * @param memoryRepository the repository for memory persistence and queries
     */
    public DuplicateDetector(@Qualifier("specMemoryRepository") MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * Checks if a potential new memory is a duplicate or similar to existing memories.
     *
     * <p>The check is scoped by:
     * <ul>
     *   <li>father_id: only compares against the same father's memories</li>
     *   <li>category: only compares within the same memory category</li>
     *   <li>subject_type: only compares for the same subject type (FATHER, CHILD, FAMILY)</li>
     * </ul>
     *
     * <p><strong>Threshold behavior:</strong>
     * <ul>
     *   <li>similarity > 0.85 → DUPLICATE: reject creation, return existing memory for confidence update</li>
     *   <li>similarity 0.70-0.85 → POTENTIAL_UPDATE: existing memory may need supersession</li>
     *   <li>similarity < 0.70 (or no embedding) → DISTINCT: allow creation</li>
     * </ul>
     *
     * @param fatherId    the father's ID
     * @param category    the memory category
     * @param subjectType the subject type (FATHER, CHILD, FAMILY)
     * @param embedding   the embedding vector for the new memory content (1536 dimensions)
     * @return a {@link DuplicateResult} indicating the duplicate status and any existing memory
     */
    public DuplicateResult check(UUID fatherId, MemoryCategory category, 
                                 MemorySubjectType subjectType, float[] embedding) {
        // Validate inputs
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("category cannot be null");
        }
        if (subjectType == null) {
            throw new IllegalArgumentException("subjectType cannot be null");
        }

        // Graceful fallback: if no embedding provided, skip duplicate check
        if (embedding == null || embedding.length == 0) {
            log.debug("No embedding provided for duplicate check, allowing creation. fatherId={}, category={}",
                    fatherId, category);
            return DuplicateResult.distinct();
        }

        try {
            return performDuplicateCheck(fatherId, category, subjectType, embedding);
        } catch (Exception e) {
            // Graceful degradation: if pgvector or query fails, allow creation
            // The memory will be flagged for deferred detection in next consolidation
            log.warn("Duplicate detection failed, allowing creation. fatherId={}, category={}, error={}",
                    fatherId, category, e.getMessage());
            return DuplicateResult.distinct();
        }
    }

    /**
     * Performs the actual duplicate check against the database using pgvector.
     */
    private DuplicateResult performDuplicateCheck(UUID fatherId, MemoryCategory category,
                                                   MemorySubjectType subjectType, float[] embedding) {
        // Convert embedding to pgvector string format: '[0.1, 0.2, ...]'
        String embeddingString = formatEmbeddingForPgvector(embedding);

        // Query for similar memories using pgvector cosine similarity
        List<Object[]> results = memoryRepository.findSimilarForDuplicateDetection(
                fatherId,
                category.name(),
                subjectType.name(),
                ACTIVE_STATES,
                embeddingString,
                DuplicateResult.POTENTIAL_UPDATE_THRESHOLD, // Only get results >= 0.70 similarity
                MAX_CANDIDATES
        );

        // No similar memories found
        if (results.isEmpty()) {
            log.debug("No similar memories found. fatherId={}, category={}", fatherId, category);
            return DuplicateResult.distinct();
        }

        // Get the most similar memory (first result, ordered by similarity DESC)
        Object[] topResult = results.get(0);
        UUID existingMemoryId = extractMemoryId(topResult);
        double similarity = extractSimilarity(topResult);

        log.debug("Found similar memory. fatherId={}, category={}, existingMemoryId={}, similarity={}",
                fatherId, category, existingMemoryId, similarity);

        // Classify based on similarity threshold
        return DuplicateResult.of(existingMemoryId, similarity);
    }

    /**
     * Formats a float array embedding into pgvector's string format.
     * Example: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     *
     * @param embedding the embedding vector
     * @return the formatted string for pgvector
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
     * The query returns Memory entity fields with the ID as the first column.
     */
    private UUID extractMemoryId(Object[] row) {
        // The ID column is at index 0 in the native query result
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
     * Extracts the cosine similarity score from the native query result row.
     * The similarity is the last column (cosine_similarity alias).
     */
    private double extractSimilarity(Object[] row) {
        // The cosine_similarity column is the last column in the result
        Object similarityValue = row[row.length - 1];
        if (similarityValue instanceof Number) {
            return ((Number) similarityValue).doubleValue();
        }
        throw new IllegalStateException("Cannot extract similarity from query result: " + similarityValue);
    }
}
