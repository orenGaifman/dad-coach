package com.dadcoach.ai.provider;

import com.dadcoach.ai.AiMessage;

import java.util.List;
import java.util.Map;

/**
 * Standardized request format for AI provider calls.
 * Hides provider-specific differences (OpenAI vs Anthropic) behind a uniform contract.
 *
 * @param model          the model identifier (e.g., "gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet-20241022")
 * @param messages       the ordered list of conversation messages
 * @param temperature    sampling temperature (0.0 = deterministic, 1.0 = creative)
 * @param topP           nucleus sampling parameter
 * @param maxTokens      maximum tokens in the generated response
 * @param jsonMode       if true, request structured JSON output from the provider
 * @param metadata       optional metadata for telemetry/routing (e.g., requestId, fatherId)
 */
public record AiProviderRequest(
    String model,
    List<AiMessage> messages,
    double temperature,
    double topP,
    int maxTokens,
    boolean jsonMode,
    Map<String, String> metadata
) {

    public AiProviderRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be null or empty");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (topP < 0.0 || topP > 1.0) {
            throw new IllegalArgumentException("topP must be between 0.0 and 1.0");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be >= 1");
        }
        messages = List.copyOf(messages);
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Convenience builder for common use cases.
     */
    public static AiProviderRequest of(String model, List<AiMessage> messages, double temperature, int maxTokens) {
        return new AiProviderRequest(model, messages, temperature, 1.0, maxTokens, false, Map.of());
    }
}
