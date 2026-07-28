package com.dadcoach.channel.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled job that deletes expired media assets daily.
 * Media assets have a 90-day retention period (expires_at = downloaded_at + 90 days).
 * This job runs once per day at 03:00 UTC to clean up assets past their retention window.
 */
@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final MediaAssetRepository mediaAssetRepository;

    public MediaCleanupJob(MediaAssetRepository mediaAssetRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
    }

    /**
     * Runs daily at 03:00 UTC. Deletes all media assets where expires_at < now().
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredMedia() {
        Instant now = Instant.now();
        log.info("Media cleanup job started. Deleting assets expired before {}", now);

        int deletedCount = mediaAssetRepository.deleteExpiredAssets(now);

        log.info("Media cleanup job completed. Deleted {} expired media assets", deletedCount);
    }
}
