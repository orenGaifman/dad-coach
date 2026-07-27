package com.dadcoach.ai;

import java.util.List;

/**
 * Utility for estimating token counts for text content.
 * Uses an approximation of ~4 characters per token for English/Spanish text,
 * which is a well-known heuristic for GPT-family models.
 */
public final class TokenEstimator {

    /**
     * Maximum context token budget for a coaching session.
     * Total context tokens (system prompt + memories + conversation history) must not exceed this.
     */
    public static final int MAX_CONTEXT_TOKENS = 2000;

    private static final double CHARS_PER_TOKEN = 4.0;

    private TokenEstimator() {
        // Utility class
    }

    /**
     * Estimate the number of tokens in a text string.
     *
     * @param text the text to estimate
     * @return estimated token count, or 0 if text is null or empty
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Estimate the total token count for a list of messages.
     *
     * @param messages the conversation messages
     * @return total estimated tokens across all messages
     */
    public static int estimateTokens(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
            .mapToInt(msg -> estimateTokens(msg.content()) + estimateTokens(msg.role()))
            .sum();
    }

    /**
     * Estimate total context tokens for a full AI request (system prompt + conversation history).
     *
     * @param systemPrompt        the system prompt text
     * @param conversationHistory the conversation messages
     * @return total estimated context tokens
     */
    public static int estimateContextTokens(String systemPrompt, List<AiMessage> conversationHistory) {
        return estimateTokens(systemPrompt) + estimateTokens(conversationHistory);
    }

    /**
     * Enforce the context token budget by trimming the oldest conversation history messages
     * until the total context fits within MAX_CONTEXT_TOKENS.
     *
     * @param systemPrompt        the system prompt (never trimmed)
     * @param conversationHistory the conversation messages (oldest trimmed first)
     * @return a trimmed list of messages that fits within the token budget
     */
    public static List<AiMessage> enforceTokenBudget(String systemPrompt, List<AiMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return List.of();
        }

        int systemTokens = estimateTokens(systemPrompt);
        int availableTokens = MAX_CONTEXT_TOKENS - systemTokens;

        if (availableTokens <= 0) {
            return List.of();
        }

        // Keep messages from newest to oldest, trimming oldest first
        int totalHistoryTokens = estimateTokens(conversationHistory);
        if (totalHistoryTokens <= availableTokens) {
            return List.copyOf(conversationHistory);
        }

        // Trim from the beginning (oldest messages) until we fit
        int startIndex = 0;
        int currentTokens = totalHistoryTokens;
        while (startIndex < conversationHistory.size() && currentTokens > availableTokens) {
            AiMessage removed = conversationHistory.get(startIndex);
            currentTokens -= (estimateTokens(removed.content()) + estimateTokens(removed.role()));
            startIndex++;
        }

        return List.copyOf(conversationHistory.subList(startIndex, conversationHistory.size()));
    }
}
