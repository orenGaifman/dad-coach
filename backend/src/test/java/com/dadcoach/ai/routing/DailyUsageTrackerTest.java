package com.dadcoach.ai.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DailyUsageTracker — verifies token usage tracking per father per day.
 */
class DailyUsageTrackerTest {

    private DailyUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new DailyUsageTracker();
    }

    @Test
    void newFatherHasZeroUsage() {
        UUID fatherId = UUID.randomUUID();
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(0);
    }

    @Test
    void recordUsageAccumulatesTokens() {
        UUID fatherId = UUID.randomUUID();

        tracker.recordUsage(fatherId, 100);
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(100);

        tracker.recordUsage(fatherId, 250);
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(350);

        tracker.recordUsage(fatherId, 50);
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(400);
    }

    @Test
    void differentFathersTrackIndependently() {
        UUID father1 = UUID.randomUUID();
        UUID father2 = UUID.randomUUID();

        tracker.recordUsage(father1, 500);
        tracker.recordUsage(father2, 200);

        assertThat(tracker.getTodayUsage(father1)).isEqualTo(500);
        assertThat(tracker.getTodayUsage(father2)).isEqualTo(200);
    }

    @Test
    void getUsagePercentageCalculatesCorrectly() {
        UUID fatherId = UUID.randomUUID();
        int budget = 50_000;

        tracker.recordUsage(fatherId, 40_000); // 80%
        assertThat(tracker.getUsagePercentage(fatherId, budget)).isEqualTo(0.8);

        tracker.recordUsage(fatherId, 5_000); // 90%
        assertThat(tracker.getUsagePercentage(fatherId, budget)).isEqualTo(0.9);
    }

    @Test
    void getUsagePercentageReturnsZeroForNewFather() {
        UUID fatherId = UUID.randomUUID();
        assertThat(tracker.getUsagePercentage(fatherId, 50_000)).isEqualTo(0.0);
    }

    @Test
    void getUsagePercentageCanExceedOneHundredPercent() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 60_000); // 120% of 50k budget

        assertThat(tracker.getUsagePercentage(fatherId, 50_000)).isEqualTo(1.2);
    }

    @Test
    void resetUsageClearsForFather() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 10_000);
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(10_000);

        tracker.resetUsage(fatherId);
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(0);
    }

    @Test
    void resetUsageDoesNotAffectOtherFathers() {
        UUID father1 = UUID.randomUUID();
        UUID father2 = UUID.randomUUID();

        tracker.recordUsage(father1, 1_000);
        tracker.recordUsage(father2, 2_000);

        tracker.resetUsage(father1);

        assertThat(tracker.getTodayUsage(father1)).isEqualTo(0);
        assertThat(tracker.getTodayUsage(father2)).isEqualTo(2_000);
    }

    @Test
    void recordUsageRejectsNegativeTokens() {
        UUID fatherId = UUID.randomUUID();
        assertThatThrownBy(() -> tracker.recordUsage(fatherId, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tokensUsed must be >= 0");
    }

    @Test
    void getUsagePercentageRejectsZeroBudget() {
        UUID fatherId = UUID.randomUUID();
        assertThatThrownBy(() -> tracker.getUsagePercentage(fatherId, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dailyBudget must be > 0");
    }

    @Test
    void getUsagePercentageRejectsNegativeBudget() {
        UUID fatherId = UUID.randomUUID();
        assertThatThrownBy(() -> tracker.getUsagePercentage(fatherId, -100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dailyBudget must be > 0");
    }

    @Test
    void recordZeroTokensIsAllowed() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 0);
        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(0);
    }

    @Test
    void cleanupStaleEntriesDoesNotRemoveTodayEntries() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 1_000);

        tracker.cleanupStaleEntries();

        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(1_000);
    }
}
