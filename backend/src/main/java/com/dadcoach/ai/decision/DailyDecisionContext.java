package com.dadcoach.ai.decision;

import java.time.Instant;
import java.util.UUID;

/**
 * Rich decision context for the Decision Engine's priority-tree evaluation.
 *
 * <p>This extends the simpler {@code com.dadcoach.ai.output.DailyDecisionContext}
 * with all the signals needed to evaluate the full 10-level priority tree.
 *
 * <p>The Decision Engine uses this context to select the daily action for a father
 * based on the hierarchy: safety > empathy > celebrate > follow_up > reflect >
 * challenge > mission > encourage > ask_question > wait.
 *
 * @param fatherId                unique identifier for the father
 * @param currentPhase            coaching phase (FOUNDATION, BUILDING, DEEPENING, MASTERY)
 * @param phaseDay                number of days in the current phase
 * @param engagementScore         current engagement score (0-100)
 * @param lastProactiveMessage    timestamp of last proactive outbound message (null if none)
 * @param lastInteraction         timestamp of last father interaction (null if none)
 * @param activeMissionExists     whether an active mission is currently assigned
 * @param streakDays              current streak count
 * @param inboundMessage          optional inbound message content (null for scheduled triggers)
 * @param hasCrisisIndicators     whether inbound message has crisis indicators (from safety classifier)
 * @param hasNegativeEmotion      whether father is expressing negative emotion
 * @param missionCompletedRecently whether a mission was completed in the last 24 hours
 * @param missionRating           last mission outcome rating (0 if none)
 * @param isStreakMilestone       whether current streak matches a milestone (7, 14, 21, 30, 60, 90)
 * @param goalCompleted           whether a goal was just completed
 * @param followUpSent            whether a follow-up has been sent for the last completed mission
 * @param fatherAnsweredQuestion  whether father answered a question in the previous message
 * @param isSunday                whether today is Sunday
 * @param hasReflectionThisWeek   whether the father has done a reflection this week
 * @param isPhaseTransition       whether a phase transition is in progress
 * @param lastChallenge           timestamp of last CHALLENGE action (null if none)
 * @param lastEncouragement       timestamp of last ENCOURAGE action (null if none)
 * @param lastConversation        timestamp of last conversation (null if none)
 * @param dailyMessageSent        whether a daily message has already been sent today
 * @param isQuietHours            whether it's currently quiet hours
 * @param outboundCountToday      number of outbound messages sent today
 * @param isResponseToInbound     whether this decision is triggered by an inbound message (exempt from gap)
 */
public record DailyDecisionContext(
    UUID fatherId,
    String currentPhase,
    int phaseDay,
    int engagementScore,
    Instant lastProactiveMessage,
    Instant lastInteraction,
    boolean activeMissionExists,
    int streakDays,
    String inboundMessage,
    boolean hasCrisisIndicators,
    boolean hasNegativeEmotion,
    boolean missionCompletedRecently,
    int missionRating,
    boolean isStreakMilestone,
    boolean goalCompleted,
    boolean followUpSent,
    boolean fatherAnsweredQuestion,
    boolean isSunday,
    boolean hasReflectionThisWeek,
    boolean isPhaseTransition,
    Instant lastChallenge,
    Instant lastEncouragement,
    Instant lastConversation,
    boolean dailyMessageSent,
    boolean isQuietHours,
    int outboundCountToday,
    boolean isResponseToInbound
) {
    public DailyDecisionContext {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (currentPhase == null || currentPhase.isBlank()) {
            throw new IllegalArgumentException("currentPhase must not be null or blank");
        }
        if (engagementScore < 0) {
            engagementScore = 0;
        }
        if (engagementScore > 100) {
            engagementScore = 100;
        }
        if (outboundCountToday < 0) {
            outboundCountToday = 0;
        }
    }
}
