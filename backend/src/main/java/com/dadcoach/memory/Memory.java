package com.dadcoach.memory;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity representing a stored piece of contextual information about a father, child, or family.
 * Maps to the "memories" table as defined in SPEC-004.
 *
 * <p>This entity implements the Memory & Knowledge System data model supporting:
 * <ul>
 *   <li>Category-based classification with importance and confidence scoring</li>
 *   <li>Lifecycle state machine (ACTIVE → CONFIRMED → SUPERSEDED/ARCHIVED/EXPIRED/DELETED)</li>
 *   <li>Vector embeddings for semantic similarity search (pgvector)</li>
 *   <li>Access and confirmation tracking for decay and retrieval ranking</li>
 *   <li>Event date support for time-bounded memories</li>
 * </ul>
 *
 * <p>Key business rules:
 * <ul>
 *   <li>Content limited to 500 characters (Req 1)</li>
 *   <li>Importance score 1-10, confidence score 0.0-1.0 (Req 4, 5)</li>
 *   <li>Tier classification: importance 1-3 (90 days), 4-6 (180 days), 7-10 (never expires) (Req 6)</li>
 *   <li>Maximum 500 active memories per father (Req 15)</li>
 * </ul>
 *
 * @see MemoryCategory
 * @see MemoryState
 * @see MemorySubjectType
 * @see MemorySourceType
 */
@Entity
@Table(name = "memories")
public class Memory {

    /**
     * Amount to reduce confidence on contradiction detection (Requirement 7).
     */
    public static final BigDecimal CONFIDENCE_DECAY_ON_CONTRADICTION = new BigDecimal("0.30");

    /**
     * Maximum active memories per father (Requirement 15).
     */
    public static final int MAX_ACTIVE_MEMORIES_PER_FATHER = 500;

    /**
     * Maximum content length in characters.
     */
    public static final int MAX_CONTENT_LENGTH = 500;

    /**
     * Embedding vector dimension (OpenAI text-embedding-ada-002).
     */
    public static final int EMBEDDING_DIMENSION = 1536;

    // ─── Primary Key ─────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Relationships ───────────────────────────────────────────────────

    /**
     * The father this memory belongs to. Required.
     */
    @NotNull
    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    /**
     * The child this memory is about (nullable for father-only or family memories).
     */
    @Column(name = "child_id")
    private UUID childId;

    // ─── Classification ──────────────────────────────────────────────────

    /**
     * The memory category determining default importance and lifecycle behavior.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private MemoryCategory category;

    /**
     * The subject type indicating who the memory is about.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", length = 10, nullable = false)
    private MemorySubjectType subjectType;

    // ─── Content ─────────────────────────────────────────────────────────

    /**
     * The memory content (max 500 characters).
     */
    @NotNull
    @Size(max = MAX_CONTENT_LENGTH)
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // ─── Scoring ─────────────────────────────────────────────────────────

    /**
     * Importance score (1-10) determining retrieval priority and tier classification.
     * - 1-3: Short-term tier (90 days expiration)
     * - 4-6: Medium-term tier (180 days expiration)
     * - 7-10: Long-term tier (never expires)
     */
    @NotNull
    @Min(1)
    @Max(10)
    @Column(name = "importance_score", nullable = false)
    private Integer importanceScore;

    /**
     * Confidence score (0.0-1.0) indicating certainty about the memory's accuracy.
     */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column(name = "confidence_score", precision = 3, scale = 2, nullable = false)
    private BigDecimal confidenceScore;

    // ─── Lifecycle State ─────────────────────────────────────────────────

    /**
     * Current lifecycle state of the memory.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private MemoryState state = MemoryState.ACTIVE;

    // ─── Source Tracking ─────────────────────────────────────────────────

    /**
     * The source type indicating how this memory was created.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30, nullable = false)
    private MemorySourceType sourceType;

    /**
     * The conversation ID this memory was extracted from (nullable).
     */
    @Column(name = "source_conversation_id")
    private UUID sourceConversationId;

    // ─── Memory Links ────────────────────────────────────────────────────

