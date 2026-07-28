package com.dadcoach.workspace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for reporting quality time spent with a child.
 */
public class QualityTimeRequest {

    private UUID childId;

    @Min(value = 15, message = "Duration must be at least 15 minutes")
    @Max(value = 480, message = "Duration must not exceed 480 minutes")
    private int durationMinutes;

    @NotNull(message = "Activity date is required")
    @PastOrPresent(message = "Activity date cannot be in the future")
    private LocalDate activityDate;

    public QualityTimeRequest() {
    }

    public QualityTimeRequest(UUID childId, int durationMinutes, LocalDate activityDate) {
        this.childId = childId;
        this.durationMinutes = durationMinutes;
        this.activityDate = activityDate;
    }

    public UUID getChildId() {
        return childId;
    }

    public void setChildId(UUID childId) {
        this.childId = childId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }
}
