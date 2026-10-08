package com.dadcoach.weeklyplan;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import com.dadcoach.workflow.Belt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Weekly plan context. "Now" is Tuesday 2026-09-29 12:00 in Asia/Jerusalem (09:00Z); the father's week
 * is Sunday 2026-09-27 .. Saturday 2026-10-03, the previous week 2026-09-20 .. 2026-09-26.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeeklyPlanContextBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");
    private static final LocalDate WEEK = LocalDate.parse("2026-09-27");
    private static final LocalDate PREVIOUS_WEEK = LocalDate.parse("2026-09-20");

    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private ChildRepository childRepository;
    @Mock private WeeklyGoalRepository weeklyGoalRepository;
    @Mock private FatherRepository fatherRepository;

    private Father father;
    private Child child;
    private final List<QualityTime> sessions = new ArrayList<>();
    private WeeklyPlanContextBuilder builder;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(7L);
        father.setDisplayName("Oren");
        father.setTimezone("Asia/Jerusalem");
        child = new Child(father, "Noa", LocalDate.parse("2019-05-01"));
        ReflectionTestUtils.setField(child, "id", 11L);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        WeeklyGoalService weeklyGoalService = new WeeklyGoalService(weeklyGoalRepository, fatherRepository, clock);
        builder = new WeeklyPlanContextBuilder(qualityTimeRepository, childRepository, weeklyGoalRepository,
                weeklyGoalService, clock, new com.dadcoach.common.DashboardLinks("https://app.dadcoach.test/"));

        when(childRepository.findByFatherIdAndStatus(7L, "ACTIVE")).thenReturn(List.of(child));
        when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(7L)).thenReturn(sessions);
        when(weeklyGoalRepository.findByFatherIdAndWeekStartDate(anyLong(), any())).thenReturn(Optional.empty());
        when(weeklyGoalRepository.findByFatherIdOrderByWeekStartDateDesc(7L)).thenReturn(List.of());
    }

    private QualityTime session(String startUtc, int minutes) {
        Instant start = Instant.parse(startUtc);
        QualityTime qt = new QualityTime(father, child, start, start.plusSeconds(minutes * 60L));
        ReflectionTestUtils.setField(qt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(qt, "childId", 11L);
        sessions.add(qt);
        return qt;
    }

    private WeeklyGoal goal(LocalDate week, int hours, int creditedMinutes, WeeklyGoalStatus status) {
        WeeklyGoal goal = new WeeklyGoal(father, week, hours, Belt.WHITE);
        goal.setId(week.toEpochDay());
        goal.setActualMinutes(creditedMinutes);
        goal.setStatus(status);
        when(weeklyGoalRepository.findByFatherIdAndWeekStartDate(7L, week)).thenReturn(Optional.of(goal));
        return goal;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> data, String key) {
        return (Map<String, Object>) data.get(key);
    }

    /** The one-line session entries of a session collection, in order. */
    private static List<String> lines(Map<String, Object> data, String key) {
        return map(data, key).values().stream().map(String::valueOf).toList();
    }

    @Test
    @DisplayName("local now, week boundaries and days left are in the father's timezone")
    void weekAndNow() {
        Map<String, Object> data = builder.build(father);

        assertThat(data.get("timezone")).isEqualTo("Asia/Jerusalem");
        assertThat(map(data, "now")).containsEntry("local_date", "2026-09-29")
                .containsEntry("local_time", "12:00").containsEntry("weekday", "TUESDAY");
        assertThat(map(data, "current_week")).containsEntry("week_start", "2026-09-27")
                .containsEntry("week_end", "2026-10-03").containsEntry("days_left_including_today", 5);
    }

    @Test
    @DisplayName("coverage = credited completed minutes + future scheduled minutes; cancelled and missed never count")
    void coverage() {
        goal(WEEK, 3, 60, WeeklyGoalStatus.ACTIVE);
        session("2026-09-27T14:00:00Z", 60).markCompleted("played football");
        session("2026-09-30T14:00:00Z", 45);                     // Wed, upcoming
        session("2026-10-01T14:00:00Z", 60).markCancelled();     // cancelled (e.g. rescheduled away)
        session("2026-09-28T14:00:00Z", 30).markMissed();

        Map<String, Object> data = builder.build(father);

        assertThat(map(data, "coverage"))
                .containsEntry("target_minutes", 180)
                .containsEntry("completed_minutes", 60)
                .containsEntry("planned_minutes", 45)
                .containsEntry("covered_minutes", 105)
                .containsEntry("uncovered_minutes", 75)
                .containsEntry("is_covered", false);
        // D-032: the same week in the hour words the father reads, so the coach never converts minutes
        assertThat(map(map(data, "coverage"), "in_hours"))
                .containsEntry("goal", "3 שעות")
                .containsEntry("completed", "שעה")
                .containsEntry("planned", "שלושת רבעי שעה")
                .containsEntry("covered", "שעה ו-45 דקות")
                .containsEntry("uncovered", "שעה ורבע");
        assertThat(map(data, "sessions_this_week")).containsOnlyKeys(
                "2026-09-27 17:00", "2026-09-28 17:00", "2026-09-30 17:00", "2026-10-01 17:00");
        assertThat(lines(data, "sessions_this_week")).extracting(line -> line.split(" \\| ")[0])
                .containsExactly("COMPLETED", "MISSED", "UPCOMING", "CANCELLED");
    }

    @Test
    @DisplayName("a fully covered week reports is_covered=true and never a negative gap")
    void fullyCovered() {
        goal(WEEK, 1, 30, WeeklyGoalStatus.ACTIVE);
        session("2026-10-02T07:00:00Z", 60);

        Map<String, Object> coverage = map(builder.build(father), "coverage");

        assertThat(coverage).containsEntry("uncovered_minutes", 0).containsEntry("is_covered", true);
    }

    @Test
    @DisplayName("an ended session nobody confirmed is awaiting confirmation and is not coverage")
    void awaitingConfirmation() {
        goal(WEEK, 2, 0, WeeklyGoalStatus.ACTIVE);
        session("2026-09-28T14:00:00Z", 60); // Monday, never confirmed

        Map<String, Object> data = builder.build(father);

        assertThat(map(data, "coverage")).containsEntry("planned_minutes", 0)
                .containsEntry("awaiting_confirmation_minutes", 60);
        assertThat(lines(data, "awaiting_confirmation")).singleElement()
                .satisfies(line -> assertThat(line).startsWith("AWAITING_CONFIRMATION | Noa | 60 min | MONDAY 2026-09-28 17:00-18:00")
                        .contains("| ended 1080 min ago | id="));
    }

    @Test
    @DisplayName("today's sessions and the next session carry local times and minutes until start")
    void todayAndNext() {
        session("2026-09-29T14:00:00Z", 60); // today 17:00 local

        Map<String, Object> data = builder.build(father);

        QualityTime today = sessions.get(0);
        assertThat(lines(data, "sessions_today")).containsExactly(
                "UPCOMING | Noa | 60 min | TUESDAY 2026-09-29 17:00-18:00 | starts in 300 min | id=" + today.getId());
        assertThat(data.get("next_session")).isEqualTo(lines(data, "sessions_today").get(0));
    }

    @Test
    @DisplayName("renders safely: no top-level nulls, empty collections say none, every value fits 200 chars")
    void renderSafe() {
        goal(WEEK, 2, 0, WeeklyGoalStatus.ACTIVE);
        for (int day = 0; day < 6; day++) {
            session(Instant.parse("2026-09-27T14:00:00Z").plusSeconds(day * 86_400L).toString(), 45);
        }
        session("2026-09-30T14:00:00Z", 30); // same local slot twice -> distinct keys
        Map<String, Object> data = builder.build(father);
        assertThat(data.values()).doesNotContainNull();
        assertThat(data.values()).noneMatch(value -> value instanceof List);
        data.values().stream().filter(Map.class::isInstance).map(Map.class::cast)
                .flatMap(nested -> nested.values().stream())
                .forEach(value -> assertThat(String.valueOf(value)).hasSizeLessThanOrEqualTo(200));

        sessions.clear();
        Map<String, Object> empty = builder.build(father);
        assertThat(empty.get("next_session")).isEqualTo("none");
        assertThat(empty.get("sessions_this_week")).isEqualTo("none");
        assertThat(empty.get("previous_week_completion_notes")).isEqualTo("none");
    }

    @Test
    @DisplayName("without a goal this week the target and gap are unknown, not zero")
    void noGoal() {
        session("2026-09-27T14:00:00Z", 60).markCompleted(null);

        Map<String, Object> data = builder.build(father);

        assertThat(map(data, "goal")).containsEntry("exists", false);
        assertThat(map(data, "coverage")).containsEntry("target_minutes", null)
                .containsEntry("uncovered_minutes", null).containsEntry("is_covered", null)
                .containsEntry("completed_minutes", 60);
    }

    @Test
    @DisplayName("previous week comes from its own goal and sessions, whether or not it was finalized yet")
    void previousWeek() {
        goal(PREVIOUS_WEEK, 2, 90, WeeklyGoalStatus.ACTIVE); // finalization job has not run yet
        session("2026-09-21T14:00:00Z", 90).markCompleted("baked a cake");
        session("2026-09-24T14:00:00Z", 30).markCancelled();
        when(weeklyGoalRepository.findByFatherIdOrderByWeekStartDateDesc(7L))
                .thenReturn(List.of(new WeeklyGoal(father, PREVIOUS_WEEK, 2, Belt.WHITE)));

        Map<String, Object> data = builder.build(father);

        Map<String, Object> previous = map(data, "previous_week");
        assertThat(previous).containsEntry("week_start", "2026-09-20").containsEntry("week_end", "2026-09-26")
                .containsEntry("goal_met", false).containsEntry("completed_minutes", 90)
                .containsEntry("completed_sessions", 1).containsEntry("not_completed_sessions", 1)
                .containsEntry("goal_status", "ACTIVE").containsEntry("goal_target_minutes", 120);
        assertThat(map(previous, "in_hours")).containsEntry("completed", "שעה וחצי").containsEntry("goal", "שעתיים");
        assertThat(map(data, "previous_week_completion_notes")).containsExactly(Map.entry("note_1", "baked a cake"));
        assertThat(map(data, "goal_history")).containsEntry("has_any_goal", true)
                .containsEntry("latest_previous_target_hours", 2);
        // this week's goal is not confused with last week's
        assertThat(map(data, "goal")).containsEntry("exists", false);
    }

    @Test
    @DisplayName("calendar connection flag and dashboard link")
    void calendarAndDashboard() {
        Map<String, Object> data = builder.build(father);

        assertThat(data).containsEntry("calendar_connected", false)
                .containsEntry("dashboard_url", "https://app.dadcoach.test/login");
    }

    @Test
    @DisplayName("at the Sunday boundary the new week starts in the father's timezone, not UTC")
    void sundayBoundary() {
        // Saturday 22:30Z = Sunday 01:30 in Jerusalem
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T22:30:00Z"), ZoneOffset.UTC);
        WeeklyPlanContextBuilder sunday = new WeeklyPlanContextBuilder(qualityTimeRepository, childRepository,
                weeklyGoalRepository, new WeeklyGoalService(weeklyGoalRepository, fatherRepository, clock), clock,
                new com.dadcoach.common.DashboardLinks("https://app.dadcoach.test"));
        goal(WEEK, 2, 120, WeeklyGoalStatus.ACTIVE);

        Map<String, Object> data = sunday.build(father);

        assertThat(map(data, "current_week")).containsEntry("week_start", "2026-10-04");
        assertThat(map(data, "goal")).containsEntry("exists", false);
        assertThat(map(data, "previous_week")).containsEntry("week_start", "2026-09-27")
                .containsEntry("goal_met", true);
    }
}
