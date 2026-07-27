package com.dadcoach.missionengine;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.father.CoachingPhase;
import com.dadcoach.mission.MissionStatus;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for MissionEngine business logic.
 *
 * Tests four correctness properties from the design document:
 * - Property 10: Mission difficulty bounded by coaching phase
 * - Property 11: Mission difficulty adaptation based on outcome ratings
 * - Property 12: Mission category non-repetition (max 2 per 7-day window)
 * - Property 15: Equitable mission distribution across children
 */
class MissionEnginePropertyTests {

    // ─── Property 10: Mission Difficulty Bounded by Phase ────────────────────

    /**
     * **Validates: Requirements 6.3, 6.4, 6.5, 6.6**
     *
     * For any coaching phase, getDifficultyBounds returns the correct bounds:
     * FOUNDATION=[1,2], BUILDING=[1,3], DEEPENING=[2,4], MASTERY=[2,5].
     */
    @Property(tries = 100)
    void difficultyBoundsAreCorrectForAnyPhase(@ForAll("allPhases") CoachingPhase phase) {
        MissionEngineImpl engine = createEngine();

        int[] bounds = engine.getDifficultyBounds(phase);

        int expectedMin = expectedMinForPhase(phase);
        int expectedMax = expectedMaxForPhase(phase);

        if (bounds[0] != expectedMin || bounds[1] != expectedMax) {
            throw new AssertionError(
                    "For phase " + phase + " expected bounds [" + expectedMin + ", " + expectedMax +
                            "] but got [" + bounds[0] + ", " + bounds[1] + "]");
        }
    }

    /**
     * **Validates: Requirements 6.3, 6.4, 6.5, 6.6**
     *
     * For any coaching phase and any difficulty value (including extreme values),
     * clampDifficulty always produces a result within the phase bounds.
     */
    @Property(tries = 500)
    void clampedDifficultyAlwaysWithinPhaseBounds(
            @ForAll("allPhases") CoachingPhase phase,
            @ForAll @IntRange(min = -10, max = 20) int difficulty) {

        MissionEngineImpl engine = createEngine();

        int clamped = engine.clampDifficulty(difficulty, phase);
        int[] bounds = engine.getDifficultyBounds(phase);

        if (clamped < bounds[0] || clamped > bounds[1]) {
            throw new AssertionError(
                    "clampDifficulty(" + difficulty + ", " + phase + ") = " + clamped +
                            " is outside bounds [" + bounds[0] + ", " + bounds[1] + "]");
        }
    }

    /**
     * **Validates: Requirements 6.3, 6.4, 6.5, 6.6**
     *
     * For any difficulty value already within bounds, clampDifficulty should return it unchanged.
     */
    @Property(tries = 200)
    void clampDifficultyPreservesInBoundsValues(@ForAll("allPhases") CoachingPhase phase) {
        MissionEngineImpl engine = createEngine();
        int[] bounds = engine.getDifficultyBounds(phase);

        // Generate a value within bounds
        for (int d = bounds[0]; d <= bounds[1]; d++) {
            int clamped = engine.clampDifficulty(d, phase);
            if (clamped != d) {
                throw new AssertionError(
                        "clampDifficulty(" + d + ", " + phase + ") should preserve in-bounds value but got " + clamped);
            }
        }
    }

    // ─── Property 11: Mission Difficulty Adaptation ──────────────────────────

    /**
     * **Validates: Requirements 6.16, 6.17**
     *
     * For any completed mission with rating 4-5, the adapted difficulty should be
     * min(currentDifficulty + 1, phase_max), and always within phase bounds.
     */
    @Property(tries = 200)
    void highRatingIncreasesDifficultyByOne(
            @ForAll("allPhases") CoachingPhase phase,
            @ForAll @IntRange(min = 1, max = 5) int currentDifficulty,
            @ForAll @IntRange(min = 4, max = 5) int rating) {

        MissionEngineImpl engine = createEngineWithMocks(phase, rating, false);
        int[] bounds = engine.getDifficultyBounds(phase);

        int adapted = engine.adaptDifficulty(1L, 10L, currentDifficulty);

        int expectedUnclamped = Math.min(currentDifficulty + 1, bounds[1]);
        int expected = Math.max(bounds[0], Math.min(expectedUnclamped, bounds[1]));

        if (adapted != expected) {
            throw new AssertionError(
                    "For phase=" + phase + ", current=" + currentDifficulty + ", rating=" + rating +
                            ": expected adapted=" + expected + " but got " + adapted);
        }

        // Always within bounds
        if (adapted < bounds[0] || adapted > bounds[1]) {
            throw new AssertionError(
                    "Adapted difficulty " + adapted + " is outside bounds [" + bounds[0] + ", " + bounds[1] + "]");
        }
    }

