package com.dadcoach.onboarding.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing onboarding wizard sessions.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create new sessions with 72-hour TTL upon invitation validation</li>
 *   <li>Retrieve sessions by ID</li>
 *   <li>Submit step data (validates invitation on each transition)</li>
 *   <li>Navigate backwards through completed steps</li>
 *   <li>Expire inactive sessions (called by scheduled job)</li>
 *   <li>Find sessions by phone number for resume detection</li>
 * </ul>
 */
public interface OnboardingSessionService {

    /**
     * Creates a new onboarding session for the given invitation token.
     * The session starts at WELCOME step with a 72-hour TTL.
     *
     * @param invitationToken the validated invitation token
     * @param clientIp the client's IP address for audit
     * @param userAgent the client's user-agent string
     * @return the newly created session
     * @throws IllegalArgumentException if invitationToken is null or blank
     * @throws IllegalStateException if the invitation is not valid
     */
    OnboardingSession create(String invitationToken, String clientIp, String userAgent);

    /**
     * Retrieves an active session by its ID.
     *
     * @param sessionId the session UUID
     * @return the session
     * @throws com.dadcoach.common.ResourceNotFoundException if session not found
     * @throws IllegalStateException if session is expired or in a terminal state
     */
    OnboardingSession getSession(UUID sessionId);

    /**
     * Submits data for the specified wizard step, advancing the session to the next step.
     *
     * @param sessionId the session UUID
     * @param step the step being submitted (must match session's current step)
     * @param data the step data as key-value pairs
     * @return the updated session with advanced step
     * @throws IllegalStateException if step doesn't match current step or session is expired
     * @throws IllegalArgumentException if data is invalid for the step
     */
    OnboardingSession submitStep(UUID sessionId, WizardStep step, Map<String, Object> data);

    /**
     * Navigates the session backwards to the specified target step.
     *
     * @param sessionId the session UUID
     * @param targetStep the step to navigate back to
     * @return the updated session at the target step
     * @throws IllegalStateException if backward navigation to target is not allowed
     */
    OnboardingSession navigateBack(UUID sessionId, WizardStep targetStep);

    /**
     * Expires all inactive sessions that have exceeded the 72-hour TTL.
     * Called by the {@code SessionCleanupJob} scheduled task.
     */
    void expireInactiveSessions();

    /**
     * Finds an active session by the phone number stored in wizard data.
     * Used for resume detection — if a phone number already has an active session,
     * the user can be redirected to resume rather than start fresh.
     *
     * @param phoneNumber the E.164 formatted phone number
     * @return the session if found, empty otherwise
     */
    Optional<OnboardingSession> findByPhoneNumber(String phoneNumber);
}
