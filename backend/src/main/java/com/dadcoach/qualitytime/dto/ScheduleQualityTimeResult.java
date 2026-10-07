package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTimeStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A booked Quality Time session. {@code calendarEventId} is null when no Google Calendar event was created:
 * the father has no connected calendar (D-007), or creating the event failed - then {@code calendarError}
 * carries the calendar error code (e.g. CALENDAR_RECONNECT_REQUIRED) and the booking still stands.
 *
 * <p>{@code joinedExistingSession}: the child was booked into a slot where the father already had a session with
 * exactly the same start and end, so the child joined that session (same id) instead of a second session being
 * created; {@code childAlreadyInSession}: the child was already in it, nothing changed. {@code childName} is all
 * the session's children joined in Hebrew ("מטר ונעם"), {@code childNames} the names one by one.</p>
 */
public record ScheduleQualityTimeResult(
        UUID qualityTimeId,
        String calendarEventId,
        String childName,
        Instant startTime,
        Instant endTime,
        QualityTimeStatus status,
        String calendarError,
        List<String> childNames,
        boolean joinedExistingSession,
        boolean childAlreadyInSession
) {

    public static ScheduleQualityTimeResult success(UUID qualityTimeId, String calendarEventId, String childName,
                                                    Instant startTime, Instant endTime) {
        return new ScheduleQualityTimeResult(qualityTimeId, calendarEventId, childName, startTime, endTime,
                QualityTimeStatus.SCHEDULED, null, childName == null ? List.of() : List.of(childName), false, false);
    }

    /** The child joined (or was already in) the father's existing session at exactly this time. */
    public static ScheduleQualityTimeResult joined(UUID qualityTimeId, String calendarEventId, String childName,
                                                   List<String> childNames, Instant startTime, Instant endTime,
                                                   boolean alreadyIn) {
        return new ScheduleQualityTimeResult(qualityTimeId, calendarEventId, childName, startTime, endTime,
                QualityTimeStatus.SCHEDULED, null, List.copyOf(childNames), true, alreadyIn);
    }

    public ScheduleQualityTimeResult withCalendarError(String error) {
        return new ScheduleQualityTimeResult(qualityTimeId, calendarEventId, childName, startTime, endTime, status, error,
                childNames, joinedExistingSession, childAlreadyInSession);
    }
}
