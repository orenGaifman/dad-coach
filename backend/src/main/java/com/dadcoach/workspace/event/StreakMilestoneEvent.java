package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a father's streak reaches a milestone threshold.
 *
 * <p>Milestone thresholds: 7, 14, 21, 30, 60, 90, 180, 365 days.
 * This event triggers celebration creation and downstream processing
 * (e.g., notification dispatch, activity feed recording).</p>
 *
 * <p>Each milestone is emitted at most once per streak. If the father's streak
 * is reset and rebuilt to the same milestone, a new event is emitted for the
 * new streak occurrence.</p>
 */
public class StreakMilestoneEvent extends WorkspaceDomainEvent {

    private final int milestoneDays;
    private final int previousStreakDays;

    public StreakMilestoneEvent(UUID fatherId, int milestoneDays, int previousStreakDays) {
        super(fatherId);
        this.milestoneDays = milestoneDays;
        this.previousStreakDays = previousStreakDays;
    }

    /**
     * Returns the milestone threshold that was reached (e.g., 7, 14, 21, 30, 60, 90, 180, 365).
     *
     * @return the milestone day count
     */
    public int getMilestoneDays() {
        return milestoneDays;
    }

    /**
     * Returns the streak day count before the milestone was reached.
     *
     * @return the previous streak days (milestone - 1 in most cases)
     */
    public int getPreviousStreakDays() {
        return previousStreakDays;
    }
}
