package com.dadcoach.ai.routing;

import com.dadcoach.conversation.ConversationType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for CostController tier enforcement.
 *
 * <p><strong>Validates: Requirements 11.1, 11.6</strong></p>
 *
 * <p>Property 12: Cost Controller Tier Enforcement —
 * For any father's daily token consumption level, the correct cost-reduction tier SHALL be enforced:
 * at 80% → all non-critical calls use GPT-4o-mini; at 90% → memory injection reduced to 8;
 * at 95% → cached/template responses only; at 100% → no AI calls.
 * Lower tiers SHALL NOT apply restrictions from higher tiers.
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
class CostControllerPropertyTest {

    private static final int BUDGET = 50_000;

    // --- Property 12: Correct tier applied at each threshold ---

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * For any usage below 80%, the NORMAL tier applies with no restrictions.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void belowEightyPercentIsAlwaysNormalTier(
            @ForAll @DoubleRange(min = 0.0, max = 0.79) double usagePercent,
            @ForAll("allConversationTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();

        // Record usage to match the percentage
        int tokensUsed = (int) (usagePercent * BUDGET);
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.NORMAL);
        assertThat(decision.usesMiniModel()).isFalse();
        assertThat(decision.maxMemories()).isEqualTo(15);
        assertThat(decision.cachedOnly()).isFalse();
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * At 80-89% usage, non-critical calls use mini model. Critical calls keep primary model.
     * Memory count is NOT reduced (lower tiers don't apply higher-tier restrictions).
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void atEightyToNinetyPercentNonCriticalUsesMiniModel(
            @ForAll @IntRange(min = 40000, max = 44999) int tokensUsed,
            @ForAll("nonCriticalTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.STRESSED);
        assertThat(decision.usesMiniModel()).isTrue();
        // Lower tier restriction check: memories NOT reduced
        assertThat(decision.maxMemories()).isEqualTo(15);
        assertThat(decision.cachedOnly()).isFalse();
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * At 80-89% usage, critical calls keep the primary model (not downgraded to mini).
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void atEightyToNinetyPercentCriticalKeepsPrimaryModel(
            @ForAll @IntRange(min = 40000, max = 44999) int tokensUsed,
            @ForAll("criticalTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.STRESSED);
        assertThat(decision.usesMiniModel()).isFalse(); // Critical stays on primary
        assertThat(decision.maxMemories()).isEqualTo(15);
        assertThat(decision.cachedOnly()).isFalse();
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * At 90-94% usage, memory injection is reduced to 8 and mini model is used.
     * Cached-only is NOT enforced (lower tiers don't apply higher-tier restrictions).
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void atNinetyToNinetyFivePercentMemoryIsReduced(
            @ForAll @IntRange(min = 45000, max = 47499) int tokensUsed,
            @ForAll("allConversationTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.DEGRADED);
        assertThat(decision.usesMiniModel()).isTrue();
        assertThat(decision.maxMemories()).isEqualTo(8);
        // Lower tier restriction check: NOT cached-only, NOT blocked
        assertThat(decision.cachedOnly()).isFalse();
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * At 95-99% usage, cached/template responses only.
     * AI calls are NOT fully blocked (lower tiers don't apply higher-tier restrictions).
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void atNinetyFiveToHundredPercentCachedOnly(
            @ForAll @IntRange(min = 47500, max = 49999) int tokensUsed,
            @ForAll("allConversationTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.MINIMAL);
        assertThat(decision.cachedOnly()).isTrue();
        // Lower tier restriction check: NOT fully blocked
        assertThat(decision.aiCallsBlocked()).isFalse();
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * At 100%+ usage, no AI calls at all.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void atHundredPercentNoAiCalls(
            @ForAll @IntRange(min = 50000, max = 75000) int tokensUsed,
            @ForAll("allConversationTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        assertThat(decision.tier()).isEqualTo(CostController.CostTier.EMERGENCY);
        assertThat(decision.aiCallsBlocked()).isTrue();
    }

    // --- Property: Lower tiers don't apply higher-tier restrictions ---

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * For any usage percentage, the tier-specific restrictions are exclusive:
     * a tier only applies its OWN restrictions, never those of higher tiers.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void lowerTiersNeverApplyHigherTierRestrictions(
            @ForAll @DoubleRange(min = 0.0, max = 1.5) double usagePercent,
            @ForAll("allConversationTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, BUDGET);
        UUID fatherId = UUID.randomUUID();

        int tokensUsed = (int) (usagePercent * BUDGET);
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        switch (decision.tier()) {
            case NORMAL -> {
                // No restrictions at all
                assertThat(decision.usesMiniModel()).isFalse();
                assertThat(decision.maxMemories()).isEqualTo(15);
                assertThat(decision.cachedOnly()).isFalse();
                assertThat(decision.aiCallsBlocked()).isFalse();
            }
            case STRESSED -> {
                // Only model downgrade for non-critical; memories stay at 15
                assertThat(decision.maxMemories()).isEqualTo(15);
                assertThat(decision.cachedOnly()).isFalse();
                assertThat(decision.aiCallsBlocked()).isFalse();
            }
            case DEGRADED -> {
                // Model downgrade + reduced memories; NOT cached-only, NOT blocked
                assertThat(decision.maxMemories()).isEqualTo(8);
                assertThat(decision.cachedOnly()).isFalse();
                assertThat(decision.aiCallsBlocked()).isFalse();
            }
            case MINIMAL -> {
                // Cached-only; NOT fully blocked
                assertThat(decision.cachedOnly()).isTrue();
                assertThat(decision.aiCallsBlocked()).isFalse();
            }
            case EMERGENCY -> {
                // All blocked
                assertThat(decision.aiCallsBlocked()).isTrue();
            }
        }
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * The tier determination is monotonic: higher usage always leads to same or higher tier.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void tierIsMonotonicWithUsage(
            @ForAll @DoubleRange(min = 0.0, max = 1.5) double usage1,
            @ForAll @DoubleRange(min = 0.0, max = 1.5) double usage2) {

        CostController controller = new CostController(new DailyUsageTracker(), BUDGET);

        CostController.CostTier tier1 = controller.determineTier(usage1);
        CostController.CostTier tier2 = controller.determineTier(usage2);

        if (usage1 <= usage2) {
            assertThat(tier1.level()).isLessThanOrEqualTo(tier2.level());
        } else {
            assertThat(tier1.level()).isGreaterThanOrEqualTo(tier2.level());
        }
    }

    /**
     * **Validates: Requirements 11.1, 11.6**
     *
     * For any budget size, the tier thresholds are respected proportionally.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 12: Cost Controller Tier Enforcement")
    void tierThresholdsWorkWithAnyBudget(
            @ForAll @IntRange(min = 1000, max = 200000) int budget,
            @ForAll @DoubleRange(min = 0.0, max = 1.5) double usagePercent,
            @ForAll("allConversationTypes") ConversationType type) {

        DailyUsageTracker tracker = new DailyUsageTracker();
        CostController controller = new CostController(tracker, budget);
        UUID fatherId = UUID.randomUUID();

        int tokensUsed = (int) (usagePercent * budget);
        tracker.recordUsage(fatherId, tokensUsed);

        CostController.CostDecision decision = controller.evaluate(fatherId, type);

        // Verify the correct tier based on percentage
        double actualPercent = (double) tokensUsed / budget;
        CostController.CostTier expectedTier = controller.determineTier(actualPercent);
        assertThat(decision.tier()).isEqualTo(expectedTier);
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<ConversationType> allConversationTypes() {
        return Arbitraries.of(ConversationType.values());
    }

    @Provide
    Arbitrary<ConversationType> nonCriticalTypes() {
        return Arbitraries.of(
            ConversationType.DAILY_COACHING,
            ConversationType.FOLLOW_UP,
            ConversationType.CELEBRATION,
            ConversationType.MISSION_GENERATION,
            ConversationType.INACTIVITY_CHECK
        );
    }

    @Provide
    Arbitrary<ConversationType> criticalTypes() {
        return Arbitraries.of(
            ConversationType.ONBOARDING,
            ConversationType.DIFFICULT_SITUATION,
            ConversationType.REFLECTION
        );
    }
}
