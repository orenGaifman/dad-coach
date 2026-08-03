package com.dadcoach.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for available time slots for Quality Time scheduling.
 * 
 * Used by GET /api/v1/quality-time/available-slots endpoint.
 * Returns available calendar slots that can be scheduled for Quality Time.
 * 
 * Requirements: 14.1
 * 
 * @see com.dadcoach.systemstate.SystemStateLoader#loadAvailableSlots
 */
public record AvailableSlotsDto(

        /**
         * List of available time slots ordered by proximity to current time.
         */
        List<AvailableSlotDto> slots,

        /**
         * Whether the father's Google Calendar is connected.
         * If false, slots will be empty and the user should be prompted to connect their calendar.
         */
        @JsonProperty("calendar_connected")
        boolean calendarConnected,

        /**
         * The father's configured timezone (IANA timezone ID).
         * All slot times are provided in UTC but should be displayed in this timezone.
         * Example: "America/Mexico_City", "America/New_York", "Asia/Jerusalem"
         */
        String timezone

) {

    /**
     * DTO representing a single available time slot for Quality Time scheduling.
     * 
     * Slots are calculated by analyzing the father's Google Calendar
     * and identifying gaps of at least 30 minutes between events.
     * 
     * Requirements: 2.3, 14.1
     */
    public record AvailableSlotDto(

            /**
             * The start time of the available slot in UTC (ISO 8601 format).
             */
            @JsonProperty("start_time")
            Instant startTime,

            /**
             * The end time of the available slot in UTC (ISO 8601 format).
             */
            @JsonProperty("end_time")
            Instant endTime,

            /**
             * The duration of the slot in minutes.
             * Always >= 30 (minimum Quality Time duration).
             */
            @JsonProperty("duration_minutes")
            int durationMinutes

    ) {

        /**
         * Creates an AvailableSlotDto from start and end times.
         *
         * @param startTime the start time
         * @param endTime   the end time
         * @return a new AvailableSlotDto with calculated duration
         */
        public static AvailableSlotDto of(Instant startTime, Instant endTime) {
            long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
            return new AvailableSlotDto(startTime, endTime, (int) durationMinutes);
        }
    }

    /**
     * Creates an AvailableSlotsDto for a connected calendar with available slots.
     *
     * @param slots    the list of available slots
     * @param timezone the father's timezone
     * @return a new AvailableSlotsDto
     */
    public static AvailableSlotsDto connected(List<AvailableSlotDto> slots, String timezone) {
        return new AvailableSlotsDto(slots, true, timezone);
    }

    /**
     * Creates an AvailableSlotsDto for a disconnected calendar.
     * The user should be prompted to connect their Google Calendar.
     *
     * @param timezone the father's timezone (may be null if not set)
     * @return a new AvailableSlotsDto with empty slots and calendarConnected=false
     */
    public static AvailableSlotsDto disconnected(String timezone) {
        return new AvailableSlotsDto(List.of(), false, timezone);
    }
}
