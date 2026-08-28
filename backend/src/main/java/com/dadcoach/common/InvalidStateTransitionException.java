package com.dadcoach.common;

/** Thrown when an invalid state transition is attempted. */
public class InvalidStateTransitionException extends RuntimeException {
    private final String entityType;
    private final Long entityId;
    private final String fromState;
    private final String toState;

    public InvalidStateTransitionException(String entityType, Long entityId, String fromState, String toState) {
        super(String.format("Invalid state transition for %s[id=%d]: cannot transition from %s to %s",
                entityType, entityId, fromState, toState));
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
}
