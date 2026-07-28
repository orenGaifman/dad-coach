package com.dadcoach.workspace.aggregation;

import java.util.List;
import java.util.UUID;

/**
 * Interface for reading conversation data from the domain layer.
 *
 * <p>This interface decouples the workspace read aggregation from the Conversation
 * domain entity and its persistence layer.</p>
 *
 * // TODO: Wire to actual implementation from conversation domain when available
 */
public interface ConversationDataService {

    /**
     * Retrieves the most recent conversations for a father, excluding system
     * prompts and AI telemetry conversations.
     *
     * @param fatherId the father's unique identifier
     * @param limit    maximum number of conversations to return (1-50)
     * @return list of recent conversation read models, ordered by lastMessageAt descending
     */
    List<ConversationReadModel> getRecentConversations(UUID fatherId, int limit);

    /**
     * Counts total conversations for a father (excluding system/telemetry).
     *
     * @param fatherId the father's unique identifier
     * @return total conversation count
     */
    int countConversationsByFatherId(UUID fatherId);
}
