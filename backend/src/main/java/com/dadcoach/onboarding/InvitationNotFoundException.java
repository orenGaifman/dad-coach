package com.dadcoach.onboarding;

/**
 * Thrown when an invitation token is not found.
 */
public class InvitationNotFoundException extends RuntimeException {
    public InvitationNotFoundException(String token) {
        super("Invitation not found for token: " + token.substring(0, Math.min(8, token.length())) + "...");
    }
}
