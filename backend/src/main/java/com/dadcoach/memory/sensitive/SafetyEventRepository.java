package com.dadcoach.memory.sensitive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SafetyEventRecord} entities.
 *
 * <p>From SPEC-004 design, safety events are stored separately from normal memories
 * with long retention for legal/compliance reasons.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Never mixed into normal memory retrieval</li>
 *   <li>Queryable by father_id for support use cases</li>
 *   <li>Support for review workflow queries</li>
 *   <li>Safety events are NOT deleted during GDPR erasure</li>
 * </ul>
 *
 * @see SafetyEventRecord
 * @see SafetyEventService
 */
@Repository
public interface SafetyEventRepository extends JpaRepository<SafetyEventRecord, UUID> {

    // ═══════════════════════════════════════════════════════════════════════════
    // Queries by Father
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all safety events for a father, ordered by creation time descending.
     *
     * @param fatherId the father's ID
     * @return list of safety events for the father
     */
    List<SafetyEventRecord> findByFatherIdOrderByCreatedAtDesc(UUID fatherId);

    /**
     * Find safety events for a father within a time range.
     *
     * @param fatherId  the father's ID
     * @param startTime start of the time range (inclusive)
     * @param endTime   end of the time range (exclusive)
     * @return list of safety events within the time range
     */
    @Query("SELECT s FROM SafetyEventRecord s WHERE s.fatherId = :fatherId " +
            "AND s.createdAt >= :startTime AND s.createdAt < :endTime " +
            "ORDER BY s.createdAt DESC")
    List<SafetyEventRecord> findByFatherIdAndTimeRange(
            @Param("fatherId") UUID fatherId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Find safety events for a father by event type.
     *
     * @param fatherId  the father's ID
     * @param eventType the type of safety event
     * @return list of safety events matching the criteria
     */
    List<SafetyEventRecord> findByFatherIdAndEventTypeOrderByCreatedAtDesc(
            UUID fatherId, SafetyEventType eventType);

    /**
     * Find safety events for a father by severity.
     *
     * @param fatherId the father's ID
     * @param severity the severity level
     * @return list of safety events matching the criteria
     */
    List<SafetyEventRecord> findByFatherIdAndSeverityOrderByCreatedAtDesc(
            UUID fatherId, SafetyEventSeverity severity);

    // ═══════════════════════════════════════════════════════════════════════════
    // Review Workflow Queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all safety events requiring review, ordered by severity and creation time.
     * Higher severity events appear first.
     *
     * @return list of safety events requiring review
     */
    @Query("SELECT s FROM SafetyEventRecord s WHERE s.requiresReview = true " +
            "ORDER BY s.severity DESC, s.createdAt ASC")
    List<SafetyEventRecord> findAllRequiringReview();

    /**
     * Find safety events requiring review for a specific father.
     *
     * @param fatherId the father's ID
     * @return list of pending reviews for the father
     */
    List<SafetyEventRecord> findByFatherIdAndRequiresReviewTrueOrderBySeverityDescCreatedAtAsc(UUID fatherId);

    /**
     * Find safety events requiring review at or above a severity threshold.
     *
     * @param minSeverity minimum severity level to include
     * @return list of high-priority safety events requiring review
     */
    @Query("SELECT s FROM SafetyEventRecord s WHERE s.requiresReview = true " +
            "AND s.severity >= :minSeverity ORDER BY s.severity DESC, s.createdAt ASC")
    List<SafetyEventRecord> findRequiringReviewBySeverityAtLeast(
            @Param("minSeverity") SafetyEventSeverity minSeverity);

    /**
     * Find safety events reviewed by a specific reviewer.
     *
     * @param reviewerId the reviewer's ID
     * @return list of events reviewed by the reviewer
     */
    List<SafetyEventRecord> findByReviewedByOrderByReviewedAtDesc(UUID reviewerId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Queries by Context
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find safety events linked to a specific conversation.
     *
     * @param conversationId the conversation's ID
     * @return list of safety events from that conversation
     */
    List<SafetyEventRecord> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * Find safety events linked to a specific memory.
     *
     * @param memoryId the memory's ID
     * @return list of safety events linked to that memory
     */
    List<SafetyEventRecord> findByMemoryIdOrderByCreatedAtDesc(UUID memoryId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Count Queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Count safety events for a father.
     *
     * @param fatherId the father's ID
     * @return count of safety events
     */
    long countByFatherId(UUID fatherId);

    /**
     * Count safety events requiring review.
     *
     * @return count of events pending review
     */
    long countByRequiresReviewTrue();

    /**
     * Count safety events requiring review at or above a severity threshold.
     *
     * @param minSeverity minimum severity level
     * @return count of high-priority events pending review
     */
    @Query("SELECT COUNT(s) FROM SafetyEventRecord s WHERE s.requiresReview = true " +
            "AND s.severity >= :minSeverity")
    long countRequiringReviewBySeverityAtLeast(@Param("minSeverity") SafetyEventSeverity minSeverity);

    /**
     * Count safety events by event type.
     *
     * @param eventType the type of event
     * @return count of events of that type
     */
    long countByEventType(SafetyEventType eventType);

    /**
     * Count safety events by severity.
     *
     * @param severity the severity level
     * @return count of events at that severity
     */
    long countBySeverity(SafetyEventSeverity severity);

    // ═══════════════════════════════════════════════════════════════════════════
    // Existence Queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a father has any safety events.
     *
     * @param fatherId the father's ID
     * @return true if the father has any safety events
     */
    boolean existsByFatherId(UUID fatherId);

    /**
     * Check if a father has any unreviewed safety events.
     *
     * @param fatherId the father's ID
     * @return true if the father has pending reviews
     */
    boolean existsByFatherIdAndRequiresReviewTrue(UUID fatherId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Retention/Expiration Queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all safety events that have expired (expiresAt is before the given time).
     * Used by the retention enforcement job to identify records ready for permanent deletion.
     *
     * @param expirationTime the time to check against (typically now)
     * @return list of expired safety events
     */
    @Query("SELECT s FROM SafetyEventRecord s WHERE s.expiresAt < :expirationTime ORDER BY s.expiresAt ASC")
    List<SafetyEventRecord> findExpiredBefore(@Param("expirationTime") Instant expirationTime);

    /**
     * Find expired safety events with pagination support for batch processing.
     * Used by the retention enforcement job to process in batches and avoid memory issues.
     *
     * @param expirationTime the time to check against
     * @param limit          maximum number of records to return
     * @return list of expired safety events limited to the specified count
     */
    @Query(value = "SELECT * FROM safety_event_records WHERE expires_at < :expirationTime " +
            "ORDER BY expires_at ASC LIMIT :limit", nativeQuery = true)
    List<SafetyEventRecord> findExpiredBeforeWithLimit(
            @Param("expirationTime") Instant expirationTime,
            @Param("limit") int limit);

    /**
     * Count the number of expired safety events.
     * Used for reporting and monitoring retention enforcement progress.
     *
     * @param expirationTime the time to check against
     * @return count of expired safety events
     */
    @Query("SELECT COUNT(s) FROM SafetyEventRecord s WHERE s.expiresAt < :expirationTime")
    long countExpiredBefore(@Param("expirationTime") Instant expirationTime);

    /**
     * Find safety events expiring within a given number of days.
     * Useful for generating reports or warnings about upcoming expirations.
     *
     * @param startTime the start of the window (typically now)
     * @param endTime   the end of the window
     * @return list of safety events expiring within the window
     */
    @Query("SELECT s FROM SafetyEventRecord s WHERE s.expiresAt >= :startTime AND s.expiresAt < :endTime " +
            "ORDER BY s.expiresAt ASC")
    List<SafetyEventRecord> findExpiringBetween(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Delete expired safety events by their IDs.
     * Used by the retention enforcement job for permanent deletion after retention period.
     *
     * @param ids the IDs of the safety events to delete
     */
    @Modifying
    @Query("DELETE FROM SafetyEventRecord s WHERE s.id IN :ids")
    void deleteByIdIn(@Param("ids") List<UUID> ids);
}
