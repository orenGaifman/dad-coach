package com.dadcoach.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled job that cleans up expired rate limit entries.
 * Runs every hour to delete entries with window_start older than 2 hours.
 */
@Component
public class RateLimitCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RateLimitCleanupJob.class);

    private static final Duration RETENTION_PERIOD = Duration.ofHours(2);

    private final RateLimitEntryRepository repository;

    public RateLimitCleanupJob(RateLimitEntryRepository repository) {
        this.repository = repository;
    }

    /**
     * Deletes rate_limit_entries where window_start < now() - 2 hours.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 1 hour in milliseconds
    @Transactional
    public void cleanupExpiredEntries() {
        log.info("RateLimitCleanupJob started");
        try {
            Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
            int deleted = repository.deleteExpiredEntries(cutoff);
            log.info("RateLimitCleanupJob completed: {} entries deleted", deleted);
        } catch (Exception e) {
            log.error("RateLimitCleanupJob failed: {}", e.getMessage(), e);
        }
    }
}
