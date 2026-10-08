package com.dadcoach.api.tools;

import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.api.error.ApiException;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.weeklyplan.SessionTimerPlanner;
import com.dadcoach.weeklyplan.WeeklyPlanContextBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * What every session/goal tool adds to its result so the agent never computes: the session's local date/time and
 * reminder instants ({@link SessionTimerPlanner}), and this week's coverage AFTER the change (the same figures as
 * weekly_plan_context).
 */
@Component
public class SessionViews {

    private static final Logger log = LoggerFactory.getLogger(SessionViews.class);
    private static final DateTimeFormatter LISTED = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm");

    private final WeeklyGoalService weeklyGoals;
    private final WeeklyPlanContextBuilder weeklyPlan;
    private final QualityTimeRepository qualityTimes;
    private final Clock clock;

    public SessionViews(WeeklyGoalService weeklyGoals, WeeklyPlanContextBuilder weeklyPlan,
                        QualityTimeRepository qualityTimes, Clock clock) {
        this.weeklyGoals = weeklyGoals;
        this.weeklyPlan = weeklyPlan;
        this.qualityTimes = qualityTimes;
        this.clock = clock;
    }

    public ZoneId zone(Father father) {
        return weeklyGoals.zoneFor(father);
    }

    void putSessionTimers(Map<String, Object> data, Father father, Instant start, Instant end) {
        ZoneId zone = zone(father);
        ZonedDateTime localStart = start.atZone(zone);
        data.put("timezone", zone.getId());
        data.put("local_date", localStart.toLocalDate().toString());
        data.put("local_start", localStart.toLocalTime().withSecond(0).withNano(0).toString());
        data.put("when_label", whenLabel(localStart, clock.instant().atZone(zone).toLocalDate()));
        data.put("timers", SessionTimerPlanner.plan(start, end, zone, clock.instant()));
    }

    /**
     * The session's day and time in Hebrew, for the confirmation to copy (qa-lab night round: the coach booked Thursday 8.10
     * and wrote "מחר, יום שישי 9.10" - it worked the day out itself). "היום, יום חמישי 8.10 ב-17:00".
     */
    static String whenLabel(ZonedDateTime localStart, java.time.LocalDate today) {
        return com.dadcoach.replies.HebrewWhen.label(localStart, today);
    }

    public String whenLabel(Father father, Instant start) {
        ZoneId zone = zone(father);
        return whenLabel(start.atZone(zone), clock.instant().atZone(zone).toLocalDate());
    }

    public java.util.Set<String> timerKeys(Father father, Instant start, Instant end) {
        return SessionTimerPlanner.plan(start, end, zone(father), clock.instant()).keySet();
    }

    /** Puts this week's coverage after the change and returns its numbers (null when it could not be computed). */
    com.dadcoach.replies.CoachReplies.Week putWeekCoverage(Map<String, Object> data, Father father) {
        try {
            Object coverage = weeklyPlan.build(father).get("coverage");
            data.put("week_coverage", coverage);
            return com.dadcoach.replies.CoachReplies.Week.of(coverage);
        } catch (RuntimeException e) {
            log.warn("Could not compute week coverage: fatherId={}, error={}", father.getId(), e.getClass().getSimpleName());
            return null;
        }
    }

    /** D-034: the ready answer a tool returns - the coach's reply is exactly these lines. */
    static void putReply(Map<String, Object> data, java.util.List<String> lines) {
        data.put("reply", String.join("\n", lines));
    }

    Instant now() {
        return clock.instant();
    }

    /** The session was created in the last few minutes (the same booking, its next child). */
    boolean justBooked(java.util.UUID id) {
        return qualityTimes.findById(id).map(QualityTime::getCreatedAt)
                .filter(created -> created != null && created.isAfter(Instant.now().minus(java.time.Duration.ofMinutes(5))))
                .isPresent();
    }

    /**
     * The father's own session with this id. Unknown, malformed or someone else's: NOT_FOUND that lists his
     * still-scheduled sessions with their ids, so an agent that used a wrong id can correct itself in the same turn.
     */
    QualityTime ownSession(Father father, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            uuid = null;
        }
        if (uuid != null) {
            var found = qualityTimes.findById(uuid).filter(qt -> father.getId().equals(qt.getFatherId()));
            if (found.isPresent()) {
                return found.get();
            }
        }
        ZoneId zone = zone(father);
        Instant now = clock.instant();
        String sessions = qualityTimes.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED).stream()
                .sorted(Comparator.comparing(QualityTime::getScheduledStart))
                .map(qt -> qt.getId() + " (" + qt.getScheduledStart().atZone(zone).format(LISTED)
                        + (qt.getScheduledEnd().isAfter(now) ? ", upcoming" : ", already ended") + ")")
                .collect(Collectors.joining("; "));
        throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "Unknown quality_time_id '" + id + "' - that id does not exist. The father's scheduled sessions are: "
                        + (sessions.isEmpty() ? "none" : sessions)
                        + ". Call this tool again now with the exact id of the session you meant.");
    }
}
