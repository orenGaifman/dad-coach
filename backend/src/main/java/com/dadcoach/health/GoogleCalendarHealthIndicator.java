package com.dadcoach.health;

import com.dadcoach.calendar.CalendarSyncLog;
import com.dadcoach.calendar.CalendarSyncLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Health indicator for Google Calendar API connectivity.
 * 
 * <p>Reports the status of Google Calendar API integration by checking the last
 * successful API call timestamp from the calendar sync logs.</p>
 * 
 * <p>The indicator reports:
 * <ul>
 *   <li>UP - when the last successful API call was within the configured threshold</li>
 *   <li>DOWN - when there's no recent successful API call or the API is unreachable</li>
 *   <li>UNKNOWN - when no API calls have been recorded yet</li>
 * </ul>
 * </p>
 * 
 * <p>Implements Requirement 16.5: The system SHALL expose a health endpoint that
 * reports Google Calendar API status (last successful call timestamp).</p>
 */
@Component
public class GoogleCalendarHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarHealthIndicator.class);

    /**
     * Maximum time since last successful API call before reporting DOWN.
     * Default: 24 hours - if no successful call in 24 hours, something is likely wrong.
     */
    private static final Duration HEALTHY_THRESHOLD = Duration.ofHours(24);

    private final CalendarSyncLogRepository calendarSyncLogRepository;

    /**
     * Constructs the health indicator with the calendar sync log repository.
     *
     * @param calendarSyncLogRepository repository for calendar sync logs (nullable)
     */
    public GoogleCalendarHealthIndicator(CalendarSyncLogRepository calendarSyncLogRepository) {
        this.calendarSyncLogRepository = calendarSyncLogRepository;
    }

    @Override
    public Health health() {
        try {
            if (calendarSyncLogRepository == null) {
                return Health.unknown()
                        .withDetail("component", "GoogleCalendarAPI")
                        .withDetail("status", "Repository not available")
                        .build();
            }

            // Get the most recent successful calendar sync
            Optional<CalendarSyncLog> lastSuccessfulSync = calendarSyncLogRepository
                    .findTopBySuccessTrueOrderBySyncedAtDesc();

            if (lastSuccessfulSync.isEmpty()) {
                // No successful syncs recorded - might be a new system or no calendar users
                return Health.unknown()
                        .withDetail("component", "GoogleCalendarAPI")
                        .withDetail("status", "No successful API calls recorded")
                        .withDetail("note", "This may be normal if no fathers have connected their calendars")
                        .build();
            }

            CalendarSyncLog syncLog = lastSuccessfulSync.get();
            Instant lastSuccessTime = syncLog.getSyncedAt();
            Duration timeSinceLastSuccess = Duration.between(lastSuccessTime, Instant.now());

            if (timeSinceLastSuccess.compareTo(HEALTHY_THRESHOLD) <= 0) {
                // Recent successful API call - healthy
                return Health.up()
                        .withDetail("component", "GoogleCalendarAPI")
                        .withDetail("lastSuccessfulCall", lastSuccessTime.toString())
                        .withDetail("timeSinceLastCall", formatDuration(timeSinceLastSuccess))
                        .build();
            } else {
                // No recent successful API call - potentially unhealthy
                return Health.down()
                        .withDetail("component", "GoogleCalendarAPI")
                        .withDetail("lastSuccessfulCall", lastSuccessTime.toString())
                        .withDetail("timeSinceLastCall", formatDuration(timeSinceLastSuccess))
                        .withDetail("threshold", formatDuration(HEALTHY_THRESHOLD))
                        .withDetail("error", "No successful API call within threshold")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error checking Google Calendar health: {}", e.getMessage(), e);
            return Health.down()
                        .withDetail("component", "GoogleCalendarAPI")
                        .withDetail("error", e.getMessage())
                        .build();
        }
    }

    /**
     * Formats a duration into a human-readable string.
     *
     * @param duration the duration to format
     * @return formatted string like "2h 30m" or "45m"
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }
}
