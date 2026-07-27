package com.dadcoach.ai.output;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.conversation.ConversationType;

import java.util.List;
import java.util.UUID;

/**
 * Represents a completed conversation ready for memory extraction.
 *
 * @param conversationId   unique conversation identifier
 * @param fatherId         the father who participated
 * @param conversationType the type of the completed conversation
 * @param messages         the full conversation transcript
 */
public record CompletedConversation(
    UUID conversationId,
    UUID fatherId,
    ConversationType conversationType,
    List<AiMessage> messages
) {
    public CompletedConversation {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (conversationType == null) {
            throw new IllegalArgumentException("conversationType must not be null");
        }
        messages = messages != null ? List.copyOf(messages) : List.of();
    }
}
