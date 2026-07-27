package com.dadcoach.ai.routing;

import com.dadcoach.conversation.ConversationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CostController — verifies tier determination and cost decisions.
 */
class CostControllerTest {

    private static final int BUDGET = 50_000;
    private DailyUsageTracker tracker;
    private CostController controller;

    @BeforeEach
    void setUp() {
        tracker = new DailyUsageTracker();
        controller = new CostController(tracker, BUDGET);
    }

    // --- Tier Determination ---

    @Test
    void belowEightyPercentIsNormalTier() {
        assertThat(controller.determineTier(0.0)).isEqualTo(CostController.CostTier.NORMAL);
        assertThat(controller.determineTier(0.5)).isEqualTo(CostController.CostTier.NORMAL);
        assertThat(controller.determineTier(0.79)).isEqualTo(CostController.CostTier.NORMAL);
    }

    @Test
    void atEightyPercentIsStressedTier() {
        assertThat(controller.determineTier(0.80)).isEqualTo(CostController.CostTier.STRESSED);
        assertThat(controller.determineTier(0.85)).isEqualTo(CostController.CostTier.STRESSED);
        assertThat(controller.determineTier(0.89)).isEqualTo(CostController.CostTier.STRESSED);
    }

    @Test
    void atNinetyPercentIsDegradedTier() {
        assertThat(controller.determineTier(0.90)).isEqualTo(CostController.CostTier.DEGRADED);
        assertThat(controller.determineTier(0.92)).isEqualTo(CostController.CostTier.DEGRADED);
        assertThat(controller.determineTier(0.94)).isEqualTo(CostController.CostTier.DEGRADED);
    }

    @Test
    void atNinetyFivePercentIsMinimalTier() {
        assertThat(controller.determineTier(0.95)).isEqualTo(CostController.CostTier.MINIMAL);
        assertThat(controller.determineTier(0.97)).isEqualTo(CostController.CostTier.MINIMAL);
        assertThat(controller.determineTier(0.99)).isEqualTo(CostController.CostTier.MINIMAL);
    }

    @Test
    void atOneHundredPercentIsEmergencyTier() {
        assertThat(controller.determineTier(1.00)).isEqualTo(CostController.CostTier.EMERGENCY);
        assertThat(controller.determineTier(1.20)).isEqualTo(CostController.CostTier.EMERGENCY);
    }

    // --- CostDecision for each tier ---

