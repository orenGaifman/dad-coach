package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a father's profile information is updated.
 */
public class FatherProfileUpdatedEvent extends WorkspaceDomainEvent {

    public FatherProfileUpdatedEvent(UUID fatherId) {
        super(fatherId);
    }
}
