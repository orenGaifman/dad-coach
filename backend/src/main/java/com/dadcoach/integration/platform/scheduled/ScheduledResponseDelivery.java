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

    /**
     * SENDING (claimed, not sent yet) -> ACCEPTED (Meta's API took it; wamid kept) or HELD (the shared-number gateway
     * keeps it for later) or FAILED. Meta's receipts then move an ACCEPTED row forward only: SENT -> DELIVERED -> READ,
     * or FAILED (DeliveryReceipts). DELIVERED means a delivery receipt; rows written before V45 used DELIVERED for
     * "accepted" and have no wamid.
     *
     * <p>D-042: a HELD row learns from the gateway's report what became of it - ACCEPTED with the wamid (sent), FAILED
     * (refused, or expired unsent), or UNKNOWN: the gateway's send may have reached Meta (5xx, timeout after the
     * request was written). UNKNOWN is never treated as failed and never sent again.</p>
     */
    public enum Status {
        SENDING, ACCEPTED, HELD, SENT, DELIVERED, READ, FAILED, UNKNOWN;

        /** Handed over for delivery (the platform is told DELIVERED, as before ACCEPTED existed). */
        public boolean handedOver() {
            return this != SENDING && this != FAILED;
        }
    }

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

    /** Meta's wamid of the accepted send (DC-B1): the key Meta's status receipts are matched on. */
    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    /** The gateway's {@code held:<n>} when the shared number held the message; never a wamid. */
    @Column(name = "gateway_hold_id", length = 64)
    private String gatewayHoldId;

    /** When the latest receipt (SENT / DELIVERED / READ / FAILED from Meta) happened. */
    @Column(name = "status_at")
    private Instant statusAt;

    protected ScheduledResponseDelivery() {
    }

    public ScheduledResponseDelivery(
            String idempotencyKey, String triggerId, String workflowInstanceId, Long fatherId, String targetStateKey,
            Instant now) {
        this.idempotencyKey = idempotencyKey;
        this.triggerId = triggerId;
        this.workflowInstanceId = workflowInstanceId;
        this.fatherId = fatherId;
        this.targetStateKey = targetStateKey;
        this.status = Status.SENDING;
        this.createdAt = now;
    }

    /** A successful send: ACCEPTED with Meta's wamid, or HELD with the gateway's id (not delivered yet). */
    public void markAccepted(Mode mode, com.dadcoach.channel.delivery.DeliveryResult result, Instant now) {
        if (result.isHeld()) {
            this.status = Status.HELD;
            this.gatewayHoldId = result.providerMessageId();
        } else {
            this.status = Status.ACCEPTED;
            this.providerMessageId = result.metaMessageId();
        }
        this.deliveryMode = mode;
        this.completedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.completedAt = now;
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

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getGatewayHoldId() {
        return gatewayHoldId;
    }
}
