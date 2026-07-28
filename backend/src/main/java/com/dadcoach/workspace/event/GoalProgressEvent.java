package com.dadcoach.workspace.event;

import java.time.Instant;
import java.util.UUID;

/**
 * External domain event representing progress on a parenting goal.
 *
 * <p>This event is expected to be published by the Goal domain (SPEC-002) when a
 * goal advances in progress. The Growth System only awards a signal if the
 * progress increases by at least 10% (handled by the GrowthSignalProcessor).</p>
 *
 * <p>This is a placeholder class that will eventually be owned by SPEC-002.
 * It is defined here to decouple the workspace from knowledge of other specs'
 * internal event structures.</p>
 */
public class GoalProgressEvent {

    private final UUID fatherId;
    private final UUID goalId;
    private final int previousProgressPercent;
    private final int currentProgressPercent;
    private final Instant occurredAt;

    public GoalProgressEvent(UUID fatherId, UUID goalId, int previousProgressPercent,
                             int currentProgressPercent, Instant occurredAt) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId is required");
        }
        if (goalId == null) {
            throw new IllegalArgumentException("goalId is required");
        }
        this.fatherId = fatherId;
        this.goalId = goalId;
        this.previousProgressPercent = previousProgressPercent;
        this.currentProgressPercent = currentProgressPercent;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public int getPreviousProgressPercent() {
        return previousProgressPercent;
    }

    public int getCurrentProgressPercent() {
        return currentProgressPercent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Returns the progress increase in percentage points.
     *
     * @return difference between current and previous progress
     */
    public int getProgressIncrease() {
        return currentProgressPercent - previousProgressPercent;
    }
}
