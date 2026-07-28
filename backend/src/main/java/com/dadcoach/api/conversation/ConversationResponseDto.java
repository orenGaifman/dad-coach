package com.dadcoach.api.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for conversations exposed through the Father API.
 * <p>
 * Security invariants:
 * <ul>
 *   <li>System prompts are NEVER included in the messages list</li>
 *   <li>Internal metadata (AI telemetry, prompt content) is omitted</li>
 *   <li>Only messages visible to the father are returned</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationResponseDto {

    private UUID id;
    private String type;
    private String status;

    @JsonProperty("message_count")
    private int messageCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("last_message_at")
    private Instant lastMessageAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    @JsonProperty("completion_reason")
    private String completionReason;

    /**
     * Messages within the conversation, filtered to exclude system prompts.
     * Only populated when retrieving a single conversation (get with messages).
     */
    private List<MessageDto> messages;

    public ConversationResponseDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCompletionReason() {
        return completionReason;
    }

    public void setCompletionReason(String completionReason) {
        this.completionReason = completionReason;
    }

    public List<MessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageDto> messages) {
        this.messages = messages;
    }

    // ─── Nested Message DTO ──────────────────────────────────────────────

    /**
     * A single message within a conversation, visible to the father.
     * System prompts and internal metadata are excluded.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageDto {

        private UUID id;
        private String direction;
        private String content;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("created_at")
        private Instant createdAt;

        @JsonProperty("sequence_number")
        private int sequenceNumber;

        public MessageDto() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }

        public void setSequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
        }
    }
}
