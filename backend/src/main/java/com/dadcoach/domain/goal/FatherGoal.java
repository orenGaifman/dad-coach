package com.dadcoach.domain.goal;

import com.dadcoach.domain.father.Father;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a weekly or monthly quality time goal for a father.
 * Goals are automatically created and tracked to keep fathers motivated.
 */
@Entity
@Table(name = "father_goal")
public class FatherGoal {

    public enum GoalType { WEEKLY, MONTHLY }
    public enum GoalStatus { ACTIVE, COMPLETED, MISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 20, nullable = false)
    private GoalType goalType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "target_minutes", nullable = false)
    private Integer targetMinutes;

    @Column(name = "completed_minutes", nullable = false)
    private Integer completedMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FatherGoal() {}

    public FatherGoal(Father father, GoalType goalType, LocalDate periodStart, 
                      LocalDate periodEnd, Integer targetMinutes) {
        this.father = father;
        this.goalType = goalType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.targetMinutes = targetMinutes;
        this.completedMinutes = 0;
        this.status = GoalStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Adds completed minutes to this goal.
     * If target is reached, marks the goal as completed.
     * 
     * @param minutes minutes to add
     * @return true if this addition caused the goal to be completed
     */
    public boolean addMinutes(int minutes) {
        this.completedMinutes += minutes;
        this.updatedAt = Instant.now();
        
        if (this.completedMinutes >= this.targetMinutes && this.status == GoalStatus.ACTIVE) {
            this.status = GoalStatus.COMPLETED;
            this.completedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Gets the progress percentage (0-100).
     */
    public int getProgressPercent() {
        if (targetMinutes == 0) return 100;
        return Math.min(100, (completedMinutes * 100) / targetMinutes);
    }

    /**
     * Gets remaining minutes to reach the goal.
     */
    public int getRemainingMinutes() {
        return Math.max(0, targetMinutes - completedMinutes);
    }

    /**
     * Checks if the goal period has ended.
     */
    public boolean isPeriodEnded() {
        return LocalDate.now().isAfter(periodEnd);
    }

    /**
     * Marks goal as missed (period ended without completion).
     */
    public void markMissed() {
        if (status == GoalStatus.ACTIVE) {
            this.status = GoalStatus.MISSED;
            this.updatedAt = Instant.now();
        }
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Father getFather() { return father; }
    public void setFather(Father father) { this.father = father; }

    public Long getFatherId() { return fatherId; }

    public GoalType getGoalType() { return goalType; }
    public void setGoalType(GoalType goalType) { this.goalType = goalType; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public Integer getTargetMinutes() { return targetMinutes; }
    public void setTargetMinutes(Integer targetMinutes) { this.targetMinutes = targetMinutes; }

    public Integer getCompletedMinutes() { return completedMinutes; }
    public void setCompletedMinutes(Integer completedMinutes) { this.completedMinutes = completedMinutes; }

    public GoalStatus getStatus() { return status; }
    public void setStatus(GoalStatus status) { this.status = status; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
