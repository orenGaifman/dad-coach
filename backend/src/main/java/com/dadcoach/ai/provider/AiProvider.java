package com.dadcoach.ai.provider;

/**
 * Provider-agnostic interface for AI model interaction.
 * Each AI provider (OpenAI, Anthropic, etc.) implements this interface,
 * allowing the Model Router to select providers at runtime.
 *
 * <p>All implementations must:
 * <ul>
 *   <li>Configure a 10-second timeout per call</li>
 *   <li>Wrap calls with a Resilience4j circuit breaker (trips at 5% error over 1h)</li>
 *   <li>Translate provider-specific requests/responses to the standardized format</li>
 * </ul>
 */
public interface AiProvider {

    /**
     * Send a prompt to the AI provider and return the response.
     *
     * @param request the standardized request containing model, messages, and parameters
     * @return the standardized response with generated content and usage metrics
     * @throws AiProviderException if the call fails (timeout, rate limit, server error, etc.)
     */
    AiProviderResponse sendPrompt(AiProviderRequest request);

    /**
     * @return the unique provider identifier (e.g., "openai", "anthropic")
     */
    String getProviderName();

    /**
     * @return true if the provider supports the specified model
     */
    boolean supportsModel(String model);
}
