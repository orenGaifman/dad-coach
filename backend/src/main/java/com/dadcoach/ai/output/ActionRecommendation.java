package com.dadcoach.ai.output;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured output from the Decision Engine — recommends an action to take.
 * This is a recommendation — the application layer decides whether to execute it.
 *
 * <p>The AI never directly mutates state; this record is purely advisory output.
 *
 * @param fatherId     the father this action is for
 * @param action       the recommended action type
 * @param priority     the priority level that triggered this action (1-10)
 * @param reasoning    human-readable explanation of why this action was chosen
 * @param confidence   confidence in the recommendation (0.0-1.0)
 * @param evaluatedAt  when the decision was evaluated
 */
public record ActionRecommendation(
    UUID fatherId,
    ActionType action,
    int priority,
    String reasoning,
    double confidence,
    Instant evaluatedAt
) {
    /**
     * Action types from the Decision Engine's priority tree.
     */
    public enum ActionType {
        SAFETY_RESPONSE,
        EMPATHIZE,
        CELEBRATE,
        FOLLOW_UP,
        CONTINUE_CONVERSATION,
        REFLECT,
        CHALLENGE,
        GENERATE_MISSION,
        GENERATE_EASIER_MISSION,
        ENCOURAGE,
        WELCOME_BACK,
        ASK_QUESTION,
        WAIT
    }

    public ActionRecommendation {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("priority must be between 1 and 10");
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }

    /**
     * Creates a WAIT recommendation (when no proactive action should be taken).
     */
    public static ActionRecommendation wait(UUID fatherId, String reasoning) {
        return new ActionRecommendation(
            fatherId, ActionType.WAIT, 10, reasoning, 1.0, Instant.now()
        );
    }
}
