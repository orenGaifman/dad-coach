package com.dadcoach.conversation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a coaching conversation with a father.
 * A conversation has a type, status, message counts, and expiration/completion tracking.
 * Only one ACTIVE conversation per father is allowed at any time.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "father_message_count", nullable = false)
    private int fatherMessageCount;

    @Column(name = "system_message_count", nullable = false)
    private int systemMessageCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_reason", length = 50)
    private String completionReason;

    protected Conversation() {
        // JPA requires no-arg constructor
    }

    private Conversation(Builder builder) {
        this.fatherId = builder.fatherId;
        this.type = builder.type;
        this.status = builder.status;
        this.messageCount = builder.messageCount;
        this.fatherMessageCount = builder.fatherMessageCount;
        this.systemMessageCount = builder.systemMessageCount;
        this.expiresAt = builder.expiresAt;
        this.createdAt = Instant.now();
        this.lastMessageAt = builder.lastMessageAt;
        this.completedAt = builder.completedAt;
        this.completionReason = builder.completionReason;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getFatherId() { return fatherId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public int getMessageCount() { return messageCount; }
    public int getFatherMessageCount() { return fatherMessageCount; }
    public int getSystemMessageCount() { return systemMessageCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletionReason() { return completionReason; }

    // --- Setters for mutable fields ---

    public void setStatus(String status) { this.status = status; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public void setFatherMessageCount(int fatherMessageCount) { this.fatherMessageCount = fatherMessageCount; }
    public void setSystemMessageCount(int systemMessageCount) { this.systemMessageCount = systemMessageCount; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID fatherId;
        private String type;
        private String status = "ACTIVE";
        private int messageCount = 0;
        private int fatherMessageCount = 0;
        private int systemMessageCount = 0;
        private Instant expiresAt;
        private Instant lastMessageAt;
        private Instant completedAt;
        private String completionReason;

        public Builder fatherId(UUID fatherId) { this.fatherId = fatherId; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder messageCount(int messageCount) { this.messageCount = messageCount; return this; }
        public Builder fatherMessageCount(int fatherMessageCount) { this.fatherMessageCount = fatherMessageCount; return this; }
        public Builder systemMessageCount(int systemMessageCount) { this.systemMessageCount = systemMessageCount; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder lastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder completionReason(String completionReason) { this.completionReason = completionReason; return this; }

        public Conversation build() {
            if (fatherId == null) throw new IllegalArgumentException("fatherId is required");
            if (type == null || type.isBlank()) throw new IllegalArgumentException("type is required");
            return new Conversation(this);
        }
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", fatherId=" + fatherId +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", messageCount=" + messageCount +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
