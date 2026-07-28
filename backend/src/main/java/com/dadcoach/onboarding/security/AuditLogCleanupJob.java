package com.dadcoach.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled job that cleans up old audit log entries.
 * Runs weekly to delete invitation_audit_log entries older than 90 days.
 */
@Component
public class AuditLogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCleanupJob.class);

    private static final Duration RETENTION_PERIOD = Duration.ofDays(90);

    private final InvitationAuditLogRepository repository;

    public AuditLogCleanupJob(InvitationAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Deletes invitation_audit_log entries older than 90 days.
     * Runs weekly (every 7 days).
     */
    @Scheduled(cron = "0 0 3 * * SUN") // Every Sunday at 03:00 UTC
    @Transactional
    public void cleanupOldEntries() {
        log.info("AuditLogCleanupJob started");
        try {
            Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
            int deleted = repository.deleteOlderThan(cutoff);
            log.info("AuditLogCleanupJob completed: {} entries deleted", deleted);
        } catch (Exception e) {
            log.error("AuditLogCleanupJob failed: {}", e.getMessage(), e);
        }
    }
}
