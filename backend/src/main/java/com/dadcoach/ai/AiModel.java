package com.dadcoach.ai;

import com.dadcoach.conversation.ConversationType;

/**
 * AI models available for coaching conversations.
 * Model selection is based on conversation complexity:
 * - CLAUDE_SONNET for complex types (ONBOARDING, DIFFICULT_SITUATION, REFLECTION)
 * - CLAUDE_HAIKU for routine types (DAILY_COACHING, FOLLOW_UP, CELEBRATION, INACTIVITY_CHECK)
 */
public enum AiModel {
    CLAUDE_SONNET("claude-3-5-sonnet-20241022", 4096),
    CLAUDE_HAIKU("claude-3-5-haiku-20241022", 4096),
    GPT_4O("gpt-4o", 4096),
    GPT_4O_MINI("gpt-4o-mini", 4096);

    private final String modelId;
    private final int maxContextTokens;

    AiModel(String modelId, int maxContextTokens) {
        this.modelId = modelId;
        this.maxContextTokens = maxContextTokens;
    }

    public String getModelId() {
        return modelId;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    /**
     * Select the appropriate AI model based on conversation type.
     * Claude Sonnet for complex conversations requiring deeper understanding.
     * Claude Haiku for routine interactions.
     */
    public static AiModel forConversationType(ConversationType type) {
        return switch (type) {
            case ONBOARDING, DIFFICULT_SITUATION, REFLECTION -> CLAUDE_SONNET;
            case DAILY_COACHING, FOLLOW_UP, CELEBRATION, INACTIVITY_CHECK, MISSION_GENERATION -> CLAUDE_HAIKU;
        };
    }
}
