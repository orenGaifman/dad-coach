package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTimeStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of scheduling a Quality Time event.
 * 
 * Contains the created Quality Time ID, Google Calendar event ID,
 * and event details for confirmation messages.
 * 
 * Requirements: 3.3, 3.4
 */
public record ScheduleQualityTimeResult(
        /**
         * The ID of the created Quality Time record.
         */
        UUID qualityTimeId,

        /**
         * The Google Calendar event ID, if calendar integration is enabled.
         * May be null if calendar is not connected.
         */
        String calendarEventId,

        /**
         * The name of the child this Quality Time is scheduled with.
         */
        String childName,

        /**
         * The scheduled start time.
         */
        Instant startTime,

        /**
         * The scheduled end time.
         */
        Instant endTime,

        /**
         * The status of the Quality Time (should be SCHEDULED on success).
         */
        QualityTimeStatus status
) {

    /**
     * Creates a successful scheduling result.
     *
     * @param qualityTimeId   the ID of the created Quality Time
     * @param calendarEventId the Google Calendar event ID (nullable)
     * @param childName       the name of the child
     * @param startTime       the start time
     * @param endTime         the end time
     * @return a new ScheduleQualityTimeResult
     */
    public static ScheduleQualityTimeResult success(
            UUID qualityTimeId,
            String calendarEventId,
            String childName,
            Instant startTime,
            Instant endTime
    ) {
        return new ScheduleQualityTimeResult(
                qualityTimeId,
                calendarEventId,
                childName,
                startTime,
                endTime,
                QualityTimeStatus.SCHEDULED
        );
    }
}
