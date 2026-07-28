package com.dadcoach.conversation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity tracking processed messages for idempotency detection.
 * Each entry has a unique idempotency key and a 24-hour TTL (expires_at).
 * Duplicate messages are detected by looking up the idempotency key
 * before any business logic is executed.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "response_id")
    private UUID responseId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ProcessedMessage() {
        // JPA requires no-arg constructor
    }

    private ProcessedMessage(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.fatherId = builder.fatherId;
        this.responseId = builder.responseId;
        this.processedAt = Instant.now();
        this.expiresAt = this.processedAt.plusSeconds(24 * 60 * 60); // 24 hours TTL
    }

    @PrePersist
    void prePersist() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
        if (expiresAt == null) {
            expiresAt = processedAt.plusSeconds(24 * 60 * 60);
        }
    }

    // --- Getters ---

    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getFatherId() { return fatherId; }
    public UUID getResponseId() { return responseId; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    // --- Setters for mutable fields ---

    public void setResponseId(UUID responseId) { this.responseId = responseId; }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String idempotencyKey;
        private UUID fatherId;
        private UUID responseId;

        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder fatherId(UUID fatherId) { this.fatherId = fatherId; return this; }
        public Builder responseId(UUID responseId) { this.responseId = responseId; return this; }

        public ProcessedMessage build() {
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
            if (fatherId == null) throw new IllegalArgumentException("fatherId is required");
            return new ProcessedMessage(this);
        }
    }

    @Override
    public String toString() {
        return "ProcessedMessage{" +
                "idempotencyKey='" + idempotencyKey + '\'' +
                ", fatherId=" + fatherId +
                ", responseId=" + responseId +
                ", processedAt=" + processedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
