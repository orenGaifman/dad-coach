package com.dadcoach.conversation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity representing a single message within a conversation.
 * Messages have a direction (INBOUND from father, OUTBOUND from system),
 * a sequence number for ordering, and optional metadata stored as JSONB.
 */
@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    protected ConversationMessage() {
        // JPA requires no-arg constructor
    }

    private ConversationMessage(Builder builder) {
        this.conversationId = builder.conversationId;
        this.direction = builder.direction;
        this.content = builder.content;
        this.messageType = builder.messageType;
        this.metadata = builder.metadata;
        this.createdAt = Instant.now();
        this.sequenceNumber = builder.sequenceNumber;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (messageType == null) {
            messageType = "TEXT";
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public String getDirection() { return direction; }
    public String getContent() { return content; }
    public String getMessageType() { return messageType; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public int getSequenceNumber() { return sequenceNumber; }

    // --- Setters for mutable fields ---

    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID conversationId;
        private String direction;
        private String content;
        private String messageType = "TEXT";
        private Map<String, Object> metadata;
        private int sequenceNumber;

        public Builder conversationId(UUID conversationId) { this.conversationId = conversationId; return this; }
        public Builder direction(String direction) { this.direction = direction; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder messageType(String messageType) { this.messageType = messageType; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder sequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; return this; }

        public ConversationMessage build() {
            if (conversationId == null) throw new IllegalArgumentException("conversationId is required");
            if (direction == null || direction.isBlank()) throw new IllegalArgumentException("direction is required");
            if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
            return new ConversationMessage(this);
        }
    }

    @Override
    public String toString() {
        return "ConversationMessage{" +
                "id=" + id +
                ", conversationId=" + conversationId +
                ", direction='" + direction + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", messageType='" + messageType + '\'' +
                '}';
    }
}
