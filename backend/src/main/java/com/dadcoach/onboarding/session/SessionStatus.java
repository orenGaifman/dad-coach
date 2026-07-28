package com.dadcoach.onboarding.session;

/**
 * Represents the lifecycle status of an onboarding session.
 *
 * <p>State transitions:
 * <ul>
 *   <li>IN_PROGRESS → COMPLETED (successful provisioning)</li>
 *   <li>IN_PROGRESS → EXPIRED (72h TTL exceeded or inactive)</li>
 *   <li>IN_PROGRESS → ABANDONED (user explicitly abandons)</li>
 * </ul>
 *
 * <p>COMPLETED, EXPIRED, and ABANDONED are terminal states.
 */
public enum SessionStatus {

    IN_PROGRESS,
    COMPLETED,
    EXPIRED,
    ABANDONED;

    /**
     * Returns true if this status is a terminal state (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == ABANDONED;
    }

    /**
     * Checks whether a transition from this status to the target status is valid.
     *
     * @param target the desired target status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(SessionStatus target) {
        if (this.isTerminal()) {
            return false;
        }
        // Only IN_PROGRESS can transition to any terminal state
        return this == IN_PROGRESS && target.isTerminal();
    }
}
