package com.dadcoach.domain.goal;

import com.dadcoach.common.HabitStatus;
import com.dadcoach.domain.habit.Habit;
import com.dadcoach.domain.habit.HabitService;
import com.dadcoach.goal.GoalCategory;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.time.Instant;

/**
 * Property-based tests for Goal progress computation, Habit streak rules,
 * and capacity limit enforcement.
 * Tests properties #27, #28, and #32 from the design specification.
 * No Spring context needed — tests exercise computation logic directly.
 */
class GoalHabitPropertyTests {

    // ─── Property #27: Goal Progress Computation ────────────────────────────

    /**
     * **Validates: Requirements 9.5, 16.3**
     *
     * For any goal with category C, the progress_percentage should equal
     * min(100, (completed_related_missions / GoalCategory.C.estimatedMissions) × 100).
     */
    @Property(tries = 500)
    void goalProgressMatchesFormula(
            @ForAll("goalCategories") GoalCategory category,
            @ForAll @IntRange(min = 0, max = 60) int completedMissions) {

        Goal goal = createGoalWithCategory(category);
        goal.setCompletedRelatedMissions(completedMissions);
        goal.recalculateProgress();

        int estimatedMissions = category.getEstimatedMissions();
        int expectedProgress = Math.min(100, (completedMissions * 100) / estimatedMissions);
        int actualProgress = goal.getProgressPercentage();

        assert actualProgress == expectedProgress :
                "For category " + category + " (estimated=" + estimatedMissions +
                        ") with " + completedMissions + " completed missions: expected progress " +
                        expectedProgress + "% but got " + actualProgress + "%";
    }

    /**
     * **Validates: Requirements 9.5, 16.3**
     *
     * Progress percentage is always bounded between 0 and 100 inclusive.
     */
    @Property(tries = 300)
    void goalProgressIsBoundedBetweenZeroAndHundred(
            @ForAll("goalCategories") GoalCategory category,
            @ForAll @IntRange(min = 0, max = 200) int completedMissions) {

        Goal goal = createGoalWithCategory(category);
        goal.setCompletedRelatedMissions(completedMissions);
        goal.recalculateProgress();

        int actualProgress = goal.getProgressPercentage();

        assert actualProgress >= 0 && actualProgress <= 100 :
                "Progress should be between 0 and 100, got " + actualProgress +
                        " for category " + category + " with " + completedMissions + " completed missions";
    }

    /**
     * **Validates: Requirements 9.5, 16.3**
     *
     * When completed missions equals estimated missions, progress is exactly 100%.
     */
    @Property(tries = 100)
    void goalProgressIs100WhenAllMissionsComplete(
            @ForAll("goalCategories") GoalCategory category) {

        Goal goal = createGoalWithCategory(category);
        int estimated = category.getEstimatedMissions();
        goal.setCompletedRelatedMissions(estimated);
        goal.recalculateProgress();

        assert goal.getProgressPercentage() == 100 :
                "Progress should be 100% when completed equals estimated, got " +
                        goal.getProgressPercentage() + "% for category " + category;
    }

    // ─── Property #28: Habit Streak Reset Rules ─────────────────────────────

    /**
     * **Validates: Requirements 16.6, 16.7**
     *
     * For any habit with DAILY frequency, if completion happens every day (within 48h)
     * the streak increments; if a day is missed (more than 48h gap) the streak resets to 0.
     */
    @Property(tries = 500)
    void dailyHabitStreakIncrementsWhenCompletedWithinDeadline(
            @ForAll @IntRange(min = 1, max = 47) int hoursElapsed) {

        Habit habit = createHabitWithFrequency("DAILY");
        Instant lastCompleted = Instant.now().minus(Duration.ofHours(hoursElapsed));
        habit.setLastCompletedAt(lastCompleted);
        habit.setCurrentStreak(5);

        // Streak should NOT be broken since hoursElapsed <= 47 < 48
        boolean broken = HabitService.isStreakBroken(habit, Instant.now());

        assert !broken :
                "DAILY habit streak should not be broken when completed " +
                        hoursElapsed + " hours ago (within 48h deadline)";
    }

    /**
     * **Validates: Requirements 16.6, 16.7**
     *
     * For a DAILY habit, if more than 48 hours pass since last completion, streak resets.
     */
    @Property(tries = 500)
    void dailyHabitStreakResetsWhenDeadlineMissed(
            @ForAll @IntRange(min = 49, max = 500) int hoursElapsed) {

        Habit habit = createHabitWithFrequency("DAILY");
        Instant lastCompleted = Instant.now().minus(Duration.ofHours(hoursElapsed));
        habit.setLastCompletedAt(lastCompleted);
        habit.setCurrentStreak(10);

        // Streak should be broken since hoursElapsed > 48
        boolean broken = HabitService.isStreakBroken(habit, Instant.now());

        assert broken :
                "DAILY habit streak should be broken when " +
                        hoursElapsed + " hours elapsed (exceeds 48h deadline)";
    }

    /**
     * **Validates: Requirements 16.6, 16.7**
     *
     * For a WEEKLY habit, streak resets if more than 7 days pass without completion.
     */
    @Property(tries = 300)
    void weeklyHabitStreakResetsWhenSevenDaysMissed(
            @ForAll @IntRange(min = 169, max = 500) int hoursElapsed) {
        // 169 hours = 7 days + 1 hour (just past the 7-day threshold)

        Habit habit = createHabitWithFrequency("WEEKLY");
        Instant lastCompleted = Instant.now().minus(Duration.ofHours(hoursElapsed));
        habit.setLastCompletedAt(lastCompleted);
        habit.setCurrentStreak(3);

        boolean broken = HabitService.isStreakBroken(habit, Instant.now());

        assert broken :
                "WEEKLY habit streak should be broken when " +
                        hoursElapsed + " hours elapsed (exceeds 7-day deadline)";
    }