    @Test
    void normalTierHasNoRestrictions() {
        UUID fatherId = UUID.randomUUID();
        // 0% usage = NORMAL
        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.NORMAL);
        assertThat(decision.usesMiniModel()).isFalse();
        assertThat(decision.maxMemories()).isEqualTo(15);
        assertThat(decision.cachedOnly()).isFalse();
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    @Test
    void stressedTierSwitchesNonCriticalToMiniModel() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 40_000); // 80%

        // Non-critical type should use mini model
        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.STRESSED);
        assertThat(decision.usesMiniModel()).isTrue();
        assertThat(decision.maxMemories()).isEqualTo(15); // NOT reduced at this tier
        assertThat(decision.cachedOnly()).isFalse();
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    @Test
    void stressedTierKeepsPrimaryModelForCriticalTypes() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 40_000); // 80%

        // Critical types should NOT use mini model
        CostController.CostDecision onboarding = controller.evaluate(fatherId, ConversationType.ONBOARDING);
        assertThat(onboarding.usesMiniModel()).isFalse();

        CostController.CostDecision difficult = controller.evaluate(fatherId, ConversationType.DIFFICULT_SITUATION);
        assertThat(difficult.usesMiniModel()).isFalse();

        CostController.CostDecision reflection = controller.evaluate(fatherId, ConversationType.REFLECTION);
        assertThat(reflection.usesMiniModel()).isFalse();
    }

    @Test
    void degradedTierReducesMemoriesAndUsesMiniModel() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 45_000); // 90%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.DEGRADED);
        assertThat(decision.usesMiniModel()).isTrue();
        assertThat(decision.maxMemories()).isEqualTo(8); // Reduced
        assertThat(decision.cachedOnly()).isFalse(); // NOT cached-only at this tier
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    @Test
    void minimalTierUsesCachedResponsesOnly() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 47_500); // 95%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.MINIMAL);
        assertThat(decision.cachedOnly()).isTrue();
        assertThat(decision.aiCallsBlocked()).isFalse(); // NOT fully blocked at this tier
    }

    @Test
    void emergencyTierBlocksAllAiCalls() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 50_000); // 100%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.EMERGENCY);
        assertThat(decision.aiCallsBlocked()).isTrue();
        assertThat(decision.cachedOnly()).isTrue();
    }

    // --- Lower tiers don't apply higher-tier restrictions ---

    @Test
    void stressedTierDoesNotReduceMemories() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 42_000); // 84%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.FOLLOW_UP);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.STRESSED);
        assertThat(decision.maxMemories()).isEqualTo(15); // Full memories
    }

    @Test
    void stressedTierDoesNotUseCachedOnly() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 42_000); // 84%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.FOLLOW_UP);
        assertThat(decision.cachedOnly()).isFalse();
    }

    @Test
    void stressedTierDoesNotBlockAiCalls() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 42_000); // 84%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.FOLLOW_UP);
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    @Test
    void degradedTierDoesNotUseCachedOnly() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 46_000); // 92%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.DEGRADED);
        assertThat(decision.cachedOnly()).isFalse();
    }

    @Test
    void degradedTierDoesNotBlockAiCalls() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 46_000); // 92%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    @Test
    void minimalTierDoesNotBlockAiCalls() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 48_000); // 96%

        CostController.CostDecision decision = controller.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.MINIMAL);
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    // --- Critical type identification ---

    @Test
    void criticalTypesAreCorrectlyIdentified() {
        assertThat(CostController.isCriticalType(ConversationType.ONBOARDING)).isTrue();
        assertThat(CostController.isCriticalType(ConversationType.DIFFICULT_SITUATION)).isTrue();
        assertThat(CostController.isCriticalType(ConversationType.REFLECTION)).isTrue();
    }

    @Test
    void nonCriticalTypesAreCorrectlyIdentified() {
        assertThat(CostController.isCriticalType(ConversationType.DAILY_COACHING)).isFalse();
        assertThat(CostController.isCriticalType(ConversationType.FOLLOW_UP)).isFalse();
        assertThat(CostController.isCriticalType(ConversationType.CELEBRATION)).isFalse();
        assertThat(CostController.isCriticalType(ConversationType.MISSION_GENERATION)).isFalse();
        assertThat(CostController.isCriticalType(ConversationType.INACTIVITY_CHECK)).isFalse();
    }

    // --- Usage recording ---

    @Test
    void recordUsageDelegatesToTracker() {
        UUID fatherId = UUID.randomUUID();
        controller.recordUsage(fatherId, 5_000);

        assertThat(tracker.getTodayUsage(fatherId)).isEqualTo(5_000);
    }

    @Test
    void getCurrentUsagePercentReturnsCorrectValue() {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 25_000);

        assertThat(controller.getCurrentUsagePercent(fatherId)).isEqualTo(0.5);
    }

    // --- Budget configuration ---

    @Test
    void getDailyTokenBudgetReturnsConfiguredValue() {
        assertThat(controller.getDailyTokenBudget()).isEqualTo(BUDGET);
    }

    @Test
    void customBudgetIsUsedInEvaluation() {
        CostController smallBudget = new CostController(tracker, 1_000);
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 800); // 80% of 1000

        CostController.CostDecision decision = smallBudget.evaluate(fatherId, ConversationType.DAILY_COACHING);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.STRESSED);
    }

    @Test
    void constructorRejectsZeroBudget() {
        assertThatThrownBy(() -> new CostController(tracker, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dailyTokenBudget must be > 0");
    }

    @Test
    void constructorRejectsNegativeBudget() {
        assertThatThrownBy(() -> new CostController(tracker, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dailyTokenBudget must be > 0");
    }

    // --- All conversation types at each tier ---

    @ParameterizedTest
    @EnumSource(ConversationType.class)
    void allTypesGetCorrectTierAtNormal(ConversationType type) {
        UUID fatherId = UUID.randomUUID();
        CostController.CostDecision decision = controller.evaluate(fatherId, type);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.NORMAL);
    }

    @ParameterizedTest
    @EnumSource(ConversationType.class)
    void allTypesGetCorrectTierAtEmergency(ConversationType type) {
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, 50_000); // 100%

        CostController.CostDecision decision = controller.evaluate(fatherId, type);
        assertThat(decision.tier()).isEqualTo(CostController.CostTier.EMERGENCY);
        assertThat(decision.aiCallsBlocked()).isTrue();
    }
}
