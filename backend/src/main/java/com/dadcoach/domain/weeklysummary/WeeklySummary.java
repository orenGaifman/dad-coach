package com.dadcoach.domain.weeklysummary;

import com.dadcoach.domain.father.Father;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a weekly progress summary for a father.
 * Maps to the "weekly_summary" table (V2 migration).
 *
 * <p>Weekly summaries are generated every Monday at 08:00 in the father's local timezone,
 * covering the prior Monday through Sunday period.</p>
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>Exactly one summary per father per week (UNIQUE(father_id, week_start))</li>
 *   <li>Excludes PAUSED, CHURNED, DELETED fathers (Property 31)</li>
 *   <li>Includes metrics: missions assigned/completed/skipped, engagement_score, streak</li>
 * </ul>
 */
@Entity
@Table(name = "weekly_summary",
       uniqueConstraints = @UniqueConstraint(columnNames = {"father_id", "week_start"}))
public class WeeklySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "missions_assigned", nullable = false)
    private int missionsAssigned = 0;

    @Column(name = "missions_completed", nullable = false)
    private int missionsCompleted = 0;

    @Column(name = "missions_skipped", nullable = false)
    private int missionsSkipped = 0;

    @Column(name = "engagement_score", nullable = false)
    private int engagementScore = 0;

    @Column(name = "coaching_streak", nullable = false)
    private int coachingStreak = 0;

    @Column(name = "highlights", columnDefinition = "TEXT")
    private String highlights;

    @Column(name = "focus_areas", columnDefinition = "TEXT")
    private String focusAreas;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WeeklySummary() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new WeeklySummary.
     *
     * @param father    the father this summary belongs to
     * @param weekStart the Monday that starts the week being summarized
     * @param weekEnd   the Sunday that ends the week being summarized
     * @param content   the summary content text
     */
    public WeeklySummary(Father father, LocalDate weekStart, LocalDate weekEnd, String content) {
        this.father = father;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.content = content;
        this.createdAt = Instant.now();
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

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public void setWeekEnd(LocalDate weekEnd) {
        this.weekEnd = weekEnd;
    }

    public int getMissionsAssigned() {
        return missionsAssigned;
    }

    public void setMissionsAssigned(int missionsAssigned) {
        this.missionsAssigned = missionsAssigned;
    }

    public int getMissionsCompleted() {
        return missionsCompleted;
    }

    public void setMissionsCompleted(int missionsCompleted) {
        this.missionsCompleted = missionsCompleted;
    }

    public int getMissionsSkipped() {
        return missionsSkipped;
    }

    public void setMissionsSkipped(int missionsSkipped) {
        this.missionsSkipped = missionsSkipped;
    }

    public int getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(int engagementScore) {
        this.engagementScore = engagementScore;
    }

    public int getCoachingStreak() {
        return coachingStreak;
    }

    public void setCoachingStreak(int coachingStreak) {
        this.coachingStreak = coachingStreak;
    }

    public String getHighlights() {
        return highlights;
    }

    public void setHighlights(String highlights) {
        this.highlights = highlights;
    }

    public String getFocusAreas() {
        return focusAreas;
    }

    public void setFocusAreas(String focusAreas) {
        this.focusAreas = focusAreas;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
