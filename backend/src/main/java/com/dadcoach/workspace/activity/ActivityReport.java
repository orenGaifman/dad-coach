package com.dadcoach.workspace.activity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity representing an activity report submitted by a father.
 *
 * <p>Activity reports can be either quality time reports (with duration) or
 * positive activity reports (with activity type and description).
 * Maps to the {@code activity_reports} table.</p>
 */
@Entity
@Table(name = "activity_reports")
public class ActivityReport {

    @Id
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "child_id")
    private UUID childId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type")
    private ActivityType activityType;

    @Column(name = "description")
    private String description;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityReport() {
        // JPA requires a no-arg constructor
    }

    private ActivityReport(Builder builder) {
        this.reportId = builder.reportId;
        this.fatherId = builder.fatherId;
        this.childId = builder.childId;
        this.reportType = builder.reportType;
        this.durationMinutes = builder.durationMinutes;
        this.activityType = builder.activityType;
        this.description = builder.description;
        this.activityDate = builder.activityDate;
        this.createdAt = builder.createdAt;
    }

    public UUID getReportId() {
        return reportId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getChildId() {
        return childId;
    }

    public String getReportType() {
        return reportType;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID reportId;
        private UUID fatherId;
        private UUID childId;
        private String reportType;
        private Integer durationMinutes;
        private ActivityType activityType;
        private String description;
        private LocalDate activityDate;
        private Instant createdAt;

        public Builder reportId(UUID reportId) {
            this.reportId = reportId;
            return this;
        }

        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }

        public Builder childId(UUID childId) {
            this.childId = childId;
            return this;
        }

        public Builder reportType(String reportType) {
            this.reportType = reportType;
            return this;
        }

        public Builder durationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
            return this;
        }

        public Builder activityType(ActivityType activityType) {
            this.activityType = activityType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder activityDate(LocalDate activityDate) {
            this.activityDate = activityDate;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ActivityReport build() {
            if (reportId == null) {
                reportId = UUID.randomUUID();
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new ActivityReport(this);
        }
    }
}
