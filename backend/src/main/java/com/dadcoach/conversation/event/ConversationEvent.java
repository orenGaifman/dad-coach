package com.dadcoach.conversation.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a business event emitted by the Conversation Engine.
 * Events include CONVERSATION_STARTED, CONVERSATION_COMPLETED, and CONVERSATION_EXPIRED.
 *
 * @param eventType        the type of event (e.g., CONVERSATION_STARTED)
 * @param conversationId   the conversation this event relates to
 * @param fatherId         the father involved in the conversation
 * @param conversationType the type of conversation (e.g., DAILY_COACHING)
 * @param completionReason the reason for completion/expiration (nullable for STARTED events)
 * @param timestamp        when the event occurred
 */
public record ConversationEvent(
        String eventType,
        UUID conversationId,
        UUID fatherId,
        String conversationType,
        String completionReason,
        Instant timestamp
) {

    public ConversationEvent {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId is required");
        }
        if (conversationType == null || conversationType.isBlank()) {
            throw new IllegalArgumentException("conversationType is required");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /** Event type constants */
    public static final String CONVERSATION_STARTED = "CONVERSATION_STARTED";
    public static final String CONVERSATION_COMPLETED = "CONVERSATION_COMPLETED";
    public static final String CONVERSATION_EXPIRED = "CONVERSATION_EXPIRED";
}
