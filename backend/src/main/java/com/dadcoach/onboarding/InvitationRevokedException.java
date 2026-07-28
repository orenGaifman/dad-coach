package com.dadcoach.onboarding;

/**
 * Thrown when an invitation has been revoked.
 */
public class InvitationRevokedException extends RuntimeException {
    public InvitationRevokedException() {
        super("Invitation has been revoked");
    }
}
