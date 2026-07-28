package com.dadcoach.workspace.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that purges expired activity feed items.
 *
 * <p>Runs daily at 03:00 UTC. Deletes all feed items whose expires_at timestamp
 * is in the past (default retention is 90 days from creation).</p>
 *
 * <p><strong>Idempotency:</strong> This operation IS idempotent. {@code DELETE WHERE expires_at < now()}
 * produces the same result regardless of how many times it is executed. Re-running after
 * a successful execution deletes zero rows. Safe for multi-instance deployments without
 * distributed locking.</p>
 *
 * <p>Requirement 6.5: Feed items are retained for 90 days then automatically purged.</p>
 */
@Component
public class ActivityFeedPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ActivityFeedPurgeJob.class);

    private final ActivityFeedService activityFeedService;

    public ActivityFeedPurgeJob(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    /**
     * Executes the feed purge at 03:00 UTC daily.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredFeedItems() {
        log.info("Starting activity feed purge job");
        try {
            int deleted = activityFeedService.purgeExpiredItems();
            log.info("Activity feed purge job completed. Deleted {} expired items.", deleted);
        } catch (Exception e) {
            log.error("Activity feed purge job failed", e);
        }
    }
}
