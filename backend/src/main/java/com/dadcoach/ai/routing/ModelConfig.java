package com.dadcoach.ai.routing;

/**
 * Configuration for a specific model routing entry.
 * Maps a conversation type to a specific model with tuned parameters.
 *
 * @param model       the model identifier (e.g., "gpt-4o", "gpt-4o-mini")
 * @param temperature sampling temperature (0.0 = deterministic, 1.0 = creative)
 * @param topP        nucleus sampling parameter
 * @param maxTokens   maximum tokens in the generated response
 */
public record ModelConfig(
    String model,
    double temperature,
    double topP,
    int maxTokens
) {

    public ModelConfig {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
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
    }
}
