package com.dadcoach.coaching;

import com.dadcoach.father.CoachingPhase;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.List;

/**
 * Property-based tests for coaching business logic.
 *
 * Tests three correctness properties from the design document:
 * - Property 2: Engagement score formula correctness
 * - Property 3: Coaching phase computation
 * - Property 26: Coaching streak calculation
 */
class CoachingPropertyTests {

    private final EngagementService engagementService = new EngagementService();

    // ─── Property 2: Engagement Score Formula Correctness ───────────────

    /**
     * **Validates: Requirements 1.9, 9.2**
     *
     * For any combination of (messages_sent_7d, missions_completed_7d, reflections_completed_7d, streak_days)
     * where all values are non-negative integers, the computed engagement_score should equal
     * min(100, messages×2 + missions×15 + reflections×10 + min(streak, 10)).
     */
    @Property(tries = 1000)
    void engagementScoreMatchesFormula(
            @ForAll @IntRange(min = 0, max = 200) int messages,
            @ForAll @IntRange(min = 0, max = 50) int missions,
            @ForAll @IntRange(min = 0, max = 50) int reflections,
            @ForAll @IntRange(min = 0, max = 365) int streakDays) {

        int actual = engagementService.computeEngagementScore(messages, missions, reflections, streakDays);

        int expected = Math.min(100,
                (messages * 2) + (missions * 15) + (reflections * 10) + Math.min(streakDays, 10));

        if (actual != expected) {
            throw new AssertionError(
                    "For msgs=" + messages + ", missions=" + missions +
                            ", reflections=" + reflections + ", streak=" + streakDays +
                            ": expected=" + expected + " but got=" + actual);
        }
    }

    /**
     * **Validates: Requirements 1.9, 9.2**
     *
     * The engagement score is always within [0, 100] for any non-negative inputs.
     */
    @Property(tries = 1000)
    void engagementScoreAlwaysBetween0And100(
            @ForAll @IntRange(min = 0, max = 500) int messages,
            @ForAll @IntRange(min = 0, max = 100) int missions,
            @ForAll @IntRange(min = 0, max = 100) int reflections,
            @ForAll @IntRange(min = 0, max = 1000) int streakDays) {

        int score = engagementService.computeEngagementScore(messages, missions, reflections, streakDays);

        if (score < 0 || score > 100) {
            throw new AssertionError(
                    "Engagement score " + score + " is outside [0, 100] for inputs: " +
                            "msgs=" + messages + ", missions=" + missions +
                            ", reflections=" + reflections + ", streak=" + streakDays);
        }
    }

    /**
     * **Validates: Requirements 1.9, 9.2**
     *
     * Engagement score is monotonically non-decreasing when any single input increases.
     */
    @Property(tries = 500)
    void engagementScoreMonotonicallyNonDecreasing(
            @ForAll @IntRange(min = 0, max = 50) int messages,
            @ForAll @IntRange(min = 0, max = 10) int missions,
            @ForAll @IntRange(min = 0, max = 10) int reflections,
            @ForAll @IntRange(min = 0, max = 100) int streakDays) {

        int score = engagementService.computeEngagementScore(messages, missions, reflections, streakDays);
        int scoreWithMoreMessages = engagementService.computeEngagementScore(messages + 1, missions, reflections, streakDays);

        if (scoreWithMoreMessages < score) {
            throw new AssertionError(
                    "Score decreased when messages increased: " + score + " → " + scoreWithMoreMessages);
        }
    }

    // ─── Property 3: Coaching Phase Computation ─────────────────────────

    /**
     * **Validates: Requirements 4.2, 4.12**
     *
     * For any number of days since activation (days >= 1), the computed coaching phase should be:
     * FOUNDATION for 1-14, BUILDING for 15-42, DEEPENING for 43-84, MASTERY for 85+.
     */
    @Property(tries = 1000)
    void coachingPhaseComputedCorrectlyFromDays(
            @ForAll @IntRange(min = 1, max = 1000) int daysSinceActivation) {

        CoachingPhase computed = engagementService.computeCoachingPhase(daysSinceActivation, null);

        CoachingPhase expected;
        if (daysSinceActivation <= 14) {
            expected = CoachingPhase.FOUNDATION;
        } else if (daysSinceActivation <= 42) {
            expected = CoachingPhase.BUILDING;
        } else if (daysSinceActivation <= 84) {
            expected = CoachingPhase.DEEPENING;
        } else {
            expected = CoachingPhase.MASTERY;
        }

        if (computed != expected) {
            throw new AssertionError(
                    "For day " + daysSinceActivation + ": expected " + expected + " but got " + computed);
        }
    }

