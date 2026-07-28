package com.dadcoach.workspace.growth.milestone;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the "milestones" definition table.
 *
 * <p>Represents a predefined milestone that fathers can reach. Milestones track
 * significant accomplishments in the father's growth journey and are defined
 * with trigger conditions as JSONB.</p>
 *
 * <p>Milestones are immutable reference data — they define what can be reached,
 * while {@link FatherMilestone} tracks what has been reached.</p>
 *
 * @see FatherMilestone
 * @see MilestoneEvaluator
 */
@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @Column(name = "milestone_id", updatable = false, nullable = false)
    private UUID milestoneId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "trigger_condition", columnDefinition = "jsonb", nullable = false)
    private String triggerCondition;

    @Column(name = "condition_version", nullable = false)
    private int conditionVersion;

    @Column(name = "icon_key")
    private String iconKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JPA-required no-arg constructor.
     */
    protected Milestone() {
    }

    /**
     * Creates a new Milestone definition.
     *
     * @param milestoneId      unique identifier
     * @param name             unique display name
     * @param description      human-readable description
     * @param category         the milestone category
     * @param triggerCondition JSON string defining the trigger condition
     * @param conditionVersion versioning for condition schema changes
     * @param iconKey          key referencing the milestone icon
     * @param sortOrder        display ordering
     * @param createdAt        when this milestone definition was created
     */
    public Milestone(UUID milestoneId, String name, String description,
                     String category, String triggerCondition,
                     int conditionVersion, String iconKey, int sortOrder,
                     Instant createdAt) {
        this.milestoneId = milestoneId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.triggerCondition = triggerCondition;
        this.conditionVersion = conditionVersion;
        this.iconKey = iconKey;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getMilestoneId() {
        return milestoneId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public int getConditionVersion() {
        return conditionVersion;
    }

    public String getIconKey() {
        return iconKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
