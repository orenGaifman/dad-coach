package com.dadcoach.memory.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmbeddingRetryEntry} entities.
 *
 * <p>Provides queries for:
 * <ul>
 *   <li>Finding entries ready for retry processing</li>
 *   <li>Looking up entries by memory ID</li>
 *   <li>Finding entries by status for monitoring</li>
 *   <li>Cleanup of completed and old failed entries</li>
 * </ul>
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * @see EmbeddingRetryEntry
 * @see EmbeddingRetryQueueService
 */
@Repository
public interface EmbeddingRetryRepository extends JpaRepository<EmbeddingRetryEntry, UUID> {

    /**
     * Find a retry entry by memory ID.
     *
     * @param memoryId the memory ID
     * @return the retry entry if found
     */
    Optional<EmbeddingRetryEntry> findByMemoryId(UUID memoryId);

    /**
     * Check if a retry entry exists for a memory.
     *
     * @param memoryId the memory ID
     * @return true if an entry exists
     */
    boolean existsByMemoryId(UUID memoryId);

    /**
     * Find entries that are ready for processing.
     * An entry is ready if status is PENDING and next_attempt_at <= now.
     *
     * @param status   the status to filter by (PENDING)
     * @param now      current time
     * @param limit    maximum entries to return
     * @return list of entries ready for processing
     */
    @Query("SELECT e FROM EmbeddingRetryEntry e " +
           "WHERE e.status = :status " +
           "AND e.nextAttemptAt <= :now " +
           "ORDER BY e.nextAttemptAt ASC")
    List<EmbeddingRetryEntry> findReadyForProcessing(
            @Param("status") EmbeddingRetryEntry.Status status,
            @Param("now") Instant now,
            @Param("limit") int limit);

    /**
     * Find entries that are ready for processing with limit.
     *
     * @param now   current time
     * @param limit maximum entries to return
     * @return list of entries ready for processing
     */
    @Query(value = "SELECT * FROM embedding_retry_queue e " +
                   "WHERE e.status = 'PENDING' " +
                   "AND e.next_attempt_at <= :now " +
                   "ORDER BY e.next_attempt_at ASC " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<EmbeddingRetryEntry> findReadyForProcessingNative(
            @Param("now") Instant now,
            @Param("limit") int limit);

    /**
     * Find entries by status.
     *
     * @param status the status to filter by
     * @return list of entries with the given status
     */
    List<EmbeddingRetryEntry> findByStatus(EmbeddingRetryEntry.Status status);

    /**
     * Count entries by status.
     *
     * @param status the status to count
     * @return count of entries
     */
    long countByStatus(EmbeddingRetryEntry.Status status);

    /**
     * Find entries that have been stuck in PROCESSING state.
     * This indicates the processor crashed or timed out.
     *
     * @param status    the PROCESSING status
     * @param threshold entries updated before this are considered stuck
     * @return list of stuck entries
     */
    @Query("SELECT e FROM EmbeddingRetryEntry e " +
           "WHERE e.status = :status " +
           "AND e.updatedAt < :threshold")
    List<EmbeddingRetryEntry> findStuckProcessing(
            @Param("status") EmbeddingRetryEntry.Status status,
            @Param("threshold") Instant threshold);

    /**
     * Delete completed entries older than the given threshold.
     * Used for cleanup of successfully processed entries.
     *
     * @param status    the COMPLETED status
     * @param threshold entries updated before this are deleted
     * @return number of deleted entries
     */
    @Modifying
    @Query("DELETE FROM EmbeddingRetryEntry e " +
           "WHERE e.status = :status " +
           "AND e.updatedAt < :threshold")
    int deleteCompletedOlderThan(
            @Param("status") EmbeddingRetryEntry.Status status,
            @Param("threshold") Instant threshold);

    /**
     * Delete permanently failed entries older than the given threshold.
     * Used for cleanup of old failed entries.
     *
     * @param status    the PERMANENTLY_FAILED status
     * @param threshold entries updated before this are deleted
     * @return number of deleted entries
     */
    @Modifying
    @Query("DELETE FROM EmbeddingRetryEntry e " +
           "WHERE e.status = :status " +
           "AND e.updatedAt < :threshold")
    int deleteFailedOlderThan(
            @Param("status") EmbeddingRetryEntry.Status status,
            @Param("threshold") Instant threshold);

    /**
     * Delete a retry entry by memory ID.
     *
     * @param memoryId the memory ID
     */
    void deleteByMemoryId(UUID memoryId);

    /**
     * Find all entries for a list of memory IDs.
     *
     * @param memoryIds list of memory IDs
     * @return list of retry entries
     */
    List<EmbeddingRetryEntry> findByMemoryIdIn(List<UUID> memoryIds);
}
