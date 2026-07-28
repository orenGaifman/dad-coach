package com.dadcoach.workspace.activity;

import com.dadcoach.workspace.WorkspaceErrorCode;
import com.dadcoach.workspace.WorkspaceException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Validates activity report requests.
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>Duration must be between 15 and 480 minutes (for quality time reports)</li>
 *   <li>Activity date must not be in the future</li>
 *   <li>Activity date must not be more than 7 days in the past</li>
 *   <li>Activity type must be a valid enum value (validated at DTO level via @NotNull)</li>
 * </ul>
 */
@Component
public class ActivityReportValidator {

    private static final int MIN_DURATION_MINUTES = 15;
    private static final int MAX_DURATION_MINUTES = 480;
    private static final int MAX_DAYS_IN_PAST = 7;

    /**
     * Validates a quality time report's duration and activity date.
     *
     * @param durationMinutes the reported duration in minutes
     * @param activityDate    the date of the activity
     * @throws WorkspaceException if validation fails
     */
    public void validateQualityTimeReport(int durationMinutes, LocalDate activityDate) {
        validateDuration(durationMinutes);
        validateActivityDate(activityDate);
    }

    /**
     * Validates a positive activity report's activity date.
     *
     * @param activityDate the date of the activity
     * @throws WorkspaceException if validation fails
     */
    public void validatePositiveActivityReport(LocalDate activityDate) {
        validateActivityDate(activityDate);
    }

    private void validateDuration(int durationMinutes) {
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new WorkspaceException(
                    WorkspaceErrorCode.VALIDATION_ERROR,
                    WorkspaceErrorCode.VALIDATION_ERROR.formatMessage(
                            String.format("Duration must be between %d and %d minutes, got %d",
                                    MIN_DURATION_MINUTES, MAX_DURATION_MINUTES, durationMinutes))
            );
        }
    }

    private void validateActivityDate(LocalDate activityDate) {
        LocalDate today = LocalDate.now();

        if (activityDate.isAfter(today)) {
            throw new WorkspaceException(
                    WorkspaceErrorCode.VALIDATION_ERROR,
                    WorkspaceErrorCode.VALIDATION_ERROR.formatMessage("Activity date cannot be in the future")
            );
        }

        LocalDate earliestAllowed = today.minusDays(MAX_DAYS_IN_PAST);
        if (activityDate.isBefore(earliestAllowed)) {
            throw new WorkspaceException(
                    WorkspaceErrorCode.VALIDATION_ERROR,
                    WorkspaceErrorCode.VALIDATION_ERROR.formatMessage(
                            String.format("Activity date cannot be more than %d days in the past", MAX_DAYS_IN_PAST))
            );
        }
    }
}