    /**
     * Reference to the memory that superseded this one (self-reference).
     */
    @Column(name = "superseded_by")
    private UUID supersededBy;

    /**
     * Conflict group identifier for memories with contradictory information.
     */
    @Column(name = "conflict_group_id")
    private UUID conflictGroupId;

    /**
     * Flag indicating this memory needs user confirmation due to a conflict with similar confidence.
     * Set when two conflicting memories have similar confidence and need user input to resolve.
     */
    @Column(name = "needs_user_confirmation", nullable = false)
    private Boolean needsUserConfirmation = false;

    /**
     * Reference to an associated goal (for GOAL category memories).
     */
    @Column(name = "goal_id")
    private UUID goalId;

    // ─── Event Dates ─────────────────────────────────────────────────────

    /**
     * Event date for EVENT category memories (nullable).
     */
    @Column(name = "event_date")
    private LocalDate eventDate;

    /**
     * End date for multi-day events (nullable).
     */
    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    /**
     * Flag indicating if this is a recurring event.
     */
    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    // ─── Embedding ───────────────────────────────────────────────────────

    /**
     * Vector embedding for semantic similarity search (1536 dimensions).
     * Uses pgvector extension. May be null if embedding generation failed.
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    // ─── Counters ────────────────────────────────────────────────────────

    /**
     * Number of times the memory has been confirmed by the father.
     */
    @Column(name = "confirmation_count", nullable = false)
    private Integer confirmationCount = 0;

    /**
     * Number of times the memory has been accessed/retrieved.
     */
    @Column(name = "access_count", nullable = false)
    private Integer accessCount = 0;

    // ─── Timestamps ──────────────────────────────────────────────────────

    /**
     * When this memory was created.
     */
    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When this memory was last updated.
     */
    @NotNull
    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    /**
     * When this memory was last confirmed by the father (nullable).
     */
    @Column(name = "last_confirmed_at")
    private Instant lastConfirmedAt;

    /**
     * When this memory was last accessed/retrieved (nullable).
     */
    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    /**
     * When this memory expires (nullable for long-term tier memories).
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    // ─── Constructors ────────────────────────────────────────────────────

    /**
     * JPA requires a no-arg constructor.
     */
    protected Memory() {
    }

    /**
     * Creates a new memory with required fields.
     *
     * @param fatherId        the father this memory belongs to
     * @param category        the memory category
     * @param subjectType     who the memory is about
     * @param content         the memory content (max 500 chars)
     * @param importanceScore importance score (1-10)
     * @param confidenceScore confidence score (0.0-1.0)
     * @param sourceType      how the memory was created
     */
    public Memory(UUID fatherId, MemoryCategory category, MemorySubjectType subjectType,
                  String content, int importanceScore, BigDecimal confidenceScore,
                  MemorySourceType sourceType) {
        this.fatherId = fatherId;
        this.category = category;
        this.subjectType = subjectType;
        this.content = content;
        this.importanceScore = importanceScore;
        this.confidenceScore = confidenceScore;
        this.sourceType = sourceType;
        this.state = MemoryState.ACTIVE;
        this.createdAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
        this.expiresAt = calculateExpiration(this.createdAt, importanceScore);
    }

    // ─── Tier Classification ─────────────────────────────────────────────

    /**
     * Returns the memory tier based on importance score.
     * <ul>
     *   <li>1-3: SHORT_TERM (90 days expiration)</li>
     *   <li>4-6: MEDIUM_TERM (180 days expiration)</li>
     *   <li>7-10: LONG_TERM (never expires)</li>
     * </ul>
     *
     * @return the memory tier
     */
    public MemoryTier getTier() {
        if (importanceScore <= 3) {
            return MemoryTier.SHORT_TERM;
        } else if (importanceScore <= 6) {
            return MemoryTier.MEDIUM_TERM;
        } else {
            return MemoryTier.LONG_TERM;
        }
    }

