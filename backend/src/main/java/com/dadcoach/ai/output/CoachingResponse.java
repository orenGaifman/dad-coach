package com.dadcoach.ai.output;

import java.time.Duration;

/**
 * Structured output from a coaching response generation.
 * This is a recommendation — the application layer decides whether to deliver it.
 *
 * <p>The AI never directly mutates state; this record is purely advisory output.
 *
 * @param message          the generated coaching message in Spanish
 * @param model            the model that produced the response
 * @param provider         the provider that handled the request
 * @param inputTokens      tokens consumed by the prompt
 * @param outputTokens     tokens in the generated response
 * @param latency          wall-clock time for the AI call
 * @param fallbackUsed     true if a fallback model/provider was used
 * @param validationPassed true if the output passed schema validation
 * @param confidence       confidence score for the response quality [0.0, 1.0]
 */
public record CoachingResponse(
    String message,
    String model,
    String provider,
    int inputTokens,
    int outputTokens,
    Duration latency,
    boolean fallbackUsed,
    boolean validationPassed,
    double confidence
) {
    public CoachingResponse {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Creates a fallback coaching response using pre-written content.
     */
    public static CoachingResponse fallback(String message) {
        return new CoachingResponse(
            message, "fallback", "none", 0, 0,
            Duration.ZERO, true, true, 0.5
        );
    }
}
