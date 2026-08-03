package com.dadcoach.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for scheduling a Quality Time event.
 * 
 * Used by POST /api/v1/quality-time/schedule endpoint.
 * Contains the child to schedule Quality Time with, the start time,
 * and the duration in minutes.
 * 
 * Requirements: 14.3
 */
public record ScheduleRequest(
        /**
         * The ID of the child to schedule Quality Time with.
         */
        @NotNull(message = "Child ID is required")
        @JsonProperty("child_id")
        UUID childId,

        /**
         * The start time of the Quality Time event (ISO 8601 format).
         */
        @NotNull(message = "Start time is required")
        @JsonProperty("start_time")
        Instant startTime,

        /**
         * The duration of the Quality Time in minutes.
         * Must be at least 30 minutes per Requirements.
         */
        @NotNull(message = "Duration is required")
        @Min(value = 30, message = "Duration must be at least 30 minutes")
        @JsonProperty("duration_minutes")
        Integer durationMinutes
) {

    /**
     * Default minimum duration for Quality Time in minutes.
     */
    public static final int DEFAULT_MIN_DURATION = 30;

    /**
     * Creates a ScheduleRequest with the minimum duration.
     *
     * @param childId   the ID of the child
     * @param startTime the start time
     * @return a new ScheduleRequest with 30-minute duration
     */
    public static ScheduleRequest withDefaultDuration(UUID childId, Instant startTime) {
        return new ScheduleRequest(childId, startTime, DEFAULT_MIN_DURATION);
    }
}
