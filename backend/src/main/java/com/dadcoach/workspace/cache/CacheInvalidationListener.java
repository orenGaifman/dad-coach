package com.dadcoach.workspace.cache;

import com.dadcoach.workspace.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens for domain events and invalidates the corresponding cache entries.
 *
 * <p>Mapping of events to cache keys:</p>
 * <ul>
 *   <li>MissionCompletedEvent → summary, active_missions, goals, weekly_stats</li>
 *   <li>ChildUpdatedEvent → children, summary</li>
 *   <li>NotificationReceivedEvent → notifications, summary</li>
 *   <li>GrowthSignalRecordedEvent → belt, streak, summary, metrics</li>
 *   <li>AchievementEarnedEvent → achievements</li>
 *   <li>FatherProfileUpdatedEvent → profile, summary</li>
 *   <li>StreakResetEvent → streak, summary</li>
 *   <li>PositiveActivityReportedEvent → streak, summary, metrics, weekly_stats</li>
 *   <li>QualityTimeReportedEvent → streak, summary, metrics, weekly_stats</li>
 * </ul>
 */
@Component
public class CacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final WorkspaceCacheService cacheService;

    public CacheInvalidationListener(WorkspaceCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @EventListener
    public void onMissionCompleted(MissionCompletedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for MissionCompletedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "summary", "active_missions", "goals", "weekly_stats");
    }

    @EventListener
    public void onChildUpdated(ChildUpdatedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for ChildUpdatedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "children", "summary");
    }

    @EventListener
    public void onNotificationReceived(NotificationReceivedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for NotificationReceivedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "notifications", "summary");
    }

    @EventListener
    public void onGrowthSignalRecorded(GrowthSignalRecordedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for GrowthSignalRecordedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "belt", "streak", "summary", "metrics");
    }

    @EventListener
    public void onAchievementEarned(AchievementEarnedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for AchievementEarnedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "achievements");
    }

    @EventListener
    public void onFatherProfileUpdated(FatherProfileUpdatedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for FatherProfileUpdatedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "profile", "summary");
    }

    @EventListener
    public void onStreakReset(StreakResetEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for StreakResetEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "streak", "summary");
    }

    @EventListener
    public void onPositiveActivityReported(PositiveActivityReportedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for PositiveActivityReportedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "streak", "summary", "metrics", "weekly_stats");
    }

    @EventListener
    public void onQualityTimeReported(QualityTimeReportedEvent event) {
        UUID fatherId = event.getFatherId();
        log.debug("Invalidating cache for QualityTimeReportedEvent, fatherId={}", fatherId);
        invalidateKeys(fatherId, "streak", "summary", "metrics", "weekly_stats");
    }

    private void invalidateKeys(UUID fatherId, String... dataTypes) {
        for (String dataType : dataTypes) {
            cacheService.invalidate(fatherId, dataType);
        }
    }
}
