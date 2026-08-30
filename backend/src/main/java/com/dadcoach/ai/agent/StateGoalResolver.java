package com.dadcoach.ai.agent;

import com.dadcoach.ai.agent.StateGoal.Goal;
import com.dadcoach.ai.agent.StateGoal.GoalType;
import com.dadcoach.ai.agent.StateGoal.Priority;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.workflow.WelcomeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the current state goal based on system state analysis.
 * 
 * <p>This component analyzes the father's current state and determines:
 * <ul>
 *   <li>What mandatory actions need to be completed</li>
 *   <li>What suggested improvements can be made</li>
 *   <li>The priority order of these actions</li>
 * </ul>
 * </p>
 * 
 * <h2>Resolution Logic:</h2>
 * <ol>
 *   <li>Check onboarding status - if not complete, that's critical</li>
 *   <li>Check weekly goal - if new week and no goal, that's mandatory</li>
 *   <li>Check scheduled quality times - if none scheduled, that's mandatory</li>
 *   <li>Check for pending follow-ups - completed QT that needs feedback</li>
 *   <li>Suggest additional quality times if below target</li>
 * </ol>
 */
@Component
public class StateGoalResolver {
    
    private static final Logger log = LoggerFactory.getLogger(StateGoalResolver.class);
    private static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");
    
    /**
     * Resolve the state goal for the current father's situation.
     * 
     * @param systemState the loaded system state
     * @return the resolved StateGoal with prioritized goals
     */
    public StateGoal resolve(SystemState systemState) {
        if (systemState == null) {
            log.warn("SystemState is null, returning default goal");
            return StateGoal.onTrack(List.of(), "לא נטען מידע על המשתמש");
        }
        
        List<Goal> goals = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        
        // 1. Check onboarding status (CRITICAL)
        checkOnboarding(systemState, goals, summary);
        
        // 2. Check last week summary (MANDATORY if new week)
        checkLastWeekReview(systemState, goals, summary);
        
        // 3. Check weekly goal status (MANDATORY)
        checkWeeklyGoal(systemState, goals, summary);
        
        // 4. Check scheduled quality times (MANDATORY if none)
        checkScheduledQualityTimes(systemState, goals, summary);
        
        // 5. Check for pending follow-ups (SUGGESTED)
        checkPendingFollowUps(systemState, goals, summary);
        
        // 6. Check if can add more quality times (SUGGESTED)
        checkCanAddMoreQualityTimes(systemState, goals, summary);
        
        // Sort by priority
        goals.sort(Comparator.comparingInt(g -> g.priority().getOrder()));
        
        // Determine if user is on track
        boolean userIsOnTrack = goals.stream()
            .noneMatch(g -> g.priority() == Priority.CRITICAL || g.priority() == Priority.MANDATORY);
        
        log.info("Resolved {} goals for father, userIsOnTrack={}", goals.size(), userIsOnTrack);
        
        if (userIsOnTrack) {
            return StateGoal.onTrack(goals, summary.toString());
        }
        return StateGoal.withPendingGoals(goals, summary.toString());
    }
    
    /**
     * Check if onboarding is complete.
     */
    private void checkOnboarding(SystemState state, List<Goal> goals, StringBuilder summary) {
        WelcomeStep welcomeStep = null;
        if (state.fatherProfile() != null) {
            welcomeStep = state.fatherProfile().welcomeStep();
        }
        
        if (welcomeStep != null && welcomeStep != WelcomeStep.COMPLETED) {
            String reason = switch (welcomeStep) {
                case INTRO -> "האב בשלב ההיכרות הראשונית";
                case CONNECT_CALENDAR -> "צריך לחבר יומן (אם רלוונטי)";
                case SET_WEEKLY_GOAL -> "צריך לקבוע יעד שבועי ראשון";
                case SCHEDULE_FIRST_QUALITY_TIME -> "צריך לקבוע זמן איכות ראשון";
                case DASHBOARD_TOUR -> "צריך לסיים סיור בדשבורד";
                case COMPLETED -> ""; // Won't reach here
            };
            
            String tool = switch (welcomeStep) {
                case INTRO -> "greet";
                case CONNECT_CALENDAR -> "set_weekly_goal"; // Skip to goal since calendar is done on web
                case SET_WEEKLY_GOAL -> "set_weekly_goal";
                case SCHEDULE_FIRST_QUALITY_TIME -> "schedule_quality_time";
                case DASHBOARD_TOUR -> "show_dashboard";
                case COMPLETED -> "show_help";
            };
            
            goals.add(Goal.critical(GoalType.COMPLETE_ONBOARDING, reason, tool));
            summary.append("🚨 האב בתהליך הרשמה - שלב: ").append(welcomeStep).append("\n");
        } else {
            summary.append("✅ ההרשמה הושלמה\n");
        }
    }
    
