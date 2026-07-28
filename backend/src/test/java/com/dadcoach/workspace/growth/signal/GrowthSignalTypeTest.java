package com.dadcoach.workspace.growth.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link GrowthSignalType}.
 */
class GrowthSignalTypeTest {

    @Nested
    @DisplayName("Enum constants")
    class EnumConstantsTests {

        @Test
        void shouldHaveExactly16SignalTypes() {
            assertThat(GrowthSignalType.values()).hasSize(16);
        }

        @ParameterizedTest
        @EnumSource(GrowthSignalType.class)
        void eachTypeShouldHaveNonBlankDescription(GrowthSignalType type) {
            assertThat(type.getDescription()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("isStreakBonus()")
    class IsStreakBonusTests {

        @ParameterizedTest
        @EnumSource(value = GrowthSignalType.class, names = {
                "STREAK_BONUS_7", "STREAK_BONUS_14", "STREAK_BONUS_21", "STREAK_BONUS_30",
                "STREAK_BONUS_60", "STREAK_BONUS_90", "STREAK_BONUS_180", "STREAK_BONUS_365"
        })
        void streakBonusTypesShouldReturnTrue(GrowthSignalType type) {
            assertThat(type.isStreakBonus()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = GrowthSignalType.class, names = {
                "MISSION_COMPLETED", "MISSION_REFLECTED", "GOAL_PROGRESS", "GOAL_COMPLETED",
                "MEANINGFUL_CONVERSATION", "DAILY_ENGAGEMENT", "QUALITY_TIME_REPORTED", "POSITIVE_ACTIVITY"
        })
        void nonStreakTypesShouldReturnFalse(GrowthSignalType type) {
            assertThat(type.isStreakBonus()).isFalse();
        }
    }

    @Nested
    @DisplayName("fromString()")
    class FromStringTests {

        @ParameterizedTest
        @EnumSource(GrowthSignalType.class)
        void shouldDeserializeExactName(GrowthSignalType type) {
            assertThat(GrowthSignalType.fromString(type.name())).isEqualTo(type);
        }

        @Test
        void shouldHandleLowerCase() {
            assertThat(GrowthSignalType.fromString("mission_completed"))
                    .isEqualTo(GrowthSignalType.MISSION_COMPLETED);
        }

        @Test
        void shouldHandleMixedCase() {
            assertThat(GrowthSignalType.fromString("Goal_Progress"))
                    .isEqualTo(GrowthSignalType.GOAL_PROGRESS);
        }

        @Test
        void shouldTrimWhitespace() {
            assertThat(GrowthSignalType.fromString("  DAILY_ENGAGEMENT  "))
                    .isEqualTo(GrowthSignalType.DAILY_ENGAGEMENT);
        }

        @Test
        void shouldThrowForNull() {
            assertThatThrownBy(() -> GrowthSignalType.fromString(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "\t"})
        void shouldThrowForBlank(String value) {
            assertThatThrownBy(() -> GrowthSignalType.fromString(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        void shouldThrowForUnknownValue() {
            assertThatThrownBy(() -> GrowthSignalType.fromString("UNKNOWN_SIGNAL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown GrowthSignalType");
        }
    }
}
