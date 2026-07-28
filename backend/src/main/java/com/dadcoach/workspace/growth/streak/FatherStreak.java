package com.dadcoach.workspace.growth.streak;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity mapping to the father_streaks table.
 *
 * <p>Tracks each father's engagement streak: consecutive calendar days with at least
 * one qualifying interaction. One record per father (UNIQUE constraint on father_id).</p>
 *
 * @see StreakService
 */
@Entity
@Table(name = "father_streaks")
public class FatherStreak {

    @Id
    @Column(name = "streak_id", updatable = false, nullable = false)
    private UUID streakId;

    @Column(name = "father_id", unique = true, nullable = false)
    private UUID fatherId;

    @Column(name = "current_streak_days", nullable = false)
    private int currentStreakDays;

    @Column(name = "longest_streak_days", nullable = false)
    private int longestStreakDays;

    @Column(name = "streak_start_date")
    private LocalDate streakStartDate;

    @Column(name = "last_qualifying_date")
    private LocalDate lastQualifyingDate;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FatherStreak() {
        // JPA
    }

    public FatherStreak(UUID fatherId) {
        this.streakId = UUID.randomUUID();
        this.fatherId = fatherId;
        this.currentStreakDays = 0;
        this.longestStreakDays = 0;
        this.timezone = "UTC";
        this.updatedAt = Instant.now();
    }

    public FatherStreak(UUID fatherId, String timezone) {
        this.streakId = UUID.randomUUID();
        this.fatherId = fatherId;
        this.currentStreakDays = 0;
        this.longestStreakDays = 0;
        this.timezone = timezone;
        this.updatedAt = Instant.now();
    }

    public UUID getStreakId() {
        return streakId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public int getCurrentStreakDays() {
        return currentStreakDays;
    }

    public void setCurrentStreakDays(int currentStreakDays) {
        this.currentStreakDays = currentStreakDays;
    }

    public int getLongestStreakDays() {
        return longestStreakDays;
    }

    public void setLongestStreakDays(int longestStreakDays) {
        this.longestStreakDays = longestStreakDays;
    }

    public LocalDate getStreakStartDate() {
        return streakStartDate;
    }

    public void setStreakStartDate(LocalDate streakStartDate) {
        this.streakStartDate = streakStartDate;
    }

    public LocalDate getLastQualifyingDate() {
        return lastQualifyingDate;
    }

    public void setLastQualifyingDate(LocalDate lastQualifyingDate) {
        this.lastQualifyingDate = lastQualifyingDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
