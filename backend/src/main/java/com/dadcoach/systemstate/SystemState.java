package com.dadcoach.systemstate;

import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WelcomeStep;
import com.dadcoach.workflow.WorkflowState;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Complete system state record for a father, used in the Read Before Write pattern.
 * 
 * <p>This immutable record captures all system state needed to process any request
 * for a given father. The WorkflowEngine loads this state at the beginning of
 * every request processing cycle.</p>
 * 
 * <p>Implements Requirements 2.1 from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>Father profile (name, children, preferences, locale, timezone)</li>
 *   <li>Current workflow state</li>
 *   <li>Google Calendar events for the next 7 days (if calendar is connected)</li>
 *   <li>Scheduled Quality Time events</li>
 *   <li>Dashboard metrics (belt, streak, achievements)</li>
 *   <li>Conversation context (last 10 messages in current workflow state)</li>
 * </ul>
 * 
 * @param fatherProfile the father's profile information
 * @param workflowState the current workflow state
 * @param calendarEvents Google Calendar events for the next 7 days
 * @param qualityTimeEvents scheduled Quality Time events
 * @param dashboardMetrics dashboard metrics including belt, streak, and achievements
 * @param conversationContext recent conversation messages
 * 
 * @see <a href="Requirements 2.1">Read Before Write - System State Loading</a>
 */
