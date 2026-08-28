package com.dadcoach.domain.father;

import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.father.CoachingPhase;
import com.dadcoach.father.CoachingStyle;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.father.OnboardingState;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WelcomeStep;
import com.dadcoach.workflow.WorkflowState;

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

    // ─── Google Calendar Integration ─────────────────────────────────────

    @Column(name = "google_calendar_enabled")
    private Boolean googleCalendarEnabled = false;

    @Column(name = "google_refresh_token", length = 512)
    private String googleRefreshToken;

    @Column(name = "google_access_token", length = 2048)
    private String googleAccessToken;

    @Column(name = "google_token_expires_at")
    private Instant googleTokenExpiresAt;

    @Column(name = "google_calendar_id", length = 255)
    private String googleCalendarId;

    // ─── Goals and Tracking ──────────────────────────────────────────────

    @Column(name = "weekly_goal_minutes", nullable = false)
    private Integer weeklyGoalMinutes = 30;

    @Column(name = "monthly_goal_minutes", nullable = false)
    private Integer monthlyGoalMinutes = 120;

    @Column(name = "goals_started_at")
    private LocalDate goalsStartedAt;

    @Column(name = "current_streak_weeks", nullable = false)
    private Integer currentStreakWeeks = 0;

    @Column(name = "longest_streak_weeks", nullable = false)
    private Integer longestStreakWeeks = 0;

    @Column(name = "total_quality_minutes", nullable = false)
    private Integer totalQualityMinutes = 0;

    // ─── Workflow State (Deterministic Workflow Engine) ──────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "current_workflow_state", length = 30)
    private WorkflowState currentWorkflowState = WorkflowState.WELCOME;

    @Enumerated(EnumType.STRING)
    @Column(name = "welcome_step", length = 40)
    private WelcomeStep welcomeStep = WelcomeStep.INTRO;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_workflow_state", length = 30)
    private WorkflowState previousWorkflowState;

    @Column(name = "workflow_state_entered_at")
    private Instant workflowStateEnteredAt;

    @Column(name = "welcomed_at")
    private Instant welcomedAt;

    // ─── Quality Time Tracking ───────────────────────────────────────────

    @Column(name = "quality_time_streak", nullable = false)
    private int qualityTimeStreak = 0;

    @Column(name = "quality_time_longest_streak", nullable = false)
    private int qualityTimeLongestStreak = 0;

    @Column(name = "total_quality_times_completed", nullable = false)
    private int totalQualityTimesCompleted = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_belt", length = 20, nullable = false)
    private Belt currentBelt = Belt.WHITE;

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

    // ─── Google Calendar Getters & Setters ───────────────────────────────

    public Boolean getGoogleCalendarEnabled() {
        return googleCalendarEnabled;
    }

    public void setGoogleCalendarEnabled(Boolean googleCalendarEnabled) {
        this.googleCalendarEnabled = googleCalendarEnabled;
    }

    public String getGoogleRefreshToken() {
        return googleRefreshToken;
    }

    public void setGoogleRefreshToken(String googleRefreshToken) {
        this.googleRefreshToken = googleRefreshToken;
    }

    public String getGoogleAccessToken() {
        return googleAccessToken;
    }

    public void setGoogleAccessToken(String googleAccessToken) {
        this.googleAccessToken = googleAccessToken;
    }

    public Instant getGoogleTokenExpiresAt() {
        return googleTokenExpiresAt;
    }

    public void setGoogleTokenExpiresAt(Instant googleTokenExpiresAt) {
        this.googleTokenExpiresAt = googleTokenExpiresAt;
    }

    public String getGoogleCalendarId() {
        return googleCalendarId;
    }

    public void setGoogleCalendarId(String googleCalendarId) {
        this.googleCalendarId = googleCalendarId;
    }

    /**
     * Checks if Google Calendar integration is properly configured.
     * @return true if refresh token exists and calendar is enabled
     */
    public boolean hasGoogleCalendarConfigured() {
        return Boolean.TRUE.equals(googleCalendarEnabled) 
            && googleRefreshToken != null 
            && !googleRefreshToken.isEmpty();
    }

    /**
     * Checks if the Google access token needs refreshing.
     * @return true if token is expired or about to expire (within 5 minutes)
     */
    public boolean needsTokenRefresh() {
        if (googleAccessToken == null || googleTokenExpiresAt == null) {
            return true;
        }
        // Refresh if token expires within 5 minutes
        return Instant.now().plusSeconds(300).isAfter(googleTokenExpiresAt);
    }

    // ─── Goals Getters & Setters ─────────────────────────────────────────

    public Integer getWeeklyGoalMinutes() {
        return weeklyGoalMinutes;
    }

    public void setWeeklyGoalMinutes(Integer weeklyGoalMinutes) {
        this.weeklyGoalMinutes = weeklyGoalMinutes;
    }

    public Integer getMonthlyGoalMinutes() {
        return monthlyGoalMinutes;
    }

    public void setMonthlyGoalMinutes(Integer monthlyGoalMinutes) {
        this.monthlyGoalMinutes = monthlyGoalMinutes;
    }

    public LocalDate getGoalsStartedAt() {
        return goalsStartedAt;
    }

    public void setGoalsStartedAt(LocalDate goalsStartedAt) {
        this.goalsStartedAt = goalsStartedAt;
    }

    public Integer getCurrentStreakWeeks() {
        return currentStreakWeeks;
    }

    public void setCurrentStreakWeeks(Integer currentStreakWeeks) {
        this.currentStreakWeeks = currentStreakWeeks;
    }

    public Integer getLongestStreakWeeks() {
        return longestStreakWeeks;
    }

    public void setLongestStreakWeeks(Integer longestStreakWeeks) {
        this.longestStreakWeeks = longestStreakWeeks;
    }

    public Integer getTotalQualityMinutes() {
        return totalQualityMinutes;
    }

    public void setTotalQualityMinutes(Integer totalQualityMinutes) {
        this.totalQualityMinutes = totalQualityMinutes;
    }

    // ─── Workflow State Getters & Setters ────────────────────────────────

    public WorkflowState getCurrentWorkflowState() {
        return currentWorkflowState;
    }

    public void setCurrentWorkflowState(WorkflowState currentWorkflowState) {
        this.currentWorkflowState = currentWorkflowState;
    }

    public WorkflowState getPreviousWorkflowState() {
        return previousWorkflowState;
    }

    public void setPreviousWorkflowState(WorkflowState previousWorkflowState) {
        this.previousWorkflowState = previousWorkflowState;
    }

    public Instant getWorkflowStateEnteredAt() {
        return workflowStateEnteredAt;
    }

    public void setWorkflowStateEnteredAt(Instant workflowStateEnteredAt) {
        this.workflowStateEnteredAt = workflowStateEnteredAt;
    }

    public WelcomeStep getWelcomeStep() {
        return welcomeStep;
    }

    public void setWelcomeStep(WelcomeStep welcomeStep) {
        this.welcomeStep = welcomeStep;
    }

    public Instant getWelcomedAt() {
        return welcomedAt;
    }

    public void setWelcomedAt(Instant welcomedAt) {
        this.welcomedAt = welcomedAt;
    }

    // ─── Quality Time Tracking Getters & Setters ─────────────────────────

    public int getQualityTimeStreak() {
        return qualityTimeStreak;
    }

    public void setQualityTimeStreak(int qualityTimeStreak) {
        this.qualityTimeStreak = qualityTimeStreak;
    }

    public int getQualityTimeLongestStreak() {
        return qualityTimeLongestStreak;
    }

    public void setQualityTimeLongestStreak(int qualityTimeLongestStreak) {
        this.qualityTimeLongestStreak = qualityTimeLongestStreak;
    }

    public int getTotalQualityTimesCompleted() {
        return totalQualityTimesCompleted;
    }

    public void setTotalQualityTimesCompleted(int totalQualityTimesCompleted) {
        this.totalQualityTimesCompleted = totalQualityTimesCompleted;
    }

    public Belt getCurrentBelt() {
        return currentBelt;
    }

    public void setCurrentBelt(Belt currentBelt) {
        this.currentBelt = currentBelt;
    }
}
