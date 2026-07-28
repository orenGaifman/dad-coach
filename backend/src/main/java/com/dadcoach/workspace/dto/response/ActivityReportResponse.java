package com.dadcoach.workspace.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO returned after successfully submitting an activity report.
 */
public class ActivityReportResponse {

    private final UUID reportId;
    private final String reportType;
    private final UUID childId;
    private final LocalDate activityDate;
    private final int pointsAwarded;

    public ActivityReportResponse(UUID reportId, String reportType, UUID childId,
                                  LocalDate activityDate, int pointsAwarded) {
        this.reportId = reportId;
        this.reportType = reportType;
        this.childId = childId;
        this.activityDate = activityDate;
        this.pointsAwarded = pointsAwarded;
    }

    public UUID getReportId() {
        return reportId;
    }

    public String getReportType() {
        return reportType;
    }

    public UUID getChildId() {
        return childId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }
}
