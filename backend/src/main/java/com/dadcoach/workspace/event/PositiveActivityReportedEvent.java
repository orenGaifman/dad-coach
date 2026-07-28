package com.dadcoach.workspace.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published when a father reports a positive parenting activity.
 *
 * <p>This event triggers growth signal processing (POSITIVE_ACTIVITY signal type)
 * and streak qualification. The childId may be null if the father reports an
 * activity without associating it with a specific child.</p>
 */
public class PositiveActivityReportedEvent extends WorkspaceDomainEvent {

    private final String activityType;
    private final UUID childId;
    private final LocalDate activityDate;
    private final UUID reportId;

    public PositiveActivityReportedEvent(UUID fatherId, String activityType, UUID childId,
                                         LocalDate activityDate, UUID reportId) {
        super(fatherId);
        this.activityType = activityType;
        this.childId = childId;
        this.activityDate = activityDate;
        this.reportId = reportId;
    }

    public String getActivityType() {
        return activityType;
    }

    public UUID getChildId() {
        return childId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public UUID getReportId() {
        return reportId;
    }
}
