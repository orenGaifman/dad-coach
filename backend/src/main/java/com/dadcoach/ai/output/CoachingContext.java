package com.dadcoach.ai.output;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.domain.conversation.ConversationType;

import java.util.List;
import java.util.UUID;

/**
 * Input context for generating a coaching response.
 * Contains all information needed to assemble the prompt, route to the model,
 * and generate a personalized coaching message.
 *
 * <p>This record is stateless — it carries all required context so the
 * IntelligenceLayer doesn't need any hidden state or session affinity.
 *
 * @param fatherId          the father's unique identifier
 * @param conversationType  the type of conversation determining model and prompt behavior
 * @param userMessage       the inbound message from the father
 * @param conversationHistory prior messages in the current conversation
 * @param systemPrompt      the assembled system prompt content
 * @param memoryContent     formatted memory block (may be null if no memories)
 * @param contextContent    formatted context block (goals, missions, phase info)
 * @param outputInstructions output format instructions for the conversation type
 * @param locale            the father's preferred language ("en" for English, "he" for Hebrew)
 */
public record CoachingContext(
    UUID fatherId,
    ConversationType conversationType,
    String userMessage,
    List<AiMessage> conversationHistory,
    String systemPrompt,
    String memoryContent,
    String contextContent,
    String outputInstructions,
    String locale
) {
    public CoachingContext {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (conversationType == null) {
            throw new IllegalArgumentException("conversationType must not be null");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be null or blank");
        }
        if (conversationHistory == null) {
            conversationHistory = List.of();
        } else {
            conversationHistory = List.copyOf(conversationHistory);
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt must not be null or blank");
        }
        if (outputInstructions == null) {
            outputInstructions = "";
        }
        if (locale == null || locale.isBlank()) {
            locale = "en";
        }
    }

    /**
     * Backward compatibility constructor without locale (defaults to English).
     */
    public CoachingContext(
            UUID fatherId,
            ConversationType conversationType,
            String userMessage,
            List<AiMessage> conversationHistory,
            String systemPrompt,
            String memoryContent,
            String contextContent,
            String outputInstructions) {
        this(fatherId, conversationType, userMessage, conversationHistory,
             systemPrompt, memoryContent, contextContent, outputInstructions, "en");
    }
}
