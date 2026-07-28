package com.dadcoach.onboarding.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OnboardingSession} entities.
 * Provides queries for session lookup by ID, invitation, phone number (resume detection),
 * and inactive session expiration.
 */
@Repository
public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, UUID> {

    /**
     * Find a session by its unique session ID.
     *
     * @param sessionId the session UUID
     * @return the session if found
     */
    Optional<OnboardingSession> findBySessionId(UUID sessionId);

    /**
     * Find all sessions associated with a specific invitation.
     *
     * @param invitationId the invitation UUID
     * @return list of sessions for that invitation
     */
    List<OnboardingSession> findByInvitationId(UUID invitationId);

    /**
     * Find an active session by phone number stored in wizard_data.
     * This is used for resume detection — checking if a phone number already has
     * an active session in progress.
     *
     * <p>Note: Since wizard_data is encrypted, this query operates on the decrypted data
     * via application-level filtering. This method returns all IN_PROGRESS sessions
     * which are then filtered by the service layer after decryption.
     *
     * @param status the session status to filter by (typically IN_PROGRESS)
     * @return list of sessions in the specified status
     */
    List<OnboardingSession> findByStatus(SessionStatus status);

    /**
     * Find all inactive sessions eligible for expiration.
     * Sessions are considered inactive if they are IN_PROGRESS and their
     * last_activity_at is before the cutoff time.
     *
     * @param status the status to filter (IN_PROGRESS)
     * @param cutoff the cutoff time — sessions with last_activity_at before this are inactive
     * @return list of inactive sessions
     */
    @Query("SELECT s FROM OnboardingSession s WHERE s.status = :status AND s.lastActivityAt < :cutoff")
    List<OnboardingSession> findInactiveSessions(@Param("status") SessionStatus status,
                                                 @Param("cutoff") Instant cutoff);

    /**
     * Find sessions that have passed their expiration time but are still IN_PROGRESS.
     *
     * @param status the status to filter (IN_PROGRESS)
     * @param now the current time
     * @return list of expired sessions that need status transition
     */
    @Query("SELECT s FROM OnboardingSession s WHERE s.status = :status AND s.expiresAt < :now")
    List<OnboardingSession> findExpiredSessions(@Param("status") SessionStatus status,
                                               @Param("now") Instant now);
}
