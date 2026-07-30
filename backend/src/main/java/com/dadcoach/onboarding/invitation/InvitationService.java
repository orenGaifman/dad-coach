package com.dadcoach.onboarding.invitation;

import java.util.UUID;

/**
 * Service interface for invitation lifecycle management.
 * Handles creation, validation, state transitions, and batch expiration of invitations.
 *
 * @see <a href="Requirement 1">Invitation System</a>
 */
public interface InvitationService {

    /**
     * Creates a new invitation with a generated token and expiration based on type.
     * SINGLE_USE expires after 7 days; REUSABLE expires after 90 days (Req 1 criteria 5).
     *
     * @param request   the creation request containing type, metadata, and optional max_uses override
     * @param createdBy the UUID of the user creating the invitation
     * @return the persisted Invitation entity
     */
    Invitation create(InvitationCreateRequest request, UUID createdBy);

    /**
     * Validates an invitation token by checking all 4 conditions (Req 1 criteria 7):
     * (a) token exists, (b) status is not terminal, (c) current_uses < max_uses, (d) expires_at > now.
     *
     * @param token    the 32-character Base62 invitation token
     * @param clientIp the client IP address (for audit logging)
     * @return the validation result indicating VALID, NOT_FOUND, EXPIRED, REVOKED, or EXHAUSTED
     */
    InvitationValidationResult validate(String token, String clientIp);

    /**
     * Transitions an invitation's status to OPENED.
     * Valid only when current status can transition to OPENED.
     *
     * @param invitationId the invitation UUID
     * @throws IllegalStateException if the transition is not allowed from current status
     */
    void markOpened(UUID invitationId);

    /**
     * Increments current_uses on the invitation. If the invitation is SINGLE_USE and uses
     * are exhausted, transitions status to USED. For REUSABLE, transitions to USED when
     * current_uses reaches max_uses.
     *
     * @param invitationId the invitation UUID
     * @throws IllegalStateException if the invitation is in a terminal state
     */
    void incrementUses(UUID invitationId);

    /**
     * Revokes an invitation, transitioning its status to REVOKED.
     * Valid from any non-terminal status.
     *
     * @param invitationId the invitation UUID
     * @param revokedBy    the UUID of the admin or user revoking the invitation
     * @throws IllegalStateException if the invitation is already in a terminal state
     */
    void revoke(UUID invitationId, UUID revokedBy);

    /**
     * Finds all non-terminal invitations past their expires_at and transitions them to EXPIRED.
     * Called by the InvitationExpirationJob (daily at 02:00 UTC).
     *
     * @return the number of invitations expired
     */
    int expireOverdue();

    /**
     * Retrieves the token string for an invitation by its ID.
     *
     * @param invitationId the invitation UUID
     * @return the 32-character token string, or empty string if not found
     */
    String getTokenById(UUID invitationId);
}
