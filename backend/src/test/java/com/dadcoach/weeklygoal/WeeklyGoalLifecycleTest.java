package com.dadcoach.weeklygoal;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.Belt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Weekly goal lifecycle: set -> ACTIVE, week-aware lookup and crediting, Sunday boundary.
 *
 * <p>Dates: Sunday 2026-09-27 starts week W1 and Sunday 2026-10-04 starts W2. Israel is on
 * IDT (UTC+3) on these dates.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Weekly goal lifecycle")
class WeeklyGoalLifecycleTest {

    private static final Long FATHER_ID = 42L;
    private static final LocalDate W1 = LocalDate.of(2026, 9, 27);
    private static final LocalDate W2 = LocalDate.of(2026, 10, 4);

    @Mock private WeeklyGoalRepository repository;
    @Mock private FatherRepository fatherRepository;

    private Father father;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(FATHER_ID);
        father.setTimezone("Asia/Jerusalem");
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        when(repository.save(any(WeeklyGoal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByFatherIdAndWeekStartDate(anyLong(), any())).thenReturn(Optional.empty());
    }

    private WeeklyGoalService serviceAt(String isoInstant) {
        return new WeeklyGoalService(repository, fatherRepository, Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC));
    }

    private WeeklyGoal goal(LocalDate week, int hours, WeeklyGoalStatus status) {
        WeeklyGoal goal = new WeeklyGoal(father, week, hours, Belt.WHITE);
        if (status == WeeklyGoalStatus.ACTIVE) {
            goal.activate();
        } else if (status == WeeklyGoalStatus.COMPLETED || status == WeeklyGoalStatus.MISSED) {
            goal.activate();
            goal.complete();
        }
        when(repository.findByFatherIdAndWeekStartDate(FATHER_ID, week)).thenReturn(Optional.of(goal));
        return goal;
    }

    @Nested
    @DisplayName("set_weekly_goal lifecycle (create -> activate)")
    class CreateAndActivate {

        @Test
        @DisplayName("creates the father's current-week goal already ACTIVE, persisted once (never left PENDING)")
        void createsActiveGoal() {
            // Sunday 09:00 IDT
            WeeklyGoal goal = serviceAt("2026-09-27T06:00:00Z").createAndActivateWeeklyGoal(FATHER_ID, 3);

            assertThat(goal.getStatus()).isEqualTo(WeeklyGoalStatus.ACTIVE);
            assertThat(goal.getWeekStartDate()).isEqualTo(W1);
            assertThat(goal.getTargetHours()).isEqualTo(3);
            ArgumentCaptor<WeeklyGoal> saved = ArgumentCaptor.forClass(WeeklyGoal.class);
            verify(repository, times(1)).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(WeeklyGoalStatus.ACTIVE);
        }

