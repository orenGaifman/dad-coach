package com.dadcoach.onboarding.invitation;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of an invitation token validation attempt.
 * Contains the validation outcome and, when valid, metadata about the invitation.
 *
 * @see InvitationService#validate(String, String)
 */
public record InvitationValidationResult(
        Status status,
        UUID invitationId,
        InvitationType type,
        Instant expiresAt,
        int remainingUses
) {

    /**
     * Enum representing the outcome of an invitation validation.
     */
    public enum Status {
        /** Invitation is valid and can be used */
        VALID,
        /** Token does not exist in the system */
        NOT_FOUND,
        /** Invitation has expired (expires_at < now or status is EXPIRED) */
        EXPIRED,
        /** Invitation has been revoked by an admin */
        REVOKED,
        /** Invitation has reached max_uses */
        EXHAUSTED
    }

    /**
     * Creates a successful validation result with invitation metadata.
     */
    public static InvitationValidationResult valid(Invitation invitation) {
        int remaining = invitation.getMaxUses() - invitation.getCurrentUses();
        return new InvitationValidationResult(
                Status.VALID,
                invitation.getInvitationId(),
                invitation.getType(),
                invitation.getExpiresAt(),
                remaining
        );
    }

    /**
     * Creates a NOT_FOUND result (token doesn't exist).
     */
    public static InvitationValidationResult notFound() {
        return new InvitationValidationResult(Status.NOT_FOUND, null, null, null, 0);
    }

    /**
     * Creates an EXPIRED result.
     */
    public static InvitationValidationResult expired() {
        return new InvitationValidationResult(Status.EXPIRED, null, null, null, 0);
    }

    /**
     * Creates a REVOKED result.
     */
    public static InvitationValidationResult revoked() {
        return new InvitationValidationResult(Status.REVOKED, null, null, null, 0);
    }

    /**
     * Creates an EXHAUSTED result (max_uses reached).
     */
    public static InvitationValidationResult exhausted() {
        return new InvitationValidationResult(Status.EXHAUSTED, null, null, null, 0);
    }

    /**
     * Returns whether the validation was successful.
     */
    public boolean isValid() {
        return status == Status.VALID;
    }
}
