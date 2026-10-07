package com.dadcoach.web.father;

import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.SessionChildren;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * A session's phase and its view. The rule is the weekly plan's (WeeklyPlanContextBuilder.phaseOf) - the home reads
 * the phases straight from the plan; the sessions list (which reaches beyond this week) uses this, and
 * WeekTruthParityTest proves the two agree.
 */
public final class SessionPhases {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private SessionPhases() {
    }

    public static String phaseOf(QualityTime qt, Instant now) {
        return switch (qt.getStatus()) {
            case COMPLETED -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
            case MISSED -> "MISSED";
            case SCHEDULED -> now.isBefore(qt.getScheduledStart()) ? "UPCOMING"
                    : now.isBefore(qt.getScheduledEnd()) ? "IN_PROGRESS" : "AWAITING_CONFIRMATION";
        };
    }

    static SessionView view(QualityTime qt, String phase, ZoneId zone, Map<Long, String> childNames) {
        ZonedDateTime start = qt.getScheduledStart().atZone(zone);
        ZonedDateTime end = qt.getScheduledEnd().atZone(zone);
        String childName = SessionChildren.hebrew(qt, childNames);
        boolean started = "IN_PROGRESS".equals(phase) || "AWAITING_CONFIRMATION".equals(phase);
        boolean open = "UPCOMING".equals(phase) || started;
        return new SessionView(
                qt.getId().toString(),
                qt.getChildId(),
                childName,
                qt.getChildIds(),
                phase,
                qt.getScheduledStart(),
                qt.getScheduledEnd(),
                start.toLocalDate().toString(),
                start.getDayOfWeek().name(),
                start.format(HH_MM),
                end.format(HH_MM),
                (int) Duration.between(qt.getScheduledStart(), qt.getScheduledEnd()).toMinutes(),
                qt.getCompletionNotes(),
                started,
                open);
    }
}
