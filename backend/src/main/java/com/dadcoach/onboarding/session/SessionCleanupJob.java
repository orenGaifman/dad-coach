package com.dadcoach.onboarding.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that expires inactive onboarding sessions.
 * Runs every 6 hours to transition sessions where last_activity_at exceeds the 72-hour TTL.
 */
@Component
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);

    private final OnboardingSessionService sessionService;

    public SessionCleanupJob(OnboardingSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Transitions sessions where last_activity_at < now() - 72h to EXPIRED.
     * Runs every 6 hours.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // 6 hours in milliseconds
    public void expireInactiveSessions() {
        log.info("SessionCleanupJob started");
        try {
            sessionService.expireInactiveSessions();
            log.info("SessionCleanupJob completed");
        } catch (Exception e) {
            log.error("SessionCleanupJob failed: {}", e.getMessage(), e);
        }
    }
}
