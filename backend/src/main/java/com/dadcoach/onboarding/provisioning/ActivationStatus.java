package com.dadcoach.onboarding.provisioning;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of an activation record.
 *
 * <p>State transitions:
 * <ul>
 *   <li>PENDING → LINK_CLICKED → MESSAGE_SENT → CONVERSATION_STARTED</li>
 *   <li>Any non-terminal → FAILED (timeout or error)</li>
 *   <li>FAILED → PENDING (retry, max 3)</li>
 * </ul>
 */
public enum ActivationStatus {
    PENDING,
    LINK_CLICKED,
    MESSAGE_SENT,
    CONVERSATION_STARTED,
    FAILED;

    /**
     * Returns true if this is a terminal (success) status.
     */
    public boolean isTerminal() {
        return this == CONVERSATION_STARTED;
    }

    /**
     * Returns the set of statuses this status can transition to.
     */
    public Set<ActivationStatus> getValidTransitions() {
        switch (this) {
            case PENDING:
                return EnumSet.of(LINK_CLICKED, MESSAGE_SENT, FAILED);
            case LINK_CLICKED:
                return EnumSet.of(MESSAGE_SENT, FAILED);
            case MESSAGE_SENT:
                return EnumSet.of(CONVERSATION_STARTED, FAILED);
            case FAILED:
                return EnumSet.of(PENDING);
            case CONVERSATION_STARTED:
            default:
                return EnumSet.noneOf(ActivationStatus.class);
        }
    }

    /**
     * Checks whether a transition from this status to the target is valid.
     *
     * @param target the desired target status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(ActivationStatus target) {
        return getValidTransitions().contains(target);
    }

    /**
     * Validates and returns the target status if the transition is valid.
     *
     * @param target the desired target status
     * @return the target status
     * @throws IllegalStateException if the transition is not allowed
     */
    public ActivationStatus transitionTo(ActivationStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("Invalid activation status transition: %s → %s", this, target));
        }
        return target;
    }
}
