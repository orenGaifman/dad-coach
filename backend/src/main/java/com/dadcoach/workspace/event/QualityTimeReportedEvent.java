package com.dadcoach.workspace.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published when a father reports quality time spent with a child.
 *
 * <p>This event triggers growth signal processing (QUALITY_TIME_REPORTED signal type)
 * and streak qualification. The childId may be null if the father reports time
 * without associating it with a specific child.</p>
 */
public class QualityTimeReportedEvent extends WorkspaceDomainEvent {

    private final UUID childId;
    private final int durationMinutes;
    private final LocalDate activityDate;
    private final UUID reportId;

    public QualityTimeReportedEvent(UUID fatherId, UUID childId, int durationMinutes,
                                    LocalDate activityDate, UUID reportId) {
        super(fatherId);
        this.childId = childId;
        this.durationMinutes = durationMinutes;
        this.activityDate = activityDate;
        this.reportId = reportId;
    }

    public UUID getChildId() {
        return childId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public UUID getReportId() {
        return reportId;
    }
}
