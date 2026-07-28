package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a father reaches a new milestone.
 *
 * <p>This event is emitted by the {@code GrowthSignalProcessorImpl} after milestone
 * evaluation detects newly reached milestones. Consumed by downstream listeners
 * (celebration creation, notification dispatch, analytics) without coupling to
 * the milestone evaluation logic.</p>
 *
 * @see WorkspaceDomainEvent
 */
public class MilestoneReachedEvent extends WorkspaceDomainEvent {

    private final UUID milestoneId;
    private final String milestoneName;

    public MilestoneReachedEvent(UUID fatherId, UUID milestoneId, String milestoneName) {
        super(fatherId);
        this.milestoneId = milestoneId;
        this.milestoneName = milestoneName;
    }

    public UUID getMilestoneId() {
        return milestoneId;
    }

    public String getMilestoneName() {
        return milestoneName;
    }
}
