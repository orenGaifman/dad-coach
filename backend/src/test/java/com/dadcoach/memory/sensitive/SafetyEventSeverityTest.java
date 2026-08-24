package com.dadcoach.memory.sensitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SafetyEventSeverity} enum.
 *
 * <p>Validates: SPEC-004 Task 12 - Severity levels for safety events
 */
@DisplayName("SafetyEventSeverity Tests")
class SafetyEventSeverityTest {

    @Nested
    @DisplayName("Severity Levels")
    class SeverityLevelTests {

        @Test
        @DisplayName("LOW should have level 1")
        void lowShouldHaveLevel1() {
            assertThat(SafetyEventSeverity.LOW.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("MEDIUM should have level 2")
        void mediumShouldHaveLevel2() {
            assertThat(SafetyEventSeverity.MEDIUM.getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("HIGH should have level 3")
        void highShouldHaveLevel3() {
            assertThat(SafetyEventSeverity.HIGH.getLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("CRITICAL should have level 4")
        void criticalShouldHaveLevel4() {
            assertThat(SafetyEventSeverity.CRITICAL.getLevel()).isEqualTo(4);
        }

        @Test
        @DisplayName("levels should be in increasing order")
        void levelsShouldBeInIncreasingOrder() {
            assertThat(SafetyEventSeverity.LOW.getLevel())
                    .isLessThan(SafetyEventSeverity.MEDIUM.getLevel());
            assertThat(SafetyEventSeverity.MEDIUM.getLevel())
                    .isLessThan(SafetyEventSeverity.HIGH.getLevel());
            assertThat(SafetyEventSeverity.HIGH.getLevel())
                    .isLessThan(SafetyEventSeverity.CRITICAL.getLevel());
        }
    }

    @Nested
    @DisplayName("isAtLeast Comparisons")
    class IsAtLeastTests {

        @ParameterizedTest(name = "{0} isAtLeast {1} = {2}")
        @CsvSource({
                "LOW, LOW, true",
                "LOW, MEDIUM, false",
                "LOW, HIGH, false",
                "LOW, CRITICAL, false",
                "MEDIUM, LOW, true",
                "MEDIUM, MEDIUM, true",
                "MEDIUM, HIGH, false",
                "MEDIUM, CRITICAL, false",
                "HIGH, LOW, true",
                "HIGH, MEDIUM, true",
                "HIGH, HIGH, true",
                "HIGH, CRITICAL, false",
                "CRITICAL, LOW, true",
                "CRITICAL, MEDIUM, true",
                "CRITICAL, HIGH, true",
                "CRITICAL, CRITICAL, true"
        })
        @DisplayName("isAtLeast should compare correctly")
        void isAtLeastShouldCompareCorrectly(
                SafetyEventSeverity severity,
                SafetyEventSeverity threshold,
                boolean expected) {
            assertThat(severity.isAtLeast(threshold)).isEqualTo(expected);
        }

        @Test
        @DisplayName("CRITICAL isAtLeast any severity should be true")
        void criticalIsAtLeastAnySeverityShouldBeTrue() {
            for (SafetyEventSeverity threshold : SafetyEventSeverity.values()) {
                assertThat(SafetyEventSeverity.CRITICAL.isAtLeast(threshold))
                        .as("CRITICAL should be at least %s", threshold)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("LOW isAtLeast CRITICAL should be false")
        void lowIsAtLeastCriticalShouldBeFalse() {
            assertThat(SafetyEventSeverity.LOW.isAtLeast(SafetyEventSeverity.CRITICAL)).isFalse();
        }
    }

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("should have exactly 4 severity levels")
        void shouldHaveExactlyFourSeverityLevels() {
            assertThat(SafetyEventSeverity.values()).hasSize(4);
        }

        @Test
        @DisplayName("should contain all expected values")
        void shouldContainAllExpectedValues() {
            assertThat(SafetyEventSeverity.values())
                    .containsExactly(
                            SafetyEventSeverity.LOW,
                            SafetyEventSeverity.MEDIUM,
                            SafetyEventSeverity.HIGH,
                            SafetyEventSeverity.CRITICAL
                    );
        }
    }
}
