package com.dadcoach.workspace.dto.request;

import com.dadcoach.workspace.activity.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for reporting a positive parenting activity.
 */
public class PositiveActivityRequest {

    private UUID childId;

    @NotNull(message = "Activity type is required")
    private ActivityType activityType;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Activity date is required")
    @PastOrPresent(message = "Activity date cannot be in the future")
    private LocalDate activityDate;

    public PositiveActivityRequest() {
    }

    public PositiveActivityRequest(UUID childId, ActivityType activityType, String description, LocalDate activityDate) {
        this.childId = childId;
        this.activityType = activityType;
        this.description = description;
        this.activityDate = activityDate;
    }

    public UUID getChildId() {
        return childId;
    }

    public void setChildId(UUID childId) {
        this.childId = childId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }
}