    /**
     * **Validates: Requirements 6.16, 6.17**
     *
     * For any completed mission with rating 1-2, the adapted difficulty should be
     * max(currentDifficulty - 1, 1), clamped to phase bounds.
     */
    @Property(tries = 200)
    void lowRatingDecreasesDifficultyByOne(
            @ForAll("allPhases") CoachingPhase phase,
            @ForAll @IntRange(min = 1, max = 5) int currentDifficulty,
            @ForAll @IntRange(min = 1, max = 2) int rating) {

        MissionEngineImpl engine = createEngineWithMocks(phase, rating, false);
        int[] bounds = engine.getDifficultyBounds(phase);

        int adapted = engine.adaptDifficulty(1L, 10L, currentDifficulty);

        int expectedUnclamped = Math.max(currentDifficulty - 1, 1);
        int expected = Math.max(bounds[0], Math.min(expectedUnclamped, bounds[1]));

        if (adapted != expected) {
            throw new AssertionError(
                    "For phase=" + phase + ", current=" + currentDifficulty + ", rating=" + rating +
                            ": expected adapted=" + expected + " but got " + adapted);
        }

        // Always within bounds
        if (adapted < bounds[0] || adapted > bounds[1]) {
            throw new AssertionError(
                    "Adapted difficulty " + adapted + " is outside bounds [" + bounds[0] + ", " + bounds[1] + "]");
        }
    }

    /**
     * **Validates: Requirements 6.16, 6.17**
     *
     * For any completed mission with rating 3, the adapted difficulty should remain
     * unchanged (clamped to phase bounds).
     */
    @Property(tries = 200)
    void neutralRatingKeepsDifficultyUnchanged(
            @ForAll("allPhases") CoachingPhase phase,
            @ForAll @IntRange(min = 1, max = 5) int currentDifficulty) {

        MissionEngineImpl engine = createEngineWithMocks(phase, 3, false);
        int[] bounds = engine.getDifficultyBounds(phase);

        int adapted = engine.adaptDifficulty(1L, 10L, currentDifficulty);

        int expected = Math.max(bounds[0], Math.min(currentDifficulty, bounds[1]));

        if (adapted != expected) {
            throw new AssertionError(
                    "For phase=" + phase + ", current=" + currentDifficulty + ", rating=3" +
                            ": expected adapted=" + expected + " (unchanged, clamped) but got " + adapted);
        }
    }

    /**
     * **Validates: Requirements 6.11**
     *
     * After 3 consecutive skipped/expired missions, difficulty decreases by 1
     * (clamped to phase bounds, minimum 1).
     */
    @Property(tries = 200)
    void threeConsecutiveSkipsDecreasesDifficultyByOne(
            @ForAll("allPhases") CoachingPhase phase,
            @ForAll @IntRange(min = 1, max = 5) int currentDifficulty) {

        MissionEngineImpl engine = createEngineWithMocks(phase, 0, true);
        int[] bounds = engine.getDifficultyBounds(phase);

        int adapted = engine.adaptDifficulty(1L, 10L, currentDifficulty);

        int expectedUnclamped = Math.max(1, currentDifficulty - 1);
        int expected = Math.max(bounds[0], Math.min(expectedUnclamped, bounds[1]));

        if (adapted != expected) {
            throw new AssertionError(
                    "For phase=" + phase + ", current=" + currentDifficulty + " with 3 consecutive skips" +
                            ": expected adapted=" + expected + " but got " + adapted);
        }
    }

    // ─── Property 12: Mission Category Non-Repetition ───────────────────────

    /**
     * **Validates: Requirements 6.7**
     *
     * For any child, when a category has been used count times in the last 7 days,
     * validateCategoryNonRepetition returns false when count >= 2.
     */
    @Property(tries = 200)
    void categoryNonRepetitionReturnsFalseWhenCountAtOrAboveTwo(
            @ForAll @IntRange(min = 2, max = 10) int categoryCountInt) {

        long categoryCount = categoryCountInt;

        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);

        when(mockRepo.countByChildIdAndCategorySince(anyLong(), anyString(), any(Instant.class)))
                .thenReturn(categoryCount);

