package com.dadcoach.workflow.dto;

import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a scheduled Quality Time event.
 * 
 * Returned by POST /api/v1/quality-time/schedule endpoint.
 * Contains the created Quality Time details including the Google Calendar event ID.
 * 
 * Requirements: 14.3
 */
public record ScheduleResponse(
        /**
         * The ID of the created Quality Time record.
         */
        @JsonProperty("quality_time_id")
        UUID qualityTimeId,

        /**
         * The Google Calendar event ID, if calendar integration is enabled.
         * May be null if calendar is not connected.
         */
        @JsonProperty("calendar_event_id")
        String calendarEventId,

        /**
         * The name of the child this Quality Time is scheduled with.
         */
        @JsonProperty("child_name")
        String childName,

        /**
         * The scheduled start time (ISO 8601 format).
         */
        @JsonProperty("start_time")
        Instant startTime,

        /**
         * The scheduled end time (ISO 8601 format).
         */
        @JsonProperty("end_time")
        Instant endTime,

        /**
         * The status of the Quality Time (should be SCHEDULED on success).
         */
        @JsonProperty("status")
        QualityTimeStatus status
) {

    /**
     * Creates a ScheduleResponse from a ScheduleQualityTimeResult.
     * 
     * Converts the internal service result to the external API response format.
     *
     * @param result the scheduling result from QualityTimeService
     * @return a new ScheduleResponse
     */
    public static ScheduleResponse fromResult(ScheduleQualityTimeResult result) {
        return new ScheduleResponse(
                result.qualityTimeId(),
                result.calendarEventId(),
                result.childName(),
                result.startTime(),
                result.endTime(),
                result.status()
        );
    }

    /**
     * Creates a successful scheduling response.
     *
     * @param qualityTimeId   the ID of the created Quality Time
     * @param calendarEventId the Google Calendar event ID (nullable)
     * @param childName       the name of the child
     * @param startTime       the start time
     * @param endTime         the end time
     * @return a new ScheduleResponse with SCHEDULED status
     */
    public static ScheduleResponse success(
            UUID qualityTimeId,
            String calendarEventId,
            String childName,
            Instant startTime,
            Instant endTime
    ) {
        return new ScheduleResponse(
                qualityTimeId,
                calendarEventId,
                childName,
                startTime,
                endTime,
                QualityTimeStatus.SCHEDULED
        );
    }
}
