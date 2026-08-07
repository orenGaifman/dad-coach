package com.dadcoach.domain.conversation;

import com.dadcoach.domain.conversation.ConversationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Conversation} entities.
 *
 * <p>Provides queries for:</p>
 * <ul>
 *   <li>Finding active conversations for a father (single-active constraint enforcement)</li>
 *   <li>Finding expired conversations (for scheduled expiration job)</li>
 *   <li>Counting active conversations (for constraint validation)</li>
 * </ul>
 *
 * <p>Indexes leveraged:</p>
 * <ul>
 *   <li>idx_conversation_father_status ON conversation(father_id, status)</li>
 *   <li>idx_conversation_expires ON conversation(expires_at) WHERE status = 'ACTIVE'</li>
 * </ul>
 */
@Repository("domainConversationRepository")
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // ─── Active Conversation Queries ─────────────────────────────────────

    /**
     * Find the active conversation for a father (there should be at most one).
     * Uses the idx_conversation_father_status index.
     *
     * @param fatherId the father ID
     * @return the active conversation, if any
     */
    @Query("SELECT c FROM DomainConversation c WHERE c.fatherId = :fatherId " +
           "AND c.status = com.dadcoach.conversation.ConversationStatus.ACTIVE")
    Optional<Conversation> findActiveByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Find all active conversations for a father (should be at most 1 due to business rule).
     * Useful for constraint validation and debugging.
     *
     * @param fatherId the father ID
     * @return list of active conversations
     */
    @Query("SELECT c FROM DomainConversation c WHERE c.fatherId = :fatherId " +
           "AND c.status = com.dadcoach.conversation.ConversationStatus.ACTIVE")
    List<Conversation> findAllActiveByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Count active conversations for a father.
     * Used to enforce the single-active-conversation-per-father business rule.
     *
     * @param fatherId the father ID
     * @return the count of active conversations (should be 0 or 1)
     */
    @Query("SELECT COUNT(c) FROM DomainConversation c WHERE c.fatherId = :fatherId " +
           "AND c.status = com.dadcoach.conversation.ConversationStatus.ACTIVE")
    long countActiveByFatherId(@Param("fatherId") Long fatherId);

    // ─── Expiration Queries ──────────────────────────────────────────────

    /**
     * Find active conversations that have passed their expiration time.
     * Used by the scheduled expiration job.
     * Uses the idx_conversation_expires partial index.
     *
     * @param now the current time
     * @return list of expired conversations
     */
    @Query("SELECT c FROM DomainConversation c WHERE c.status = com.dadcoach.conversation.ConversationStatus.ACTIVE " +
           "AND c.expiresAt < :now")
    List<Conversation> findExpired(@Param("now") Instant now);

    // ─── History Queries ─────────────────────────────────────────────────

    /**
     * Find conversations for a father ordered by creation time descending.
     *
     * @param fatherId the father ID
     * @return list of conversations ordered by most recent first
     */
    List<Conversation> findByFatherIdOrderByCreatedAtDesc(Long fatherId);

    /**
     * Find conversations for a father with a specific status.
     *
     * @param fatherId the father ID
     * @param status   the conversation status to filter by
     * @return list of matching conversations
     */
    List<Conversation> findByFatherIdAndStatus(Long fatherId, ConversationStatus status);
}
