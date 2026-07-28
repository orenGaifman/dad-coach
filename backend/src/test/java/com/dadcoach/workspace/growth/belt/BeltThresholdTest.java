package com.dadcoach.workspace.growth.belt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BeltThreshold}.
 */
class BeltThresholdTest {

    // ─── beltForScore ────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "0, WHITE",
            "50, WHITE",
            "99, WHITE",
            "100, YELLOW",
            "200, YELLOW",
            "249, YELLOW",
            "250, ORANGE",
            "350, ORANGE",
            "449, ORANGE",
            "450, GREEN",
            "600, GREEN",
            "699, GREEN",
            "700, BLUE",
            "800, BLUE",
            "899, BLUE",
            "900, PURPLE",
            "1000, PURPLE",
            "1049, PURPLE",
            "1050, BROWN",
            "1100, BROWN",
            "1199, BROWN",
            "1200, BLACK",
            "5000, BLACK",
            "2147483647, BLACK"
    })
    void beltForScore_shouldReturnCorrectBelt(int score, BeltLevel expectedBelt) {
        assertThat(BeltThreshold.beltForScore(score)).isEqualTo(expectedBelt);
    }

    @Test
    void beltForScore_shouldThrowForNegativeScore() {
        assertThatThrownBy(() -> BeltThreshold.beltForScore(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    // ─── getMinScore ─────────────────────────────────────────────────────

    @Test
    void getMinScore_shouldReturnCorrectMinimums() {
        assertThat(BeltThreshold.getMinScore(BeltLevel.WHITE)).isEqualTo(0);
        assertThat(BeltThreshold.getMinScore(BeltLevel.YELLOW)).isEqualTo(100);
        assertThat(BeltThreshold.getMinScore(BeltLevel.ORANGE)).isEqualTo(250);
        assertThat(BeltThreshold.getMinScore(BeltLevel.GREEN)).isEqualTo(450);
        assertThat(BeltThreshold.getMinScore(BeltLevel.BLUE)).isEqualTo(700);
        assertThat(BeltThreshold.getMinScore(BeltLevel.PURPLE)).isEqualTo(900);
        assertThat(BeltThreshold.getMinScore(BeltLevel.BROWN)).isEqualTo(1050);
        assertThat(BeltThreshold.getMinScore(BeltLevel.BLACK)).isEqualTo(1200);
    }

    @Test
    void getMinScore_shouldThrowForNull() {
        assertThatThrownBy(() -> BeltThreshold.getMinScore(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── getMaxScore ─────────────────────────────────────────────────────

    @Test
    void getMaxScore_shouldReturnCorrectMaximums() {
        assertThat(BeltThreshold.getMaxScore(BeltLevel.WHITE)).isEqualTo(99);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.YELLOW)).isEqualTo(249);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.ORANGE)).isEqualTo(449);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.GREEN)).isEqualTo(699);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.BLUE)).isEqualTo(899);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.PURPLE)).isEqualTo(1049);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.BROWN)).isEqualTo(1199);
        assertThat(BeltThreshold.getMaxScore(BeltLevel.BLACK)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void getMaxScore_shouldThrowForNull() {
        assertThatThrownBy(() -> BeltThreshold.getMaxScore(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── getPointsToNextBelt ─────────────────────────────────────────────

    @Test
    void getPointsToNextBelt_shouldReturnCorrectPointsNeeded() {
        // WHITE (0-99) → YELLOW starts at 100
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.WHITE, 0)).isEqualTo(100);
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.WHITE, 50)).isEqualTo(50);
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.WHITE, 99)).isEqualTo(1);

        // YELLOW (100-249) → ORANGE starts at 250
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.YELLOW, 100)).isEqualTo(150);
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.YELLOW, 249)).isEqualTo(1);

        // BROWN (1050-1199) → BLACK starts at 1200
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.BROWN, 1050)).isEqualTo(150);
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.BROWN, 1199)).isEqualTo(1);
    }

    @Test
    void getPointsToNextBelt_shouldReturnZeroForBlack() {
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.BLACK, 1200)).isZero();
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.BLACK, 5000)).isZero();
    }

    @Test
    void getPointsToNextBelt_shouldReturnZeroWhenScoreExceedsNextThreshold() {
        // Score already at next belt threshold — points to next is 0
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.WHITE, 100)).isZero();
        assertThat(BeltThreshold.getPointsToNextBelt(BeltLevel.WHITE, 500)).isZero();
    }

    @Test
    void getPointsToNextBelt_shouldThrowForNullBelt() {
        assertThatThrownBy(() -> BeltThreshold.getPointsToNextBelt(null, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPointsToNextBelt_shouldThrowForNegativeScore() {
        assertThatThrownBy(() -> BeltThreshold.getPointsToNextBelt(BeltLevel.WHITE, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── getProgressPercentage ───────────────────────────────────────────

    @Test
    void getProgressPercentage_shouldReturnZeroAtBeltMinimum() {
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.WHITE, 0)).isZero();
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.YELLOW, 100)).isZero();
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.ORANGE, 250)).isZero();
    }

    @Test
    void getProgressPercentage_shouldReturn100ForBlack() {
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.BLACK, 1200)).isEqualTo(100);
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.BLACK, 5000)).isEqualTo(100);
    }

    @Test
    void getProgressPercentage_shouldReturnCorrectMidpointValues() {
        // WHITE range: 0 to 99, next belt at 100. Midpoint = 50 → 50%
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.WHITE, 50)).isEqualTo(50);

        // YELLOW range: 100 to 249, next belt at 250. Range = 150. Score 175 → 50%
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.YELLOW, 175)).isEqualTo(50);

        // GREEN range: 450 to 699, next belt at 700. Range = 250. Score 575 → 50%
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.GREEN, 575)).isEqualTo(50);
    }

    @Test
    void getProgressPercentage_shouldCapAt100WhenScoreExceedsRange() {
        // Score exceeds current belt's range (already at next belt threshold)
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.WHITE, 100)).isEqualTo(100);
        assertThat(BeltThreshold.getProgressPercentage(BeltLevel.WHITE, 500)).isEqualTo(100);
    }

    @Test
    void getProgressPercentage_shouldThrowForNullBelt() {
        assertThatThrownBy(() -> BeltThreshold.getProgressPercentage(null, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getProgressPercentage_shouldThrowForNegativeScore() {
        assertThatThrownBy(() -> BeltThreshold.getProgressPercentage(BeltLevel.WHITE, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── getThreshold ────────────────────────────────────────────────────

    @Test
    void getThreshold_shouldReturnCorrectRecords() {
        BeltThreshold.Threshold whiteThreshold = BeltThreshold.getThreshold(BeltLevel.WHITE);
        assertThat(whiteThreshold.minScore()).isZero();
        assertThat(whiteThreshold.maxScore()).isEqualTo(99);

        BeltThreshold.Threshold blackThreshold = BeltThreshold.getThreshold(BeltLevel.BLACK);
        assertThat(blackThreshold.minScore()).isEqualTo(1200);
        assertThat(blackThreshold.maxScore()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void getThreshold_shouldThrowForNull() {
        assertThatThrownBy(() -> BeltThreshold.getThreshold(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── getAllThresholds ────────────────────────────────────────────────

    @Test
    void getAllThresholds_shouldContainAllBeltLevels() {
        Map<BeltLevel, BeltThreshold.Threshold> thresholds = BeltThreshold.getAllThresholds();
        assertThat(thresholds).hasSize(8);
        for (BeltLevel level : BeltLevel.values()) {
            assertThat(thresholds).containsKey(level);
        }
    }

    @Test
    void getAllThresholds_shouldBeContiguousWithNoGaps() {
        // Verify that max of one belt + 1 = min of next belt (no gaps)
        BeltLevel[] levels = BeltLevel.values();
        for (int i = 0; i < levels.length - 1; i++) {
            int currentMax = BeltThreshold.getMaxScore(levels[i]);
            int nextMin = BeltThreshold.getMinScore(levels[i + 1]);
            assertThat(nextMin).isEqualTo(currentMax + 1);
        }
    }

    // ─── Threshold record ────────────────────────────────────────────────

    @Test
    void threshold_contains_shouldReturnTrueForScoreInRange() {
        BeltThreshold.Threshold threshold = new BeltThreshold.Threshold(100, 249);
        assertThat(threshold.contains(100)).isTrue();
        assertThat(threshold.contains(175)).isTrue();
        assertThat(threshold.contains(249)).isTrue();
    }

    @Test
    void threshold_contains_shouldReturnFalseForScoreOutsideRange() {
        BeltThreshold.Threshold threshold = new BeltThreshold.Threshold(100, 249);
        assertThat(threshold.contains(99)).isFalse();
        assertThat(threshold.contains(250)).isFalse();
    }

    @Test
    void threshold_range_shouldReturnCorrectSpan() {
        BeltThreshold.Threshold threshold = new BeltThreshold.Threshold(100, 249);
        assertThat(threshold.range()).isEqualTo(150);
    }

    @Test
    void threshold_shouldRejectNegativeMinScore() {
        assertThatThrownBy(() -> new BeltThreshold.Threshold(-1, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void threshold_shouldRejectMaxLessThanMin() {
        assertThatThrownBy(() -> new BeltThreshold.Threshold(100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxScore");
    }
}
