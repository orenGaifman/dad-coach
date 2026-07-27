package com.dadcoach.ai.output;

import java.util.List;

/**
 * Structured output from memory extraction.
 * Contains memories recommended for persistence — the application layer decides
 * whether to store them.
 *
 * <p>The AI never directly mutates state; this record is purely advisory output.
 *
 * @param memories         list of extracted memory recommendations
 * @param conversationId   identifier of the source conversation
 * @param model            the model that performed the extraction
 * @param validationPassed true if the output passed schema validation
 */
public record MemoryExtractionOutput(
    List<ExtractedMemory> memories,
    String conversationId,
    String model,
    boolean validationPassed
) {
    public MemoryExtractionOutput {
        memories = memories != null ? List.copyOf(memories) : List.of();
    }

    /**
     * A single extracted memory recommendation.
     *
     * @param category        memory category (IDENTITY, RELATIONSHIP, PREFERENCE, GOAL, etc.)
     * @param content         the memory content (max 500 characters)
     * @param importanceScore importance rating (1-10)
     * @param confidenceScore confidence in accuracy (0.0-1.0)
     * @param subjectType     who/what the memory is about (e.g., "child", "father", "family")
     */
    public record ExtractedMemory(
        String category,
        String content,
        int importanceScore,
        double confidenceScore,
        String subjectType
    ) {
        public ExtractedMemory {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("category must not be null or blank");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("content must not be null or blank");
            }
            if (content.length() > 500) {
                throw new IllegalArgumentException("content must not exceed 500 characters");
            }
            if (importanceScore < 1 || importanceScore > 10) {
                throw new IllegalArgumentException("importanceScore must be between 1 and 10");
            }
            confidenceScore = Math.max(0.0, Math.min(1.0, confidenceScore));
            if (subjectType == null || subjectType.isBlank()) {
                throw new IllegalArgumentException("subjectType must not be null or blank");
            }
        }
    }
}
