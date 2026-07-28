package com.dadcoach.onboarding.provisioning;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a father's AI coaching profile.
 * Built during provisioning from wizard data, containing the initial
 * coaching configuration used by the Intelligence Layer.
 */
@Entity
@Table(name = "ai_profiles", indexes = {
    @Index(name = "idx_ai_profiles_father", columnList = "father_id", unique = true)
})
public class AiProfile {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "father_id", nullable = false, unique = true)
    private Long fatherId;

    @Column(name = "coaching_style", length = 30, nullable = false)
    private String coachingStyle;

    @Column(name = "language", length = 5, nullable = false)
    private String language;

    @Column(name = "children_context", columnDefinition = "TEXT")
    private String childrenContext;

    @Column(name = "goals_context", columnDefinition = "TEXT")
    private String goalsContext;

    @Column(name = "personality_brief", columnDefinition = "TEXT")
    private String personalityBrief;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProfile() {
        // JPA requires a no-arg constructor
    }

    public AiProfile(Long fatherId, String coachingStyle, String language,
                     String childrenContext, String goalsContext, String personalityBrief) {
        this.fatherId = fatherId;
        this.coachingStyle = coachingStyle;
        this.language = language;
        this.childrenContext = childrenContext;
        this.goalsContext = goalsContext;
        this.personalityBrief = personalityBrief;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID profileId) { this.profileId = profileId; }

    public Long getFatherId() { return fatherId; }
    public void setFatherId(Long fatherId) { this.fatherId = fatherId; }

    public String getCoachingStyle() { return coachingStyle; }
    public void setCoachingStyle(String coachingStyle) { this.coachingStyle = coachingStyle; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getChildrenContext() { return childrenContext; }
    public void setChildrenContext(String childrenContext) { this.childrenContext = childrenContext; }

    public String getGoalsContext() { return goalsContext; }
    public void setGoalsContext(String goalsContext) { this.goalsContext = goalsContext; }

    public String getPersonalityBrief() { return personalityBrief; }
    public void setPersonalityBrief(String personalityBrief) { this.personalityBrief = personalityBrief; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
