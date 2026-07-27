package com.dadcoach.ai;

/**
 * Abstraction for AI model interaction.
 * Supports sending completion requests and estimating token usage.
 */
public interface AiProvider {

    /**
     * Send a completion request with the given context and model.
     *
     * @param request the AI request containing model, prompt, and history
     * @return the AI response with generated content and token usage
     * @throws AiProviderUnavailableException if the AI provider is unavailable after retries
     */
    AiResponse complete(AiRequest request);

    /**
     * Estimate token count for a given text.
     * Uses approximate estimation (1 token ≈ 4 characters for English/Spanish text).
     *
     * @param text the text to estimate tokens for
     * @return estimated number of tokens
     */
    int estimateTokens(String text);
}
