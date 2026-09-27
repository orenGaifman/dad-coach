package com.dadcoach.weeklyplan;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dad Coach's reminder policy for one quality-time session, expressed as the concrete UTC instants at
 * which the workflow should wake up. The Workflow Platform only materializes these instants (its
 * generic schedule_state_transition tool); the policy itself lives here so the agent never has to do
 * timezone arithmetic.
 *
 * <ul>
 *   <li>{@code session_morning_reminder} - 08:00 in the father's timezone on the session date. Omitted
 *       when the session starts before 10:00 local (the 1-hour reminder already covers it) or when
 *       08:00 has already passed.</li>
 *   <li>{@code session_reminder_1h} - one hour before the start. Omitted when already past.</li>
 *   <li>{@code session_follow_up} - 30 minutes after the end. Omitted when already past.</li>
 * </ul>
 *
 * <p>Keys are the Dad Coach 3 transition keys, so an entry can be passed to schedule_state_transition
 * as-is; a key that is absent means "do not schedule".</p>
 */
public final class SessionTimerPlanner {

    public static final String MORNING_REMINDER = "session_morning_reminder";
    public static final String REMINDER_1H = "session_reminder_1h";
    public static final String FOLLOW_UP = "session_follow_up";

    static final LocalTime MORNING_REMINDER_TIME = LocalTime.of(8, 0);
    static final LocalTime MORNING_REMINDER_MIN_SESSION_START = LocalTime.of(10, 0);
    static final Duration REMINDER_LEAD = Duration.ofHours(1);
    static final Duration FOLLOW_UP_DELAY = Duration.ofMinutes(30);

    private SessionTimerPlanner() {
    }

    /**
     * @return transition key -> ISO-8601 UTC instant, in firing order, containing only timers still in
     *         the future relative to {@code now}
     */
    public static Map<String, String> plan(Instant start, Instant end, ZoneId zone, Instant now) {
        Map<String, String> timers = new LinkedHashMap<>();

        ZonedDateTime localStart = start.atZone(zone);
        if (!localStart.toLocalTime().isBefore(MORNING_REMINDER_MIN_SESSION_START)) {
            Instant morning = localStart.toLocalDate().atTime(MORNING_REMINDER_TIME).atZone(zone).toInstant();
            if (morning.isAfter(now)) {
                timers.put(MORNING_REMINDER, morning.toString());
            }
        }

        Instant oneHourBefore = start.minus(REMINDER_LEAD);
        if (oneHourBefore.isAfter(now)) {
            timers.put(REMINDER_1H, oneHourBefore.toString());
        }

        Instant followUp = end.plus(FOLLOW_UP_DELAY);
        if (followUp.isAfter(now)) {
            timers.put(FOLLOW_UP, followUp.toString());
        }
        return timers;
    }
}
