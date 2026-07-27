package com.dadcoach.ai.decision;

import com.dadcoach.ai.output.ActionRecommendation;
import com.dadcoach.ai.output.ActionRecommendation.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Priority-tree Decision Engine that selects the daily action for each father.
 *
 * <p>Evaluates a strict priority hierarchy:
 * <ol>
 *   <li>SAFETY_RESPONSE — crisis indicators (overrides everything)</li>
 *   <li>EMPATHIZE — negative emotion detected</li>
 *   <li>CELEBRATE — mission rating ≥ 4, streak milestone, goal completed</li>
 *   <li>FOLLOW_UP — mission completed &lt; 24h ago, father answered a question</li>
 *   <li>REFLECT — Sunday + no reflection this week, phase transition</li>
 *   <li>CHALLENGE — engagement &gt; 60, phase ≥ BUILDING, last challenge &gt; 7 days</li>
 *   <li>GENERATE_MISSION — no active mission, daily coaching time</li>
 *   <li>ENCOURAGE — engagement &lt; 40, last encouragement &gt; 3 days</li>
 *   <li>ASK_QUESTION — no conversation in 2 days</li>
 *   <li>WAIT — daily message sent, quiet hours, 5+ outbound today</li>
 * </ol>
 *
 * <p>Phase constraints:
 * <ul>
 *   <li>FOUNDATION phase → never CHALLENGE</li>
 *   <li>phase_day &lt; 7 → never REFLECT</li>
 * </ul>
 *
 * <p>The 4-hour gap is enforced for all proactive messages.
 * Messages responding to inbound are exempt from the gap.
 *
 * <p>This engine is stateless — it receives all context and returns a recommendation.
 * The {@link ActionHistory} tracks per-father history for gap enforcement.
 */
