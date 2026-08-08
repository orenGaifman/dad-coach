package com.dadcoach.weeklygoal;

import com.dadcoach.domain.father.Father;
import com.dadcoach.workflow.Belt;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a weekly quality time goal.
 * 
 * <p>Each week, fathers set a target number of hours for quality time with their children.
 * At the end of the week, if they meet or exceed their goal, they advance to the next belt.
 * </p>
 * 
 * <p>The weekly cycle:</p>
 * <ol>
 *   <li>Sunday: Show last week's summary (WEEKLY_SUMMARY state)</li>
 *   <li>Sunday: Set new weekly goal (SET_WEEKLY_GOAL state)</li>
 *   <li>Sunday: Distribute hours among children (DISTRIBUTE_GOAL state)</li>
 *   <li>Sunday: Schedule quality time slots (SCHEDULE_WEEK state)</li>
 *   <li>Mon-Sat: Execute quality times and track progress</li>
 *   <li>Saturday night: Calculate if goal was met</li>
 * </ol>
 */
@Entity
@Table(name = "weekly_goal", indexes = {
    @Index(name = "idx_weekly_goal_father_week", columnList = "father_id, week_start_date", unique = true),
    @Index(name = "idx_weekly_goal_status", columnList = "status")
})
public class WeeklyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    /**
     * The start date of the week (Sunday).
     */
    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    /**
     * Target quality time hours for the week (minimum 1 hour).
     */
    @Column(name = "target_hours", nullable = false)
    private int targetHours;

    /**
     * Actual quality time minutes completed this week.
     * Stored in minutes for precision.
     */
    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes = 0;

    /**
     * Number of quality times scheduled for the week.
     */
    @Column(name = "scheduled_count", nullable = false)
    private int scheduledCount = 0;

    /**
     * Number of quality times completed this week.
     */
    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;

    /**
     * Belt level at the start of the week.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "starting_belt", nullable = false)
    private Belt startingBelt;

    /**
     * Belt level at the end of the week (after promotion if applicable).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ending_belt")
    private Belt endingBelt;

    /**
     * Whether the father was promoted this week.
     */
    @Column(name = "belt_promoted", nullable = false)
    private boolean beltPromoted = false;

    /**
     * Status of the weekly goal.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WeeklyGoalStatus status = WeeklyGoalStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WeeklyGoal() {
        // JPA requires a no-arg constructor
    }

    public WeeklyGoal(Father father, LocalDate weekStartDate, int targetHours, Belt startingBelt) {
        this.father = father;
        this.weekStartDate = weekStartDate;
        this.targetHours = Math.max(1, targetHours); // Minimum 1 hour
        this.startingBelt = startingBelt;
        this.endingBelt = startingBelt;
        this.createdAt = Instant.now();
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Returns actual hours as a decimal (e.g., 90 minutes = 1.5 hours).
     */
    public double getActualHours() {
        return actualMinutes / 60.0;
    }

    /**
     * Returns progress percentage (0-100+).
     */
    public int getProgressPercentage() {
        if (targetHours <= 0) return 0;
        int targetMinutes = targetHours * 60;
        return Math.round((actualMinutes * 100f) / targetMinutes);
    }

    /**
     * Returns true if the goal has been met (actual >= target).
     */
    public boolean isGoalMet() {
        return actualMinutes >= (targetHours * 60);
    }

    /**
     * Adds completed quality time minutes.
     */
    public void addCompletedMinutes(int minutes) {
        this.actualMinutes += minutes;
        this.completedCount++;
    }

    /**
     * Increments scheduled count when a quality time is scheduled.
     */
    public void incrementScheduled() {
        this.scheduledCount++;
    }

    /**
     * Decrements scheduled count when a quality time is cancelled.
     */
    public void decrementScheduled() {
        if (this.scheduledCount > 0) {
            this.scheduledCount--;
        }
    }

    /**
     * Activates the goal (called when goal setting is complete).
     */
    public void activate() {
        this.status = WeeklyGoalStatus.ACTIVE;
    }

    /**
     * Completes the weekly goal and determines if belt promotion occurred.
     * 
     * @return true if the father was promoted to the next belt
     */
    public boolean complete() {
        this.status = isGoalMet() ? WeeklyGoalStatus.COMPLETED : WeeklyGoalStatus.MISSED;
        this.completedAt = Instant.now();
        
        if (isGoalMet() && startingBelt.getNextBelt() != null) {
            this.endingBelt = startingBelt.getNextBelt();
            this.beltPromoted = true;
            return true;
        }
        
        this.endingBelt = startingBelt;
        return false;
    }

    /**
     * Returns hours remaining to meet the goal.
     */
    public double getHoursRemaining() {
        int targetMinutes = targetHours * 60;
        int remaining = targetMinutes - actualMinutes;
        return Math.max(0, remaining / 60.0);
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

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public int getTargetHours() {
        return targetHours;
    }

    public void setTargetHours(int targetHours) {
        this.targetHours = Math.max(1, targetHours);
    }

    public int getActualMinutes() {
        return actualMinutes;
    }

    public void setActualMinutes(int actualMinutes) {
        this.actualMinutes = actualMinutes;
    }

    public int getScheduledCount() {
        return scheduledCount;
    }

    public void setScheduledCount(int scheduledCount) {
        this.scheduledCount = scheduledCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
    }

    public Belt getStartingBelt() {
        return startingBelt;
    }

    public void setStartingBelt(Belt startingBelt) {
        this.startingBelt = startingBelt;
    }

    public Belt getEndingBelt() {
        return endingBelt;
    }

    public void setEndingBelt(Belt endingBelt) {
        this.endingBelt = endingBelt;
    }

    public boolean isBeltPromoted() {
        return beltPromoted;
    }

    public void setBeltPromoted(boolean beltPromoted) {
        this.beltPromoted = beltPromoted;
    }

    public WeeklyGoalStatus getStatus() {
        return status;
    }

    public void setStatus(WeeklyGoalStatus status) {
        this.status = status;
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
