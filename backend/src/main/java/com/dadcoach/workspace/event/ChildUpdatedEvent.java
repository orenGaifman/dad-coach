package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a child's information is updated.
 */
public class ChildUpdatedEvent extends WorkspaceDomainEvent {

    private final UUID childId;

    public ChildUpdatedEvent(UUID fatherId, UUID childId) {
        super(fatherId);
        this.childId = childId;
    }

    public UUID getChildId() {
        return childId;
    }
}
