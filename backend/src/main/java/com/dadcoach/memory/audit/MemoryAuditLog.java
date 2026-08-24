package com.dadcoach.memory.audit;

import com.dadcoach.memory.MemoryState;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a memory audit log entry.
 * Maps to the "memory_audit_log" table as defined in SPEC-004.
 *
 * <p>From SPEC-004 Requirement 24 (REQ-24):
 * Every memory lifecycle event (create, update, archive, confirm, expire) SHALL produce
 * a durable audit record containing: event_type, memory_id, father_id, timestamp,
 * actor_type (AI/USER/SYSTEM), before/after state snapshots. The audit trail is separate
 * from operational memory storage and retained independently.
 *
 * <p>From SPEC-004 Requirement 2 Criteria 9:
 * THE Memory_System SHALL log every state transition with: memory_id, from_state,
 * to_state, trigger_reason, triggered_by (system/father), and timestamp.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Append-only: Audit entries are never modified or deleted (except by retention policy)</li>
 *   <li>Durable: Written synchronously with memory operations</li>
 *   <li>Independent: Separate from operational memory storage</li>
 *   <li>Retention: Audit metadata retained for 2 years per product policy</li>
 * </ul>
 *
 * <p><strong>Immutability Enforcement:</strong>
 * This entity enforces append-only behavior at the JPA level through:
 * <ul>
 *   <li>{@code @PreUpdate} callback that throws {@link UnsupportedOperationException} if update is attempted</li>
 *   <li>All core fields marked with {@code updatable = false}</li>
 *   <li>No public setters for business fields (only JPA-required setters remain)</li>
 * </ul>
 *
 * <p><strong>Audit Fields (per SPEC-004 design):</strong>
 * <ul>
 *   <li>{@code operation_type} - The type of lifecycle event (CREATE, UPDATE, CONFIRM, etc.)</li>
 *   <li>{@code from_state} - The memory state before the operation (null for CREATE)</li>
 *   <li>{@code to_state} - The memory state after the operation</li>
 *   <li>{@code trigger_type} - Who/what triggered the event (AI, USER, SYSTEM)</li>
 *   <li>{@code triggered_by} - Specific actor reference (e.g., "SYSTEM:decay_job", "USER:father_123")</li>
 * </ul>
 *
 * @see EventType
 * @see ActorType
 * @see MemoryAuditService
 */
@Entity
@Table(name = "memory_audit_log",
        indexes = {
                @Index(name = "idx_memory_audit_father", columnList = "father_id, created_at DESC"),
                @Index(name = "idx_memory_audit_memory", columnList = "memory_id, created_at DESC")
        })
public class MemoryAuditLog {

    /**
     * Prevents updates to existing audit log entries.
     * Audit log is append-only by design per SPEC-004 Requirement 24.
     *
     * @throws UnsupportedOperationException always, to enforce append-only behavior
     */
    @PreUpdate
    protected void preventUpdate() {
        throw new UnsupportedOperationException(
                "Audit log entries are immutable. Updates are not permitted per SPEC-004 Requirement 24.");
    }

    // ─── Primary Key ─────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Core Audit Fields ───────────────────────────────────────────────

    /**
     * The memory this audit entry relates to.
     * Note: Not a FK since the memory may be deleted while audit is retained.
     */
    @NotNull
    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    /**
     * The father this memory belongs to.
     */
    @NotNull
    @Column(name = "father_id", nullable = false, updatable = false)
    private UUID fatherId;

    /**
     * The type of lifecycle event being logged (operation_type).
     * Examples: CREATE, UPDATE, CONFIRM, ARCHIVE, SUPERSEDE, EXPIRE, DELETE.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 30, nullable = false, updatable = false)
    private EventType operationType;

    // ─── State Transition Fields (SPEC-004 Req 2 Criteria 9) ─────────────

    /**
     * The memory lifecycle state before the operation.
     * Null for CREATE events where there is no prior state.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 20, updatable = false)
    private MemoryState fromState;

    /**
     * The memory lifecycle state after the operation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 20, updatable = false)
    private MemoryState toState;

    // ─── Actor Information ───────────────────────────────────────────────

    /**
     * Who or what type of actor triggered this event (trigger_type).
     * Values: AI, USER, SYSTEM.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 30, nullable = false, updatable = false)
    private ActorType triggerType;

    /**
     * Specific actor reference identifying who/what triggered this event.
     * Format examples:
     * <ul>
     *   <li>"SYSTEM:decay_job" - Triggered by system decay job</li>
     *   <li>"SYSTEM:consolidation_job" - Triggered by consolidation job</li>
     *   <li>"SYSTEM:expiration_job" - Triggered by expiration job</li>
     *   <li>"USER:confirmation" - Triggered by user confirming memory</li>
     *   <li>"USER:correction" - Triggered by user correcting memory</li>
     *   <li>"USER:deletion_request" - Triggered by user deletion request</li>
     *   <li>"AI:extraction" - Triggered by AI memory extraction</li>
     *   <li>"AI:consolidation" - Triggered by AI consolidation</li>
     * </ul>
     */
    @NotNull
    @Size(max = 100)
    @Column(name = "triggered_by", length = 100, nullable = false, updatable = false)
    private String triggeredBy;

    // ─── State Snapshots ─────────────────────────────────────────────────

    /**
     * JSON snapshot of memory state before the event.
     * Null for CREATE events.
     * Contains key fields: content, category, importance_score, confidence_score, state.
     */
    @Column(name = "state_before", columnDefinition = "JSONB", updatable = false)
    private String stateBefore;

