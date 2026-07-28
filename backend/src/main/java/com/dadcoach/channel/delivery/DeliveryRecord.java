package com.dadcoach.channel.delivery;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity tracking the full delivery lifecycle of an outbound message.
 * Each outbound message has one DeliveryRecord that persists its delivery
 * status transitions, retry count, and timing information.
 *
 * <p>Status lifecycle: PENDING → SENT → DELIVERED → READ / FAILED
 * <p>Status updates are correlated by provider_message_id from webhook callbacks.
 */
@Entity
@Table(
    name = "delivery_records",
    indexes = {
        @Index(name = "idx_delivery_status", columnList = "status"),
        @Index(name = "idx_delivery_provider_id", columnList = "provider_message_id")
    }
)
public class DeliveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeliveryRecord() {
        // JPA requires no-arg constructor
    }

    /**
     * Creates a new DeliveryRecord for an outbound message in PENDING status.
     */
    public DeliveryRecord(UUID messageId, UUID fatherId, String channel) {
        this.messageId = messageId;
        this.fatherId = fatherId;
        this.channel = channel;
        this.direction = "OUTBOUND";
        this.status = DeliveryStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ─── Status transition methods ───────────────────────────────────────

    /**
     * Marks the message as SENT (provider accepted the message).
     */
    public void markSent(String providerMessageId, Instant sentAt) {
        this.status = DeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = sentAt;
    }

    /**
     * Marks the message as DELIVERED (reached father's device).
     */
    public void markDelivered(Instant deliveredAt) {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
        // Infer SENT if we missed that update
        if (this.sentAt == null) {
            this.sentAt = deliveredAt;
        }
    }

    /**
     * Marks the message as READ (father opened/viewed it).
     * Accepts out-of-order: READ may arrive without prior DELIVERED.
     */
    public void markRead(Instant readAt) {
        this.status = DeliveryStatus.READ;
        this.readAt = readAt;
        // Infer intermediate states if missed
        if (this.deliveredAt == null) {
            this.deliveredAt = readAt;
        }
        if (this.sentAt == null) {
            this.sentAt = readAt;
        }
    }

    /**
     * Marks the message as FAILED with a reason.
     */
    public void markFailed(String failureReason, Instant failedAt) {
        this.status = DeliveryStatus.FAILED;
        this.failureReason = failureReason;
        this.failedAt = failedAt;
    }

    /**
     * Increments the retry count.
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getMessageId() { return messageId; }

    public UUID getFatherId() { return fatherId; }

    public String getChannel() { return channel; }

    public String getProviderMessageId() { return providerMessageId; }

    public DeliveryStatus getStatus() { return status; }

    public String getDirection() { return direction; }

    public String getFailureReason() { return failureReason; }

    public int getRetryCount() { return retryCount; }

    public Instant getSentAt() { return sentAt; }

    public Instant getDeliveredAt() { return deliveredAt; }

    public Instant getReadAt() { return readAt; }

    public Instant getFailedAt() { return failedAt; }

    public Instant getCreatedAt() { return createdAt; }

    // ─── Setters for testing ─────────────────────────────────────────────

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    @Override
    public String toString() {
        return "DeliveryRecord{" +
                "id=" + id +
                ", messageId=" + messageId +
                ", fatherId=" + fatherId +
                ", channel='" + channel + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", providerMessageId='" + providerMessageId + '\'' +
                '}';
    }
}
