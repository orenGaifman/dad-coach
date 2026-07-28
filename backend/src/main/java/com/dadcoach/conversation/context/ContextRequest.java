package com.dadcoach.conversation.context;

import java.util.UUID;

/**
 * Value object encapsulating parameters for context assembly.
 * Carries the father ID, conversation details, and the inbound message content
 * (used to derive the topic for memory retrieval scoping).
 *
 * @param fatherId         the father's UUID (as used by the conversation system)
 * @param conversationId   the active conversation UUID
 * @param conversationType the conversation type (e.g., DAILY_COACHING, ONBOARDING)
 * @param messageContent   the inbound message text (used for topic derivation)
 * @param maxHistoryMessages maximum number of historical messages to include (default 20)
 * @param maxMemories      maximum number of ranked memories to retrieve (default 10)
 */
public record ContextRequest(
        UUID fatherId,
        UUID conversationId,
        String conversationType,
        String messageContent,
        int maxHistoryMessages,
        int maxMemories
) {

    private static final int DEFAULT_MAX_HISTORY = 20;
    private static final int DEFAULT_MAX_MEMORIES = 10;

    public ContextRequest {
        if (fatherId == null) throw new IllegalArgumentException("fatherId is required");
        if (conversationId == null) throw new IllegalArgumentException("conversationId is required");
        if (conversationType == null || conversationType.isBlank()) {
            throw new IllegalArgumentException("conversationType is required");
        }
        if (maxHistoryMessages <= 0) maxHistoryMessages = DEFAULT_MAX_HISTORY;
        if (maxMemories <= 0) maxMemories = DEFAULT_MAX_MEMORIES;
    }

    /**
     * Creates a ContextRequest with default limits.
     */
    public static ContextRequest of(UUID fatherId, UUID conversationId,
                                    String conversationType, String messageContent) {
        return new ContextRequest(fatherId, conversationId, conversationType,
                messageContent, DEFAULT_MAX_HISTORY, DEFAULT_MAX_MEMORIES);
    }
}