    /**
     * Check if we need to review last week's results (new week started).
     */
    private void checkLastWeekReview(SystemState state, List<Goal> goals, StringBuilder summary) {
        SystemState.WeeklyGoalInfo goalInfo = state.weeklyGoalInfo();
        if (goalInfo == null) {
            return;
        }
        
        // Check if it's a new week with last week data but no new goal set yet
        // This indicates the user should review last week before setting a new goal
        if (goalInfo.isNewWeekWithPreviousData()) {
            SystemState.LastWeekSummary lastWeek = goalInfo.lastWeekSummary();
            if (lastWeek != null) {
                String contextData = String.format("שבוע שעבר: %d זמני איכות, %.1f שעות", 
                    lastWeek.completedCount(), lastWeek.actualHours());
                
                goals.add(Goal.mandatory(
                    GoalType.REVIEW_LAST_WEEK,
                    "התחיל שבוע חדש - צריך לסכם את השבוע שעבר ולקבוע יעד חדש",
                    "show_weekly_summary",
                    contextData
                ));
                summary.append("📊 שבוע חדש התחיל - יש לסכם את השבוע שעבר\n");
            }
        }
    }
    
    /**
     * Check if weekly goal is set for current week.
     */
    private void checkWeeklyGoal(SystemState state, List<Goal> goals, StringBuilder summary) {
        SystemState.WeeklyGoalInfo goalInfo = state.weeklyGoalInfo();
        
        // If no weekly goal info or no goal set
        if (goalInfo == null || !goalInfo.hasGoal()) {
            // Check if we already have an onboarding goal (to avoid duplicates)
            boolean hasOnboardingGoal = goals.stream()
                .anyMatch(g -> g.type() == GoalType.COMPLETE_ONBOARDING);
            
            if (!hasOnboardingGoal) {
                goals.add(Goal.mandatory(
                    GoalType.SET_WEEKLY_GOAL,
                    "לא הוגדר יעד שבועי - צריך לקבוע כמה זמני איכות השבוע",
                    "set_weekly_goal"
                ));
                summary.append("⚠️ לא הוגדר יעד שבועי\n");
            }
        } else {
            int target = goalInfo.targetQualityTimes();
            int completed = goalInfo.completedQualityTimes();
            int scheduled = goalInfo.scheduledQualityTimes();
            summary.append(String.format("🎯 יעד שבועי: %d | הושלמו: %d | מתוכננים: %d\n", 
                target, completed, scheduled));
        }
    }
    
    /**
     * Check if there are scheduled quality times for this week.
     */
    private void checkScheduledQualityTimes(SystemState state, List<Goal> goals, StringBuilder summary) {
        List<SystemState.QualityTimeEvent> qtEvents = state.qualityTimeEvents();
        SystemState.WeeklyGoalInfo goalInfo = state.weeklyGoalInfo();
        
        // Count upcoming scheduled QTs (not completed/cancelled)
        long upcomingScheduled = 0;
        if (qtEvents != null) {
            Instant now = Instant.now();
            upcomingScheduled = qtEvents.stream()
                .filter(qt -> "SCHEDULED".equals(qt.status()))
                .filter(qt -> qt.scheduledStart().isAfter(now))
                .count();
        }
        
        // Check if goal is set but no QTs scheduled
        boolean hasGoal = goalInfo != null && goalInfo.hasGoal();
        int target = hasGoal ? goalInfo.targetQualityTimes() : 0;
        int completedAndScheduled = hasGoal ? 
            goalInfo.completedQualityTimes() + goalInfo.scheduledQualityTimes() : 0;
        
        if (hasGoal && upcomingScheduled == 0 && completedAndScheduled < target) {
            // Check if we already have higher priority goals
            boolean hasHigherPriority = goals.stream()
                .anyMatch(g -> g.priority() == Priority.CRITICAL || 
                              (g.priority() == Priority.MANDATORY && g.type() == GoalType.SET_WEEKLY_GOAL));
            
            if (!hasHigherPriority) {
                String childName = getFirstChildName(state);
                String contextData = childName != null ? "ילד: " + childName : null;
                
                goals.add(Goal.mandatory(
                    GoalType.SCHEDULE_QUALITY_TIME,
                    "יש יעד שבועי אבל אין זמני איכות מתוכננים",
                    "schedule_quality_time",
                    contextData
                ));
                summary.append("⚠️ אין זמני איכות מתוכננים השבוע\n");
            }
        } else if (upcomingScheduled > 0) {
            summary.append(String.format("📅 יש %d זמני איכות מתוכננים\n", upcomingScheduled));
        }
    }
    
