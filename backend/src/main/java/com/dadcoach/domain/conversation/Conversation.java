package com.dadcoach.domain.conversation;

import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.domain.conversation.ConversationStatus;
import com.dadcoach.domain.conversation.ConversationType;
import com.dadcoach.domain.father.Father;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity representing a coaching conversation.
 * Maps to the "conversation" table (V2 migration).
 *
 * <p>State machine transitions:</p>
 * <pre>
 *   ACTIVE → COMPLETED (Objective met or max messages reached)
 *   ACTIVE → EXPIRED (Expiration time reached without completion)
 *   ACTIVE → ABANDONED (Father unresponsive for 48h)
 * </pre>
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>Exactly one active conversation per Father at any time</li>
 *   <li>Maximum 8 outbound messages per conversation before auto-completing</li>
 *   <li>DIFFICULT_SITUATION conversations preempt (close) existing active conversations</li>
 * </ul>
 */
@Entity(name = "DomainConversation")
@Table(name = "conversation")
public class Conversation {

    /** Maximum number of outbound (system-sent) messages per conversation. */
    public static final int MAX_OUTBOUND_MESSAGES = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Conversation() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new conversation in ACTIVE status.
     *
     * @param father    the father participating in the conversation
     * @param type      the conversation type
     * @param objective the conversation objective
     * @param expiresAt when this conversation expires if not completed
     */
    public Conversation(Father father, ConversationType type, String objective, Instant expiresAt) {
        this.father = father;
        this.type = type;
        this.objective = objective;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.status = ConversationStatus.ACTIVE;
    }

    // ─── State Transition ────────────────────────────────────────────────

    /**
     * Transitions this conversation to the given target status.
     *
     * @param target the desired new status
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void transitionTo(ConversationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Conversation", id, status.name(), target.name());
        }
        this.status = target;

        if (target == ConversationStatus.COMPLETED || target == ConversationStatus.EXPIRED
                || target == ConversationStatus.ABANDONED) {
            this.completedAt = Instant.now();
        }
    }

    /**
     * Checks whether this conversation is in a terminal state.
     */
    public boolean isTerminal() {
        return status.getValidTransitions().isEmpty();
    }

    /**
     * Checks whether this conversation has expired (current time past expires_at and still ACTIVE).
     */
    public boolean isExpired() {
        return status == ConversationStatus.ACTIVE && expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    // ─── Message Count ───────────────────────────────────────────────────

    /**
     * Increments the outbound message count. This tracks system-sent messages.
     *
     * @return the new message count after increment
     */
    public int incrementMessageCount() {
        this.messageCount++;
        return this.messageCount;
    }

    /**
     * Checks whether the outbound message limit has been reached (8 messages).
     *
     * @return true if message_count >= MAX_OUTBOUND_MESSAGES
     */
    public boolean hasReachedMessageLimit() {
        return messageCount >= MAX_OUTBOUND_MESSAGES;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Father getFather() {
        return father;
    }

    public void setFather(Father father) {
        this.father = father;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public void setFatherId(Long fatherId) {
        this.fatherId = fatherId;
    }

    public ConversationType getType() {
        return type;
    }

    public void setType(ConversationType type) {
        this.type = type;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ConversationStatus status) {
        this.status = status;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
