package com.dadcoach.conversation;

import com.dadcoach.conversation.entity.Conversation;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for conversation CRUD operations and state management.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Maximum 1 ACTIVE conversation per father at any time</li>
 *   <li>DIFFICULT_SITUATION preempts (closes) existing active conversation with reason PREEMPTED</li>
 *   <li>Only defined status transitions allowed: ACTIVE → COMPLETED, ACTIVE → EXPIRED, ACTIVE → ABANDONED</li>
 *   <li>Expiration windows configurable per conversation type</li>
 *   <li>Message counts tracked per direction (INBOUND/OUTBOUND)</li>
 * </ul>
 */
public interface ConversationService {

    /**
     * Finds the currently active conversation for a father.
     *
     * @param fatherId the father's UUID
     * @return the active conversation, or empty if none exists
     */
    Optional<Conversation> findActiveConversation(UUID fatherId);

    /**
     * Creates a new conversation for a father.
     * Enforces the 1-active-conversation-per-father rule.
     * If type is DIFFICULT_SITUATION and an active conversation exists,
     * preempts it (transitions to COMPLETED with reason PREEMPTED).
     *
     * @param fatherId the father's UUID
     * @param type     the conversation type (e.g., ONBOARDING, DAILY_COACHING)
     * @return the newly created conversation
     * @throws IllegalStateException if an active conversation exists and the new type is not DIFFICULT_SITUATION
     */
    Conversation createConversation(UUID fatherId, String type);

    /**
     * Transitions a conversation to COMPLETED status.
     *
     * @param conversationId the conversation UUID
     * @param reason         the completion reason (OBJECTIVE_MET, MAX_MESSAGES, PREEMPTED)
     * @return the updated conversation
     * @throws com.dadcoach.common.InvalidStateTransitionException if the conversation is not in ACTIVE status
     */
    Conversation completeConversation(UUID conversationId, String reason);

    /**
     * Transitions a conversation to EXPIRED status with reason EXPIRATION.
     *
     * @param conversationId the conversation UUID
     * @return the updated conversation
     * @throws com.dadcoach.common.InvalidStateTransitionException if the conversation is not in ACTIVE status
     */
    Conversation expireConversation(UUID conversationId);

    /**
     * Transitions a conversation to ABANDONED status with reason ABANDONED.
     *
     * @param conversationId the conversation UUID
     * @return the updated conversation
     * @throws com.dadcoach.common.InvalidStateTransitionException if the conversation is not in ACTIVE status
     */
    Conversation abandonConversation(UUID conversationId);

    /**
     * Increments the appropriate message counter based on message direction.
     * Also updates the total messageCount and lastMessageAt timestamp.
     *
     * @param conversationId the conversation UUID
     * @param direction      the message direction: "INBOUND" (father) or "OUTBOUND" (system)
     */
    void incrementMessageCount(UUID conversationId, String direction);

    /**
     * Checks whether a conversation has expired based on its expiresAt timestamp.
     *
     * @param conversation the conversation to check
     * @return true if the conversation is expired (expiresAt is non-null and in the past)
     */
    boolean isExpired(Conversation conversation);
}
