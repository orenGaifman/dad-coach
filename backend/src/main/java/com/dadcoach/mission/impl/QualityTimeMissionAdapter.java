package com.dadcoach.mission.impl;

import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionType;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Adapter that wraps a {@link QualityTime} entity to implement the {@link Mission} interface.
 * 
 * <p>This adapter enables the workflow engine to work with Quality Time events through
 * the abstract Mission interface, supporting future extensibility to other mission types.</p>
 * 
 * <p><strong>Design Pattern:</strong> Adapter Pattern - QualityTime is adapted to the
 * Mission interface without modifying the original QualityTime entity.</p>
 * 
 * Requirements: 1.1 (Mission abstraction for Quality Time)
 * 
 * @see Mission
 * @see QualityTime
 */
public class QualityTimeMissionAdapter implements Mission {

    private final QualityTime qualityTime;

    /**
     * Creates a new adapter wrapping the given Quality Time entity.
     * 
     * @param qualityTime the Quality Time entity to adapt (must not be null)
     * @throws NullPointerException if qualityTime is null
     */
    public QualityTimeMissionAdapter(QualityTime qualityTime) {
        this.qualityTime = Objects.requireNonNull(qualityTime, "qualityTime must not be null");
    }

    @Override
    public UUID getId() {
        return qualityTime.getId();
    }

    @Override
    public Long getFatherId() {
        return qualityTime.getFatherId();
    }

    @Override
    public Long getChildId() {
        return qualityTime.getChildId();
    }

    @Override
    public MissionType getType() {
        return MissionType.QUALITY_TIME;
    }

    @Override
    public QualityTimeStatus getStatus() {
        return qualityTime.getStatus();
    }

    @Override
    public Instant getScheduledStart() {
        return qualityTime.getScheduledStart();
    }

    @Override
    public Instant getScheduledEnd() {
        return qualityTime.getScheduledEnd();
    }

    @Override
    public String getCompletionNotes() {
        return qualityTime.getCompletionNotes();
    }

    @Override
    public Instant getCompletedAt() {
        return qualityTime.getCompletedAt();
    }

    /**
     * Returns the underlying Quality Time entity.
     * 
     * <p>Use with caution - this breaks the abstraction but may be needed
     * for Quality Time-specific operations.</p>
     * 
     * @return the wrapped Quality Time entity
     */
    public QualityTime getQualityTime() {
        return qualityTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QualityTimeMissionAdapter that = (QualityTimeMissionAdapter) o;
        return Objects.equals(qualityTime.getId(), that.qualityTime.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualityTime.getId());
    }

    @Override
    public String toString() {
        return "QualityTimeMission{" +
                "id=" + getId() +
                ", fatherId=" + getFatherId() +
                ", childId=" + getChildId() +
                ", status=" + getStatus() +
                ", scheduledStart=" + getScheduledStart() +
                ", scheduledEnd=" + getScheduledEnd() +
                '}';
    }
}
