package com.dadcoach.workspace.magiclink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled job to clean up expired magic link tokens.
 * 
 * Runs daily and removes tokens that have been expired for more than 7 days.
 * This keeps the table small while preserving recent tokens for debugging.
 */
@Component
public class MagicLinkCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkCleanupJob.class);
    
    /**
     * Retention period for expired tokens (7 days after expiration).
     */
    private static final Duration RETENTION_PERIOD = Duration.ofDays(7);

    private final MagicLinkRepository repository;
    private final Clock clock;

    public MagicLinkCleanupJob(MagicLinkRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Runs daily at 3 AM to clean up old expired tokens.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now(clock).minus(RETENTION_PERIOD);
        
        int deleted = repository.deleteExpiredBefore(cutoff);
        
        if (deleted > 0) {
            log.info("Magic link cleanup: deleted {} expired tokens older than {}", 
                    deleted, cutoff);
        }
    }
}
