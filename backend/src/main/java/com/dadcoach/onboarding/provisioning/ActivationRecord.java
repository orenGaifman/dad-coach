package com.dadcoach.onboarding.provisioning;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an activation record for the WhatsApp activation handshake.
 * Created during provisioning with status PENDING, updated as the father interacts.
 */
@Entity
@Table(name = "activation_records", indexes = {
    @Index(name = "idx_activation_father", columnList = "father_id", unique = true),
    @Index(name = "idx_activation_status", columnList = "status")
})
public class ActivationRecord {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "activation_id")
    private UUID activationId;

    @Column(name = "father_id", nullable = false, unique = true)
    private UUID fatherId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private ActivationStatus status;

    @Column(name = "deep_link_generated_at")
    private Instant deepLinkGeneratedAt;

    @Column(name = "link_clicked_at")
    private Instant linkClickedAt;

    @Column(name = "message_received_at")
    private Instant messageReceivedAt;

    @Column(name = "conversation_started_at")
    private Instant conversationStartedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    protected ActivationRecord() {
        // JPA requires a no-arg constructor
    }

    public ActivationRecord(UUID fatherId, UUID sessionId) {
        this.fatherId = fatherId;
        this.sessionId = sessionId;
        this.status = ActivationStatus.PENDING;
        this.deepLinkGeneratedAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }

    public UUID getFatherId() { return fatherId; }
    public void setFatherId(UUID fatherId) { this.fatherId = fatherId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public ActivationStatus getStatus() { return status; }
    public void setStatus(ActivationStatus status) { this.status = status; }

    public Instant getDeepLinkGeneratedAt() { return deepLinkGeneratedAt; }
    public void setDeepLinkGeneratedAt(Instant deepLinkGeneratedAt) { this.deepLinkGeneratedAt = deepLinkGeneratedAt; }

    public Instant getLinkClickedAt() { return linkClickedAt; }
    public void setLinkClickedAt(Instant linkClickedAt) { this.linkClickedAt = linkClickedAt; }

    public Instant getMessageReceivedAt() { return messageReceivedAt; }
    public void setMessageReceivedAt(Instant messageReceivedAt) { this.messageReceivedAt = messageReceivedAt; }

    public Instant getConversationStartedAt() { return conversationStartedAt; }
    public void setConversationStartedAt(Instant conversationStartedAt) { this.conversationStartedAt = conversationStartedAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
