package com.dadcoach.conversation.repository;

import com.dadcoach.conversation.entity.SideEffectOutbox;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SideEffectOutbox entities.
 * Provides methods for the background poller to find pending side-effects.
 */
@Repository
public interface SideEffectOutboxRepository extends JpaRepository<SideEffectOutbox, UUID> {

    /**
     * Find entries that are ready to be processed:
     * - Status is PENDING or FAILED (eligible for retry)
     * - next_retry_at is null (new) or in the past (retry time reached)
     * Results are limited to a configurable batch size.
     */
    @Query("SELECT s FROM SideEffectOutbox s " +
           "WHERE (s.status = 'PENDING' OR (s.status = 'FAILED' AND s.retryCount < s.maxRetries)) " +
           "AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= CURRENT_TIMESTAMP) " +
           "ORDER BY s.createdAt ASC")
    List<SideEffectOutbox> findPending(Limit limit);
}
