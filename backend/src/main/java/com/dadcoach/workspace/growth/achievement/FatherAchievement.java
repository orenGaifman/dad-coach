package com.dadcoach.workspace.growth.achievement;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the "father_achievements" table (V8.004).
 *
 * <p>Records that a specific father has earned a specific achievement.
 * The unique constraint on (father_id, achievement_id) ensures each achievement
 * can only be earned once per father — award is idempotent.</p>
 *
 * <p>Once earned, an achievement is permanent (Design Decision AD-8).</p>
 *
 * @see Achievement
 */
@Entity
@Table(name = "father_achievements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_father_achievement", columnNames = {"father_id", "achievement_id"})
})
public class FatherAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "achievement_id", nullable = false)
    private UUID achievementId;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    /**
     * JPA-required no-arg constructor.
     */
    protected FatherAchievement() {
    }

    /**
     * Creates a new record indicating a father has earned an achievement.
     *
     * @param fatherId      the father's unique identifier
     * @param achievementId the achievement's unique identifier
     * @param earnedAt      the instant when the achievement was earned
     */
    public FatherAchievement(UUID fatherId, UUID achievementId, Instant earnedAt) {
        this.fatherId = fatherId;
        this.achievementId = achievementId;
        this.earnedAt = earnedAt;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getAchievementId() {
        return achievementId;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }
}
