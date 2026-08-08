package com.dadcoach.domain.conversation;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing a single message in a conversation.
 * Used to maintain conversation history for AI context.
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

    // Getters
    public Long getId() { return id; }
    public Long getFatherId() { return fatherId; }
    public Direction getDirection() { return direction; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
