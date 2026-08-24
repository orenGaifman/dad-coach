package com.dadcoach.memory.sensitive;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity representing a safety event record.
 * Maps to the "safety_event_records" table as defined in SPEC-004 design.
 *
 * <p>Safety events are stored separately from normal memories with long retention
 * for legal/compliance reasons. They are NEVER deleted during GDPR erasure.
 *
 * <p>From SPEC-004 Requirement 24:
 * Safety-related events need their own separate table with long retention
 * for legal/compliance reasons.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Separate from normal memory storage</li>
 *   <li>Never mixed into normal memory retrieval</li>
 *   <li>Long retention (7 years) for legal/compliance - enforced via expiresAt field</li>
 *   <li>Review workflow with reviewed_by and reviewed_at</li>
 *   <li>Queryable by father_id for support use cases</li>
 *   <li>NOT deleted during GDPR erasure - only after retention period expires</li>
 * </ul>
 *
 * @see SafetyEventType
 * @see SafetyEventSeverity
 * @see SafetyEventService
 * @see SafetyEventRetentionService
 */
@Entity
@Table(name = "safety_event_records",
        indexes = {
                @Index(name = "idx_safety_events_father", columnList = "father_id, created_at DESC"),
                @Index(name = "idx_safety_events_requires_review", columnList = "requires_review, severity"),
                @Index(name = "idx_safety_events_created_at", columnList = "created_at DESC"),
                @Index(name = "idx_safety_events_expires_at", columnList = "expires_at")
        })
public class SafetyEventRecord {

    /**
     * Maximum length for the summary field as per SPEC-004 design.
     * Brief summary for quick scanning in review lists.
     */
    public static final int MAX_SUMMARY_LENGTH = 100;

    /**
     * Maximum length for the description field.
     * Detailed descriptions for in-depth review.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    /**
     * Default retention period in years for safety events.
     * Safety events are kept for 7 years per legal compliance requirements.
     * This is longer than regular audit logs (2 years) due to legal requirements.
     */
    public static final int DEFAULT_RETENTION_YEARS = 7;

    // ─── Primary Key ─────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Core Fields ─────────────────────────────────────────────────────

    /**
     * The father this safety event is associated with.
     */
    @NotNull
    @Column(name = "father_id", nullable = false, updatable = false)
    private UUID fatherId;

    /**
     * The type of safety event.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30, nullable = false, updatable = false)
    private SafetyEventType eventType;

    /**
     * The severity level of this safety event.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false, updatable = false)
    private SafetyEventSeverity severity;

    /**
     * Brief summary of the safety event (max 100 chars as per SPEC-004 design).
     * Used for quick scanning in review lists and dashboards.
     */
    @NotNull
    @Size(max = MAX_SUMMARY_LENGTH)
    @Column(name = "summary", length = MAX_SUMMARY_LENGTH, nullable = false, updatable = false)
    private String summary;

    /**
     * Detailed description of the safety event (max 500 chars).
     * Provides additional context for in-depth review.
     */
    @Size(max = MAX_DESCRIPTION_LENGTH)
    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    private String description;

    // ─── Optional Context Links ──────────────────────────────────────────

    /**
     * The conversation ID where this event was detected (nullable).
     */
    @Column(name = "conversation_id", updatable = false)
    private UUID conversationId;

    /**
     * Reference to a related memory if applicable (nullable).
     */
    @Column(name = "memory_id", updatable = false)
    private UUID memoryId;

    /**
     * Additional metadata as JSON for extensibility.
     * May contain: source_text_snippet, ai_confidence, trigger_keywords, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSONB", updatable = false)
    private Map<String, Object> metadata;

    // ─── Review Workflow ─────────────────────────────────────────────────

    /**
     * Flag indicating whether this event requires human review.
     * All events start as requiring review; cleared after review.
     */
    @NotNull
    @Column(name = "requires_review", nullable = false)
    private Boolean requiresReview = true;

    /**
     * The ID of the support staff member who reviewed this event (nullable).
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * When this event was reviewed (nullable).
     */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * Notes from the reviewer (nullable).
     */
    @Size(max = 1000)
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    // ─── Timestamps ──────────────────────────────────────────────────────

    /**
     * When this safety event was recorded.
     */
    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When this safety event expires and can be permanently deleted.
     * Default is 7 years from creation per legal compliance requirements.
     * Safety events are NOT deleted during GDPR erasure - only after this date.
     */
    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // ─── Constructors ────────────────────────────────────────────────────

    /**
     * JPA requires a no-arg constructor.
     */
    protected SafetyEventRecord() {
    }