public record SystemState(
    FatherProfile fatherProfile,
    WorkflowState workflowState,
    List<CalendarEvent> calendarEvents,
    List<QualityTimeEvent> qualityTimeEvents,
    DashboardMetrics dashboardMetrics,
    List<ConversationMessage> conversationContext,
    WeeklyGoalInfo weeklyGoalInfo
) {
    
    /**
     * Creates a SystemState with defensive copies of all collections to ensure immutability.
     */
    public SystemState {
        calendarEvents = calendarEvents != null ? List.copyOf(calendarEvents) : List.of();
        qualityTimeEvents = qualityTimeEvents != null ? List.copyOf(qualityTimeEvents) : List.of();
        conversationContext = conversationContext != null ? List.copyOf(conversationContext) : List.of();
    }
    
    /**
     * Father profile information including personal details and preferences.
     * 
     * @param fatherId the father's unique identifier
     * @param displayName the father's display name
     * @param phone the father's phone number
     * @param children list of the father's children
     * @param locale the father's preferred locale (e.g., "en", "he")
     * @param timezone the father's timezone (e.g., "America/Mexico_City")
     * @param preferredCoachingTime the father's preferred time for coaching interactions
     * @param googleCalendarConnected whether Google Calendar is connected
     * @param welcomeStep the current step in the welcome onboarding flow (null if completed)
     */
    public record FatherProfile(
        Long fatherId,
        String displayName,
        String phone,
        List<ChildInfo> children,
        String locale,
        String timezone,
        LocalTime preferredCoachingTime,
        boolean googleCalendarConnected,
        WelcomeStep welcomeStep
    ) {
        /**
         * Creates a FatherProfile with defensive copy of children list.
         */
        public FatherProfile {
            children = children != null ? List.copyOf(children) : List.of();
        }
    }
    
    /**
     * Child information for workflow decisions.
     * 
     * @param childId the child's unique identifier
     * @param name the child's name
     * @param birthDate the child's birth date
     * @param age the child's current age in years
     * @param gender the child's gender
     * @param interests the child's interests
     */
    public record ChildInfo(
        Long childId,
        String name,
        LocalDate birthDate,
        int age,
        String gender,
        List<String> interests
    ) {
        /**
         * Creates a ChildInfo with defensive copy of interests list.
         */
        public ChildInfo {
            interests = interests != null ? List.copyOf(interests) : List.of();
        }
    }
    
    /**
     * Google Calendar event from the father's calendar.
     * 
     * @param eventId the Google Calendar event ID
     * @param title the event title
     * @param startTime the event start time
     * @param endTime the event end time
     * @param allDay whether this is an all-day event
     */
    public record CalendarEvent(
        String eventId,
        String title,
        Instant startTime,
        Instant endTime,
        boolean allDay
    ) {}
    
    /**
     * Quality Time event scheduled or completed by the father.
     * 
     * @param qualityTimeId the Quality Time unique identifier
     * @param childId the child the Quality Time is with
     * @param childName the child's name
     * @param scheduledStart the scheduled start time
     * @param scheduledEnd the scheduled end time
     * @param status the Quality Time status (SCHEDULED, COMPLETED, MISSED, CANCELLED)
     * @param googleCalendarEventId the linked Google Calendar event ID (if any)
     * @param completedAt when the Quality Time was completed (if completed)
     * @param completionNotes notes about the completion (if any)
     */
    public record QualityTimeEvent(
        java.util.UUID qualityTimeId,
        Long childId,
        String childName,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        String googleCalendarEventId,
        Instant completedAt,
        String completionNotes
    ) {}
    
    /**
     * Dashboard metrics for the father's progress.
     * 
     * @param currentBelt the father's current belt level
     * @param currentStreak current consecutive Quality Time completions
     * @param longestStreak highest consecutive Quality Time completions ever achieved
     * @param totalCompleted total number of Quality Times completed
     * @param recentAchievements list of recently earned achievements
     * @param progressToNextBelt percentage progress to the next belt (0-100)
     * @param qualityTimesToNextBelt number of Quality Times needed for next belt
     */
    public record DashboardMetrics(
        Belt currentBelt,
        int currentStreak,
        int longestStreak,
        int totalCompleted,
        List<Achievement> recentAchievements,
        int progressToNextBelt,
        int qualityTimesToNextBelt
    ) {
        /**
         * Creates DashboardMetrics with defensive copy of achievements list.
         */
        public DashboardMetrics {
            recentAchievements = recentAchievements != null ? List.copyOf(recentAchievements) : List.of();
        }
    }
    
    /**
     * An achievement earned by the father.
     * 
     * @param achievementId unique identifier for the achievement type
     * @param name display name of the achievement
     * @param description description of how the achievement was earned
     * @param earnedAt when the achievement was earned
     */
    public record Achievement(
        String achievementId,
        String name,
        String description,
        Instant earnedAt
    ) {}
    
    /**
     * A message from the conversation context.
     * 
     * @param messageId unique identifier for the message
     * @param direction INBOUND (from father) or OUTBOUND (from system)
     * @param content the message content
     * @param createdAt when the message was created
     */
    public record ConversationMessage(
        java.util.UUID messageId,
        String direction,
        String content,
        Instant createdAt
    ) {}
    
    /**
     * Weekly goal information for the current week.
     * 
     * @param hasGoal whether a weekly goal is set for this week
     * @param targetQualityTimes target number of quality times for the week (hours)
     * @param completedQualityTimes number of quality times completed this week
     * @param scheduledQualityTimes number of quality times scheduled this week
     * @param weekStartDate the start date of the current week (Sunday)
     * @param lastWeekSummary summary of last week's goal (null if no previous goal)
     */
    public record WeeklyGoalInfo(
        boolean hasGoal,
        int targetQualityTimes,
        int completedQualityTimes,
        int scheduledQualityTimes,
        LocalDate weekStartDate,
        LastWeekSummary lastWeekSummary
    ) {
        /**
         * Creates a WeeklyGoalInfo indicating no goal is set.
         */
        public static WeeklyGoalInfo noGoal() {
            return new WeeklyGoalInfo(false, 0, 0, 0, null, null);
        }
        
        /**
         * Creates a WeeklyGoalInfo indicating no goal is set, but with last week summary.
         */
        public static WeeklyGoalInfo noGoalWithLastWeek(LastWeekSummary lastWeek) {
            return new WeeklyGoalInfo(false, 0, 0, 0, null, lastWeek);
        }
        
        /**
         * Returns remaining quality times to reach the goal.
         */
        public int remainingToGoal() {
            return Math.max(0, targetQualityTimes - completedQualityTimes);
        }
        
        /**
         * Returns true if this is the start of a new week and there's a previous week's data.
         */
        public boolean isNewWeekWithPreviousData() {
            return !hasGoal && lastWeekSummary != null;
        }
    }
    
    /**
     * Summary of last week's goal performance.
     * Used to show the weekly summary before setting a new goal.
     * 
     * @param targetHours the target hours from last week
     * @param actualMinutes actual minutes completed last week
     * @param completedCount number of quality times completed
     * @param goalMet whether the goal was met
     * @param weekStartDate start date of last week
     */
    public record LastWeekSummary(
        int targetHours,
        int actualMinutes,
        int completedCount,
        boolean goalMet,
        LocalDate weekStartDate
    ) {
        /**
         * Returns the actual hours completed (rounded to 1 decimal).
         */
        public double actualHours() {
            return Math.round(actualMinutes / 6.0) / 10.0; // Round to 1 decimal
        }
    }
    
    // ─── Convenience Methods ─────────────────────────────────────────────
    
    /**
     * Checks if the father has Google Calendar connected.
     * 
     * @return true if Google Calendar is connected
     */
    public boolean hasGoogleCalendarConnected() {
        return fatherProfile != null && fatherProfile.googleCalendarConnected();
    }
    
    /**
     * Checks if the father has a weekly goal set for the current week.
     * 
     * @return true if a weekly goal is set
     */
    public boolean hasWeeklyGoal() {
        return weeklyGoalInfo != null && weeklyGoalInfo.hasGoal();
    }
    
    /**
     * Gets the next scheduled Quality Time event (if any).
     * 
     * @return the next scheduled Quality Time, or null if none
     */
    public QualityTimeEvent getNextScheduledQualityTime() {
        if (qualityTimeEvents == null) {
            return null;
        }
        return qualityTimeEvents.stream()
            .filter(qt -> "SCHEDULED".equals(qt.status()))
            .filter(qt -> qt.scheduledStart().isAfter(Instant.now()))
            .min((a, b) -> a.scheduledStart().compareTo(b.scheduledStart()))
            .orElse(null);
    }
    
    /**
     * Gets the father's first child, or null if no children.
     * Useful when only one child exists and no child selection is needed.
     * 
     * @return the first child, or null if no children
     */
    public ChildInfo getDefaultChild() {
        if (fatherProfile == null || fatherProfile.children() == null || fatherProfile.children().isEmpty()) {
            return null;
        }
        return fatherProfile.children().get(0);
    }
    
    /**
     * Checks if the father has multiple children (requiring child selection during scheduling).
     * 
     * @return true if the father has more than one child
     */
    public boolean hasMultipleChildren() {
        return fatherProfile != null 
            && fatherProfile.children() != null 
            && fatherProfile.children().size() > 1;
    }
}
