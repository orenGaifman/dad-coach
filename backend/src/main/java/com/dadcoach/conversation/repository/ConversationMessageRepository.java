package com.dadcoach.conversation.repository;

import com.dadcoach.conversation.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ConversationMessage entities.
 * Provides access to messages within a conversation ordered by sequence number.
 */
@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    /**
     * Find all messages for a conversation, ordered by sequence number ascending.
     */
    List<ConversationMessage> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);

    /**
     * Count total messages in a conversation.
     */
    int countByConversationId(UUID conversationId);
}
