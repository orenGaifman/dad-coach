package com.dadcoach.domain.habit;

import com.dadcoach.common.HabitStatus;
import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.domain.father.Father;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity representing a habit being tracked in the coaching system.
 * Maps to the "habit" table (V2 migration).
 */
@Entity
@Table(name = "habit")
public class Habit {

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

    @Column(name = "frequency", length = 20, nullable = false)
    private String frequency = "DAILY";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private HabitStatus status = HabitStatus.ACTIVE;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "total_completions", nullable = false)
    private int totalCompletions = 0;

    @Column(name = "last_completed_at")
    private Instant lastCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Habit() {
        // JPA requires a no-arg constructor
    }

    public Habit(Father father, String title, String description, String frequency) {
        this.father = father;
        this.title = title;
        this.description = description;
        this.frequency = frequency;
        this.createdAt = Instant.now();
    }

    // ─── State transition ────────────────────────────────────────────────

    /**
     * Transitions this habit to the given target status.
     *
     * @param target the desired new status
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void transitionTo(HabitStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Habit", id, status.name(), target.name());
        }
        this.status = target;
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

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public HabitStatus getStatus() {
        return status;
    }

    public void setStatus(HabitStatus status) {
        this.status = status;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }

    public int getTotalCompletions() {
        return totalCompletions;
    }

    public void setTotalCompletions(int totalCompletions) {
        this.totalCompletions = totalCompletions;
    }

    public Instant getLastCompletedAt() {
        return lastCompletedAt;
    }

    public void setLastCompletedAt(Instant lastCompletedAt) {
        this.lastCompletedAt = lastCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
