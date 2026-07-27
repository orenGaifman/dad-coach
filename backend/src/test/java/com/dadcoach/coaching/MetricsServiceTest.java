package com.dadcoach.coaching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsService.
 */
class MetricsServiceTest {

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService();
    }

    // ─── Mission Completion Rate Tests ──────────────────────────────────

    @Test
    void missionCompletionRate_noMissionsAssigned_returnsZero() {
        assertEquals(0.0, metricsService.computeMissionCompletionRate(0, 0));
    }

    @Test
    void missionCompletionRate_allCompleted_returns100() {
        assertEquals(100.0, metricsService.computeMissionCompletionRate(10, 10));
    }

    @Test
    void missionCompletionRate_halfCompleted_returns50() {
        assertEquals(50.0, metricsService.computeMissionCompletionRate(5, 10));
    }

    @Test
    void missionCompletionRate_noneCompleted_returnsZero() {
        assertEquals(0.0, metricsService.computeMissionCompletionRate(0, 10));
    }

    @Test
    void missionCompletionRate_negativeInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.computeMissionCompletionRate(-1, 5));
    }

    @Test
    void missionCompletionRate_completedExceedsAssigned_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.computeMissionCompletionRate(11, 10));
    }

    // ─── Relationship Progress Tests ────────────────────────────────────

    @Test
    void relationshipProgress_noCompletedMissions_returns50() {
        assertEquals(50.0, metricsService.computeRelationshipProgress(0.0, false));
    }

    @Test
    void relationshipProgress_perfectRating_returns100() {
        assertEquals(100.0, metricsService.computeRelationshipProgress(5.0, true));
    }

    @Test
    void relationshipProgress_minimumRating_returns20() {
        assertEquals(20.0, metricsService.computeRelationshipProgress(1.0, true));
    }

    @Test
    void relationshipProgress_averageRating() {
        // avg rating = 3.0 → (3.0/5.0) × 100 = 60.0
        assertEquals(60.0, metricsService.computeRelationshipProgress(3.0, true));
    }

    @Test
    void relationshipProgress_invalidRating_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.computeRelationshipProgress(6.0, true));
    }

    @Test
    void relationshipProgress_belowMinRating_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.computeRelationshipProgress(0.5, true));
    }

    // ─── Consistency Score Tests ────────────────────────────────────────

    @Test
    void consistencyScore_zeroDays_returnsZero() {
        assertEquals(0.0, metricsService.computeConsistencyScore(0));
    }

    @Test
    void consistencyScore_allDays_returns100() {
        assertEquals(100.0, metricsService.computeConsistencyScore(30));
    }

    @Test
    void consistencyScore_halfDays_returns50() {
        assertEquals(50.0, metricsService.computeConsistencyScore(15));
    }

    @Test
    void consistencyScore_negativeInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.computeConsistencyScore(-1));
    }

    @Test
    void consistencyScore_above30_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.computeConsistencyScore(31));
    }
}