        @Test
        @DisplayName("a second goal for the same week is still rejected and nothing is saved")
        void duplicateWeekIsProtected() {
            goal(W1, 3, WeeklyGoalStatus.ACTIVE);
            WeeklyGoalService service = serviceAt("2026-09-30T09:00:00Z"); // Wednesday

            assertThatThrownBy(() -> service.createAndActivateWeeklyGoal(FATHER_ID, 4))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("the web two-step path (createWeeklyGoal) still creates PENDING - unchanged")
        void webCreateStaysPending() {
            WeeklyGoal goal = serviceAt("2026-09-27T06:00:00Z").createWeeklyGoal(FATHER_ID, 3);

            assertThat(goal.getStatus()).isEqualTo(WeeklyGoalStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("current goal lookup (status / progress / context)")
    class CurrentGoal {

        @Test
        @DisplayName("finds the current week's ACTIVE goal")
        void findsCurrentWeekGoal() {
            WeeklyGoal w1 = goal(W1, 3, WeeklyGoalStatus.ACTIVE);

            assertThat(serviceAt("2026-09-29T09:00:00Z").getActiveGoal(FATHER_ID)).contains(w1);
            verify(repository, never()).findByFatherIdAndStatus(anyLong(), any());
        }

        @Test
        @DisplayName("a current-week goal that is not ACTIVE is not reported as active")
        void pendingIsNotActive() {
            goal(W1, 3, WeeklyGoalStatus.PENDING);

            assertThat(serviceAt("2026-09-29T09:00:00Z").getActiveGoal(FATHER_ID)).isEmpty();
        }

        @Test
        @DisplayName("Sunday boundary: last week's not-yet-finalized ACTIVE goal is not this week's goal")
        void lastWeeksActiveGoalIsNotCurrent() {
            goal(W1, 3, WeeklyGoalStatus.ACTIVE);

            // Sunday W2 07:00 IDT - before the weekly completion job finalizes W1
            assertThat(serviceAt("2026-10-04T04:00:00Z").getActiveGoal(FATHER_ID)).isEmpty();
        }

        @Test
        @DisplayName("Sunday boundary: old and new ACTIVE goals coexisting resolve unambiguously to the new week")
        void twoActiveGoalsAreNotAmbiguous() {
            goal(W1, 3, WeeklyGoalStatus.ACTIVE);
            WeeklyGoal w2 = goal(W2, 4, WeeklyGoalStatus.ACTIVE);

            assertThat(serviceAt("2026-10-04T06:00:00Z").getActiveGoal(FATHER_ID)).contains(w2);
            verify(repository, never()).findByFatherIdAndStatus(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("quality-time completion credit")
    class Credit {

        @Test
        @DisplayName("credits the ACTIVE goal of the session's week")
        void creditsSessionWeek() {
            WeeklyGoal w1 = goal(W1, 3, WeeklyGoalStatus.ACTIVE);

            // Tuesday 2026-09-29 17:00 IDT session, completed the same evening
            serviceAt("2026-09-29T15:30:00Z").recordCompletedQualityTime(FATHER_ID, Instant.parse("2026-09-29T14:00:00Z"), 60);

            assertThat(w1.getActualMinutes()).isEqualTo(60);
            assertThat(w1.getCompletedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a session in the new week never credits the previous week's still-ACTIVE goal")
        void previousWeeksActiveGoalIsNotCredited() {
            WeeklyGoal w1 = goal(W1, 3, WeeklyGoalStatus.ACTIVE);

            // Monday of W2, no W2 goal yet
            serviceAt("2026-10-05T16:00:00Z").recordCompletedQualityTime(FATHER_ID, Instant.parse("2026-10-05T14:00:00Z"), 60);

            assertThat(w1.getActualMinutes()).isZero();
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("a Saturday-night session completed on Sunday credits its own week, not the new week's goal")
        void lateCompletionCreditsSessionWeek() {
            WeeklyGoal w1 = goal(W1, 3, WeeklyGoalStatus.ACTIVE);
            WeeklyGoal w2 = goal(W2, 3, WeeklyGoalStatus.ACTIVE);

            // Session Saturday 2026-10-03 21:00 IDT; completed Sunday 2026-10-04 08:00 IDT
            serviceAt("2026-10-04T05:00:00Z").recordCompletedQualityTime(FATHER_ID, Instant.parse("2026-10-03T18:00:00Z"), 60);

            assertThat(w1.getActualMinutes()).isEqualTo(60);
            assertThat(w2.getActualMinutes()).isZero();
        }

        @Test
        @DisplayName("an already-finalized week is not modified")
        void finalizedWeekIsNotModified() {
            WeeklyGoal w1 = goal(W1, 1, WeeklyGoalStatus.MISSED);

            serviceAt("2026-10-04T10:00:00Z").recordCompletedQualityTime(FATHER_ID, Instant.parse("2026-10-03T18:00:00Z"), 60);

            assertThat(w1.getActualMinutes()).isZero();
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("week definition and timezone source")
    class WeekDefinition {

        @Test
        @DisplayName("the week is Sunday-Saturday in the father's own timezone")
        void usesFatherTimezone() {
            WeeklyGoalService service = serviceAt("2026-10-04T02:00:00Z");
            Instant instant = Instant.parse("2026-10-04T02:00:00Z"); // Sun 05:00 IDT = Sat 22:00 New York

            assertThat(service.weekStartFor(father, instant)).isEqualTo(W2);
            father.setTimezone("America/New_York");
            assertThat(service.weekStartFor(father, instant)).isEqualTo(W1);
        }

        @Test
        @DisplayName("an unset or invalid father timezone falls back to the default zone")
        void invalidTimezoneFallsBack() {
            WeeklyGoalService service = serviceAt("2026-10-04T02:00:00Z");
            Instant instant = Instant.parse("2026-10-04T02:00:00Z");

            father.setTimezone("Not/AZone");
            assertThat(service.weekStartFor(father, instant)).isEqualTo(W2);
            father.setTimezone(null);
            assertThat(service.weekStartFor(father, instant)).isEqualTo(W2);
        }
    }

    @Nested
    @DisplayName("unchanged behavior outside the regression")
    class Unchanged {

        @Test
        @DisplayName("weekly completion still finalizes ACTIVE goals of earlier weeks")
        void weeklyCompletionUnchanged() {
            WeeklyGoal w1 = new WeeklyGoal(father, W1, 1, Belt.WHITE);
            w1.activate();
            w1.addCompletedMinutes(60);
            when(repository.findGoalsToComplete(any())).thenReturn(List.of(w1));

            serviceAt("2026-10-04T06:00:00Z").completeWeeklyGoals();

            assertThat(w1.getStatus()).isEqualTo(WeeklyGoalStatus.COMPLETED);
            assertThat(father.getCurrentStreakWeeks()).isEqualTo(1);
        }

        @Test
        @DisplayName("weekly summary still reports the last finalized goal")
        void summaryUnchanged() {
            WeeklyGoal w1 = new WeeklyGoal(father, W1, 3, Belt.WHITE);
            w1.activate();
            w1.complete();
            when(repository.findLastCompletedOrMissedGoal(FATHER_ID)).thenReturn(Optional.of(w1));

            WeeklyGoalService.WeeklySummary summary = serviceAt("2026-10-04T06:00:00Z").generateWeeklySummary(FATHER_ID);

            assertThat(summary.hasPreviousGoal()).isTrue();
            assertThat(summary.targetHours()).isEqualTo(3);
            assertThat(summary.goalMet()).isFalse();
        }
    }
}
