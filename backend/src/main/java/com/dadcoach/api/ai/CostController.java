package com.dadcoach.api.ai;

import com.dadcoach.ai.routing.DailyUsageTracker;
import com.dadcoach.domain.conversation.ConversationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * Controls AI cost by enforcing degradation tiers based on daily per-father token usage.
 *
 * <p>Degradation levels (from design spec):
 * <ul>
 *   <li>Level 0 (Normal, &lt;80%): Full context, primary model, all features active</li>
 *   <li>Level 1 (Stressed, 80%): Non-critical calls use GPT-4o-mini</li>
 *   <li>Level 2 (Degraded, 90%): Reduced memories (8), mini model only</li>
 *   <li>Level 3 (Minimal, 95%): Cached/template responses only</li>
 *   <li>Level 4 (Emergency, 100%): No AI calls at all</li>
 * </ul>
 *
 * <p><strong>Important:</strong> Lower tiers do NOT apply restrictions from higher tiers.
 * At 80% you ONLY switch non-critical to mini model — you don't also reduce memories.
 */
public class CostController {

    private static final Logger log = LoggerFactory.getLogger(CostController.class);

    /**
     * Degradation tiers based on budget consumption percentage.
     */
    public enum CostTier {
        /** Normal operation — full context, primary model */
        NORMAL(0),
        /** 80% budget — non-critical calls use GPT-4o-mini */
        STRESSED(1),
        /** 90% budget — reduced memories (8), mini model only */
        DEGRADED(2),
        /** 95% budget — cached/template responses only */
        MINIMAL(3),
        /** 100% budget — no AI calls at all */
        EMERGENCY(4);

        private final int level;

        CostTier(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }

    /**
     * The result of a cost evaluation, containing the tier and its specific restrictions.
     */
    public record CostDecision(
        CostTier tier,
        boolean usesMiniModel,
        int maxMemories,
        boolean cachedOnly,
        boolean aiCallsBlocked
    ) {
        /**
         * Creates a NORMAL decision with no restrictions.
         */
        public static CostDecision normal() {
            return new CostDecision(CostTier.NORMAL, false, 15, false, false);
        }
    }

    /**
     * Conversation types considered "critical" and exempt from model downgrade at Tier 1.
     * Critical conversations use the full model even under budget pressure.
     */
    private static final Set<ConversationType> CRITICAL_TYPES = Set.of(
        ConversationType.ONBOARDING,
        ConversationType.DIFFICULT_SITUATION,
        ConversationType.REFLECTION
    );

    private static final double THRESHOLD_STRESSED = 0.80;
    private static final double THRESHOLD_DEGRADED = 0.90;
    private static final double THRESHOLD_MINIMAL = 0.95;
    private static final double THRESHOLD_EMERGENCY = 1.00;

    private static final int DEFAULT_DAILY_TOKEN_BUDGET = 50_000;
    private static final int REDUCED_MEMORY_COUNT = 8;
    private static final int NORMAL_MEMORY_COUNT = 15;

    private final DailyUsageTracker usageTracker;
    private final int dailyTokenBudget;

    /**
     * Creates a CostController with a custom daily budget.
     *
     * @param usageTracker    the usage tracker for reading/writing daily consumption
     * @param dailyTokenBudget the configurable daily token budget per father
     */
    public CostController(DailyUsageTracker usageTracker, int dailyTokenBudget) {
        if (dailyTokenBudget <= 0) {
            throw new IllegalArgumentException("dailyTokenBudget must be > 0, was: " + dailyTokenBudget);
        }
        this.usageTracker = usageTracker;
        this.dailyTokenBudget = dailyTokenBudget;
    }

    /**
     * Creates a CostController with the default daily budget (50,000 tokens).
     *
     * @param usageTracker the usage tracker
     */
    public CostController(DailyUsageTracker usageTracker) {
        this(usageTracker, DEFAULT_DAILY_TOKEN_BUDGET);
    }

