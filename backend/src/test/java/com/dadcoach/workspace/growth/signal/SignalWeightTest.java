package com.dadcoach.workspace.growth.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link SignalWeight}.
 */
class SignalWeightTest {

    @Nested
    @DisplayName("getPoints()")
    class GetPointsTests {

        @Test
        void missionCompletedShouldReturn10() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.MISSION_COMPLETED)).isEqualTo(10);
        }

        @Test
        void missionReflectedShouldReturn5() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.MISSION_REFLECTED)).isEqualTo(5);
        }

        @Test
        void goalProgressShouldReturn15() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.GOAL_PROGRESS)).isEqualTo(15);
        }

        @Test
        void goalCompletedShouldReturn50() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.GOAL_COMPLETED)).isEqualTo(50);
        }

        @Test
        void meaningfulConversationShouldReturn8() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.MEANINGFUL_CONVERSATION)).isEqualTo(8);
        }

        @Test
        void dailyEngagementShouldReturn3() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.DAILY_ENGAGEMENT)).isEqualTo(3);
        }

        @Test
        void streakBonus7ShouldReturn20() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_7)).isEqualTo(20);
        }

        @Test
        void streakBonus14ShouldReturn30() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_14)).isEqualTo(30);
        }

        @Test
        void streakBonus21ShouldReturn40() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_21)).isEqualTo(40);
        }

        @Test
        void streakBonus30ShouldReturn50() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_30)).isEqualTo(50);
        }

        @Test
        void streakBonus60ShouldReturn75() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_60)).isEqualTo(75);
        }

        @Test
        void streakBonus90ShouldReturn100() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_90)).isEqualTo(100);
        }

        @Test
        void streakBonus180ShouldReturn150() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_180)).isEqualTo(150);
        }

        @Test
        void streakBonus365ShouldReturn300() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.STREAK_BONUS_365)).isEqualTo(300);
        }

        @Test
        void qualityTimeReportedShouldReturn12() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.QUALITY_TIME_REPORTED)).isEqualTo(12);
        }

        @Test
        void positiveActivityShouldReturn5() {
            assertThat(SignalWeight.getPoints(GrowthSignalType.POSITIVE_ACTIVITY)).isEqualTo(5);
        }

        @ParameterizedTest
        @EnumSource(GrowthSignalType.class)
        void everySignalTypeShouldHavePositiveWeight(GrowthSignalType type) {
            assertThat(SignalWeight.getPoints(type)).isPositive();
        }

        @Test
        void shouldThrowForNullType() {
            assertThatThrownBy(() -> SignalWeight.getPoints(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    @Nested
    @DisplayName("getAllWeights()")
    class GetAllWeightsTests {

        @Test
        void shouldReturnMapWithAll16Entries() {
            Map<GrowthSignalType, Integer> weights = SignalWeight.getAllWeights();
            assertThat(weights).hasSize(16);
        }

        @Test
        void shouldReturnUnmodifiableMap() {
            Map<GrowthSignalType, Integer> weights = SignalWeight.getAllWeights();
            assertThatThrownBy(() -> weights.put(GrowthSignalType.MISSION_COMPLETED, 999))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest
        @EnumSource(GrowthSignalType.class)
        void shouldContainEverySignalType(GrowthSignalType type) {
            assertThat(SignalWeight.getAllWeights()).containsKey(type);
        }
    }

    @Nested
    @DisplayName("Utility class constraints")
    class UtilityClassTests {

        @Test
        void constructorShouldThrow() throws Exception {
            var constructor = SignalWeight.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertThatThrownBy(constructor::newInstance)
                    .hasCauseInstanceOf(UnsupportedOperationException.class);
        }
    }
}
