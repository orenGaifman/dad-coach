package com.dadcoach.workspace.commitment;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entity representing a father's commitment to spend quality time with a child.
 * 
 * The commitment captures:
 * - When: scheduled date/time
 * - With whom: child (optional)
 * - What: activity type and notes
 * - Status: tracking from scheduled → reminded → completed/missed
 */
@Entity
@Table(name = "quality_time_commitment")
public class QualityTimeCommitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "father_id", nullable = false)
    private Long fatherId;

    @Column(name = "child_id")
    private Long childId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes = 30;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(name = "activity_note", length = 500)
    private String activityNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommitmentStatus status = CommitmentStatus.SCHEDULED;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "reminder_message_id", length = 100)
    private String reminderMessageId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_note", length = 500)
    private String completionNote;

    @Column(name = "points_awarded")
    private Integer pointsAwarded = 0;

    @Column(name = "created_via", length = 30)
    private String createdVia = "WHATSAPP";

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QualityTimeCommitment() {}

    public QualityTimeCommitment(Long fatherId, Instant scheduledAt, LocalDate scheduledDate, LocalTime scheduledTime) {
        this.fatherId = fatherId;
        this.scheduledAt = scheduledAt;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.status = CommitmentStatus.SCHEDULED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ─── Status Transitions ──────────────────────────────────────────────

    public void markReminded(String messageId) {
        this.status = CommitmentStatus.REMINDED;
        this.reminderSentAt = Instant.now();
        this.reminderMessageId = messageId;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String note, int points) {
        this.status = CommitmentStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.completionNote = note;
        this.pointsAwarded = points;
        this.updatedAt = Instant.now();
    }

    public void markMissed() {
        this.status = CommitmentStatus.MISSED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = CommitmentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    // ─── Query Methods ───────────────────────────────────────────────────

    public boolean isUpcoming() {
        return status == CommitmentStatus.SCHEDULED || status == CommitmentStatus.REMINDED;
    }

    public boolean needsReminder(Instant now, int minutesBefore) {
        if (status != CommitmentStatus.SCHEDULED) {
            return false;
        }
        Instant reminderTime = scheduledAt.minusSeconds(minutesBefore * 60L);
        return now.isAfter(reminderTime) && now.isBefore(scheduledAt);
    }

    public boolean isPastDue(Instant now) {
        return now.isAfter(scheduledAt) && isUpcoming();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFatherId() { return fatherId; }
    public void setFatherId(Long fatherId) { this.fatherId = fatherId; }

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }

    public String getActivityNote() { return activityNote; }
    public void setActivityNote(String activityNote) { this.activityNote = activityNote; }

    public CommitmentStatus getStatus() { return status; }
    public void setStatus(CommitmentStatus status) { this.status = status; }

    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }

    public String getReminderMessageId() { return reminderMessageId; }
    public void setReminderMessageId(String reminderMessageId) { this.reminderMessageId = reminderMessageId; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }

    public Integer getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(Integer pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public String getCreatedVia() { return createdVia; }
    public void setCreatedVia(String createdVia) { this.createdVia = createdVia; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Status of a quality time commitment.
     */
    public enum CommitmentStatus {
        SCHEDULED,   // Father committed, waiting for time
        REMINDED,    // 30-min reminder sent
        COMPLETED,   // Father reported completion
        MISSED,      // Time passed with no completion
        CANCELLED    // Father cancelled
    }
}
