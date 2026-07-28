package com.dadcoach.workspace.security;

import com.dadcoach.workspace.RateLimitExceededException;
import com.dadcoach.workspace.activity.ActivityReportRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enforces daily rate limits on activity reporting per father.
 *
 * <p>Limits:</p>
 * <ul>
 *   <li>QUALITY_TIME: max 10 reports per day</li>
 *   <li>POSITIVE_ACTIVITY: max 20 reports per day</li>
 * </ul>
 */
@Component
public class ActivityReportRateLimiter {

    private static final int QUALITY_TIME_DAILY_LIMIT = 10;
    private static final int POSITIVE_ACTIVITY_DAILY_LIMIT = 20;
    private static final long RETRY_AFTER_SECONDS = 86400; // 24 hours

    public static final String REPORT_TYPE_QUALITY_TIME = "QUALITY_TIME";
    public static final String REPORT_TYPE_POSITIVE_ACTIVITY = "POSITIVE_ACTIVITY";

    private final ActivityReportRepository activityReportRepository;

    public ActivityReportRateLimiter(ActivityReportRepository activityReportRepository) {
        this.activityReportRepository = activityReportRepository;
    }

    /**
     * Checks if the father has exceeded the daily limit for the given report type.
     *
     * @param fatherId   the father's UUID
     * @param reportType the type of report (QUALITY_TIME or POSITIVE_ACTIVITY)
     * @throws RateLimitExceededException if the daily limit has been reached
     */
    public void checkLimit(UUID fatherId, String reportType) {
        LocalDate today = LocalDate.now();
        long count = activityReportRepository.countByFatherIdAndReportTypeAndActivityDate(
                fatherId, reportType, today);

        int limit = getLimit(reportType);
        if (count >= limit) {
            throw new RateLimitExceededException(RETRY_AFTER_SECONDS);
        }
    }

    private int getLimit(String reportType) {
        return switch (reportType) {
            case REPORT_TYPE_QUALITY_TIME -> QUALITY_TIME_DAILY_LIMIT;
            case REPORT_TYPE_POSITIVE_ACTIVITY -> POSITIVE_ACTIVITY_DAILY_LIMIT;
            default -> throw new IllegalArgumentException("Unknown report type: " + reportType);
        };
    }
}
