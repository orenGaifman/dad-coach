package com.dadcoach.statemachine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit log for all state transitions in the system.
 *
 * <p>Records both successful and invalid (rejected) state transitions for
 * full audit trail and future ML training data.</p>
 */
@Entity
@Table(name = "state_transition_log")
public class StateTransitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "from_state", nullable = false, length = 30)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 30)
    private String toState;

    @Column(name = "trigger_reason", length = 200)
    private String triggerReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StateTransitionLog() {
        // JPA requires default constructor
    }

    public StateTransitionLog(String entityType, Long entityId, String fromState,
                              String toState, String triggerReason) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromState = fromState;
        this.toState = toState;
        this.triggerReason = triggerReason;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
