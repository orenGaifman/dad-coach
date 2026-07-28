package com.dadcoach.onboarding;

/**
 * Thrown when an invitation has reached its maximum number of uses.
 */
public class InvitationExhaustedException extends RuntimeException {
    public InvitationExhaustedException() {
        super("Invitation has reached maximum uses");
    }
}
