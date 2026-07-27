package com.dadcoach.domain.reflection;

import com.dadcoach.domain.conversation.Conversation;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.mission.Mission;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity representing a father's reflection.
 * Maps to the "reflection" table (V2 migration).
 *
 * <p>Reflections are structured self-assessments that can be tied to:</p>
 * <ul>
 *   <li>A completed mission (MISSION type)</li>
 *   <li>A weekly review (WEEKLY type)</li>
 *   <li>A coaching phase transition (PHASE type)</li>
 * </ul>
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>At most 1 reflection per father per calendar day (Property 35)</li>
 * </ul>
 */
@Entity
@Table(name = "reflection")
public class Reflection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "conversation_id", insertable = false, updatable = false)
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @Column(name = "mission_id", insertable = false, updatable = false)
    private Long missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ReflectionType type;

    @Column(name = "emotional_tone", length = 20)
    private String emotionalTone;

    @Column(name = "insights", columnDefinition = "TEXT")
    private String insights;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reflection() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new Reflection.
     *
     * @param father the father who completed the reflection
     * @param type   the type of reflection
     */
    public Reflection(Father father, ReflectionType type) {
        this.father = father;
        this.type = type;
        this.createdAt = Instant.now();
    }

    /**
     * Creates a new mission-related Reflection.
     *
     * @param father  the father who completed the reflection
     * @param type    the type of reflection
     * @param mission the mission being reflected upon
     */
    public Reflection(Father father, ReflectionType type, Mission mission) {
        this(father, type);
        this.mission = mission;
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

    public Mission getMission() {
        return mission;
    }

    public void setMission(Mission mission) {
        this.mission = mission;
    }

    public Long getMissionId() {
        return missionId;
    }

    public void setMissionId(Long missionId) {
        this.missionId = missionId;
    }

    public ReflectionType getType() {
        return type;
    }

    public void setType(ReflectionType type) {
        this.type = type;
    }

    public String getEmotionalTone() {
        return emotionalTone;
    }

    public void setEmotionalTone(String emotionalTone) {
        this.emotionalTone = emotionalTone;
    }

    public String getInsights() {
        return insights;
    }

    public void setInsights(String insights) {
        this.insights = insights;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
