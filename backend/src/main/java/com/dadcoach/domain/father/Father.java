package com.dadcoach.domain.father;

import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.father.CoachingPhase;
import com.dadcoach.father.CoachingStyle;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.father.OnboardingState;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * JPA entity representing a father in the coaching system.
 * Maps to the "father" table (V1 + V2 columns).
 */
@Entity
@Table(name = "father")
public class Father {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", length = 32, nullable = false, unique = true)
    private String phone;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private FatherStatus status = FatherStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_state", length = 30)
    private OnboardingState onboardingState = OnboardingState.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "coaching_phase", length = 20)
    private CoachingPhase coachingPhase = CoachingPhase.FOUNDATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "coaching_style", length = 20)
    private CoachingStyle coachingStyle = CoachingStyle.BALANCED;

    @Column(name = "preferred_coaching_time")
    private LocalTime preferredCoachingTime = LocalTime.of(8, 0);

    @Column(name = "timezone", length = 64)
    private String timezone = "Asia/Jerusalem";

    @Column(name = "locale", length = 10)
    private String locale = "he";

    @Column(name = "engagement_score")
    private int engagementScore = 0;

    @Column(name = "coaching_streak")
    private int coachingStreak = 0;

    @Column(name = "longest_streak")
    private int longestStreak = 0;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Column(name = "last_interaction_at")
    private Instant lastInteractionAt;

    @Column(name = "pause_until")
    private LocalDate pauseUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata = "{}";

    protected Father() {
        // JPA requires a no-arg constructor
    }

    public Father(String phone) {
        this.phone = phone;
        this.createdAt = Instant.now();
    }

    // ─── State transition ────────────────────────────────────────────────

    /**
     * Transitions this father to the given target status.
     *
     * @param target the desired new status
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void transitionTo(FatherStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Father", id, status.name(), target.name());
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public FatherStatus getStatus() {
        return status;
    }

    public void setStatus(FatherStatus status) {
        this.status = status;
    }

    public OnboardingState getOnboardingState() {
        return onboardingState;
    }

    public void setOnboardingState(OnboardingState onboardingState) {
        this.onboardingState = onboardingState;
    }

    public CoachingPhase getCoachingPhase() {
        return coachingPhase;
    }

    public void setCoachingPhase(CoachingPhase coachingPhase) {
        this.coachingPhase = coachingPhase;
    }

    public CoachingStyle getCoachingStyle() {
        return coachingStyle;
    }

    public void setCoachingStyle(CoachingStyle coachingStyle) {
        this.coachingStyle = coachingStyle;
    }

    public LocalTime getPreferredCoachingTime() {
        return preferredCoachingTime;
    }

    public void setPreferredCoachingTime(LocalTime preferredCoachingTime) {
        this.preferredCoachingTime = preferredCoachingTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
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

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }

    public LocalDate getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(LocalDate activationDate) {
        this.activationDate = activationDate;
    }

    public Instant getLastInteractionAt() {
        return lastInteractionAt;
    }

    public void setLastInteractionAt(Instant lastInteractionAt) {
        this.lastInteractionAt = lastInteractionAt;
    }

    public LocalDate getPauseUntil() {
        return pauseUntil;
    }

    public void setPauseUntil(LocalDate pauseUntil) {
        this.pauseUntil = pauseUntil;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
