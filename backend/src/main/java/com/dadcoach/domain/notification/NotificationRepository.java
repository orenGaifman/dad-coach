package com.dadcoach.domain.notification;

import com.dadcoach.notification.NotificationType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Notification} entities.
 *
 * <p>Provides queries for:</p>
 * <ul>
 *   <li>Finding due notifications (scheduled and past their time)</li>
 *   <li>Counting daily proactive notifications per father</li>
 *   <li>Finding failed notifications for retry</li>
 * </ul>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find all notifications that are due for delivery (status SCHEDULED and scheduled_for <= now).
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'SCHEDULED' AND n.scheduledFor <= :now ORDER BY n.priority ASC, n.scheduledFor ASC")
    List<Notification> findDue(@Param("now") Instant now);

    /**
     * Count proactive notifications for a father on a given day (between dayStart and dayEnd).
     * Excludes conversation replies (which are not tracked as notifications in this table).
     * Only counts notifications in SCHEDULED, DISPATCHED, or DELIVERED status.
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.father.id = :fatherId " +
            "AND n.scheduledFor >= :dayStart AND n.scheduledFor < :dayEnd " +
            "AND n.status IN ('SCHEDULED', 'DISPATCHED', 'DELIVERED')")
    int countDailyByFather(@Param("fatherId") Long fatherId,
                           @Param("dayStart") Instant dayStart,
                           @Param("dayEnd") Instant dayEnd);

    /**
     * Find all failed notifications that are eligible for retry (retry_count < maxRetries).
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries ORDER BY n.priority ASC")
    List<Notification> findFailedForRetry(@Param("maxRetries") int maxRetries);

    /**
     * Find notifications for a father scheduled at the same time (for deconfliction).
     */
    @Query("SELECT n FROM Notification n WHERE n.father.id = :fatherId " +
            "AND n.scheduledFor = :scheduledFor AND n.status = 'SCHEDULED' ORDER BY n.priority ASC")
    List<Notification> findByFatherIdAndScheduledFor(@Param("fatherId") Long fatherId,
                                                     @Param("scheduledFor") Instant scheduledFor);

    /**
     * Find all scheduled notifications for a father.
     */
    List<Notification> findByFatherIdAndStatus(Long fatherId, NotificationStatus status);
}
