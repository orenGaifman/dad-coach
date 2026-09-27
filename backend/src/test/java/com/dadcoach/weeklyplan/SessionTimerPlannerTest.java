package com.dadcoach.weeklyplan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTimerPlannerTest {

    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem"); // UTC+3 in September

    @Test
    @DisplayName("afternoon session: morning reminder at 08:00 local, 1h before start, 30min after end")
    void afternoonSession() {
        // Tuesday 17:00-18:00 local
        Map<String, String> timers = SessionTimerPlanner.plan(
                Instant.parse("2026-09-29T14:00:00Z"), Instant.parse("2026-09-29T15:00:00Z"),
                JERUSALEM, Instant.parse("2026-09-27T10:00:00Z"));

        assertThat(timers).containsExactly(
                Map.entry("session_morning_reminder", "2026-09-29T05:00:00Z"),
                Map.entry("session_reminder_1h", "2026-09-29T13:00:00Z"),
                Map.entry("session_follow_up", "2026-09-29T15:30:00Z"));
    }

    @Test
    @DisplayName("session starting before 10:00 local gets no morning reminder")
    void earlySessionSkipsMorningReminder() {
        // 09:30 local
        Map<String, String> timers = SessionTimerPlanner.plan(
                Instant.parse("2026-09-29T06:30:00Z"), Instant.parse("2026-09-29T07:00:00Z"),
                JERUSALEM, Instant.parse("2026-09-27T10:00:00Z"));

        assertThat(timers).containsOnlyKeys("session_reminder_1h", "session_follow_up");
    }

    @Test
    @DisplayName("timers already in the past are omitted (session later today)")
    void pastTimersOmitted() {
        // now 12:00 local; session 12:30-13:00 local: morning and 1h reminders are past
        Map<String, String> timers = SessionTimerPlanner.plan(
                Instant.parse("2026-09-29T09:30:00Z"), Instant.parse("2026-09-29T10:00:00Z"),
                JERUSALEM, Instant.parse("2026-09-29T09:00:00Z"));

        assertThat(timers).containsExactly(Map.entry("session_follow_up", "2026-09-29T10:30:00Z"));
    }

    @Test
    @DisplayName("morning reminder follows the father's own timezone")
    void usesFatherTimezone() {
        // 18:00-19:00 in New York (UTC-4)
        Map<String, String> timers = SessionTimerPlanner.plan(
                Instant.parse("2026-09-29T22:00:00Z"), Instant.parse("2026-09-29T23:00:00Z"),
                ZoneId.of("America/New_York"), Instant.parse("2026-09-27T10:00:00Z"));

        assertThat(timers.get("session_morning_reminder")).isEqualTo("2026-09-29T12:00:00Z");
    }
}