    /**
     * Calculates the expiration instant based on creation time and importance score.
     *
     * @param creationTime    the time the memory was created
     * @param importanceScore the importance score (1-10)
     * @return the expiration instant, or null for long-term tier
     */
    public static Instant calculateExpiration(Instant creationTime, int importanceScore) {
        if (importanceScore >= 7) {
            return null; // Long-term never expires
        } else if (importanceScore >= 4) {
            return creationTime.plusSeconds(180L * 24 * 60 * 60); // 180 days
        } else {
            return creationTime.plusSeconds(90L * 24 * 60 * 60); // 90 days
        }
    }

    // ─── Confidence Operations ───────────────────────────────────────────

    /**
     * Applies confidence decay due to contradiction detection (Requirement 7).
     * Reduces confidence_score by 0.3, with a minimum of 0.0.
     */
    public void applyConfidenceDecayOnContradiction() {
        this.confidenceScore = this.confidenceScore
                .subtract(CONFIDENCE_DECAY_ON_CONTRADICTION)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Increases confidence score by the given amount (max 1.0).
     *
     * <p><b>IMPORTANT (SPEC-004 Requirement 5 Criteria 2):</b>
     * This method should ONLY be called in response to explicit user evidence:
     * <ul>
     *   <li>Father repeats the same information in a later conversation (+0.2)</li>
     *   <li>Deterministic domain event validates the memory (+0.1)</li>
     * </ul>
     *
     * <p>This method should NEVER be called as a side effect of:
     * <ul>
     *   <li>Memory retrieval</li>
     *   <li>Prompt injection</li>
     *   <li>Access count updates</li>
     *   <li>Any system usage without new user evidence</li>
     * </ul>
     *
     * @param amount the amount to increase confidence by
     */
    public void increaseConfidence(BigDecimal amount) {
        this.confidenceScore = this.confidenceScore
                .add(amount)
                .min(BigDecimal.ONE)
                .setScale(2, RoundingMode.HALF_UP);
        this.lastUpdatedAt = Instant.now();
    }

    // ─── Access Tracking ─────────────────────────────────────────────────

    /**
     * Records an access to this memory (Requirement 16).
     * Increments access_count and updates last_accessed_at.
     *
     * <p><b>IMPORTANT DESIGN DECISION (SPEC-004 Requirement 5 Criteria 2):</b>
     * This method intentionally does NOT modify confidence_score.
     * Confidence can ONLY increase through explicit user evidence:
     * <ul>
     *   <li>User confirmation ({@link #confirm()})</li>
     *   <li>User correction via supersession</li>
     *   <li>User repeats information (new evidence)</li>
     *   <li>Deterministic domain event validation</li>
     * </ul>
     *
     * <p>System usage (retrieval, prompt injection) NEVER increases confidence.
     * This ensures confidence reflects actual certainty, not usage frequency.
     */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = Instant.now();
        // NOTE: confidence_score is NOT modified here (by design per SPEC-004 Req 5 criteria 2)
    }

    // ─── State Transitions ───────────────────────────────────────────────

    /**
     * Confirms this memory, transitioning to CONFIRMED state.
     * Sets confidence to max(current, 0.9), resets decay timer, increments confirmation count.
     */
    public void confirm() {
        if (!this.state.canTransitionTo(MemoryState.CONFIRMED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.state + " to CONFIRMED");
        }
        this.state = MemoryState.CONFIRMED;
        this.confidenceScore = this.confidenceScore.max(new BigDecimal("0.90"))
                .setScale(2, RoundingMode.HALF_UP);
        this.confirmationCount++;
        this.lastConfirmedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
        // Reset expiration based on confirmation (extends tier duration)
        this.expiresAt = calculateExpiration(Instant.now(), this.importanceScore);
    }

