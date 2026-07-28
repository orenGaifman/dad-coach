package com.dadcoach.onboarding.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Invitation} entities.
 * Provides queries for token lookup, expired invitation detection, and admin listing.
 */
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /**
     * Find an invitation by its unique token.
     * Used for invitation validation during the onboarding flow.
     *
     * @param token the 32-character Base62 invitation token
     * @return the invitation if found
     */
    Optional<Invitation> findByToken(String token);

    /**
     * Find all invitations that have expired but have not yet been transitioned to a terminal status.
     * Used by the {@code InvitationExpirationJob} to batch-transition overdue invitations to EXPIRED.
     *
     * @param now the current timestamp to compare against expires_at
     * @param terminalStatuses statuses considered terminal (EXPIRED, REVOKED, USED) — these are excluded
     * @return list of invitations eligible for expiration
     */
    @Query("SELECT i FROM Invitation i WHERE i.status NOT IN (:terminalStatuses) AND i.expiresAt < :now")
    List<Invitation> findExpiredInvitations(@Param("now") Instant now,
                                           @Param("terminalStatuses") Collection<InvitationStatus> terminalStatuses);

    /**
     * Find all invitations created by a specific user.
     * Used for admin listing of invitations.
     *
     * @param createdBy the UUID of the user who created the invitations
     * @return list of invitations created by the specified user
     */
    List<Invitation> findByCreatedBy(UUID createdBy);
}
