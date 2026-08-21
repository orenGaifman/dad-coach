package com.dadcoach.domain.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for message log entries.
 */
@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    /**
     * Find recent messages for a father, ordered by creation time (newest first).
     * Limited to last N messages for AI context.
     */
    @Query(value = "SELECT * FROM message_log WHERE father_id = :fatherId ORDER BY created_at DESC LIMIT :limit",
           nativeQuery = true)
    List<MessageLog> findRecentByFatherId(@Param("fatherId") Long fatherId, @Param("limit") int limit);

    /**
     * Find recent messages for a father created after a specific timestamp,
     * ordered by creation time (newest first).
     *
     * @param fatherId the father's ID
     * @param since only return messages created after this timestamp
     * @param limit maximum number of messages to return
     * @return list of messages ordered by created_at descending
     */
    @Query(value = "SELECT * FROM message_log WHERE father_id = :fatherId AND created_at > :since ORDER BY created_at DESC LIMIT :limit",
           nativeQuery = true)
    List<MessageLog> findRecentByFatherIdAndSince(
            @Param("fatherId") Long fatherId, 
            @Param("since") Instant since, 
            @Param("limit") int limit);

    /**
     * Delete old messages, keeping only the most recent N per father.
     * Used for cleanup to prevent unbounded table growth.
     */
    @Modifying
    @Query(value = """
        DELETE FROM message_log 
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM message_log 
                WHERE father_id = :fatherId 
                ORDER BY created_at DESC 
                LIMIT :keepCount
            ) AS recent
        ) AND father_id = :fatherId
        """, nativeQuery = true)
    void cleanupOldMessages(@Param("fatherId") Long fatherId, @Param("keepCount") int keepCount);
}