    /**
     * Marks this memory as superseded by another memory.
     *
     * @param supersedingMemoryId the ID of the memory that supersedes this one
     */
    public void markSuperseded(UUID supersedingMemoryId) {
        if (!this.state.canTransitionTo(MemoryState.SUPERSEDED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.state + " to SUPERSEDED");
        }
        this.state = MemoryState.SUPERSEDED;
        this.supersededBy = supersedingMemoryId;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Archives this memory (typically due to capacity enforcement).
     */
    public void archive() {
        if (!this.state.canTransitionTo(MemoryState.ARCHIVED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.state + " to ARCHIVED");
        }
        this.state = MemoryState.ARCHIVED;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Expires this memory.
     */
    public void expire() {
        if (!this.state.canTransitionTo(MemoryState.EXPIRED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.state + " to EXPIRED");
        }
        this.state = MemoryState.EXPIRED;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Marks this memory for deletion.
     */
    public void delete() {
        if (!this.state.canTransitionTo(MemoryState.DELETED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.state + " to DELETED");
        }
        this.state = MemoryState.DELETED;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Reactivates an archived or expired memory.
     */
    public void reactivate() {
        if (!this.state.canTransitionTo(MemoryState.ACTIVE)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.state + " to ACTIVE");
        }
        this.state = MemoryState.ACTIVE;
        this.lastUpdatedAt = Instant.now();
        // Reset expiration from reactivation time
        this.expiresAt = calculateExpiration(Instant.now(), this.importanceScore);
    }

    // ─── Query Helpers ───────────────────────────────────────────────────

    /**
     * Checks whether this memory is currently active or confirmed.
     */
    public boolean isRetrievable() {
        return this.state == MemoryState.ACTIVE || this.state == MemoryState.CONFIRMED;
    }

    /**
     * Checks whether this memory has an embedding available for similarity search.
     */
    public boolean hasEmbedding() {
        return this.embedding != null && this.embedding.length == EMBEDDING_DIMENSION;
    }

    /**
     * Computes the combined score used for capacity enforcement ranking.
     * Score = importance_score × confidence_score.
     *
     * @return the combined score
     */
    public BigDecimal getCombinedScore() {
        return BigDecimal.valueOf(importanceScore).multiply(confidenceScore);
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public void setFatherId(UUID fatherId) {
        this.fatherId = fatherId;
    }

    public UUID getChildId() {
        return childId;
    }

    public void setChildId(UUID childId) {
        this.childId = childId;
    }

    public MemoryCategory getCategory() {
        return category;
    }

    public void setCategory(MemoryCategory category) {
        this.category = category;
    }

    public MemorySubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(MemorySubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(Integer importanceScore) {
        this.importanceScore = importanceScore;
        // Recalculate expiration when importance changes
        this.expiresAt = calculateExpiration(this.createdAt, importanceScore);
        this.lastUpdatedAt = Instant.now();
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public MemoryState getState() {
        return state;
    }

    public void setState(MemoryState state) {
        this.state = state;
    }

    public MemorySourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(MemorySourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(UUID sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(UUID supersededBy) {
        this.supersededBy = supersededBy;
    }

    public UUID getConflictGroupId() {
        return conflictGroupId;
    }

    public void setConflictGroupId(UUID conflictGroupId) {
        this.conflictGroupId = conflictGroupId;
    }

    public Boolean getNeedsUserConfirmation() {
        return needsUserConfirmation;
    }

    public void setNeedsUserConfirmation(Boolean needsUserConfirmation) {
        this.needsUserConfirmation = needsUserConfirmation;
    }

    /**
     * Flags this memory as needing user confirmation due to a conflict with similar confidence.
     */
    public void flagForUserConfirmation() {
        this.needsUserConfirmation = true;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Clears the user confirmation flag.
     */
    public void clearUserConfirmationFlag() {
        this.needsUserConfirmation = false;
        this.lastUpdatedAt = Instant.now();
    }

    public UUID getGoalId() {
        return goalId;
    }

    public void setGoalId(UUID goalId) {
        this.goalId = goalId;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalDate getEventEndDate() {
        return eventEndDate;
    }

    public void setEventEndDate(LocalDate eventEndDate) {
        this.eventEndDate = eventEndDate;
    }

    public Boolean getIsRecurring() {
        return isRecurring;
    }

    public void setIsRecurring(Boolean recurring) {
        isRecurring = recurring;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public Integer getConfirmationCount() {
        return confirmationCount;
    }

    public void setConfirmationCount(Integer confirmationCount) {
        this.confirmationCount = confirmationCount;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Instant getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    public void setLastConfirmedAt(Instant lastConfirmedAt) {
        this.lastConfirmedAt = lastConfirmedAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