    /**
     * Check for quality times that ended and need follow-up (completion feedback).
     */
    private void checkPendingFollowUps(SystemState state, List<Goal> goals, StringBuilder summary) {
        List<SystemState.QualityTimeEvent> qtEvents = state.qualityTimeEvents();
        if (qtEvents == null || qtEvents.isEmpty()) {
            return;
        }
        
        Instant now = Instant.now();
        
        // Find scheduled QTs that have ended but aren't marked complete
        List<SystemState.QualityTimeEvent> pendingFollowUps = qtEvents.stream()
            .filter(qt -> "SCHEDULED".equals(qt.status()))
            .filter(qt -> qt.scheduledEnd() != null && qt.scheduledEnd().isBefore(now))
            .toList();
        
        if (!pendingFollowUps.isEmpty()) {
            SystemState.QualityTimeEvent firstPending = pendingFollowUps.get(0);
            String contextData = String.format("זמן איכות עם %s שהסתיים", 
                firstPending.childName() != null ? firstPending.childName() : "הילד");
            
            goals.add(Goal.suggested(
                GoalType.COMPLETE_PENDING_QUALITY_TIME,
                "יש זמן איכות שהסתיים וצריך לסמן אותו כהושלם",
                "complete_quality_time",
                contextData
            ));
            summary.append(String.format("💡 יש %d זמני איכות שהסתיימו וצריך לעדכן\n", pendingFollowUps.size()));
        }
    }
    
    /**
     * Check if user can add more quality times to reach goal.
     */
    private void checkCanAddMoreQualityTimes(SystemState state, List<Goal> goals, StringBuilder summary) {
        SystemState.WeeklyGoalInfo goalInfo = state.weeklyGoalInfo();
        if (goalInfo == null || !goalInfo.hasGoal()) {
            return;
        }
        
        int remaining = goalInfo.remainingToGoal();
        if (remaining > 0) {
            // Check if we already have a mandatory schedule goal
            boolean alreadyHasScheduleGoal = goals.stream()
                .anyMatch(g -> g.type() == GoalType.SCHEDULE_QUALITY_TIME);
            
            if (!alreadyHasScheduleGoal) {
                String contextData = String.format("נשארו %d זמני איכות להשלמת היעד", remaining);
                goals.add(Goal.suggested(
                    GoalType.ADD_MORE_QUALITY_TIME,
                    "אפשר להוסיף עוד זמני איכות כדי להגיע ליעד",
                    "schedule_quality_time",
                    contextData
                ));
            }
        }
    }
    
    /**
     * Get the first child's name for context.
     */
    private String getFirstChildName(SystemState state) {
        if (state.fatherProfile() != null && 
            state.fatherProfile().children() != null && 
            !state.fatherProfile().children().isEmpty()) {
            return state.fatherProfile().children().get(0).name();
        }
        return null;
    }
    
    /**
     * Check if today is the start of a new week (Sunday in Israel).
     */
    @SuppressWarnings("unused")
    private boolean isNewWeekStart() {
        LocalDate today = LocalDate.now(ISRAEL_ZONE);
        return today.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
    
    /**
     * Get the current week's Sunday date.
     */
    @SuppressWarnings("unused")
    private LocalDate getCurrentWeekStart() {
        LocalDate today = LocalDate.now(ISRAEL_ZONE);
        int daysFromSunday = today.getDayOfWeek().getValue() % 7;
        return today.minusDays(daysFromSunday);
    }
}
