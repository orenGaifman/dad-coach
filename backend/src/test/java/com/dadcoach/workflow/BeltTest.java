package com.dadcoach.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Belt enum.
 * Validates: Requirement 8.5 (Belt progression milestones)
 */
@DisplayName("Belt")
class BeltTest {

    @Nested
    @DisplayName("Belt thresholds")
    class ThresholdsTest {

        @Test
        @DisplayName("WHITE belt covers 0-2 completions")
        void whiteBeltThresholds() {
            assertThat(Belt.WHITE.getMinCompletions()).isEqualTo(0);
            assertThat(Belt.WHITE.getMaxCompletions()).isEqualTo(2);
        }

        @Test
        @DisplayName("YELLOW belt covers 3-9 completions")
        void yellowBeltThresholds() {
            assertThat(Belt.YELLOW.getMinCompletions()).isEqualTo(3);
            assertThat(Belt.YELLOW.getMaxCompletions()).isEqualTo(9);
        }

        @Test
        @DisplayName("ORANGE belt covers 10-24 completions")
        void orangeBeltThresholds() {
            assertThat(Belt.ORANGE.getMinCompletions()).isEqualTo(10);
            assertThat(Belt.ORANGE.getMaxCompletions()).isEqualTo(24);
        }

        @Test
        @DisplayName("GREEN belt covers 25-49 completions")
        void greenBeltThresholds() {
            assertThat(Belt.GREEN.getMinCompletions()).isEqualTo(25);
            assertThat(Belt.GREEN.getMaxCompletions()).isEqualTo(49);
        }

        @Test
        @DisplayName("BLUE belt covers 50-99 completions")
        void blueBeltThresholds() {
            assertThat(Belt.BLUE.getMinCompletions()).isEqualTo(50);
            assertThat(Belt.BLUE.getMaxCompletions()).isEqualTo(99);
        }

        @Test
        @DisplayName("BROWN belt covers 100-199 completions")
        void brownBeltThresholds() {
            assertThat(Belt.BROWN.getMinCompletions()).isEqualTo(100);
            assertThat(Belt.BROWN.getMaxCompletions()).isEqualTo(199);
        }

        @Test
        @DisplayName("BLACK belt covers 200+ completions")
        void blackBeltThresholds() {
            assertThat(Belt.BLACK.getMinCompletions()).isEqualTo(200);
            assertThat(Belt.BLACK.getMaxCompletions()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("Belt thresholds are contiguous with no gaps")
        void thresholdsAreContiguous() {
            Belt[] belts = Belt.values();
            for (int i = 0; i < belts.length - 1; i++) {
                assertThat(belts[i + 1].getMinCompletions())
                        .as("Gap between %s and %s", belts[i], belts[i + 1])
                        .isEqualTo(belts[i].getMaxCompletions() + 1);
            }
        }
    }

    @Nested
    @DisplayName("fromCompletionCount")
    class FromCompletionCountTest {

        @ParameterizedTest(name = "count {0} returns {1}")
        @CsvSource({
                "0, WHITE",
                "1, WHITE",
                "2, WHITE",
                "3, YELLOW",
                "9, YELLOW",
                "10, ORANGE",
                "24, ORANGE",
                "25, GREEN",
                "49, GREEN",
                "50, BLUE",
                "99, BLUE",
                "100, BROWN",
                "199, BROWN",
                "200, BLACK",
                "500, BLACK",
                "1000, BLACK"
        })
        @DisplayName("returns correct belt for completion count")
        void returnsCorrectBeltForCount(int count, Belt expected) {
            assertThat(Belt.fromCompletionCount(count)).isEqualTo(expected);
        }

        @Test
        @DisplayName("handles very large completion counts as BLACK")
        void handlesVeryLargeCount() {
            assertThat(Belt.fromCompletionCount(Integer.MAX_VALUE)).isEqualTo(Belt.BLACK);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative count")
        void throwsForNegativeCount() {
            assertThatThrownBy(() -> Belt.fromCompletionCount(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -10, -100, Integer.MIN_VALUE})
        @DisplayName("throws for various negative values")
        void throwsForVariousNegativeValues(int negativeCount) {
            assertThatThrownBy(() -> Belt.fromCompletionCount(negativeCount))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getNextBelt")
    class GetNextBeltTest {

        @Test
        @DisplayName("WHITE returns YELLOW")
        void whiteReturnsYellow() {
            assertThat(Belt.WHITE.getNextBelt()).isEqualTo(Belt.YELLOW);
        }

        @Test
        @DisplayName("YELLOW returns ORANGE")
        void yellowReturnsOrange() {
            assertThat(Belt.YELLOW.getNextBelt()).isEqualTo(Belt.ORANGE);
        }

        @Test
        @DisplayName("ORANGE returns GREEN")
        void orangeReturnsGreen() {
            assertThat(Belt.ORANGE.getNextBelt()).isEqualTo(Belt.GREEN);
        }

        @Test
        @DisplayName("GREEN returns BLUE")
        void greenReturnsBlue() {
            assertThat(Belt.GREEN.getNextBelt()).isEqualTo(Belt.BLUE);
        }

        @Test
        @DisplayName("BLUE returns BROWN")
        void blueReturnsBrown() {
            assertThat(Belt.BLUE.getNextBelt()).isEqualTo(Belt.BROWN);
        }

        @Test
        @DisplayName("BROWN returns BLACK")
        void brownReturnsBlack() {
            assertThat(Belt.BROWN.getNextBelt()).isEqualTo(Belt.BLACK);
        }

        @Test
        @DisplayName("BLACK returns null (highest belt)")
        void blackReturnsNull() {
            assertThat(Belt.BLACK.getNextBelt()).isNull();
        }

        @Test
        @DisplayName("progression order matches enum declaration order")
        void progressionMatchesEnumOrder() {
            Belt[] belts = Belt.values();
            for (int i = 0; i < belts.length - 1; i++) {
                assertThat(belts[i].getNextBelt())
                        .as("Next belt for %s", belts[i])
                        .isEqualTo(belts[i + 1]);
            }
        }
    }

    @Nested
    @DisplayName("Belt enum structure")
    class EnumStructureTest {

        @Test
        @DisplayName("has exactly 7 belt levels")
        void hasSevenBelts() {
            assertThat(Belt.values()).hasSize(7);
        }

        @Test
        @DisplayName("belt levels are in progression order")
        void beltsInProgressionOrder() {
            assertThat(Belt.values()).containsExactly(
                    Belt.WHITE,
                    Belt.YELLOW,
                    Belt.ORANGE,
                    Belt.GREEN,
                    Belt.BLUE,
                    Belt.BROWN,
                    Belt.BLACK
            );
        }
    }
}
