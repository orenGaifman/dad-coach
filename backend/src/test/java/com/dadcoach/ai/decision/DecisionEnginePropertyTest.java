package com.dadcoach.ai.decision;

import com.dadcoach.ai.output.ActionRecommendation;
import com.dadcoach.ai.output.ActionRecommendation.ActionType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the Decision Engine.
 *
 * <p>Tests Properties 3, 4, and 5 from the design document:
 * <ul>
 *   <li>Property 3: Priority ordering — highest matching priority always wins</li>
 *   <li>Property 4: Phase constraints — FOUNDATION → no CHALLENGE; phaseDay &lt; 7 → no REFLECT</li>
 *   <li>Property 5: Proactive gap enforcement — 4-hour gap for non-response actions</li>
 * </ul>
 */
class DecisionEnginePropertyTest {

    private static final Instant NOW = Instant.parse("2024-06-15T14:00:00Z");
    private static final Instant FIVE_HOURS_BEFORE = NOW.minus(Duration.ofHours(5));
    private static final Instant ONE_HOUR_BEFORE = NOW.minus(Duration.ofHours(1));
    private static final Instant THREE_DAYS_BEFORE = NOW.minus(Duration.ofDays(3));

    // Helper to build a DailyDecisionContext with all 27 params clearly labeled
    private DailyDecisionContext ctx(
            UUID fatherId, String phase, int phaseDay, int engagementScore,
            Instant lastProactiveMessage, Instant lastInteraction,
            boolean activeMissionExists, int streakDays, String inboundMessage,
            boolean hasCrisisIndicators, boolean hasNegativeEmotion,
            boolean missionCompletedRecently, int missionRating,
            boolean isStreakMilestone, boolean goalCompleted,
            boolean followUpSent, boolean fatherAnsweredQuestion,
            boolean isSunday, boolean hasReflectionThisWeek, boolean isPhaseTransition,
            Instant lastChallenge, Instant lastEncouragement, Instant lastConversation,
            boolean dailyMessageSent, boolean isQuietHours,
            int outboundCountToday, boolean isResponseToInbound) {
        return new DailyDecisionContext(
            fatherId, phase, phaseDay, engagementScore,
            lastProactiveMessage, lastInteraction,
            activeMissionExists, streakDays, inboundMessage,
            hasCrisisIndicators, hasNegativeEmotion,
            missionCompletedRecently, missionRating,
            isStreakMilestone, goalCompleted,
            followUpSent, fatherAnsweredQuestion,
            isSunday, hasReflectionThisWeek, isPhaseTransition,
            lastChallenge, lastEncouragement, lastConversation,
            dailyMessageSent, isQuietHours,
            outboundCountToday, isResponseToInbound
        );
    }

    // ===== Property 3: Decision Engine Priority Ordering =====

