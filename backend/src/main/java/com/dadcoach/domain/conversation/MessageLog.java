package com.dadcoach.domain.conversation;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a single message in a conversation.
 * Used to maintain conversation history for AI context.
 * 
 * For outbound (AI) messages, also captures AI decision metadata:
 * - Which tool the AI chose
 * - Tool parameters
 * - State transitions
 * - Success/failure status
 */
@Entity
@Table(name = "message_log")
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "father_id", nullable = false)
    private Long fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // AI Decision tracking fields (for outbound messages)
    @Column(name = "tool_used", length = 100)
    private String toolUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_parameters", columnDefinition = "jsonb")
    private Map<String, Object> toolParameters;

    @Column(name = "previous_state", length = 50)
    private String previousState;

    @Column(name = "new_state", length = 50)
    private String newState;

    @Column(name = "tool_success")
    private Boolean toolSuccess;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum Direction {
        INBOUND,  // From father to system
        OUTBOUND  // From system to father
    }

    protected MessageLog() {
        // JPA
    }

    public MessageLog(Long fatherId, Direction direction, String content) {
        this.fatherId = fatherId;
        this.direction = direction;
        this.content = content;
        this.createdAt = Instant.now();
    }

    // Static factory methods
    public static MessageLog inbound(Long fatherId, String content) {
        return new MessageLog(fatherId, Direction.INBOUND, content);
    }

    public static MessageLog outbound(Long fatherId, String content) {
        return new MessageLog(fatherId, Direction.OUTBOUND, content);
    }

    /**
     * Creates an outbound message with AI decision metadata.
     */
    public static MessageLog outboundWithAiDecision(
            Long fatherId,
            String content,
            String toolUsed,
            Map<String, Object> toolParameters,
            String previousState,
            String newState,
            boolean toolSuccess,
            String errorMessage) {
        MessageLog msg = new MessageLog(fatherId, Direction.OUTBOUND, content);
        msg.toolUsed = toolUsed;
        msg.toolParameters = toolParameters;
        msg.previousState = previousState;
        msg.newState = newState;
        msg.toolSuccess = toolSuccess;
        msg.errorMessage = errorMessage;
        return msg;
    }

    // Getters
    public Long getId() { return id; }
    public Long getFatherId() { return fatherId; }
    public Direction getDirection() { return direction; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public String getToolUsed() { return toolUsed; }
    public Map<String, Object> getToolParameters() { return toolParameters; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
    public Boolean getToolSuccess() { return toolSuccess; }
    public String getErrorMessage() { return errorMessage; }
}
