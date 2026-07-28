package com.dadcoach.workspace.growth.achievement;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * JPA entity mapping to the "achievements" definition table (V8.004).
 *
 * <p>Represents a predefined achievement that fathers can earn. Achievements are
 * seeded at deployment time and define criteria as JSONB that the
 * {@link AchievementCriteriaEvaluator} interprets at runtime.</p>
 *
 * <p>Achievements are immutable reference data — they define what can be earned,
 * while {@link FatherAchievement} tracks what has been earned.</p>
 *
 * @see AchievementCategory
 * @see FatherAchievement
 */
@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @Column(name = "achievement_id", updatable = false, nullable = false)
    private UUID achievementId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private AchievementCategory category;

    @Column(name = "criteria_json", columnDefinition = "jsonb", nullable = false)
    private String criteriaJson;

    @Column(name = "criteria_version", nullable = false)
    private int criteriaVersion;

    @Column(name = "icon_key")
    private String iconKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * JPA-required no-arg constructor.
     */
    protected Achievement() {
    }

    /**
     * Creates a new Achievement definition.
     *
     * @param achievementId   unique identifier
     * @param name            unique display name
     * @param description     human-readable description
     * @param category        the achievement category
     * @param criteriaJson    JSON string defining the criteria for earning this achievement
     * @param criteriaVersion versioning for criteria schema changes
     * @param iconKey         key referencing the achievement icon
     * @param sortOrder       display ordering within a category
     */
    public Achievement(UUID achievementId, String name, String description,
                       AchievementCategory category, String criteriaJson,
                       int criteriaVersion, String iconKey, int sortOrder) {
        this.achievementId = achievementId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.criteriaJson = criteriaJson;
        this.criteriaVersion = criteriaVersion;
        this.iconKey = iconKey;
        this.sortOrder = sortOrder;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getAchievementId() {
        return achievementId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public AchievementCategory getCategory() {
        return category;
    }

    public String getCriteriaJson() {
        return criteriaJson;
    }

    public int getCriteriaVersion() {
        return criteriaVersion;
    }

    public String getIconKey() {
        return iconKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
