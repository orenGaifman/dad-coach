package com.dadcoach.conversation.repository;

import com.dadcoach.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Conversation entities.
 * Provides methods for finding active conversations and detecting stale ones.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Find the active conversation for a given father.
     * At most one ACTIVE conversation per father is allowed.
     */
    @Query("SELECT c FROM Conversation c WHERE c.fatherId = :fatherId AND c.status = 'ACTIVE'")
    Optional<Conversation> findActiveByFatherId(@Param("fatherId") UUID fatherId);

    /**
     * Find conversations with a given status whose expiration time has passed.
     * Used by the recovery service to detect stale conversations.
     */
    List<Conversation> findByStatusAndExpiresAtBefore(String status, Instant time);
}
