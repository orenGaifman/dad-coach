package com.dadcoach.ai;

import com.dadcoach.conversation.ConversationType;

/**
 * AI models available for coaching conversations.
 * Model selection is based on conversation complexity:
 * - GPT_4O for complex types (ONBOARDING, DIFFICULT_SITUATION, REFLECTION)
 * - GPT_4O_MINI for routine types (DAILY_COACHING, FOLLOW_UP, CELEBRATION, INACTIVITY_CHECK)
 */
public enum AiModel {
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
     * GPT-4o for complex conversations requiring deeper understanding.
     * GPT-4o-mini for routine interactions.
     */
    public static AiModel forConversationType(ConversationType type) {
        return switch (type) {
            case ONBOARDING, DIFFICULT_SITUATION, REFLECTION -> GPT_4O;
            case DAILY_COACHING, FOLLOW_UP, CELEBRATION, INACTIVITY_CHECK, MISSION_GENERATION -> GPT_4O_MINI;
        };
    }
}
