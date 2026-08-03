package com.dadcoach.workflow;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable JPA entity representing a workflow state transition log entry.
 * Maps to the "workflow_state_transition_log" table.
 *
 * <p>This entity captures every state transition in the workflow state machine,
 * providing a complete audit trail of a father's journey through the workflow.
 * Transition logs are append-only and never updated or deleted.</p>
 *
 * <p>Implements Requirement 1.4: "WHEN a state transition occurs, THE Workflow_Engine SHALL
 * log the transition to the state_transition_log table with timestamp, from_state, to_state,
 * and trigger_reason."</p>
 *
 * @see WorkflowState
 * @see <a href="Requirements 1.4">State Transition Logging</a>
 */
@Entity
@Table(name = "workflow_state_transition_log")
@Immutable
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The UUID identifier of the father whose workflow state changed.
     * References the father's external_id.
     */
    @Column(name = "father_id", nullable = false, updatable = false)
    private UUID fatherId;

    /**
     * The workflow state the father was in before the transition.
     * Stored as the enum name string (e.g., "WELCOME", "WAITING").
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30, nullable = false, updatable = false)
    private WorkflowState fromState;

    /**
     * The workflow state the father transitioned to.
     * Stored as the enum name string (e.g., "SCHEDULE_QUALITY_TIME", "QUALITY_TIME_FOLLOW_UP").
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 30, nullable = false, updatable = false)
    private WorkflowState toState;

    /**
     * The reason or trigger that caused this state transition.
     * Examples: "USER_ACKNOWLEDGMENT", "CALENDAR_EVENT_CREATED", "QUALITY_TIME_ENDED",
     * "COMPLETION_CONFIRMED", "TIMEOUT", "SCHEDULER_TRIGGER".
     */
    @Column(name = "trigger_reason", length = 50, nullable = false, updatable = false)
    private String triggerReason;

    /**
     * Optional UUID of the message that triggered this transition.
     * Null for scheduler-triggered transitions or other non-message triggers.
     */
    @Column(name = "trigger_message_id", updatable = false)
    private UUID triggerMessageId;

    /**
     * Timestamp when this transition occurred.
     * Automatically set on creation if not provided.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JPA-required no-arg constructor. Not for application use.
     */
    protected WorkflowTransition() {
    }

    private WorkflowTransition(Builder builder) {
        this.id = builder.id;
        this.fatherId = builder.fatherId;
        this.fromState = builder.fromState;
        this.toState = builder.toState;
        this.triggerReason = builder.triggerReason;
        this.triggerMessageId = builder.triggerMessageId;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    }

    // ─── Getters (no setters — entity is immutable) ─────────────────────

    /**
     * Returns the unique identifier of this transition log entry.
     *
     * @return the transition log ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the UUID of the father whose workflow state changed.
     *
     * @return the father's external UUID
     */
    public UUID getFatherId() {
        return fatherId;
    }

    /**
     * Returns the workflow state before the transition.
     *
     * @return the source state
     */
    public WorkflowState getFromState() {
        return fromState;
    }

    /**
     * Returns the workflow state after the transition.
     *
     * @return the target state
     */
    public WorkflowState getToState() {
        return toState;
    }

    /**
     * Returns the reason that triggered this transition.
     *
     * @return the trigger reason
     */
    public String getTriggerReason() {
        return triggerReason;
    }

    /**
     * Returns the optional message ID that triggered this transition.
     *
     * @return the trigger message ID, or null if not message-triggered
     */
    public UUID getTriggerMessageId() {
        return triggerMessageId;
    }

    /**
     * Returns the timestamp when this transition occurred.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    // ─── Object Methods ──────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTransition that = (WorkflowTransition) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorkflowTransition{" +
                "id=" + id +
                ", fatherId=" + fatherId +
                ", fromState=" + fromState +
                ", toState=" + toState +
                ", triggerReason='" + triggerReason + '\'' +
                ", triggerMessageId=" + triggerMessageId +
                ", createdAt=" + createdAt +
                '}';
    }

    // ─── Builder ─────────────────────────────────────────────────────────

    /**
     * Creates a new builder for constructing a WorkflowTransition instance.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating WorkflowTransition instances.
     * Enforces required fields and provides sensible defaults.
     */
    public static final class Builder {
        private UUID id;
        private UUID fatherId;
        private WorkflowState fromState;
        private WorkflowState toState;
        private String triggerReason;
        private UUID triggerMessageId;
        private Instant createdAt;

        private Builder() {
        }

        /**
         * Sets the transition log ID. Optional - auto-generated if not provided.
         *
         * @param id the transition log ID
         * @return this builder
         */
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the father's UUID. Required.
         *
         * @param fatherId the father's external UUID
         * @return this builder
         */
        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }

        /**
         * Sets the source workflow state. Required.
         *
         * @param fromState the state before transition
         * @return this builder
         */
        public Builder fromState(WorkflowState fromState) {
            this.fromState = fromState;
            return this;
        }

        /**
         * Sets the target workflow state. Required.
         *
         * @param toState the state after transition
         * @return this builder
         */
        public Builder toState(WorkflowState toState) {
            this.toState = toState;
            return this;
        }

        /**
         * Sets the trigger reason. Required.
         *
         * @param triggerReason the reason for the transition
         * @return this builder
         */
        public Builder triggerReason(String triggerReason) {
            this.triggerReason = triggerReason;
            return this;
        }

        /**
         * Sets the optional trigger message ID.
         *
         * @param triggerMessageId the ID of the message that triggered this transition
         * @return this builder
         */
        public Builder triggerMessageId(UUID triggerMessageId) {
            this.triggerMessageId = triggerMessageId;
            return this;
        }

        /**
         * Sets the creation timestamp. Optional - defaults to now.
         *
         * @param createdAt the timestamp of the transition
         * @return this builder
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Builds the WorkflowTransition instance after validating required fields.
         *
         * @return the constructed WorkflowTransition
         * @throws IllegalStateException if required fields are missing
         */
        public WorkflowTransition build() {
            if (fatherId == null) {
                throw new IllegalStateException("fatherId is required");
            }
            if (fromState == null) {
                throw new IllegalStateException("fromState is required");
            }
            if (toState == null) {
                throw new IllegalStateException("toState is required");
            }
            if (triggerReason == null || triggerReason.isBlank()) {
                throw new IllegalStateException("triggerReason is required");
            }
            return new WorkflowTransition(this);
        }
    }
}
