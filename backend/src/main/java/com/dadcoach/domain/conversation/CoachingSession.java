package com.dadcoach.domain.conversation;

import com.dadcoach.domain.conversation.CoachingSessionOutcome;
import com.dadcoach.domain.father.Father;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a coaching session — outcome metadata linked to a completed conversation.
 * Maps to the "coaching_session" table (V2 migration).
 *
 * <p>A CoachingSession is NOT a separate entity with an independent lifecycle running in parallel
 * to the Conversation. It is outcome metadata computed when a Conversation completes.
 * The Conversation state machine governs the active interaction; the CoachingSession captures
 * the assessed outcome after completion.</p>
 *
 * <p>Outcome state machine:</p>
 * <pre>
 *   ACTIVE → OBJECTIVE_MET (Coaching goal achieved)
 *   ACTIVE → PARTIALLY_MET (Partial progress, conversation ended)
 *   ACTIVE → NOT_MET (No meaningful progress observed)
 *   ACTIVE → FATHER_DISENGAGED (30 min inactivity timeout)
 *   ACTIVE → ERROR (System or AI failure)
 * </pre>
 */
@Entity
@Table(name = "coaching_session")
public class CoachingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "conversation_id", insertable = false, updatable = false)
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 30, nullable = false)
    private CoachingSessionOutcome outcome;

    @Column(name = "model_used", length = 30, nullable = false)
    private String modelUsed;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens = 0;

    @Column(name = "context_memories_used", columnDefinition = "BIGINT[] DEFAULT '{}'")
    private Long[] contextMemoriesUsed = new Long[0];

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CoachingSession() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new coaching session linked to a completed conversation.
     *
     * @param conversation the conversation this session is derived from
     * @param father       the father who participated
     * @param outcome      the assessed outcome of the session
     * @param modelUsed    the AI model used during the conversation
     */
    public CoachingSession(Conversation conversation, Father father,
                           CoachingSessionOutcome outcome, String modelUsed) {
        this.conversation = conversation;
        this.father = father;
        this.outcome = outcome;
        this.modelUsed = modelUsed;
        this.createdAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
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

    public CoachingSessionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(CoachingSessionOutcome outcome) {
        this.outcome = outcome;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long[] getContextMemoriesUsed() {
        return contextMemoriesUsed;
    }

    public void setContextMemoriesUsed(Long[] contextMemoriesUsed) {
        this.contextMemoriesUsed = contextMemoriesUsed;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
