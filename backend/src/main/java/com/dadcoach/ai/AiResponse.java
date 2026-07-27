package com.dadcoach.ai;

/**
 * Response received from the AI provider.
 *
 * @param content       the generated text content
 * @param model         the model that produced the response
 * @param promptTokens  number of tokens used in the prompt
 * @param responseTokens number of tokens in the generated response
 * @param totalTokens   total tokens consumed (prompt + response)
 * @param finishReason  reason the generation stopped (e.g., "stop", "length")
 */
public record AiResponse(
    String content,
    AiModel model,
    int promptTokens,
    int responseTokens,
    int totalTokens,
    String finishReason
) {}
