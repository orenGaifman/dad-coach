package com.dadcoach.memory.extraction;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Data Transfer Object representing an AI-generated memory recommendation.
 *
 * <p>From SPEC-004 Requirement 25 (AI Output Validation):
 * AI recommendations are treated as untrusted input. This DTO captures the
 * raw output from the AI extraction model before validation by {@link ExtractionValidator}.
 *
 * <p>This DTO contains String representations of enum fields (category, subjectType, sourceType)
 * because the AI may produce invalid enum values that need to be caught during validation.
 *
 * <p><strong>Correctness Property:</strong>
 * Memories are NEVER created directly by AI — the ExtractionValidator validates every
 * recommendation before persistence.
 *
 * @param content         the memory content text (untrusted, needs length/content validation)
 * @param category        the category as a String (must be a valid MemoryCategory enum value)
 * @param subjectType     the subject type as a String (must be a valid MemorySubjectType enum value)
 * @param sourceType      the source type as a String (must be a valid MemorySourceType enum value)
 * @param importanceScore the importance score (must be 1-10)
 * @param confidenceScore the confidence score (must be 0.0-1.0)
 * @param childId         optional child ID if the memory is about a specific child
 * @param eventDate       optional event date for EVENT category memories
 * @see ExtractionValidator
 */
public record AiMemoryRecommendation(
        String content,
        String category,
        String subjectType,
        String sourceType,
        Integer importanceScore,
        Double confidenceScore,
        UUID childId,
        LocalDate eventDate
) {

    /**
     * Builder for creating AiMemoryRecommendation instances.
     * Provides a fluent API for constructing recommendations.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String content;
        private String category;
        private String subjectType;
        private String sourceType;
        private Integer importanceScore;
        private Double confidenceScore;
        private UUID childId;
        private LocalDate eventDate;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder subjectType(String subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder importanceScore(Integer importanceScore) {
            this.importanceScore = importanceScore;
            return this;
        }

        public Builder confidenceScore(Double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder childId(UUID childId) {
            this.childId = childId;
            return this;
        }

        public Builder eventDate(LocalDate eventDate) {
            this.eventDate = eventDate;
            return this;
        }

        public AiMemoryRecommendation build() {
            return new AiMemoryRecommendation(
                    content, category, subjectType, sourceType,
                    importanceScore, confidenceScore, childId, eventDate
            );
        }
    }
}
