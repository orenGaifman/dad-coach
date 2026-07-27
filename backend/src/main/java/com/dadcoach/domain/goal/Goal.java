package com.dadcoach.domain.goal;

import com.dadcoach.domain.father.Father;
import com.dadcoach.goal.GoalCategory;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity representing a parenting goal in the coaching system.
 * Maps to the "goal" table (V2 migration).
 *
 * Progress is calculated as: min(100, (completedRelatedMissions / estimatedTotalMissions) × 100)
 */
@Entity
@Table(name = "goal")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private GoalCategory category;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage = 0;

    @Column(name = "estimated_total_missions", nullable = false)
    private int estimatedTotalMissions;

    @Column(name = "completed_related_missions", nullable = false)
    private int completedRelatedMissions = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Goal() {
        // JPA requires a no-arg constructor
    }

    public Goal(Father father, String title, GoalCategory category, int priority) {
        this.father = father;
        this.title = title;
        this.category = category;
        this.priority = priority;
        this.estimatedTotalMissions = category.getEstimatedMissions();
        this.createdAt = Instant.now();
    }

    public Goal(Father father, String title, String description, GoalCategory category, int priority) {
        this(father, title, category, priority);
        this.description = description;
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Recalculates progress_percentage based on completed_related_missions and estimated_total_missions.
     * Formula: min(100, (completedRelatedMissions / estimatedTotalMissions) × 100)
     */
    public void recalculateProgress() {
        if (estimatedTotalMissions <= 0) {
            this.progressPercentage = 0;
            return;
        }
        this.progressPercentage = Math.min(100,
                (completedRelatedMissions * 100) / estimatedTotalMissions);
    }

    /**
     * Increments the completed missions count and recalculates progress.
     */
    public void incrementCompletedMissions() {
        this.completedRelatedMissions++;
        recalculateProgress();
    }

    /**
     * Marks this goal as completed.
     */
    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        this.progressPercentage = 100;
    }

    /**
     * Archives this goal.
     */
    public void archive() {
        this.status = "ARCHIVED";
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GoalCategory getCategory() {
        return category;
    }

    public void setCategory(GoalCategory category) {
        this.category = category;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public int getEstimatedTotalMissions() {
        return estimatedTotalMissions;
    }

    public void setEstimatedTotalMissions(int estimatedTotalMissions) {
        this.estimatedTotalMissions = estimatedTotalMissions;
    }

    public int getCompletedRelatedMissions() {
        return completedRelatedMissions;
    }

    public void setCompletedRelatedMissions(int completedRelatedMissions) {
        this.completedRelatedMissions = completedRelatedMissions;
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
