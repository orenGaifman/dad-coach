package com.dadcoach.integration.platform.scheduled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One Workflow Platform scheduled-response callback, keyed by its idempotency key
 * ({@code scheduled-response:{triggerId}}). The UNIQUE key is what makes delivery idempotent.
 */
@Entity
@Table(name = "scheduled_response_delivery")
public class ScheduledResponseDelivery {

    public enum Status { SENDING, DELIVERED, FAILED }

    /** How the message went out: free-form inside the 24h window, or an approved template outside it. */
    public enum Mode { FREE_FORM, TEMPLATE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "trigger_id", nullable = false, length = 64)
    private String triggerId;

    @Column(name = "workflow_instance_id", length = 64)
    private String workflowInstanceId;

    @Column(name = "father_id", nullable = false)
    private Long fatherId;

    @Column(name = "target_state_key", length = 100)
    private String targetStateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", length = 20)
    private Mode deliveryMode;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ScheduledResponseDelivery() {
    }

    public ScheduledResponseDelivery(
            String idempotencyKey, String triggerId, String workflowInstanceId, Long fatherId, String targetStateKey) {
        this.idempotencyKey = idempotencyKey;
        this.triggerId = triggerId;
        this.workflowInstanceId = workflowInstanceId;
        this.fatherId = fatherId;
        this.targetStateKey = targetStateKey;
        this.status = Status.SENDING;
        this.createdAt = Instant.now();
    }

    public void markDelivered(Mode mode) {
        this.status = Status.DELIVERED;
        this.deliveryMode = mode;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public Status getStatus() {
        return status;
    }

    public Mode getDeliveryMode() {
        return deliveryMode;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
