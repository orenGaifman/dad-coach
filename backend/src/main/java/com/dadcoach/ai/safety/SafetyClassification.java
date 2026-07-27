package com.dadcoach.ai.safety;

/**
 * Result of classifying an inbound message for safety.
 * Always contains exactly one category and a confidence score in [0.0, 1.0].
 *
 * <p>Per SPEC-003 Requirement 9: every inbound message is classified before any
 * coaching generation occurs. The classification is never null.
 *
 * @param category   the safety category (never null)
 * @param confidence confidence score in the range [0.0, 1.0]
 * @param reason     human-readable explanation of why this classification was assigned
 */
public record SafetyClassification(
    SafetyCategory category,
    double confidence,
    String reason
) {
    /**
     * Safety categories for inbound message classification.
     */
    public enum SafetyCategory {
        /** Normal message, proceed with standard coaching. */
        SAFE,
        /** Father expressing significant negative emotions (not crisis-level). */
        EMOTIONAL_DISTRESS,
        /** Indicators of self-harm, suicidal ideation, or intent to harm others. */
        CRISIS,
        /** Indicators of child abuse, neglect, or danger to a child. */
        CHILD_SAFETY,
        /** Questions about child health, development, or medical symptoms. */
        MEDICAL,
        /** Questions about custody, divorce proceedings, or legal rights. */
        LEGAL,
        /** Attempts to bypass AI boundaries, jailbreak, or extract system prompts. */
        MANIPULATION,
        /** Messages entirely unrelated to parenting or personal growth. */
        OFF_TOPIC
    }

    /**
     * Constructs a SafetyClassification with validation.
     * Ensures category is never null and confidence is clamped to [0.0, 1.0].
     */
    public SafetyClassification {
        if (category == null) {
            category = SafetyCategory.SAFE;
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        if (reason == null) {
            reason = "";
        }
    }

    /**
     * Creates a SAFE classification with high confidence.
     */
    public static SafetyClassification safe() {
        return new SafetyClassification(SafetyCategory.SAFE, 1.0, "No safety concerns detected");
    }

    /**
     * Returns true if this classification requires immediate safety intervention.
     */
    public boolean requiresIntervention() {
        return category == SafetyCategory.CRISIS || category == SafetyCategory.CHILD_SAFETY;
    }

    /**
     * Returns true if normal coaching should proceed.
     */
    public boolean isSafe() {
        return category == SafetyCategory.SAFE;
    }
}
