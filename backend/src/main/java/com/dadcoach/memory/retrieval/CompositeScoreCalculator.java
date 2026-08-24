package com.dadcoach.memory.retrieval;

import com.dadcoach.memory.Memory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Calculates the composite score for memory retrieval ranking.
 *
 * <p>This component implements the composite scoring formula defined in SPEC-004 Requirement 16 criteria 2.
 * The composite score determines the priority order when retrieving memories for a coaching session,
 * balancing three factors: importance, recency, and semantic relevance.
 *
 * <h3>Composite Score Formula</h3>
 * <pre>
 * composite_score = (importance/10 × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)
 * </pre>
 *
 * <p>Where:
 * <ul>
 *   <li><b>importance</b>: Memory's importance_score (1-10), normalized to 0.0-1.0 by dividing by 10</li>
 *   <li><b>recency_factor</b>: Calculated as {@code max(0, 1.0 - (days_since_last_access × 0.05))}.
 *       A memory accessed today has recency 1.0, and recency decays to 0 after 20 days</li>
 *   <li><b>relevance</b>: Cosine similarity score from pgvector (0.0-1.0), indicating semantic
 *       similarity between the query and memory embedding</li>
 * </ul>
 *
 * <h3>Score Interpretation</h3>
 * <p>The resulting composite score ranges from 0.0 to 1.0:
 * <ul>
 *   <li><b>0.85-1.0</b>: Excellent match - high importance, recently accessed, semantically relevant</li>
 *   <li><b>0.65-0.85</b>: Good match - strong in at least two of the three factors</li>
 *   <li><b>0.45-0.65</b>: Moderate match - may still be useful depending on context</li>
 *   <li><b>0.0-0.45</b>: Weak match - low priority for retrieval</li>
 * </ul>
 *
 * <h3>Weight Distribution</h3>
 * <p>The formula weights importance (50%) more heavily than recency (30%) and relevance (20%),
 * reflecting the product decision that core identity and relationship memories should remain
 * accessible even when not recently accessed or semantically similar to the current query.
 *
 * <h3>Edge Cases</h3>
 * <ul>
 *   <li>If {@code lastAccessedAt} is null (memory never accessed), {@code createdAt} is used as fallback</li>
 *   <li>Negative days since access (should not occur) results in recency factor of 1.0</li>
 *   <li>Cosine similarity values outside 0-1 range are not clamped by this calculator
 *       (assumes valid input from pgvector)</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 16.2</b> - Composite score formula for memory retrieval ranking
 *
 * @see Memory
 * @see MemoryRetriever
 */
@Component
public class CompositeScoreCalculator {

    /**
     * Weight for the importance factor in the composite score.
     */
    public static final double IMPORTANCE_WEIGHT = 0.5;

    /**
     * Weight for the recency factor in the composite score.
     */
    public static final double RECENCY_WEIGHT = 0.3;

    /**
     * Weight for the relevance (cosine similarity) factor in the composite score.
     */
    public static final double RELEVANCE_WEIGHT = 0.2;

    /**
     * Decay rate per day for the recency factor.
     * A memory loses 0.05 recency score per day since last access.
     * After 20 days (1.0 / 0.05), recency factor reaches 0.
     */
    public static final double RECENCY_DECAY_RATE_PER_DAY = 0.05;

    /**
     * Maximum importance score (used for normalization).
     */
    public static final double MAX_IMPORTANCE_SCORE = 10.0;

    private final Clock clock;

    /**
     * Creates a CompositeScoreCalculator using the system default clock.
     * This constructor is used by Spring for production.
     */
    public CompositeScoreCalculator() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a CompositeScoreCalculator with a custom clock.
     * This constructor enables time-based testing by allowing clock injection.
     *
     * @param clock the clock to use for time calculations
     */
    public CompositeScoreCalculator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Calculates the composite score for a memory based on importance, recency, and relevance.
     *
     * <p>The formula is:
     * <pre>
     * (importance/10 × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)
     * </pre>
     *
     * <p>Where recency_factor = max(0, 1.0 - (days_since_last_access × 0.05))
     *
     * @param memory           the memory entity containing importance score and access timestamps
     * @param cosineSimilarity the cosine similarity score from pgvector (0.0-1.0)
     * @return the composite score (0.0-1.0 range)
     * @throws IllegalArgumentException if memory is null
     */
    public double calculate(Memory memory, float cosineSimilarity) {
        if (memory == null) {
            throw new IllegalArgumentException("Memory cannot be null");
        }

        double importanceComponent = calculateImportanceComponent(memory.getImportanceScore());
        double recencyComponent = calculateRecencyComponent(memory);
        double relevanceComponent = calculateRelevanceComponent(cosineSimilarity);

        return importanceComponent + recencyComponent + relevanceComponent;
    }

    /**
     * Calculates the importance component of the composite score.
     *
     * <p>Formula: (importance_score / 10) × 0.5
     *
     * @param importanceScore the memory's importance score (1-10)
     * @return the weighted importance component (0.05-0.5)
     */
    double calculateImportanceComponent(int importanceScore) {
        double normalizedImportance = importanceScore / MAX_IMPORTANCE_SCORE;
        return normalizedImportance * IMPORTANCE_WEIGHT;
    }

    /**
     * Calculates the recency component of the composite score.
     *
     * <p>Formula: max(0, 1.0 - (days_since_last_access × 0.05)) × 0.3
     *
     * <p>Uses lastAccessedAt if available, falls back to createdAt if the memory
     * has never been accessed.
     *
     * @param memory the memory entity
     * @return the weighted recency component (0.0-0.3)
     */
    double calculateRecencyComponent(Memory memory) {
        double recencyFactor = calculateRecencyFactor(memory);
        return recencyFactor * RECENCY_WEIGHT;
    }

    /**
     * Calculates the raw recency factor (0.0-1.0) before applying weight.
     *
     * <p>Formula: max(0, 1.0 - (days_since_last_access × 0.05))
     *
     * @param memory the memory entity
     * @return the recency factor (0.0-1.0)
     */
    public double calculateRecencyFactor(Memory memory) {
        Instant referenceTime = getEffectiveAccessTime(memory);
        Instant now = Instant.now(clock);

        long daysSinceAccess = ChronoUnit.DAYS.between(referenceTime, now);

        // Handle edge case where reference time is in the future (should not happen normally)
        if (daysSinceAccess < 0) {
            daysSinceAccess = 0;
        }

        return Math.max(0, 1.0 - (daysSinceAccess * RECENCY_DECAY_RATE_PER_DAY));
    }

    /**
     * Calculates the relevance component of the composite score.
     *
     * <p>Formula: cosine_similarity × 0.2
     *
     * @param cosineSimilarity the cosine similarity from pgvector (0.0-1.0)
     * @return the weighted relevance component (0.0-0.2)
     */
    double calculateRelevanceComponent(float cosineSimilarity) {
        return cosineSimilarity * RELEVANCE_WEIGHT;
    }

    /**
     * Gets the effective access time for recency calculation.
     *
     * <p>Returns lastAccessedAt if available, otherwise falls back to createdAt.
     * This ensures memories that have never been accessed still have a valid
     * reference point for recency calculation.
     *
     * @param memory the memory entity
     * @return the effective timestamp for recency calculation
     */
    private Instant getEffectiveAccessTime(Memory memory) {
        if (memory.getLastAccessedAt() != null) {
            return memory.getLastAccessedAt();
        }
        return memory.getCreatedAt();
    }
}
