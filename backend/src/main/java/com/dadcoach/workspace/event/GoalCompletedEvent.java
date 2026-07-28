package com.dadcoach.workspace.event;

import java.time.Instant;
import java.util.UUID;

/**
 * External domain event representing a parenting goal being completed.
 *
 * <p>This event is expected to be published by the Goal domain (SPEC-002) when a
 * goal reaches 100% completion. It awards GOAL_COMPLETED growth signal points.</p>
 *
 * <p>This is a placeholder class that will eventually be owned by SPEC-002.
 * It is defined here to decouple the workspace from knowledge of other specs'
 * internal event structures.</p>
 */
public class GoalCompletedEvent {

    private final UUID fatherId;
    private final UUID goalId;
    private final Instant completedAt;

    public GoalCompletedEvent(UUID fatherId, UUID goalId, Instant completedAt) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId is required");
        }
        if (goalId == null) {
            throw new IllegalArgumentException("goalId is required");
        }
        this.fatherId = fatherId;
        this.goalId = goalId;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
