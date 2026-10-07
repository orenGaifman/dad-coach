package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTimeStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A booked Quality Time session. {@code calendarEventId} is null when no Google Calendar event was created:
 * the father has no connected calendar (D-007), or creating the event failed - then {@code calendarError}
 * carries the calendar error code (e.g. CALENDAR_RECONNECT_REQUIRED) and the booking still stands.
 */
public record ScheduleQualityTimeResult(
        UUID qualityTimeId,
        String calendarEventId,
        String childName,
        Instant startTime,
        Instant endTime,
        QualityTimeStatus status,
        String calendarError
) {

    public static ScheduleQualityTimeResult success(UUID qualityTimeId, String calendarEventId, String childName,
                                                    Instant startTime, Instant endTime) {
        return new ScheduleQualityTimeResult(qualityTimeId, calendarEventId, childName, startTime, endTime,
                QualityTimeStatus.SCHEDULED, null);
    }

    public ScheduleQualityTimeResult withCalendarError(String error) {
        return new ScheduleQualityTimeResult(qualityTimeId, calendarEventId, childName, startTime, endTime, status, error);
    }
}