public class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private static final Duration PROACTIVE_GAP = Duration.ofHours(4);
    private static final Duration CHALLENGE_COOLDOWN = Duration.ofDays(7);
    private static final Duration ENCOURAGEMENT_COOLDOWN = Duration.ofDays(3);
    private static final Duration CONVERSATION_SILENCE_THRESHOLD = Duration.ofDays(2);
    private static final int MAX_OUTBOUND_PER_DAY = 5;

    private static final String PHASE_FOUNDATION = "FOUNDATION";

    private final ActionHistory actionHistory;

    public DecisionEngine(ActionHistory actionHistory) {
        this.actionHistory = actionHistory;
    }

    /**
     * Decides what action to take for a father based on the priority tree.
     *
     * <p>Evaluates priorities 1 through 10 in strict order. The first matching
     * priority wins. Phase constraints and gap enforcement are applied as filters.
     *
     * @param context the decision context containing all required signals
     * @return the recommended action with priority level and reasoning
     */
    public ActionRecommendation decide(DailyDecisionContext context) {
        Instant now = Instant.now();
        return decide(context, now);
    }

    /**
     * Decides what action to take, using the provided timestamp (for testability).
     *
     * @param context the decision context
     * @param now     the current time to use for gap calculations
     * @return the recommended action
     */
    public ActionRecommendation decide(DailyDecisionContext context, Instant now) {
        log.debug("Evaluating decision tree for father={}, phase={}, day={}",
            context.fatherId(), context.currentPhase(), context.phaseDay());

        // Check 4-hour proactive gap FIRST (unless responding to inbound)
        if (!context.isResponseToInbound() && isProactiveGapViolated(context, now)) {
            return buildRecommendation(context, ActionType.WAIT, 10,
                "4-hour proactive gap not yet elapsed", 1.0, now);
        }

        // Priority 1: SAFETY_RESPONSE
        if (context.hasCrisisIndicators()) {
            return buildRecommendation(context, ActionType.SAFETY_RESPONSE, 1,
                "Crisis indicators detected in message", 0.95, now);
        }

        // Priority 2: EMPATHIZE
        if (context.hasNegativeEmotion()) {
            return buildRecommendation(context, ActionType.EMPATHIZE, 2,
                "Negative emotion detected — empathy first", 0.85, now);
        }

        // Priority 3: CELEBRATE
        if (matchesCelebrate(context)) {
            return buildRecommendation(context, ActionType.CELEBRATE, 3,
                buildCelebrateReasoning(context), 0.9, now);
        }

        // Priority 4: FOLLOW_UP
        ActionType followUpAction = matchesFollowUp(context);
        if (followUpAction != null) {
            return buildRecommendation(context, followUpAction, 4,
                followUpAction == ActionType.CONTINUE_CONVERSATION
                    ? "Father answered a question — continue conversation"
                    : "Mission completed recently — follow up",
                0.8, now);
        }

        // Priority 5: REFLECT (phase constraint: phaseDay < 7 → never REFLECT)
        if (matchesReflect(context)) {
            return buildRecommendation(context, ActionType.REFLECT, 5,
                context.isPhaseTransition()
                    ? "Phase transition detected — time for reflection"
                    : "Sunday with no weekly reflection yet",
                0.8, now);
        }

        // Priority 6: CHALLENGE (phase constraint: FOUNDATION → never CHALLENGE)
        if (matchesChallenge(context, now)) {
            return buildRecommendation(context, ActionType.CHALLENGE, 6,
                "High engagement, phase allows challenges, cooldown elapsed", 0.75, now);
        }

        // Priority 7: GENERATE_MISSION
        ActionType missionAction = matchesMission(context);
        if (missionAction != null) {
            return buildRecommendation(context, missionAction, 7,
                missionAction == ActionType.GENERATE_EASIER_MISSION
                    ? "Current mission expired without action — generate easier mission"
                    : "No active mission — generate new mission",
                0.7, now);
        }

        // Priority 8: ENCOURAGE
        ActionType encourageAction = matchesEncourage(context, now);
        if (encourageAction != null) {
            return buildRecommendation(context, encourageAction, 8,
                encourageAction == ActionType.WELCOME_BACK
                    ? "Father returning after absence"
                    : "Low engagement — encouragement needed",
                0.7, now);
        }

        // Priority 9: ASK_QUESTION
        if (matchesAskQuestion(context, now)) {
            return buildRecommendation(context, ActionType.ASK_QUESTION, 9,
                "No conversation in 2+ days — initiate engagement", 0.6, now);
        }

        // Priority 10: WAIT (default)
        return buildRecommendation(context, ActionType.WAIT, 10,
            buildWaitReasoning(context), 1.0, now);
    }

    // ===== Priority Matching Methods =====

    private boolean matchesCelebrate(DailyDecisionContext context) {
        return (context.missionCompletedRecently() && context.missionRating() >= 4)
            || context.isStreakMilestone()
            || context.goalCompleted();
    }

    private String buildCelebrateReasoning(DailyDecisionContext context) {
        if (context.missionCompletedRecently() && context.missionRating() >= 4) {
            return "Mission completed with high rating (" + context.missionRating() + ")";
        }
        if (context.isStreakMilestone()) {
            return "Streak milestone reached (" + context.streakDays() + " days)";
        }
        return "Goal completed";
    }

    private ActionType matchesFollowUp(DailyDecisionContext context) {
        if (context.fatherAnsweredQuestion()) {
            return ActionType.CONTINUE_CONVERSATION;
        }
        if (context.missionCompletedRecently() && !context.followUpSent()) {
            return ActionType.FOLLOW_UP;
        }
        return null;
    }

    private boolean matchesReflect(DailyDecisionContext context) {
        // Phase constraint: phase_day < 7 → never REFLECT
        if (context.phaseDay() < 7) {
            return false;
        }
        return (context.isSunday() && !context.hasReflectionThisWeek())
            || context.isPhaseTransition();
    }

    private boolean matchesChallenge(DailyDecisionContext context, Instant now) {
        // Phase constraint: FOUNDATION → never CHALLENGE
        if (PHASE_FOUNDATION.equalsIgnoreCase(context.currentPhase())) {
            return false;
        }
        if (context.engagementScore() <= 60) {
            return false;
        }
        // Check challenge cooldown (7 days)
        if (context.lastChallenge() != null) {
            Duration sinceLastChallenge = Duration.between(context.lastChallenge(), now);
            if (sinceLastChallenge.compareTo(CHALLENGE_COOLDOWN) < 0) {
                return false;
            }
        }
        return true;
    }

    private ActionType matchesMission(DailyDecisionContext context) {
        if (!context.activeMissionExists()) {
            return ActionType.GENERATE_MISSION;
        }
        // If current mission expired without action, generate easier one
        // This would require additional context fields; for now, return null
        return null;
    }

    private ActionType matchesEncourage(DailyDecisionContext context, Instant now) {
        // Father returning after 3+ day absence
        if (context.lastInteraction() != null) {
            Duration sinceLastInteraction = Duration.between(context.lastInteraction(), now);
            if (sinceLastInteraction.toDays() >= 3 && context.isResponseToInbound()) {
                return ActionType.WELCOME_BACK;
            }
        }

        // Low engagement with encouragement cooldown
        if (context.engagementScore() < 40) {
            if (context.lastEncouragement() != null) {
                Duration sinceLastEncouragement = Duration.between(context.lastEncouragement(), now);
                if (sinceLastEncouragement.compareTo(ENCOURAGEMENT_COOLDOWN) >= 0) {
                    return ActionType.ENCOURAGE;
                }
            } else {
                // No encouragement ever sent
                return ActionType.ENCOURAGE;
            }
        }
        return null;
    }

    private boolean matchesAskQuestion(DailyDecisionContext context, Instant now) {
        if (context.activeMissionExists()) {
            return false; // Spec says "no pending mission"
        }
        if (context.lastConversation() != null) {
            Duration sinceLastConversation = Duration.between(context.lastConversation(), now);
            return sinceLastConversation.compareTo(CONVERSATION_SILENCE_THRESHOLD) >= 0;
        }
        // No conversation ever → ask
        return true;
    }

    private boolean isProactiveGapViolated(DailyDecisionContext context, Instant now) {
        // Check from the context's lastProactiveMessage field first
        if (context.lastProactiveMessage() != null) {
            Duration elapsed = Duration.between(context.lastProactiveMessage(), now);
            if (elapsed.compareTo(PROACTIVE_GAP) < 0) {
                return true;
            }
        }
        // Also check from ActionHistory
        return actionHistory.isProactiveGapViolated(context.fatherId(), now);
    }

    private String buildWaitReasoning(DailyDecisionContext context) {
        if (context.dailyMessageSent()) {
            return "Daily message already sent";
        }
        if (context.isQuietHours()) {
            return "Currently in quiet hours";
        }
        if (context.outboundCountToday() >= MAX_OUTBOUND_PER_DAY) {
            return "Maximum outbound messages (5) reached for today";
        }
        return "No matching priority — defaulting to WAIT";
    }

    private ActionRecommendation buildRecommendation(
            DailyDecisionContext context,
            ActionType action,
            int priority,
            String reasoning,
            double confidence,
            Instant evaluatedAt) {
        return new ActionRecommendation(
            context.fatherId(),
            action,
            priority,
            reasoning,
            confidence,
            evaluatedAt
        );
    }
}
