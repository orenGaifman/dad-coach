package com.dadcoach.memory.retrieval;

/**
 * Metadata associated with a memory retrieval result.
 *
 * <p>Contains all the scoring components used to determine the ranking of a memory
 * during retrieval, as specified in SPEC-004 Requirement 16 and Requirement 19.
 *
 * <p>The retrieval metadata provides transparency into why a memory was ranked
 * at a particular position, enabling:
 * <ul>
 *   <li>Debugging and tuning of retrieval algorithms</li>
 *   <li>Informing downstream components (e.g., Context_Manager) about score breakdown</li>
 *   <li>Flagging uncertain memories (confidence between 0.3 and 0.5) per Requirement 5 criteria 8</li>
 * </ul>
 *
 * <h3>Score Components</h3>
 * <ul>
 *   <li><b>compositeScore</b>: Final ranking score (0.0-1.0) calculated as:
 *       (importance/10 × 0.5) + (recencyFactor × 0.3) + (relevanceScore × 0.2)</li>
 *   <li><b>importanceScore</b>: Memory's importance (1-10) from the Memory entity</li>
 *   <li><b>confidenceScore</b>: Memory's confidence (0.0-1.0) from the Memory entity</li>
 *   <li><b>recencyFactor</b>: Time-decay factor (0.0-1.0) based on days since last access</li>
 *   <li><b>relevanceScore</b>: Cosine similarity (0.0-1.0) between query and memory embeddings</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 16.2, 19</b> - Retrieval ranking scores and rich metadata
 *
 * @see CompositeScoreCalculator
 * @see MemoryRetriever
 */
public class RetrievalMetadata {

    /**
     * The final composite score used for ranking (0.0-1.0).
     * Calculated using the formula: (importance/10 × 0.5) + (recencyFactor × 0.3) + (relevanceScore × 0.2)
     */
    private final double compositeScore;

    /**
     * The memory's importance score (1-10).
     * Higher values indicate more critical information for coaching context.
     */
    private final int importanceScore;

    /**
     * The memory's confidence score (0.0-1.0).
     * Indicates how certain the system is about the memory's accuracy.
     */
    private final double confidenceScore;

    /**
     * The recency factor (0.0-1.0).
     * Calculated as: max(0, 1.0 - (days_since_last_access × 0.05))
     * A memory accessed today has recency 1.0, decaying to 0 after 20 days.
     */
    private final double recencyFactor;

    /**
     * The semantic relevance score (0.0-1.0).
     * Cosine similarity between the query embedding and memory embedding.
     */
    private final double relevanceScore;

    /**
     * Flag indicating if this memory has uncertain confidence (0.3 <= confidence < 0.5).
     * Per SPEC-004 Requirement 5 criteria 8, uncertain memories should be flagged
     * to inform the Context_Manager.
     */
    private final boolean uncertain;

    /**
     * Creates a new RetrievalMetadata instance with all score components.
     *
     * @param compositeScore  the final ranking score (0.0-1.0)
     * @param importanceScore the memory's importance (1-10)
     * @param confidenceScore the memory's confidence (0.0-1.0)
     * @param recencyFactor   the recency decay factor (0.0-1.0)
     * @param relevanceScore  the cosine similarity (0.0-1.0)
     */
    public RetrievalMetadata(double compositeScore, int importanceScore, double confidenceScore,
                             double recencyFactor, double relevanceScore) {
        this.compositeScore = compositeScore;
        this.importanceScore = importanceScore;
        this.confidenceScore = confidenceScore;
        this.recencyFactor = recencyFactor;
        this.relevanceScore = relevanceScore;
        // Flag as uncertain if confidence is between 0.3 (min retrievable) and 0.5
        this.uncertain = confidenceScore >= 0.3 && confidenceScore < 0.5;
    }

    /**
     * Returns the composite score used for ranking.
     *
     * @return the composite score (0.0-1.0)
     */
    public double getCompositeScore() {
        return compositeScore;
    }

    /**
     * Returns the memory's importance score.
     *
     * @return the importance score (1-10)
     */
    public int getImportanceScore() {
        return importanceScore;
    }

    /**
     * Returns the memory's confidence score.
     *
     * @return the confidence score (0.0-1.0)
     */
    public double getConfidenceScore() {
        return confidenceScore;
    }

    /**
     * Returns the recency factor.
     *
     * @return the recency factor (0.0-1.0)
     */
    public double getRecencyFactor() {
        return recencyFactor;
    }

    /**
     * Returns the semantic relevance score.
     *
     * @return the relevance score (0.0-1.0)
     */
    public double getRelevanceScore() {
        return relevanceScore;
    }

    /**
     * Returns whether this memory has uncertain confidence.
     *
     * @return true if confidence is between 0.3 and 0.5
     */
    public boolean isUncertain() {
        return uncertain;
    }

    @Override
    public String toString() {
        return "RetrievalMetadata{" +
                "compositeScore=" + String.format("%.4f", compositeScore) +
                ", importanceScore=" + importanceScore +
                ", confidenceScore=" + String.format("%.2f", confidenceScore) +
                ", recencyFactor=" + String.format("%.4f", recencyFactor) +
                ", relevanceScore=" + String.format("%.4f", relevanceScore) +
                ", uncertain=" + uncertain +
                '}';
    }
}
