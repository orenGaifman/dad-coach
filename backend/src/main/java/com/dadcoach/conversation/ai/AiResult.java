package com.dadcoach.conversation.ai;

import java.util.Map;

/**
 * Result of the AI orchestration sub-pipeline.
 * Always contains a deliverable response — either from AI generation, retry, or fallback.
 *
 * <p>Tracks metadata about how the response was produced for observability:
 * whether fallback was used, if a retry occurred, the suggested follow-up action,
 * and whether safety escalation triggered an immediate response.
 */
public record AiResult(
        String responseContent,
        String suggestedFollowUpAction,
        boolean fallbackUsed,
        boolean retried,
        String safetyClassification,
        Map<String, Object> metadata
) {

    public AiResult {
        if (responseContent == null || responseContent.isBlank()) {
            throw new IllegalArgumentException("responseContent is required");
        }
        if (suggestedFollowUpAction == null) {
            suggestedFollowUpAction = "NONE";
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Creates a successful AI result (first attempt passed validation).
     */
    public static AiResult success(String content, String followUpAction, Map<String, Object> metadata) {
        return new AiResult(content, followUpAction, false, false, null, metadata);
    }

    /**
     * Creates a successful AI result from a retry attempt.
     */
    public static AiResult retried(String content, String followUpAction, Map<String, Object> metadata) {
        return new AiResult(content, followUpAction, false, true, null, metadata);
    }

    /**
     * Creates a fallback AI result.
     */
    public static AiResult fallback(String content) {
        return new AiResult(content, "NONE", true, false, null, Map.of("fallback_used", true));
    }

    /**
     * Creates a safety escalation result (CRISIS or CHILD_SAFETY).
     * No coaching generation occurs — immediate pre-written response is delivered.
     */
    public static AiResult safetyEscalation(String content, String category) {
        return new AiResult(content, "NONE", false, false, category,
                Map.of("safety_escalation", true, "safety_category", category));
    }

    /**
     * Returns true if this result was produced by a safety escalation
     * (no coaching generation occurred).
     */
    public boolean isSafetyEscalation() {
        return safetyClassification != null;
    }
}
