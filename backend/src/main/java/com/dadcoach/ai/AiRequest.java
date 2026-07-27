package com.dadcoach.ai;

import java.util.List;

/**
 * Request to send to the AI provider for a completion.
 *
 * @param model               the AI model to use
 * @param systemPrompt        the system-level instruction prompt
 * @param conversationHistory the list of messages forming the conversation context
 * @param maxResponseTokens   maximum tokens allowed in the response
 * @param promptVersion       version identifier for the prompt template
 */
public record AiRequest(
    AiModel model,
    String systemPrompt,
    List<AiMessage> conversationHistory,
    int maxResponseTokens,
    String promptVersion
) {}
