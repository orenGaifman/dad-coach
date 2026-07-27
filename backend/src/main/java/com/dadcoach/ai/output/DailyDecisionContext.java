package com.dadcoach.ai.output;

import java.time.Instant;
import java.util.UUID;

/**
 * Context for the Decision Engine to evaluate what action to take for a father.
 *
 * @param fatherId              the father's unique identifier
 * @param currentPhase          current coaching phase (FOUNDATION, BUILDING, DEEPENING, MASTERY)
 * @param phaseDay              day count within current phase
 * @param engagementScore       current engagement score (0-100)
 * @param lastProactiveMessage  timestamp of last proactive outbound message
 * @param lastInteraction       timestamp of last father interaction
 * @param activeMissionExists   whether an active mission is assigned
 * @param streakDays            current streak count
 * @param inboundMessage        optional inbound message triggering the decision (may be null for scheduled triggers)
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
    String inboundMessage
) {
    public DailyDecisionContext {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (currentPhase == null || currentPhase.isBlank()) {
            throw new IllegalArgumentException("currentPhase must not be null or blank");
        }
    }
}