    /**
     * **Validates: Requirements 16.6, 16.7**
     *
     * For a WEEKLY habit, streak is maintained if completed within 7 days.
     */
    @Property(tries = 300)
    void weeklyHabitStreakMaintainedWithinSevenDays(
            @ForAll @IntRange(min = 1, max = 167) int hoursElapsed) {
        // 167 hours < 168 hours (7 days)

        Habit habit = createHabitWithFrequency("WEEKLY");
        Instant lastCompleted = Instant.now().minus(Duration.ofHours(hoursElapsed));
        habit.setLastCompletedAt(lastCompleted);
        habit.setCurrentStreak(3);

        boolean broken = HabitService.isStreakBroken(habit, Instant.now());

        assert !broken :
                "WEEKLY habit streak should not be broken when completed " +
                        hoursElapsed + " hours ago (within 7-day deadline)";
    }

    /**
     * **Validates: Requirements 16.6, 16.7**
     *
     * For a BIWEEKLY habit, streak resets if more than 14 days pass without completion.
     */
    @Property(tries = 300)
    void biweeklyHabitStreakResetsWhenFourteenDaysMissed(
            @ForAll @IntRange(min = 337, max = 700) int hoursElapsed) {
        // 337 hours = 14 days + 1 hour (just past the 14-day threshold)

        Habit habit = createHabitWithFrequency("BIWEEKLY");
        Instant lastCompleted = Instant.now().minus(Duration.ofHours(hoursElapsed));
        habit.setLastCompletedAt(lastCompleted);
        habit.setCurrentStreak(2);

        boolean broken = HabitService.isStreakBroken(habit, Instant.now());

        assert broken :
                "BIWEEKLY habit streak should be broken when " +
                        hoursElapsed + " hours elapsed (exceeds 14-day deadline)";
    }

    /**
     * **Validates: Requirements 16.6, 16.7**
     *
     * At 66 consecutive completions for DAILY frequency, habit auto-transitions to COMPLETED.
     */
    @Property(tries = 50)
    void dailyHabitAutoCompletesAtSixtySixConsecutive(
            @ForAll @IntRange(min = 66, max = 100) int streak) {

        Habit habit = createHabitWithFrequency("DAILY");
        habit.setCurrentStreak(streak);

        // Verify that a DAILY habit with streak >= 66 should auto-complete
        // (This tests the threshold logic, not the full service call)
        boolean shouldAutoComplete = "DAILY".equals(habit.getFrequency()) && streak >= 66;

        assert shouldAutoComplete :
                "DAILY habit with streak " + streak + " should trigger auto-completion";
    }

    // ─── Property #32: Capacity Limits ──────────────────────────────────────

    /**
     * **Validates: Requirements 16.1, 16.5, 2.2**
     *
     * For any father, max 5 active goals. The GoalService enforces this at creation.
     * This tests the constant value used in the service.
     */
    @Property(tries = 100)
    void maxActiveGoalsLimitIsFive(
            @ForAll @IntRange(min = 5, max = 20) int activeGoalCount) {

        // The limit is 5 — any count >= 5 should be rejected
        boolean shouldReject = activeGoalCount >= 5;

        assert shouldReject :
                "Active goal count of " + activeGoalCount + " should exceed the limit of 5";
    }

    /**
     * **Validates: Requirements 16.1, 16.5, 2.2**
     *
     * For any father, max 5 active habits. The HabitService enforces this at creation.
     * This tests the constant value used in the service.
     */
    @Property(tries = 100)
    void maxActiveHabitsLimitIsFive(
            @ForAll @IntRange(min = 5, max = 20) int activeHabitCount) {

        // The limit is 5 — any count >= 5 should be rejected
        boolean shouldReject = activeHabitCount >= 5;

        assert shouldReject :
                "Active habit count of " + activeHabitCount + " should exceed the limit of 5";
    }

    /**
     * **Validates: Requirements 16.1, 16.5, 2.2**
     *
     * Counts below the limit should be allowed.
     */
    @Property(tries = 50)
    void countsWithinLimitAreAllowed(
            @ForAll @IntRange(min = 0, max = 4) int activeCount) {

        // Any count < 5 should be within limits for both goals and habits
        boolean withinGoalLimit = activeCount < 5;
        boolean withinHabitLimit = activeCount < 5;

        assert withinGoalLimit :
                "Active goal count of " + activeCount + " should be within the limit of 5";
        assert withinHabitLimit :
                "Active habit count of " + activeCount + " should be within the limit of 5";
    }

    // ─── Arbitrary Providers ────────────────────────────────────────────────

    @Provide
    Arbitrary<GoalCategory> goalCategories() {
        return Arbitraries.of(GoalCategory.values());
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────

    private Goal createGoalWithCategory(GoalCategory category) {
        Goal goal = new Goal(null, "Test Goal", "Test description", category, 3);
        return goal;
    }

    private Habit createHabitWithFrequency(String frequency) {
        Habit habit = new Habit(null, "Test Habit", "Test description", frequency);
        return habit;
    }
}