    /**
     * JSON snapshot of memory state after the event.
     * Contains key fields: content, category, importance_score, confidence_score, state.
     */
    @Column(name = "state_after", columnDefinition = "JSONB", updatable = false)
    private String stateAfter;

    // ─── Timestamp ───────────────────────────────────────────────────────

    /**
     * When this audit entry was created.
     */
    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ─── Constructors ────────────────────────────────────────────────────

    /**
     * JPA requires a no-arg constructor.
     */
    protected MemoryAuditLog() {
    }

    /**
     * Creates a new audit log entry with all required fields.
     *
     * @param memoryId      the memory being audited
     * @param fatherId      the father who owns the memory
     * @param operationType the type of lifecycle event (operation_type)
     * @param fromState     the memory state before the operation (null for CREATE)
     * @param toState       the memory state after the operation
     * @param triggerType   who or what type triggered the event (trigger_type)
     * @param triggeredBy   specific actor reference
     * @param stateBefore   JSON snapshot of state before the event (null for CREATE)
     * @param stateAfter    JSON snapshot of state after the event
     */
    public MemoryAuditLog(UUID memoryId, UUID fatherId, EventType operationType,
                          MemoryState fromState, MemoryState toState,
                          ActorType triggerType, String triggeredBy,
                          String stateBefore, String stateAfter) {
        this.memoryId = memoryId;
        this.fatherId = fatherId;
        this.operationType = operationType;
        this.fromState = fromState;
        this.toState = toState;
        this.triggerType = triggerType;
        this.triggeredBy = triggeredBy;
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
        this.createdAt = Instant.now();
    }

    /**
     * Creates a new audit log entry with required fields (backward-compatible constructor).
     *
     * @param memoryId   the memory being audited
     * @param fatherId   the father who owns the memory
     * @param eventType  the type of lifecycle event
     * @param actorType  who or what triggered the event
     * @param stateAfter JSON snapshot of memory state after the event
     * @deprecated Use the full constructor with fromState, toState, and triggeredBy for complete audit trail
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public MemoryAuditLog(UUID memoryId, UUID fatherId, EventType eventType,
                          ActorType actorType, String stateAfter) {
        this.memoryId = memoryId;
        this.fatherId = fatherId;
        this.operationType = eventType;
        this.triggerType = actorType;
        this.triggeredBy = actorType.name() + ":legacy";
        this.stateAfter = stateAfter;
        this.createdAt = Instant.now();
    }

    /**
     * Creates a new audit log entry with before and after states (backward-compatible constructor).
     *
     * @param memoryId    the memory being audited
     * @param fatherId    the father who owns the memory
     * @param eventType   the type of lifecycle event
     * @param actorType   who or what triggered the event
     * @param stateBefore JSON snapshot of memory state before the event (null for CREATE)
     * @param stateAfter  JSON snapshot of memory state after the event
     * @deprecated Use the full constructor with fromState, toState, and triggeredBy for complete audit trail
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public MemoryAuditLog(UUID memoryId, UUID fatherId, EventType eventType,
                          ActorType actorType, String stateBefore, String stateAfter) {
        this(memoryId, fatherId, eventType, actorType, stateAfter);
        this.stateBefore = stateBefore;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    /**
     * Returns the operation type (event type) for this audit entry.
     *
     * @return the operation type
     */
    public EventType getOperationType() {
        return operationType;
    }

    /**
     * Returns the event type (alias for operationType for backward compatibility).
     *
     * @return the event type
     * @deprecated Use {@link #getOperationType()} instead
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public EventType getEventType() {
        return operationType;
    }

    /**
     * Returns the memory state before the operation.
     *
     * @return the from_state, or null for CREATE events
     */
    public MemoryState getFromState() {
        return fromState;
    }

    /**
     * Returns the memory state after the operation.
     *
     * @return the to_state
     */
    public MemoryState getToState() {
        return toState;
    }

    /**
     * Returns the trigger type (actor type) for this audit entry.
     *
     * @return the trigger type
     */
    public ActorType getTriggerType() {
        return triggerType;
    }

    /**
     * Returns the actor type (alias for triggerType for backward compatibility).
     *
     * @return the actor type
     * @deprecated Use {@link #getTriggerType()} instead
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public ActorType getActorType() {
        return triggerType;
    }

    /**
     * Returns the specific actor reference that triggered this event.
     *
     * @return the triggered_by reference
     */
    public String getTriggeredBy() {
        return triggeredBy;
    }

    public String getStateBefore() {
        return stateBefore;
    }

    public String getStateAfter() {
        return stateAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ─── Setters (limited - audit entries are immutable) ───────────────

    /**
     * Sets the ID. Used internally by JPA during entity persistence.
     * <p>Note: This is required for JPA to assign the generated UUID.
     * Business code should not call this method directly.
     *
     * @param id the entity ID
     */
    void setId(UUID id) {
        this.id = id;
    }

    // Note: No setters for business fields to enforce immutability.
    // All values must be set at construction time.

    // ─── Object Methods ──────────────────────────────────────────────────

    @Override
    public String toString() {
        return "MemoryAuditLog{" +
                "id=" + id +
                ", memoryId=" + memoryId +
                ", fatherId=" + fatherId +
                ", operationType=" + operationType +
                ", fromState=" + fromState +
                ", toState=" + toState +
                ", triggerType=" + triggerType +
                ", triggeredBy='" + triggeredBy + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
