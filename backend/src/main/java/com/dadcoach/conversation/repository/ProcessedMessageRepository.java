package com.dadcoach.conversation.repository;

import com.dadcoach.conversation.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for ProcessedMessage entities (idempotency tracking).
 * Provides lookup by idempotency key and cleanup of expired entries.
 */
@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    /**
     * Find a processed message by its idempotency key.
     * Used to detect duplicate messages before processing.
     */
    Optional<ProcessedMessage> findByIdempotencyKey(String idempotencyKey);

    /**
     * Delete all processed messages whose TTL has expired.
     * Called periodically to clean up stale idempotency records.
     */
    @Modifying
    @Query("DELETE FROM ProcessedMessage p WHERE p.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
