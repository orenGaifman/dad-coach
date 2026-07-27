package com.dadcoach.common;

/**
 * Thrown when an invalid state transition is attempted on a domain entity.
 *
 * <p>This exception captures the entity type, entity ID, and the invalid
 * from/to states to provide clear diagnostic information for debugging
 * and audit logging.</p>
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final String entityType;
    private final Long entityId;
    private final String fromState;
    private final String toState;

    /**
     * Creates a new InvalidStateTransitionException.
     *
     * @param entityType the type of entity (e.g., "Father", "Mission")
     * @param entityId   the ID of the entity
     * @param fromState  the current state of the entity
     * @param toState    the target state that was rejected
     */
    public InvalidStateTransitionException(String entityType, Long entityId, String fromState, String toState) {
        super(formatMessage(entityType, entityId, fromState, toState));
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    @Override
    public String getMessage() {
        return formatMessage(entityType, entityId, fromState, toState);
    }

    private static String formatMessage(String entityType, Long entityId, String fromState, String toState) {
        return String.format(
                "Invalid state transition for %s[id=%d]: cannot transition from %s to %s",
                entityType, entityId, fromState, toState
        );
    }
}