    /**
     * Creates a new safety event record with required fields.
     *
     * @param fatherId    the father associated with this event
     * @param eventType   the type of safety event
     * @param severity    the severity level
     * @param summary     brief summary of the event (max 100 chars)
     */
    public SafetyEventRecord(UUID fatherId, SafetyEventType eventType,
                             SafetyEventSeverity severity, String summary) {
        this.fatherId = fatherId;
        this.eventType = eventType;
        this.severity = severity;
        this.summary = truncateSummary(summary);
        this.requiresReview = true;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(DEFAULT_RETENTION_YEARS * 365L, ChronoUnit.DAYS);
    }

    /**
     * Creates a new safety event record with a custom retention period.
     *
     * @param fatherId        the father associated with this event
     * @param eventType       the type of safety event
     * @param severity        the severity level
     * @param summary         brief summary of the event (max 100 chars)
     * @param retentionYears  custom retention period in years
     */
    public SafetyEventRecord(UUID fatherId, SafetyEventType eventType,
                             SafetyEventSeverity severity, String summary, int retentionYears) {
        this.fatherId = fatherId;
        this.eventType = eventType;
        this.severity = severity;
        this.summary = truncateSummary(summary);
        this.requiresReview = true;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(retentionYears * 365L, ChronoUnit.DAYS);
    }

    /**
     * Creates a new safety event record with summary and detailed description.
     *
     * @param fatherId    the father associated with this event
     * @param eventType   the type of safety event
     * @param severity    the severity level
     * @param summary     brief summary of the event (max 100 chars)
     * @param description detailed description of the event (max 500 chars)
     */
    public SafetyEventRecord(UUID fatherId, SafetyEventType eventType,
                             SafetyEventSeverity severity, String summary, String description) {
        this(fatherId, eventType, severity, summary);
        this.description = truncateDescription(description);
    }

    // ─── Review Operations ───────────────────────────────────────────────

    /**
     * Marks this event as reviewed by the given reviewer.
     *
     * @param reviewerId the ID of the support staff member
     * @param notes      optional review notes
     */
    public void markReviewed(UUID reviewerId, String notes) {
        this.requiresReview = false;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.reviewNotes = notes;
    }

    /**
     * Marks this event as requiring re-review (e.g., after escalation).
     */
    public void flagForReview() {
        this.requiresReview = true;
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    /**
     * Truncates summary to maximum allowed length (100 chars).
     */
    private static String truncateSummary(String summary) {
        if (summary == null) {
            return "";
        }
        if (summary.length() <= MAX_SUMMARY_LENGTH) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
    }

    /**
     * Truncates description to maximum allowed length (500 chars).
     */
    private static String truncateDescription(String description) {
        if (description == null) {
            return null;
        }
        if (description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
    }

    /**
     * Checks if this event has been reviewed.
     *
     * @return true if reviewed
     */
    public boolean isReviewed() {
        return !requiresReview && reviewedBy != null;
    }

    /**
     * Checks if this is a critical or high severity event.
     *
     * @return true if severity is HIGH or CRITICAL
     */
    public boolean isHighPriority() {
        return severity.isAtLeast(SafetyEventSeverity.HIGH);
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public SafetyEventType getEventType() {
        return eventType;
    }

    public SafetyEventSeverity getSeverity() {
        return severity;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Boolean getRequiresReview() {
        return requiresReview;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this safety event has expired and can be deleted.
     *
     * @return true if the current time is past the expiration date
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this safety event will expire within the given number of days.
     *
     * @param days the number of days to check
     * @return true if the event will expire within the given days
     */
    public boolean expiresWithinDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).isAfter(expiresAt);
    }

    // ─── Setters (limited for immutable fields) ──────────────────────────

    /**
     * Sets the ID. Used internally by JPA during entity persistence.
     */
    void setId(UUID id) {
        this.id = id;
    }

    /**
     * Sets the conversation ID context for this event.
     *
     * @param conversationId the conversation where the event was detected
     */
    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    /**
     * Sets the memory ID context for this event.
     *
     * @param memoryId the related memory if applicable
     */
    public void setMemoryId(UUID memoryId) {
        this.memoryId = memoryId;
    }

    /**
     * Sets additional metadata for this event.
     *
     * @param metadata additional context as key-value pairs
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // ─── Object Methods ──────────────────────────────────────────────────

    @Override
    public String toString() {
        return "SafetyEventRecord{" +
                "id=" + id +
                ", fatherId=" + fatherId +
                ", eventType=" + eventType +
                ", severity=" + severity +
                ", summary='" + summary + '\'' +
                ", requiresReview=" + requiresReview +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
