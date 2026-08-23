package com.dadcoach.qualitytime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link QualityTime} entities.
 * 
 * Provides methods for querying Quality Time events for workflow operations,
 * scheduling, and follow-up transitions.
 * 
 * Requirements: 3.4, 6.6, 12.4
 */
@Repository
public interface QualityTimeRepository extends JpaRepository<QualityTime, UUID> {

    /**
     * Find all Quality Time events for a specific father.
     * 
     * @param fatherId the father's internal database ID
     * @return list of Quality Time events ordered by scheduled start descending
     */
    List<QualityTime> findByFatherIdOrderByScheduledStartDesc(Long fatherId);

    /**
     * Find the latest scheduled Quality Time for a father.
     * Used to show the next upcoming Quality Time on the dashboard.
     * 
     * Requirements: 6.6
     * 
     * @param fatherId the father's internal database ID
     * @return the most recent scheduled Quality Time, if any
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.fatherId = :fatherId " +
           "AND qt.status = 'SCHEDULED' " +
           "ORDER BY qt.scheduledStart ASC")
    Optional<QualityTime> findLatestScheduledForFather(@Param("fatherId") Long fatherId);

    /**
     * Find all Quality Time events scheduled between two instants.
     * Used by scheduler jobs to find events that need reminders or follow-ups.
     * 
     * Requirements: 12.4
     * 
     * @param start the start of the time range (inclusive)
     * @param end the end of the time range (exclusive)
     * @return list of Quality Time events scheduled within the range
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.scheduledStart >= :start " +
           "AND qt.scheduledStart < :end AND qt.status = 'SCHEDULED'")
    List<QualityTime> findScheduledBetween(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Find Quality Time events by status with scheduled end before a given instant.
     * Used by the follow-up transition scheduler job to detect events that have ended
     * and need follow-up questions.
     * 
     * Requirements: 6.6, 12.4
     * 
     * @param status the Quality Time status to filter by
     * @param before the instant that scheduledEnd must be before
     * @return list of Quality Time events matching the criteria
     */
    List<QualityTime> findByStatusAndScheduledEndBefore(QualityTimeStatus status, Instant before);

    /**
     * Find scheduled Quality Time events that haven't received a follow-up yet
     * and have ended before the given time.
     * Used by the follow-up transition job for idempotent processing.
     * 
     * Requirements: 12.4
     * 
     * @param before the instant that scheduledEnd must be before
     * @return list of Quality Time events needing follow-up
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.status = 'SCHEDULED' " +
           "AND qt.scheduledEnd < :before AND qt.followUpSent = false")
    List<QualityTime> findScheduledEndedAndNotFollowedUp(@Param("before") Instant before);

    /**
     * Find scheduled Quality Time events for today that haven't received reminders.
     * Used by the morning reminder scheduler job.
     * 
     * Requirements: 6.2, 6.3
     * 
     * @param dayStart the start of the day
     * @param dayEnd the end of the day
     * @return list of Quality Time events scheduled today without reminders sent
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.status = 'SCHEDULED' " +
           "AND qt.scheduledStart >= :dayStart AND qt.scheduledStart < :dayEnd " +
           "AND qt.reminderSent = false")
    List<QualityTime> findScheduledTodayWithoutReminder(
            @Param("dayStart") Instant dayStart, 
            @Param("dayEnd") Instant dayEnd);

    /**
     * Find Quality Time events by father ID and status.
     * 
     * @param fatherId the father's internal database ID
     * @param status the Quality Time status
     * @return list of matching Quality Time events
     */
    List<QualityTime> findByFatherIdAndStatus(Long fatherId, QualityTimeStatus status);

    /**
     * Count completed Quality Time events for a father.
     * Used for belt calculation and dashboard metrics.
     * 
     * @param fatherId the father's internal database ID
     * @return count of completed Quality Time events
     */
    @Query("SELECT COUNT(qt) FROM QualityTime qt WHERE qt.fatherId = :fatherId " +
           "AND qt.status = 'COMPLETED'")
    long countCompletedByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Find all scheduled Quality Time events for a father that have a Google Calendar event ID.
     * Used for syncing externally deleted calendar events (Requirement 3.7).
     * 
     * @param fatherId the father's internal database ID
     * @return list of scheduled Quality Time events with calendar event IDs
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.fatherId = :fatherId " +
           "AND qt.status = 'SCHEDULED' " +
           "AND qt.googleCalendarEventId IS NOT NULL")
    List<QualityTime> findScheduledWithCalendarEventByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Find scheduled Quality Time events starting within the given time window
     * that haven't received the pre-QT reminder yet.
     * Used by the pre-QT reminder scheduler job to transition fathers to QUALITY_TIME_REMINDER state.
     * 
     * <p>This is for the 1-hour pre-QT reminder that triggers state transition to QUALITY_TIME_REMINDER.</p>
     * 
     * @param windowStart the start of the reminder window (typically ~1 hour before QT)
     * @param windowEnd the end of the reminder window (QT start time)
     * @return list of Quality Time events approaching that need pre-QT reminders
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.status = 'SCHEDULED' " +
           "AND qt.scheduledStart >= :windowStart AND qt.scheduledStart <= :windowEnd " +
           "AND qt.preQtReminderSent = false")
    List<QualityTime> findApproachingWithoutPreQtReminder(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
