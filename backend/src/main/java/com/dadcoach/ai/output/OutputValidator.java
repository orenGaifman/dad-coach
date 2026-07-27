package com.dadcoach.ai.output;

import com.dadcoach.ai.safety.SafetyClassification;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates all AI output types against their defined schemas.
 * Checks required fields, enum values, numeric ranges, string lengths,
 * and confidence scores.
 *
 * <p>Per SPEC-003 Requirement 15: every AI output is validated before delivery.
 */
@Component
public class OutputValidator {

    /** Allowed memory categories for MemoryExtractionOutput. */
    private static final Set<String> MEMORY_CATEGORIES = Set.of(
        "IDENTITY", "RELATIONSHIP", "PREFERENCE", "GOAL",
        "CHALLENGE", "MILESTONE", "CONTEXT", "CONVERSATION_SUMMARY"
    );

    /** Allowed mission categories for MissionOutput. */
    private static final Set<String> MISSION_CATEGORIES = Set.of(
        "CONNECTION", "COMMUNICATION", "DISCIPLINE", "EDUCATION",
        "HEALTH", "EMOTIONAL", "INDEPENDENCE", "FUN", "ROUTINE", "CUSTOM"
    );

    /** Allowed emotional tones for ReflectionInsightOutput. */
    private static final Set<String> EMOTIONAL_TONES = Set.of(
        "positive", "neutral", "negative"
    );

    /**
     * Validates a CoachingResponse against its schema.
     * Rules: message non-empty and ≤2000 chars, model non-null, confidence in [0,1].
     */
    public ValidationResult validate(CoachingResponse response) {
        var builder = new ValidationResult.Builder();

        if (response == null) {
            builder.addFailure("coachingResponse", "non-null", "null");
            return builder.build();
        }

        validateRequiredString(builder, "message", response.message(), 2000);
        validateRequiredNonNull(builder, "model", response.model());
        validateConfidence(builder, "confidence", response.confidence());

        return builder.build();
    }

    /**
     * Validates a MissionOutput against its schema.
     * Rules: title non-empty ≤200, description non-empty, category from allowed set,
     * difficulty 1-5, estimatedMinutes 1-120.
     */
    public ValidationResult validate(MissionOutput output) {
        var builder = new ValidationResult.Builder();

        if (output == null) {
            builder.addFailure("missionOutput", "non-null", "null");
            return builder.build();
        }

        validateRequiredString(builder, "title", output.title(), 200);
        validateRequiredString(builder, "description", output.description(), Integer.MAX_VALUE);
        validateEnum(builder, "category", output.category(), MISSION_CATEGORIES);
        validateIntRange(builder, "difficulty", output.difficulty(), 1, 5);
        validateIntRange(builder, "estimatedMinutes", output.estimatedMinutes(), 1, 120);

        return builder.build();
    }

    /**
     * Validates a MemoryExtractionOutput against its schema.
     * Rules: memories list non-null, each memory has valid content ≤500, category from allowed set,
     * importance 1-10, confidence [0,1].
     */
    public ValidationResult validate(MemoryExtractionOutput output) {
        var builder = new ValidationResult.Builder();

        if (output == null) {
            builder.addFailure("memoryExtractionOutput", "non-null", "null");
            return builder.build();
        }

        if (output.memories() == null) {
            builder.addFailure("memories", "non-null list", "null");
        } else {
            for (int i = 0; i < output.memories().size(); i++) {
                var memory = output.memories().get(i);
                String prefix = "memories[" + i + "].";

                validateRequiredString(builder, prefix + "content", memory.content(), 500);
                validateEnum(builder, prefix + "category", memory.category(), MEMORY_CATEGORIES);
                validateIntRange(builder, prefix + "importanceScore", memory.importanceScore(), 1, 10);
                validateConfidence(builder, prefix + "confidenceScore", memory.confidenceScore());
            }
        }

        return builder.build();
    }

    /**
     * Validates a SafetyClassification against its schema.
     * Rules: category from SafetyCategory enum, confidence [0,1].
     */
    public ValidationResult validate(SafetyClassification classification) {
        var builder = new ValidationResult.Builder();

        if (classification == null) {
            builder.addFailure("safetyClassification", "non-null", "null");
            return builder.build();
        }

        if (classification.category() == null) {
            builder.addFailure("category", "non-null SafetyCategory enum value", "null");
        }
        validateConfidence(builder, "confidence", classification.confidence());

        return builder.build();
    }

    /**
     * Validates an ActionRecommendation against its schema.
     * Rules: action from ActionType enum, priority 1-10, confidence [0,1].
     */
    public ValidationResult validate(ActionRecommendation recommendation) {
        var builder = new ValidationResult.Builder();

        if (recommendation == null) {
            builder.addFailure("actionRecommendation", "non-null", "null");
            return builder.build();
        }

        if (recommendation.action() == null) {
            builder.addFailure("action", "non-null ActionType enum value", "null");
        }
        validateIntRange(builder, "priority", recommendation.priority(), 1, 10);
        validateConfidence(builder, "confidence", recommendation.confidence());

        return builder.build();
    }

    /**
     * Validates a WeeklySummaryOutput against its schema.
     * Rules: summary non-null and non-blank, fatherId non-null.
     */
    public ValidationResult validate(WeeklySummaryOutput output) {
        var builder = new ValidationResult.Builder();

        if (output == null) {
            builder.addFailure("weeklySummaryOutput", "non-null", "null");
            return builder.build();
        }

        validateRequiredNonNull(builder, "fatherId", output.fatherId());
        validateRequiredString(builder, "summary", output.summary(), Integer.MAX_VALUE);

        return builder.build();
    }

    /**
     * Validates a ReflectionInsightOutput against its schema.
     * Rules: insights non-null list, emotionalTone from allowed set.
     */
    public ValidationResult validate(ReflectionInsightOutput output) {
        var builder = new ValidationResult.Builder();

        if (output == null) {
            builder.addFailure("reflectionInsightOutput", "non-null", "null");
            return builder.build();
        }

        if (output.insights() == null) {
            builder.addFailure("insights", "non-null list", "null");
        }
        validateEnum(builder, "emotionalTone", output.emotionalTone(), EMOTIONAL_TONES);

        return builder.build();
    }

    // --- Private helper methods ---

    private void validateRequiredString(ValidationResult.Builder builder, String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            builder.addFailure(field, "non-empty string", value == null ? "null" : "blank");
        } else if (value.length() > maxLength) {
            builder.addFailure(field, "length ≤ " + maxLength, "length " + value.length());
        }
    }

    private void validateRequiredNonNull(ValidationResult.Builder builder, String field, Object value) {
        if (value == null) {
            builder.addFailure(field, "non-null", "null");
        }
    }

    private void validateConfidence(ValidationResult.Builder builder, String field, double value) {
        if (value < 0.0 || value > 1.0) {
            builder.addFailure(field, "value in [0.0, 1.0]", String.valueOf(value));
        }
    }

    private void validateIntRange(ValidationResult.Builder builder, String field, int value, int min, int max) {
        if (value < min || value > max) {
            builder.addFailure(field, "value in [" + min + ", " + max + "]", String.valueOf(value));
        }
    }

    private void validateEnum(ValidationResult.Builder builder, String field, String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            builder.addFailure(field, "one of " + allowed, value == null ? "null" : "blank");
        } else if (!allowed.contains(value)) {
            builder.addFailure(field, "one of " + allowed, value);
        }
    }
}
