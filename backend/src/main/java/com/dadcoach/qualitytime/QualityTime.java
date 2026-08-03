package com.dadcoach.qualitytime;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a Quality Time event in the coaching system.
 * Maps to the "quality_time" table.
 * 
 * Quality Time represents scheduled time where a father spends dedicated time
 * with their child, backed by Google Calendar. This is the core engagement unit
 * of the deterministic workflow engine.
 * 
 * Requirements: 3.4 - Google Calendar Integration (Quality Time event storage)
 */
@Entity
@Table(name = "quality_time")
public class QualityTime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "child_id", insertable = false, updatable = false)
    private Long childId;

    @Column(name = "google_calendar_event_id", length = 255)
    private String googleCalendarEventId;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QualityTimeStatus status = QualityTimeStatus.SCHEDULED;

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    @Column(name = "follow_up_sent", nullable = false)
    private boolean followUpSent = false;

    protected QualityTime() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new Quality Time event.
     * 
     * @param father the father scheduling the quality time
     * @param child the child the quality time is with
     * @param scheduledStart the start time of the quality time
     * @param scheduledEnd the end time of the quality time
     */
    public QualityTime(Father father, Child child, Instant scheduledStart, Instant scheduledEnd) {
        this.father = father;
        this.child = child;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
    }

    // ─── JPA Lifecycle Callbacks ─────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Marks this quality time as completed.
     * 
     * @param notes optional notes about what was done during the quality time
     */
    public void markCompleted(String notes) {
        this.status = QualityTimeStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.completionNotes = notes;
    }

    /**
     * Marks this quality time as missed.
     */
    public void markMissed() {
        this.status = QualityTimeStatus.MISSED;
    }

    /**
     * Marks this quality time as cancelled.
     */
    public void markCancelled() {
        this.status = QualityTimeStatus.CANCELLED;
    }

    /**
     * Checks if this quality time is still scheduled (not completed, missed, or cancelled).
     * 
     * @return true if still scheduled
     */
    public boolean isScheduled() {
        return this.status == QualityTimeStatus.SCHEDULED;
    }

    /**
     * Checks if the scheduled end time has passed.
     * 
     * @return true if end time is in the past
     */
    public boolean hasEnded() {
        return Instant.now().isAfter(this.scheduledEnd);
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Father getFather() {
        return father;
    }

    public void setFather(Father father) {
        this.father = father;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public Child getChild() {
        return child;
    }

    public void setChild(Child child) {
        this.child = child;
    }

    public Long getChildId() {
        return childId;
    }

    public String getGoogleCalendarEventId() {
        return googleCalendarEventId;
    }

    public void setGoogleCalendarEventId(String googleCalendarEventId) {
        this.googleCalendarEventId = googleCalendarEventId;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(Instant scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledEnd(Instant scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }

    public QualityTimeStatus getStatus() {
        return status;
    }

    public void setStatus(QualityTimeStatus status) {
        this.status = status;
    }

    public String getCompletionNotes() {
        return completionNotes;
    }

    public void setCompletionNotes(String completionNotes) {
        this.completionNotes = completionNotes;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public boolean isFollowUpSent() {
        return followUpSent;
    }

    public void setFollowUpSent(boolean followUpSent) {
        this.followUpSent = followUpSent;
    }
}