    /**
     * Evaluates the current cost tier for a father based on their daily token usage.
     *
     * @param fatherId         the father's unique identifier
     * @param conversationType the type of conversation being requested
     * @return the cost decision with tier-specific restrictions
     */
    public CostDecision evaluate(UUID fatherId, ConversationType conversationType) {
        double usagePercent = usageTracker.getUsagePercentage(fatherId, dailyTokenBudget);
        CostTier tier = determineTier(usagePercent);

        log.debug("Father {} usage: {:.1f}% → tier {} for {}", fatherId, usagePercent * 100, tier, conversationType);

        return switch (tier) {
            case NORMAL -> CostDecision.normal();
            case STRESSED -> buildStressedDecision(conversationType);
            case DEGRADED -> buildDegradedDecision();
            case MINIMAL -> buildMinimalDecision();
            case EMERGENCY -> buildEmergencyDecision();
        };
    }

    /**
     * Determines the cost tier purely from usage percentage.
     * This is a pure function for testability.
     *
     * @param usagePercent the percentage of budget consumed (0.0 to 1.0+)
     * @return the applicable cost tier
     */
    public CostTier determineTier(double usagePercent) {
        if (usagePercent >= THRESHOLD_EMERGENCY) {
            return CostTier.EMERGENCY;
        } else if (usagePercent >= THRESHOLD_MINIMAL) {
            return CostTier.MINIMAL;
        } else if (usagePercent >= THRESHOLD_DEGRADED) {
            return CostTier.DEGRADED;
        } else if (usagePercent >= THRESHOLD_STRESSED) {
            return CostTier.STRESSED;
        } else {
            return CostTier.NORMAL;
        }
    }

    /**
     * Records token usage after an AI call completes.
     *
     * @param fatherId   the father's unique identifier
     * @param tokensUsed the total tokens consumed (input + output)
     */
    public void recordUsage(UUID fatherId, int tokensUsed) {
        usageTracker.recordUsage(fatherId, tokensUsed);
    }

    /**
     * Returns the configured daily token budget.
     */
    public int getDailyTokenBudget() {
        return dailyTokenBudget;
    }

    /**
     * Returns the current usage percentage for a father.
     */
    public double getCurrentUsagePercent(UUID fatherId) {
        return usageTracker.getUsagePercentage(fatherId, dailyTokenBudget);
    }

    /**
     * Returns whether a conversation type is considered critical.
     * Critical types are exempt from model downgrade at Tier 1.
     */
    public static boolean isCriticalType(ConversationType type) {
        return CRITICAL_TYPES.contains(type);
    }

    // --- Private decision builders (each tier applies ONLY its own restrictions) ---

    /**
     * Tier 1 (80%): Non-critical calls use mini model.
     * Does NOT reduce memories or block AI calls.
     */
    private CostDecision buildStressedDecision(ConversationType conversationType) {
        boolean useMini = !CRITICAL_TYPES.contains(conversationType);
        return new CostDecision(CostTier.STRESSED, useMini, NORMAL_MEMORY_COUNT, false, false);
    }

    /**
     * Tier 2 (90%): Reduced memories (8), mini model.
     * Does NOT use cached-only mode or block AI calls.
     */
    private CostDecision buildDegradedDecision() {
        return new CostDecision(CostTier.DEGRADED, true, REDUCED_MEMORY_COUNT, false, false);
    }

    /**
     * Tier 3 (95%): Cached/template responses only.
     * Does NOT technically block AI calls (cached responses are returned instead).
     */
    private CostDecision buildMinimalDecision() {
        return new CostDecision(CostTier.MINIMAL, true, REDUCED_MEMORY_COUNT, true, false);
    }

    /**
     * Tier 4 (100%): No AI calls at all.
     */
    private CostDecision buildEmergencyDecision() {
        return new CostDecision(CostTier.EMERGENCY, true, 0, true, true);
    }
}