        MissionEngineImpl engine = new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);

        boolean result = engine.validateCategoryNonRepetition(10L, "CONNECTION");

        if (result) {
            throw new AssertionError(
                    "validateCategoryNonRepetition should return false when count=" + categoryCount +
                            " (>= 2), but returned true");
        }
    }

    /**
     * **Validates: Requirements 6.7**
     *
     * For any child, when a category has been used count times in the last 7 days,
     * validateCategoryNonRepetition returns true when count < 2.
     */
    @Property(tries = 100)
    void categoryNonRepetitionReturnsTrueWhenCountBelowTwo(
            @ForAll @IntRange(min = 0, max = 1) int categoryCountInt) {

        long categoryCount = categoryCountInt;

        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);

        when(mockRepo.countByChildIdAndCategorySince(anyLong(), anyString(), any(Instant.class)))
                .thenReturn(categoryCount);

        MissionEngineImpl engine = new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);

        boolean result = engine.validateCategoryNonRepetition(10L, "HEALTH");

        if (!result) {
            throw new AssertionError(
                    "validateCategoryNonRepetition should return true when count=" + categoryCount +
                            " (< 2), but returned false");
        }
    }

    // ─── Property 15: Equitable Mission Distribution ────────────────────────

    /**
     * **Validates: Requirements 6.13, 10.8**
     *
     * For any father with N active children (N > 1), when each child has at least
     * floor(total/N) - 1 missions, isDistributionEquitable returns true.
     */
    @Property(tries = 200)
    void equitableDistributionReturnsTrueWhenAllChildrenMeetThreshold(
            @ForAll @IntRange(min = 2, max = 5) int numChildren,
            @ForAll @IntRange(min = 0, max = 20) int totalMissions) {

        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        // Create N active children
        List<Child> children = new java.util.ArrayList<>();
        for (int i = 0; i < numChildren; i++) {
            Child child = new Child(father, "Child" + i, LocalDate.of(2018, 1, 1));
            child.setId((long) (100 + i));
            child.setFatherId(1L);
            children.add(child);
        }

        when(mockChildRepo.findByFatherId(1L)).thenReturn(children);

        // Distribute missions equitably: each child gets floor(total/N) missions
        long perChild = totalMissions / numChildren;
        long remainder = totalMissions % numChildren;

        for (int i = 0; i < numChildren; i++) {
            long childMissions = perChild + (i < remainder ? 1 : 0);
            when(mockRepo.countMissionsByChildIdSince(eq((long) (100 + i)), any(Instant.class)))
                    .thenReturn(childMissions);
        }

        MissionEngineImpl engine = new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);

        boolean result = engine.isDistributionEquitable(1L, 7);

        if (!result) {
            throw new AssertionError(
                    "Distribution with " + numChildren + " children and " + totalMissions +
                            " total missions (equitably distributed) should be equitable but returned false");
        }
    }

    /**
     * **Validates: Requirements 6.13, 10.8**
     *
     * For any father with N active children (N > 1), when one child has fewer than
     * floor(total/N) - 1 missions, isDistributionEquitable returns false.
     */
    @Property(tries = 200)
    void inequitableDistributionReturnsFalseWhenOneChildBelowThreshold(
            @ForAll @IntRange(min = 2, max = 4) int numChildren,
            @ForAll @IntRange(min = 4, max = 20) int totalMissions) {

        // Only test cases where threshold is meaningful (threshold >= 1)
        long threshold = (totalMissions / numChildren) - 1;
        if (threshold < 1) {
            return; // Skip trivial cases where threshold is 0 or negative
        }

        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        // Create N active children
        List<Child> children = new java.util.ArrayList<>();
        for (int i = 0; i < numChildren; i++) {
            Child child = new Child(father, "Child" + i, LocalDate.of(2018, 1, 1));
            child.setId((long) (100 + i));
            child.setFatherId(1L);
            children.add(child);
        }

        when(mockChildRepo.findByFatherId(1L)).thenReturn(children);

        // Give the first child all the missions, and the last child 0 missions
        // This creates a distribution where the last child is below threshold
        for (int i = 0; i < numChildren - 1; i++) {
            when(mockRepo.countMissionsByChildIdSince(eq((long) (100 + i)), any(Instant.class)))
                    .thenReturn((long) totalMissions);
        }
        // Last child gets 0 missions — definitely below threshold
        when(mockRepo.countMissionsByChildIdSince(eq((long) (100 + numChildren - 1)), any(Instant.class)))
                .thenReturn(0L);

        MissionEngineImpl engine = new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);

        boolean result = engine.isDistributionEquitable(1L, 7);

        // Recompute what the engine sees as total
        long engineTotal = ((long) totalMissions * (numChildren - 1)); // N-1 children with totalMissions each
        long engineThreshold = (engineTotal / numChildren) - 1;

        // Only assert inequitable if engineThreshold > 0
        if (engineThreshold > 0 && result) {
            throw new AssertionError(
                    "Distribution with " + numChildren + " children where one has 0 missions " +
                            "and total=" + engineTotal + " (threshold=" + engineThreshold +
                            ") should NOT be equitable but returned true");
        }
    }

    /**
     * **Validates: Requirements 6.13, 10.8**
     *
     * For a father with a single child (N=1), distribution is always equitable.
     */
    @Property(tries = 50)
    void singleChildAlwaysEquitable(@ForAll @IntRange(min = 0, max = 50) int missionCountInt) {
        long missionCount = missionCountInt;
        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Child child = new Child(father, "OnlyChild", LocalDate.of(2018, 1, 1));
        child.setId(100L);
        child.setFatherId(1L);

        when(mockChildRepo.findByFatherId(1L)).thenReturn(List.of(child));
        when(mockRepo.countMissionsByChildIdSince(eq(100L), any(Instant.class)))
                .thenReturn(missionCount);

        MissionEngineImpl engine = new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);

        boolean result = engine.isDistributionEquitable(1L, 7);

        if (!result) {
            throw new AssertionError(
                    "Single child with " + missionCount + " missions should always be equitable");
        }
    }

    // ─── Arbitrary Providers ────────────────────────────────────────────────

    @Provide
    Arbitrary<CoachingPhase> allPhases() {
        return Arbitraries.of(CoachingPhase.values());
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────

    private MissionEngineImpl createEngine() {
        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);
        return new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);
    }

    /**
     * Creates a MissionEngineImpl with mocks configured for difficulty adaptation tests.
     *
     * @param phase               the coaching phase for the father
     * @param lastRating          the outcome rating of the last completed mission (0 = no completed mission)
     * @param hasThreeConsSkips   whether to simulate 3 consecutive skipped/expired missions
     */
    private MissionEngineImpl createEngineWithMocks(CoachingPhase phase, int lastRating, boolean hasThreeConsSkips) {
        MissionRepository mockRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setCoachingPhase(phase);
        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));

        if (hasThreeConsSkips) {
            // Simulate 3 consecutive skipped missions
            Child child = new Child(father, "TestChild", LocalDate.of(2018, 6, 15));
            child.setId(10L);
            Mission skip1 = new Mission(father, child, "M1", "D1", "C1", 2, 10);
            skip1.setStatus(MissionStatus.SKIPPED);
            skip1.setAssignedAt(Instant.now().minus(1, ChronoUnit.DAYS));
            Mission skip2 = new Mission(father, child, "M2", "D2", "C2", 2, 10);
            skip2.setStatus(MissionStatus.SKIPPED);
            skip2.setAssignedAt(Instant.now().minus(2, ChronoUnit.DAYS));
            Mission skip3 = new Mission(father, child, "M3", "D3", "C3", 2, 10);
            skip3.setStatus(MissionStatus.EXPIRED);
            skip3.setAssignedAt(Instant.now().minus(3, ChronoUnit.DAYS));

            when(mockRepo.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(List.of(skip1, skip2, skip3));
        } else {
            when(mockRepo.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            if (lastRating > 0) {
                Child child = new Child(father, "TestChild", LocalDate.of(2018, 6, 15));
                child.setId(10L);
                Mission completedMission = new Mission(father, child, "Completed", "Desc", "CONNECTION", 2, 15);
                completedMission.setId(100L);
                completedMission.setStatus(MissionStatus.COMPLETED);
                completedMission.setOutcomeRating(lastRating);
                completedMission.setCompletedAt(Instant.now().minus(1, ChronoUnit.DAYS));

                when(mockRepo.findRecentCompletedByChildId(10L, 1))
                        .thenReturn(List.of(completedMission));
            } else {
                when(mockRepo.findRecentCompletedByChildId(10L, 1))
                        .thenReturn(Collections.emptyList());
            }
        }

        return new MissionEngineImpl(mockRepo, mockFatherRepo, mockChildRepo);
    }

    private int expectedMinForPhase(CoachingPhase phase) {
        return switch (phase) {
            case FOUNDATION -> 1;
            case BUILDING -> 1;
            case DEEPENING -> 2;
            case MASTERY -> 2;
        };
    }

    private int expectedMaxForPhase(CoachingPhase phase) {
        return switch (phase) {
            case FOUNDATION -> 2;
            case BUILDING -> 3;
            case DEEPENING -> 4;
            case MASTERY -> 5;
        };
    }
}
