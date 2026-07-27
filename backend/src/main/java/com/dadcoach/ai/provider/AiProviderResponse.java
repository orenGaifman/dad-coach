package com.dadcoach.ai.provider;

import java.time.Duration;

/**
 * Standardized response format from AI provider calls.
 * Hides provider-specific response structures behind a uniform contract.
 *
 * @param content        the generated text content
 * @param model          the model that produced the response (may differ from request if provider substituted)
 * @param provider       identifier of the provider that handled the request (e.g., "openai", "anthropic")
 * @param inputTokens    number of tokens consumed by the prompt/input
 * @param outputTokens   number of tokens in the generated response
 * @param finishReason   reason the generation stopped (e.g., "stop", "length", "content_filter")
 * @param latency        wall-clock time for the provider call
 */
public record AiProviderResponse(
    String content,
    String model,
    String provider,
    int inputTokens,
    int outputTokens,
    String finishReason,
    Duration latency
) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
