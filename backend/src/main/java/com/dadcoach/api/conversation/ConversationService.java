package com.dadcoach.api.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for conversation read operations in the Father API.
 * <p>
 * Controllers delegate to this service for conversation retrieval.
 * The implementation is responsible for ownership filtering and
 * excluding system prompts from message views.
 */
public interface ConversationService {

    /**
     * Lists conversations for the given father, paginated by cursor.
     *
     * @param fatherId the father's UUID
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize the number of items per page
     * @return a page of conversations (without messages)
     */
    ConversationPage listConversations(UUID fatherId, String cursor, int pageSize);

    /**
     * Retrieves a single conversation with its messages, filtering out system prompts.
     *
     * @param conversationId the conversation UUID
     * @return the conversation with filtered messages, or empty if not found
     */
    Optional<ConversationResponseDto> getConversationWithMessages(UUID conversationId);

    /**
     * Returns the fatherId that owns the given conversation, for ownership checks.
     *
     * @param conversationId the conversation UUID
     * @return the owning father's UUID, or empty if conversation not found
     */
    Optional<UUID> getConversationOwnerId(UUID conversationId);

    /**
     * Paginated result for conversation listing.
     */
    record ConversationPage(
            List<ConversationResponseDto> items,
            String nextCursor,
            boolean hasMore
    ) {
    }
}
