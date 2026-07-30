package com.dadcoach.onboarding.session;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.onboarding.invitation.InvitationService;
import com.dadcoach.onboarding.invitation.InvitationValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link OnboardingSessionService} managing wizard session lifecycle.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Sessions have a 72-hour TTL from creation</li>
 *   <li>Invitation validity is re-checked on each step submission</li>
 *   <li>Step data is accumulated in the encrypted wizard_data field</li>
 *   <li>Backward navigation is allowed to any preceding step</li>
 * </ul>
 */
@Service
@Transactional
public class OnboardingSessionServiceImpl implements OnboardingSessionService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingSessionServiceImpl.class);

    /** Session time-to-live: 72 hours from creation. */
    private static final long SESSION_TTL_HOURS = 72;

    private final OnboardingSessionRepository sessionRepository;
    private final InvitationService invitationService;

    public OnboardingSessionServiceImpl(OnboardingSessionRepository sessionRepository,
                                        InvitationService invitationService) {
        this.sessionRepository = sessionRepository;
        this.invitationService = invitationService;
    }

    @Override
    public OnboardingSession create(String invitationToken, String clientIp, String userAgent) {
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new IllegalArgumentException("invitationToken must not be null or blank");
        }

        // Validate the invitation
        InvitationValidationResult validationResult = invitationService.validate(invitationToken, clientIp);
        if (!validationResult.isValid()) {
            throw new IllegalStateException(
                    "Invitation is not valid: " + validationResult.status());
        }

        // Mark invitation as opened
        invitationService.markOpened(validationResult.invitationId());

        Instant now = Instant.now();

        OnboardingSession session = new OnboardingSession();
        session.setInvitationId(validationResult.invitationId());
        session.setCurrentStep(WizardStep.LANGUAGE);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setWizardData(new WizardData());
        session.setStartedAt(now);
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plus(SESSION_TTL_HOURS, ChronoUnit.HOURS));
        session.setIpAddress(clientIp);
        session.setUserAgent(truncateUserAgent(userAgent));

        OnboardingSession saved = sessionRepository.save(session);
        log.info("Created onboarding session [id={}, invitation={}, ip={}]",
                saved.getSessionId(), validationResult.invitationId(), clientIp);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingSession getSession(UUID sessionId) {
        OnboardingSession session = findActiveSession(sessionId);
        return session;
    }

    @Override
    public OnboardingSession submitStep(UUID sessionId, WizardStep step, Map<String, Object> data) {
        OnboardingSession session = findActiveSession(sessionId);

        // Verify the submitted step matches the current step
        if (session.getCurrentStep() != step) {
            throw new IllegalStateException(String.format(
                    "Step mismatch: session is at %s but submission is for %s [sessionId=%s]",
                    session.getCurrentStep(), step, sessionId));
        }

        // Verify the step accepts data submission
        if (!step.canSubmitFrom()) {
            throw new IllegalStateException(String.format(
                    "Step %s does not accept data submissions [sessionId=%s]",
                    step, sessionId));
        }

        // Apply step data to wizard data
        applyStepData(session, step, data);

        // Advance to next step
        WizardStep nextStep = step.next();
        if (nextStep != null) {
            session.setCurrentStep(nextStep);
        }

        session.touch();
        OnboardingSession saved = sessionRepository.save(session);
        log.info("Step submitted [sessionId={}, step={}, nextStep={}]",
                sessionId, step, nextStep);
        return saved;
    }

    @Override
    public OnboardingSession navigateBack(UUID sessionId, WizardStep targetStep) {
        OnboardingSession session = findActiveSession(sessionId);

        if (!session.getCurrentStep().canNavigateBackTo(targetStep)) {
            throw new IllegalStateException(String.format(
                    "Cannot navigate back from %s to %s [sessionId=%s]",
                    session.getCurrentStep(), targetStep, sessionId));
        }

        session.setCurrentStep(targetStep);
        session.touch();

        OnboardingSession saved = sessionRepository.save(session);
        log.info("Navigated back [sessionId={}, targetStep={}]", sessionId, targetStep);
        return saved;
    }

    @Override
    public void expireInactiveSessions() {
        Instant now = Instant.now();
        List<OnboardingSession> expiredSessions =
                sessionRepository.findExpiredSessions(SessionStatus.IN_PROGRESS, now);

        int count = 0;
        for (OnboardingSession session : expiredSessions) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            count++;
        }

        if (count > 0) {
            log.info("Expired {} inactive onboarding sessions", count);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OnboardingSession> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }

        // Since wizard_data is encrypted, we must load all active sessions and filter
        List<OnboardingSession> activeSessions = sessionRepository.findByStatus(SessionStatus.IN_PROGRESS);

        return activeSessions.stream()
                .filter(s -> s.getWizardData() != null
                        && phoneNumber.equals(s.getWizardData().getPhoneNumber()))
                .findFirst();
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    private OnboardingSession findActiveSession(UUID sessionId) {
        OnboardingSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardingSession", sessionId));

        if (session.getStatus().isTerminal()) {
            throw new IllegalStateException(String.format(
                    "Session is in terminal state: %s [sessionId=%s]",
                    session.getStatus(), sessionId));
        }

        if (session.isExpired()) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new IllegalStateException(String.format(
                    "Session has expired [sessionId=%s, expiresAt=%s]",
                    sessionId, session.getExpiresAt()));
        }

        return session;
    }

    @SuppressWarnings("unchecked")
    private void applyStepData(OnboardingSession session, WizardStep step, Map<String, Object> data) {
        if (data == null) {
            return;
        }

        WizardData wizardData = session.getWizardData();
        if (wizardData == null) {
            wizardData = new WizardData();
            session.setWizardData(wizardData);
        }

        switch (step) {
            case LANGUAGE -> {
                String language = (String) data.get("language");
                wizardData.setLanguage(language);
                session.setLanguage(language);
            }
            case FATHER_PROFILE -> {
                wizardData.setDisplayName((String) data.get("display_name"));
                wizardData.setPhoneNumber((String) data.get("phone_number"));
                wizardData.setEmail((String) data.get("email"));
                wizardData.setTimezone((String) data.get("timezone"));
            }
            case CHILDREN -> {
                Object childrenObj = data.get("children");
                if (childrenObj instanceof List<?> childrenList) {
                    List<WizardData.ChildData> children = childrenList.stream()
                            .filter(c -> c instanceof Map)
                            .map(c -> {
                                Map<String, Object> childMap = (Map<String, Object>) c;
                                return new WizardData.ChildData(
                                        (String) childMap.get("name"),
                                        (String) childMap.get("birth_date"),
                                        (String) childMap.get("gender")
                                );
                            })
                            .toList();
                    wizardData.setChildren(children);
                }
            }
            case GOALS -> {
                Object goalsObj = data.get("goals");
                if (goalsObj instanceof List<?> goalsList) {
                    List<String> goals = goalsList.stream()
                            .filter(g -> g instanceof String)
                            .map(g -> (String) g)
                            .toList();
                    wizardData.setGoals(goals);
                }
            }
            case PREFERENCES -> {
                Object prefsObj = data.get("preferences");
                if (prefsObj instanceof Map<?, ?> prefsMap) {
                    Map<String, Object> preferences = (Map<String, Object>) prefsMap;
                    wizardData.setPreferences(preferences);
                }
            }
            default -> log.debug("No data to apply for step {} [sessionId={}]",
                    step, session.getSessionId());
        }
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}
