package com.dadcoach.workspace.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for all workspace domain events.
 *
 * <p>Every domain event in the workspace bounded context carries a unique event ID,
 * the father it relates to, and the time it occurred.</p>
 */
public abstract class WorkspaceDomainEvent {

    private final UUID eventId;
    private final UUID fatherId;
    private final Instant occurredAt;

    protected WorkspaceDomainEvent(UUID fatherId) {
        this.eventId = UUID.randomUUID();
        this.fatherId = fatherId;
        this.occurredAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
