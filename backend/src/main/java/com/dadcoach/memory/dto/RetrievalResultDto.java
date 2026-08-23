package com.dadcoach.memory.dto;

import com.dadcoach.memory.retrieval.RetrievalMetadata;

/**
 * Data Transfer Object representing a memory retrieval result with rich metadata.
 *
 * <p>This DTO combines a {@link MemoryDto} with {@link RetrievalMetadata} to provide
 * a complete view of a retrieved memory including its ranking scores and components.
 *
 * <p>Per SPEC-004 Requirement 19 and the design document, retrieval results must include:
 * <ul>
 *   <li>The full memory data (via MemoryDto)</li>
 *   <li>Composite score and all scoring components (via RetrievalMetadata)</li>
 *   <li>Uncertainty flag for low-confidence memories</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <p>RetrievalResultDto instances are returned by {@code MemoryRetriever.retrieveRanked()}
 * and are sorted by descending composite score. The metadata allows consumers to:
 * <ul>
 *   <li>Understand why a memory was ranked at its position</li>
 *   <li>Handle uncertain memories appropriately (e.g., request confirmation)</li>
 *   <li>Apply additional filtering or re-ranking based on specific criteria</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 16.2, 19</b> - Rich retrieval results with metadata
 *
 * @see MemoryDto
 * @see RetrievalMetadata
 * @see com.dadcoach.memory.retrieval.MemoryRetriever
 */
public class RetrievalResultDto {

    /**
     * The memory data.
     */
    private final MemoryDto memory;

    /**
     * The retrieval metadata containing scores and ranking components.
     */
    private final RetrievalMetadata metadata;

    /**
     * Creates a new RetrievalResultDto with the specified memory and metadata.
     *
     * @param memory   the memory DTO
     * @param metadata the retrieval metadata with scores
     */
    public RetrievalResultDto(MemoryDto memory, RetrievalMetadata metadata) {
        this.memory = memory;
        this.metadata = metadata;
    }

    /**
     * Returns the memory data.
     *
     * @return the MemoryDto
     */
    public MemoryDto getMemory() {
        return memory;
    }

    /**
     * Returns the retrieval metadata.
     *
     * @return the RetrievalMetadata containing scores
     */
    public RetrievalMetadata getMetadata() {
        return metadata;
    }

    /**
     * Convenience method to get the composite score directly.
     *
     * @return the composite score from metadata
     */
    public double getCompositeScore() {
        return metadata.getCompositeScore();
    }

    /**
     * Convenience method to check if this memory is flagged as uncertain.
     *
     * @return true if the memory has uncertain confidence (0.3 <= confidence < 0.5)
     */
    public boolean isUncertain() {
        return metadata.isUncertain();
    }

    @Override
    public String toString() {
        return "RetrievalResultDto{" +
                "memoryId=" + (memory != null ? memory.getId() : "null") +
                ", category=" + (memory != null ? memory.getCategory() : "null") +
                ", compositeScore=" + String.format("%.4f", metadata.getCompositeScore()) +
                ", uncertain=" + metadata.isUncertain() +
                '}';
    }
}
