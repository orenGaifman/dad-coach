package com.dadcoach.qualitytime;

import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.qualitytime.dto.UpcomingQualityTimeDto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing Quality Time events.
 * 
 * Quality Time represents scheduled time where a father spends dedicated time
 * with their child, backed by Google Calendar. This is the core engagement unit
 * of the deterministic workflow engine.
 * 
 * <p>This service handles:</p>
 * <ul>
 *   <li>Scheduling new Quality Time events (creates Google Calendar event + database record)</li>
 *   <li>Completing Quality Time (updates status, increments streak, checks belt milestone)</li>
 *   <li>Cancelling Quality Time (removes from calendar, updates status)</li>
 *   <li>Getting upcoming Quality Time for a father (dashboard display)</li>
 * </ul>
 * 
 * <p>Requirements: 3.3, 3.4</p>
 * 
 * @see QualityTime
 * @see QualityTimeStatus
 */
public interface QualityTimeService {

    /**
     * Schedules a new Quality Time event for a father with a specific child.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Re-reads Google Calendar to detect conflicts (Read Before Write principle)</li>
     *   <li>Creates a Google Calendar event with title, duration, description, reminders, and green color</li>
     *   <li>Creates a QualityTime database record with the Google Calendar event ID</li>
     * </ol>
     * 
     * <p>The Google Calendar event will include:</p>
     * <ul>
     *   <li>Title: "👨‍👧 Quality Time with [Child Name]"</li>
     *   <li>Duration: As specified (minimum 30 minutes recommended)</li>
     *   <li>Description: "Dad Coach scheduled Quality Time — enjoy your moment together!"</li>
     *   <li>Reminders: 1 hour before (popup), 15 minutes before (popup)</li>
     *   <li>Color: Green (colorId 10)</li>
     * </ul>
     * 
     * <p>Requirements: 3.3, 2.6</p>
     *
     * @param fatherId  the ID of the father scheduling the Quality Time
     * @param childId   the ID of the child the Quality Time is with
     * @param startTime the scheduled start time
     * @param duration  the duration of the Quality Time
     * @return the scheduling result containing the created Quality Time details
     * @throws IllegalArgumentException if the father or child is not found
     * @throws IllegalStateException    if there is a calendar conflict at the requested time
     * @throws QualityTimeSchedulingException if calendar event creation fails after retry
     */
    ScheduleQualityTimeResult scheduleQualityTime(
            Long fatherId,
            Long childId,
            Instant startTime,
            Duration duration
    );

    /**
     * Marks a Quality Time event as completed.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Updates the QualityTime status to COMPLETED</li>
     *   <li>Sets the completed_at timestamp</li>
     *   <li>Stores any completion notes</li>
     *   <li>Increments the father's quality_time_streak</li>
     *   <li>Updates quality_time_longest_streak if new record</li>
     *   <li>Increments total_quality_times_completed</li>
     *   <li>Recalculates and updates current_belt</li>
     * </ol>
     * 
     * <p>Requirements: 7.2, 8.5</p>
     *
     * @param qualityTimeId the ID of the Quality Time to complete
     * @param notes         optional notes about what was done during the Quality Time (nullable)
     * @return the completion result containing updated streak and belt information
     * @throws IllegalArgumentException if the Quality Time is not found
     * @throws IllegalStateException    if the Quality Time is not in SCHEDULED status
     */
    CompleteQualityTimeResult completeQualityTime(UUID qualityTimeId, String notes);

    /**
     * Cancels a scheduled Quality Time event.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Updates the QualityTime status to CANCELLED</li>
     *   <li>Deletes the corresponding Google Calendar event (if exists)</li>
     * </ol>
     * 
     * <p>Cancellation does not affect the father's streak or belt progression.</p>
     * 
     * <p>Requirements: 3.7</p>
     *
     * @param qualityTimeId the ID of the Quality Time to cancel
     * @throws IllegalArgumentException if the Quality Time is not found
     * @throws IllegalStateException    if the Quality Time is not in SCHEDULED status
     */
    void cancelQualityTime(UUID qualityTimeId);

    /**
     * Gets the next upcoming (scheduled) Quality Time for a father.
     * 
     * <p>Returns the soonest scheduled Quality Time that hasn't ended yet.
     * Used for dashboard display and reminder logic.</p>
     * 
     * <p>Requirements: 6.4, 13.1</p>
     *
     * @param fatherId the ID of the father
     * @return the upcoming Quality Time, or empty if none scheduled
     */
    Optional<UpcomingQualityTimeDto> getUpcomingQualityTime(Long fatherId);

    /**
     * Gets all upcoming (scheduled) Quality Time events for a father.
     * 
     * <p>Returns all scheduled Quality Time events ordered by start time ascending.
     * Used for showing a father's complete schedule.</p>
     * 
     * <p>Requirements: 3.4</p>
     *
     * @param fatherId the ID of the father
     * @return list of upcoming Quality Time events, may be empty
     */
    List<UpcomingQualityTimeDto> getAllUpcomingQualityTime(Long fatherId);

    /**
     * Synchronizes Quality Time events with Google Calendar to detect externally deleted events.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Load all SCHEDULED Quality Time records that have a Google Calendar event ID</li>
     *   <li>For each record, check if the calendar event still exists</li>
     *   <li>If the event no longer exists in Google Calendar, mark the Quality Time as CANCELLED</li>
     * </ol>
     * 
     * <p>This implements the sync requirement where if a father deletes an event 
     * directly in Google Calendar, the next calendar read detects this and 
     * updates the Quality Time record.</p>
     * 
     * <p>Requirements: 3.7</p>
     *
     * @param fatherId the ID of the father whose Quality Time events should be synced
     * @return the number of Quality Time events that were detected as externally deleted and marked as CANCELLED
     */
    int syncExternallyDeletedEvents(Long fatherId);
}