    /**
     * **Validates: Requirements 4.1**
     *
     * Property 3: If priority 1 (SAFETY) conditions are met, SAFETY_RESPONSE is always
     * returned regardless of other conditions.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 3: Decision Engine Priority Ordering")
    void safetyAlwaysTakesPrecedence(
            @ForAll("phasesExcludingFoundation") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 0, max = 100) int engagementScore,
            @ForAll @IntRange(min = 0, max = 5) int streakDays,
            @ForAll boolean hasNegativeEmotion,
            @ForAll boolean missionCompletedRecently,
            @ForAll boolean goalCompleted) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,
            !missionCompletedRecently, streakDays, "crisis message",
            true, hasNegativeEmotion,
            missionCompletedRecently, 4,
            false, goalCompleted,
            false, false,
            false, false, false,
            null, null, ONE_HOUR_BEFORE,
            false, false, 0, true
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action()).isEqualTo(ActionType.SAFETY_RESPONSE);
        assertThat(result.priority()).isEqualTo(1);
    }

    /**
     * **Validates: Requirements 4.1**
     *
     * When both Priority 2 (EMPATHIZE) and Priority 3+ match but NOT safety,
     * EMPATHIZE wins.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 3: Decision Engine Priority Ordering")
    void empathyTakesPrecedenceOverCelebrateAndLower(
            @ForAll("phasesExcludingFoundation") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 61, max = 100) int engagementScore) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,
            true, 30, "some message",
            false, true,  // NOT safety, HAS negative emotion → Priority 2
            true, 5,
            true, true,   // celebrate conditions match → P3
            false, false,
            true, false, false,
            null, null, ONE_HOUR_BEFORE,
            false, false, 0, true
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action()).isEqualTo(ActionType.EMPATHIZE);
        assertThat(result.priority()).isEqualTo(2);
    }

    /**
     * **Validates: Requirements 4.1**
     *
     * When CELEBRATE (P3) and lower priorities match, CELEBRATE wins.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 3: Decision Engine Priority Ordering")
    void celebrateTakesPrecedenceOverLowerPriorities(
            @ForAll("phasesExcludingFoundation") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 61, max = 100) int engagementScore) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,
            false, 30, null,
            false, false, // no safety, no empathy
            true, 5,
            true, true,   // celebrate: mission rating>=4, streak milestone, goal
            false, true,  // fatherAnsweredQuestion → P4
            true, false, false,
            null, null, THREE_DAYS_BEFORE,
            false, false, 0, true
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action()).isEqualTo(ActionType.CELEBRATE);
        assertThat(result.priority()).isEqualTo(3);
    }

    /**
     * **Validates: Requirements 4.1**
     *
     * General property: the returned priority is always within valid range [1, 10]
     * and the action is never null.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 3: Decision Engine Priority Ordering")
    void returnedPriorityIsAlwaysWithinValidRange(
            @ForAll("arbitraryContext") DailyDecisionContext context) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.priority()).isBetween(1, 10);
        assertThat(result.action()).isNotNull();
        assertThat(result.fatherId()).isEqualTo(context.fatherId());
    }

    // ===== Property 4: Decision Engine Phase Constraints =====

    /**
     * **Validates: Requirements 4.8**
     *
     * Property 4: For any context where the father is in FOUNDATION phase,
     * the Decision Engine SHALL never return CHALLENGE.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 4: Decision Engine Phase Constraints")
    void foundationPhaseNeverReturnsChallenge(
            @ForAll @IntRange(min = 1, max = 14) int phaseDay,
            @ForAll @IntRange(min = 61, max = 100) int engagementScore,
            @ForAll boolean hasNegativeEmotion,
            @ForAll boolean missionCompleted,
            @ForAll boolean goalCompleted,
            @ForAll boolean isSunday) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), "FOUNDATION", phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,
            !missionCompleted, 0, null,
            false, hasNegativeEmotion,
            missionCompleted, missionCompleted ? 5 : 0,
            false, goalCompleted,
            false, false,
            isSunday, false, false,
            null, null, ONE_HOUR_BEFORE,
            false, false, 0, true
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action())
            .as("FOUNDATION phase returned CHALLENGE with phaseDay=%d, engagement=%d",
                phaseDay, engagementScore)
            .isNotEqualTo(ActionType.CHALLENGE);
    }

    /**
     * **Validates: Requirements 4.8**
     *
     * Property 4: For any context where phase_day < 7, it SHALL never return REFLECT.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 4: Decision Engine Phase Constraints")
    void phaseDayBelow7NeverReturnsReflect(
            @ForAll("allPhases") String phase,
            @ForAll @IntRange(min = 1, max = 6) int phaseDay,
            @ForAll @IntRange(min = 0, max = 100) int engagementScore,
            @ForAll boolean hasNegativeEmotion,
            @ForAll boolean missionCompleted) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,
            !missionCompleted, 0, null,
            false, hasNegativeEmotion,
            missionCompleted, missionCompleted ? 3 : 0,
            false, false,
            false, false,
            true, false, true,  // Sunday + phase transition → would normally trigger REFLECT
            null, null, ONE_HOUR_BEFORE,
            false, false, 0, true
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action())
            .as("phase_day=%d (< 7) returned REFLECT", phaseDay)
            .isNotEqualTo(ActionType.REFLECT);
    }

    /**
     * **Validates: Requirements 4.8**
     *
     * Combined: FOUNDATION + phaseDay < 7 means neither CHALLENGE nor REFLECT.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 4: Decision Engine Phase Constraints")
    void foundationWithEarlyDayNeverReturnsChallengeOrReflect(
            @ForAll @IntRange(min = 1, max = 6) int phaseDay,
            @ForAll @IntRange(min = 0, max = 100) int engagementScore,
            @ForAll boolean missionCompleted,
            @ForAll boolean goalCompleted) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), "FOUNDATION", phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,
            !missionCompleted, 7, null,
            false, false,
            missionCompleted, missionCompleted ? 5 : 0,
            false, goalCompleted,
            false, false,
            true, false, true,
            null, null, THREE_DAYS_BEFORE,
            false, false, 0, true
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action()).isNotEqualTo(ActionType.CHALLENGE);
        assertThat(result.action()).isNotEqualTo(ActionType.REFLECT);
    }

    // ===== Property 5: Proactive Message Gap Enforcement =====

    /**
     * **Validates: Requirements 4.5**
     *
     * Property 5: For any action history where a proactive outbound message was sent
     * less than 4 hours ago, the Decision Engine SHALL return WAIT for any proactive
     * action (non-response-to-inbound).
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 5: Proactive Message Gap Enforcement")
    void proactiveGapReturnsWaitWhenViolated(
            @ForAll("allPhases") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 0, max = 100) int engagementScore,
            @ForAll @IntRange(min = 0, max = 239) int minutesSinceLastProactive) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        Instant lastProactive = NOW.minus(Duration.ofMinutes(minutesSinceLastProactive));

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, engagementScore,
            lastProactive, FIVE_HOURS_BEFORE,
            false, 30, null,
            false, true, true, 5, true, true,
            false, false,
            true, false, false,
            null, null, THREE_DAYS_BEFORE,
            false, false, 0,
            false // NOT response to inbound — gap applies
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action())
            .as("Expected WAIT due to 4-hour gap violation (minutes since: %d)", minutesSinceLastProactive)
            .isEqualTo(ActionType.WAIT);
    }

    /**
     * **Validates: Requirements 4.5**
     *
     * Messages responding to inbound are EXEMPT from the 4-hour gap.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 5: Proactive Message Gap Enforcement")
    void responseToInboundExemptFromGap(
            @ForAll("allPhases") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 0, max = 100) int engagementScore,
            @ForAll @IntRange(min = 0, max = 239) int minutesSinceLastProactive) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        Instant lastProactive = NOW.minus(Duration.ofMinutes(minutesSinceLastProactive));

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, engagementScore,
            lastProactive, ONE_HOUR_BEFORE,
            true, 0, "crisis message",
            true, false, false, 0, false, false,
            false, false,
            false, false, false,
            null, null, ONE_HOUR_BEFORE,
            false, false, 0,
            true // IS response to inbound — gap exempt
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action())
            .as("Response to inbound should be exempt from gap")
            .isEqualTo(ActionType.SAFETY_RESPONSE);
    }

    /**
     * **Validates: Requirements 4.5**
     *
     * When the gap has elapsed (>= 4 hours), normal priority evaluation occurs.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 5: Proactive Message Gap Enforcement")
    void proactiveGapAllowsActionAfterFourHours(
            @ForAll("allPhases") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 240, max = 1440) int minutesSinceLastProactive) {

        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        Instant lastProactive = NOW.minus(Duration.ofMinutes(minutesSinceLastProactive));

        DailyDecisionContext context = ctx(
            UUID.randomUUID(), phase, phaseDay, 50,
            lastProactive, ONE_HOUR_BEFORE,
            true, 0, null,
            false, true, false, 0, false, false,
            false, false,
            false, false, false,
            null, null, ONE_HOUR_BEFORE,
            false, false, 0,
            false // NOT inbound response — but gap is elapsed
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action())
            .as("After gap elapsed, should evaluate priorities normally")
            .isEqualTo(ActionType.EMPATHIZE);
    }

    /**
     * **Validates: Requirements 4.5**
     *
     * ActionHistory-based gap enforcement: when history records a recent proactive
     * message, it should block proactive actions.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 5: Proactive Message Gap Enforcement")
    void actionHistoryEnforcesGap(
            @ForAll("allPhases") String phase,
            @ForAll @IntRange(min = 7, max = 90) int phaseDay,
            @ForAll @IntRange(min = 0, max = 100) int engagementScore) {

        UUID fatherId = UUID.randomUUID();
        ActionHistory history = new ActionHistory();
        DecisionEngine engine = new DecisionEngine(history);

        // Record a proactive action 2 hours ago in ActionHistory
        history.recordAction(fatherId, ActionType.ENCOURAGE, NOW.minus(Duration.ofHours(2)), true);

        DailyDecisionContext context = ctx(
            fatherId, phase, phaseDay, engagementScore,
            null, FIVE_HOURS_BEFORE,  // no lastProactiveMessage in context
            false, 30, null,
            false, true, true, 5, true, true,
            false, false,
            true, false, false,
            null, null, THREE_DAYS_BEFORE,
            false, false, 0,
            false // NOT response to inbound — gap applies
        );

        ActionRecommendation result = engine.decide(context, NOW);

        assertThat(result.action())
            .as("ActionHistory gap enforcement should return WAIT")
            .isEqualTo(ActionType.WAIT);
    }

    // ===== Generators =====

    @Provide
    Arbitrary<String> allPhases() {
        return Arbitraries.of("FOUNDATION", "BUILDING", "DEEPENING", "MASTERY");
    }

    @Provide
    Arbitrary<String> phasesExcludingFoundation() {
        return Arbitraries.of("BUILDING", "DEEPENING", "MASTERY");
    }

    @Provide
    Arbitrary<DailyDecisionContext> arbitraryContext() {
        return Combinators.combine(
            Arbitraries.create(UUID::randomUUID),
            allPhases(),
            Arbitraries.integers().between(1, 90),
            Arbitraries.integers().between(0, 100),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.integers().between(0, 100)
        ).as((fatherId, phase, phaseDay, engagement, hasCrisis, hasEmotion,
              missionCompleted, streak) ->
            ctx(fatherId, phase, phaseDay, engagement,
                null, Instant.now().minus(Duration.ofHours(5)),
                !missionCompleted, streak,
                hasCrisis ? "crisis msg" : null,
                hasCrisis, hasEmotion,
                missionCompleted, missionCompleted ? 5 : 0,
                false, false,
                false, false,
                false, false, false,
                null, null, Instant.now().minus(Duration.ofDays(3)),
                false, false, 0, true
            )
        );
    }
}
