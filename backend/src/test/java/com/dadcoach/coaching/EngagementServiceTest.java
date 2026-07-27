package com.dadcoach.coaching;

import com.dadcoach.father.CoachingPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EngagementService.
 */
class EngagementServiceTest {

    private EngagementService engagementService;

    @BeforeEach
    void setUp() {
        engagementService = new EngagementService();
    }

    // ─── Engagement Score Tests ─────────────────────────────────────────

    @Test
    void computeEngagementScore_allZeros_returnsZero() {
        assertEquals(0, engagementService.computeEngagementScore(0, 0, 0, 0));
    }

    @Test
    void computeEngagementScore_basicCalculation() {
        // 5 msgs × 2 = 10, 2 missions × 15 = 30, 1 reflection × 10 = 10, min(3, 10) = 3
        // total = 53
        assertEquals(53, engagementService.computeEngagementScore(5, 2, 1, 3));
    }

    @Test
    void computeEngagementScore_cappedAt100() {
        // 50 msgs × 2 = 100 → already at 100 before other components
        assertEquals(100, engagementService.computeEngagementScore(50, 5, 5, 30));
    }

    @Test
    void computeEngagementScore_streakCappedAt10() {
        // 0 msgs, 0 missions, 0 reflections, streak=100 → min(100, 10) = 10
        assertEquals(10, engagementService.computeEngagementScore(0, 0, 0, 100));
    }

    @Test
    void computeEngagementScore_streakExactly10() {
        // 0 msgs, 0 missions, 0 reflections, streak=10 → min(10, 10) = 10
        assertEquals(10, engagementService.computeEngagementScore(0, 0, 0, 10));
    }

    @Test
    void computeEngagementScore_negativeInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> engagementService.computeEngagementScore(-1, 0, 0, 0));
    }

    // ─── Coaching Streak Tests ──────────────────────────────────────────

    @Test
    void computeCoachingStreak_emptyList_returnsZero() {
        assertEquals(0, engagementService.computeCoachingStreak(Collections.emptyList()));
    }

    @Test
    void computeCoachingStreak_nullList_returnsZero() {
        assertEquals(0, engagementService.computeCoachingStreak(null));
    }

    @Test
    void computeCoachingStreak_allTrue_returnsFullLength() {
        assertEquals(5, engagementService.computeCoachingStreak(Arrays.asList(true, true, true, true, true)));
    }

    @Test
    void computeCoachingStreak_allFalse_returnsZero() {
        assertEquals(0, engagementService.computeCoachingStreak(Arrays.asList(false, false, false)));
    }

    @Test
    void computeCoachingStreak_endingWithFalse_returnsZero() {
        assertEquals(0, engagementService.computeCoachingStreak(Arrays.asList(true, true, true, false)));
    }

    @Test
    void computeCoachingStreak_brokenInMiddle() {
        // [true, true, false, true, true, true] → streak = 3 (last 3 consecutive true)
        assertEquals(3, engagementService.computeCoachingStreak(Arrays.asList(true, true, false, true, true, true)));
    }

    @Test
    void computeCoachingStreak_singleTrue_returnsOne() {
        assertEquals(1, engagementService.computeCoachingStreak(List.of(true)));
    }

    // ─── Coaching Phase Tests ───────────────────────────────────────────

    @Test
    void computeCoachingPhase_day1_foundation() {
        assertEquals(CoachingPhase.FOUNDATION,
                engagementService.computeCoachingPhase(1, CoachingPhase.FOUNDATION));
    }

    @Test
    void computeCoachingPhase_day14_foundation() {
        assertEquals(CoachingPhase.FOUNDATION,
                engagementService.computeCoachingPhase(14, CoachingPhase.FOUNDATION));
    }

    @Test
    void computeCoachingPhase_day15_building() {
        assertEquals(CoachingPhase.BUILDING,
                engagementService.computeCoachingPhase(15, CoachingPhase.FOUNDATION));
    }

    @Test
    void computeCoachingPhase_day42_building() {
        assertEquals(CoachingPhase.BUILDING,
                engagementService.computeCoachingPhase(42, CoachingPhase.BUILDING));
    }

    @Test
    void computeCoachingPhase_day43_deepening() {
        assertEquals(CoachingPhase.DEEPENING,
                engagementService.computeCoachingPhase(43, CoachingPhase.BUILDING));
    }

    @Test
    void computeCoachingPhase_day84_deepening() {
        assertEquals(CoachingPhase.DEEPENING,
                engagementService.computeCoachingPhase(84, CoachingPhase.DEEPENING));
    }

    @Test
    void computeCoachingPhase_day85_mastery() {
        assertEquals(CoachingPhase.MASTERY,
                engagementService.computeCoachingPhase(85, CoachingPhase.DEEPENING));
    }

    @Test
    void computeCoachingPhase_day1000_mastery() {
        assertEquals(CoachingPhase.MASTERY,
                engagementService.computeCoachingPhase(1000, CoachingPhase.MASTERY));
    }

    @Test
    void computeCoachingPhase_forwardOnly_neverRegresses() {
        // Even if daysSinceActivation=1, if current phase is BUILDING, stays BUILDING
        assertEquals(CoachingPhase.BUILDING,
                engagementService.computeCoachingPhase(1, CoachingPhase.BUILDING));
    }

    @Test
    void computeCoachingPhase_forwardOnly_mastery_stays() {
        assertEquals(CoachingPhase.MASTERY,
                engagementService.computeCoachingPhase(10, CoachingPhase.MASTERY));
    }

    @Test
    void computeCoachingPhase_nullCurrentPhase_computesNormally() {
        assertEquals(CoachingPhase.FOUNDATION,
                engagementService.computeCoachingPhase(5, null));
    }

    @Test
    void computeCoachingPhase_invalidDays_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> engagementService.computeCoachingPhase(0, CoachingPhase.FOUNDATION));
    }
}
