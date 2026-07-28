package com.dadcoach.conversation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the transactional outbox pattern.
 * Side-effects (memory extraction, event publication, metric updates) are written
 * to this table within the same transaction as conversation state changes.
 * A background poller processes entries with retry logic and exponential backoff.
 */
@Entity
@Table(name = "side_effect_outbox")
public class SideEffectOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "effect_type", nullable = false, length = 50)
    private String effectType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    protected SideEffectOutbox() {
        // JPA requires no-arg constructor
    }

    private SideEffectOutbox(Builder builder) {
        this.fatherId = builder.fatherId;
        this.conversationId = builder.conversationId;
        this.effectType = builder.effectType;
        this.payload = builder.payload;
        this.status = builder.status;
        this.retryCount = builder.retryCount;
        this.maxRetries = builder.maxRetries;
        this.createdAt = Instant.now();
        this.nextRetryAt = builder.nextRetryAt;
        this.completedAt = builder.completedAt;
        this.errorDetail = builder.errorDetail;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getFatherId() { return fatherId; }
    public UUID getConversationId() { return conversationId; }
    public String getEffectType() { return effectType; }
    public Map<String, Object> getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorDetail() { return errorDetail; }

    // --- Setters for mutable fields ---

    public void setStatus(String status) { this.status = status; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID fatherId;
        private UUID conversationId;
        private String effectType;
        private Map<String, Object> payload;
        private String status = "PENDING";
        private int retryCount = 0;
        private int maxRetries = 3;
        private Instant nextRetryAt;
        private Instant completedAt;
        private String errorDetail;

        public Builder fatherId(UUID fatherId) { this.fatherId = fatherId; return this; }
        public Builder conversationId(UUID conversationId) { this.conversationId = conversationId; return this; }
        public Builder effectType(String effectType) { this.effectType = effectType; return this; }
        public Builder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder nextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder errorDetail(String errorDetail) { this.errorDetail = errorDetail; return this; }

        public SideEffectOutbox build() {
            if (fatherId == null) throw new IllegalArgumentException("fatherId is required");
            if (effectType == null || effectType.isBlank()) throw new IllegalArgumentException("effectType is required");
            if (payload == null) throw new IllegalArgumentException("payload is required");
            return new SideEffectOutbox(this);
        }
    }

    @Override
    public String toString() {
        return "SideEffectOutbox{" +
                "id=" + id +
                ", fatherId=" + fatherId +
                ", effectType='" + effectType + '\'' +
                ", status='" + status + '\'' +
                ", retryCount=" + retryCount +
                ", maxRetries=" + maxRetries +
                '}';
    }
}