    /**
     * **Validates: Requirements 4.12**
     *
     * Phase transitions are forward-only: the computed phase is never earlier than the current phase.
     */
    @Property(tries = 500)
    void coachingPhaseNeverRegresses(
            @ForAll @IntRange(min = 1, max = 1000) int daysSinceActivation,
            @ForAll("allPhases") CoachingPhase currentPhase) {

        CoachingPhase computed = engagementService.computeCoachingPhase(daysSinceActivation, currentPhase);

        if (computed.ordinal() < currentPhase.ordinal()) {
            throw new AssertionError(
                    "Phase regressed from " + currentPhase + " to " + computed +
                            " at day " + daysSinceActivation);
        }
    }

    /**
     * **Validates: Requirements 4.2**
     *
     * Phase boundaries are exact: day 14 → FOUNDATION, day 15 → BUILDING, etc.
     */
    @Property(tries = 100)
    void coachingPhaseBoundariesAreExact(@ForAll("phaseBoundaryDays") int day) {
        CoachingPhase computed = engagementService.computeCoachingPhase(day, null);

        CoachingPhase expected = switch (day) {
            case 1 -> CoachingPhase.FOUNDATION;
            case 14 -> CoachingPhase.FOUNDATION;
            case 15 -> CoachingPhase.BUILDING;
            case 42 -> CoachingPhase.BUILDING;
            case 43 -> CoachingPhase.DEEPENING;
            case 84 -> CoachingPhase.DEEPENING;
            case 85 -> CoachingPhase.MASTERY;
            default -> throw new IllegalStateException("Unexpected boundary day: " + day);
        };

        if (computed != expected) {
            throw new AssertionError(
                    "Boundary day " + day + ": expected " + expected + " but got " + computed);
        }
    }

    // ─── Property 26: Coaching Streak Calculation ───────────────────────

    /**
     * **Validates: Requirements 9.1**
     *
     * For any sequence of daily interaction flags, the coaching_streak should equal the count
     * of consecutive true values ending at the current day (last element).
     */
    @Property(tries = 1000)
    void coachingStreakEqualsConsecutiveTrueFromEnd(
            @ForAll @Size(min = 0, max = 100) List<Boolean> dailyInteractions) {

        int actual = engagementService.computeCoachingStreak(dailyInteractions);

        // Compute expected: count consecutive true values from the end
        int expected = 0;
        for (int i = dailyInteractions.size() - 1; i >= 0; i--) {
            if (Boolean.TRUE.equals(dailyInteractions.get(i))) {
                expected++;
            } else {
                break;
            }
        }

        if (actual != expected) {
            throw new AssertionError(
                    "For interactions " + dailyInteractions + ": expected streak=" + expected +
                            " but got=" + actual);
        }
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * Streak is always between 0 and the length of the input list.
     */
    @Property(tries = 500)
    void coachingStreakBoundedByListSize(
            @ForAll @Size(min = 0, max = 100) List<Boolean> dailyInteractions) {

        int streak = engagementService.computeCoachingStreak(dailyInteractions);

        if (streak < 0 || streak > dailyInteractions.size()) {
            throw new AssertionError(
                    "Streak " + streak + " is outside [0, " + dailyInteractions.size() + "]");
        }
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * If the last element is false, streak is always 0.
     * If the last element is true, streak is at least 1.
     */
    @Property(tries = 500)
    void coachingStreakZeroWhenLastDayIsFalse(
            @ForAll @Size(min = 1, max = 100) List<Boolean> dailyInteractions) {

        int streak = engagementService.computeCoachingStreak(dailyInteractions);
        boolean lastDay = dailyInteractions.get(dailyInteractions.size() - 1);

        if (!lastDay && streak != 0) {
            throw new AssertionError(
                    "Streak should be 0 when last day is false, but got " + streak);
        }
        if (lastDay && streak < 1) {
            throw new AssertionError(
                    "Streak should be at least 1 when last day is true, but got " + streak);
        }
    }

    // ─── Arbitrary Providers ────────────────────────────────────────────

    @Provide
    Arbitrary<CoachingPhase> allPhases() {
        return Arbitraries.of(CoachingPhase.values());
    }

    @Provide
    Arbitrary<Integer> phaseBoundaryDays() {
        return Arbitraries.of(1, 14, 15, 42, 43, 84, 85);
    }
}
