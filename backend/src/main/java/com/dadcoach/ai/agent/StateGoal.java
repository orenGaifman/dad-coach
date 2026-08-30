package com.dadcoach.ai.agent;

import java.util.List;

/**
 * Represents the proactive goal the AI should guide the user towards.
 * 
 * <p>This record enables goal-driven AI behavior. Instead of being purely reactive
 * (asking "what do you want to do?"), the AI understands what the user NEEDS to do
 * and proactively guides them there.</p>
 * 
 * <p>When a user sends "hi" or any greeting/unclear message, the AI should:
 * <ol>
 *   <li>Check the priorityGoal - what's the most important thing they need to do?</li>
 *   <li>Guide them towards completing that goal</li>
 *   <li>Only ask "what do you want to do?" when all mandatory goals are complete</li>
 * </ol>
 * </p>
 * 
 * <h2>Goal Priority Order:</h2>
 * <ol>
 *   <li>CRITICAL - Blocking issues (e.g., onboarding not complete)</li>
 *   <li>MANDATORY - Must-do actions (e.g., weekly goal not set, no QT scheduled)</li>
 *   <li>SUGGESTED - Recommended actions (e.g., review progress, add more QT)</li>
 *   <li>NONE - User is on track, can offer options</li>
 * </ol>
 * 
 * @param priorityGoal the highest priority goal to guide the user towards
 * @param allPendingGoals all pending goals in priority order (for context)
 * @param userIsOnTrack true if the user has completed all mandatory tasks
 * @param summaryForAi human-readable summary for the AI to understand the situation
 */
public record StateGoal(
    Goal priorityGoal,
    List<Goal> allPendingGoals,
    boolean userIsOnTrack,
    String summaryForAi
) {
    
    /**
     * Priority levels for goals.
     */
    public enum Priority {
        /** Blocking issues that must be resolved first */
        CRITICAL(1),
        /** Must-do actions for the system to work properly */
        MANDATORY(2),
        /** Recommended actions to improve experience */
        SUGGESTED(3),
        /** No pending goals - user is on track */
        NONE(4);
        
        private final int order;
        
        Priority(int order) {
            this.order = order;
        }
        
        public int getOrder() {
            return order;
        }
    }
    
    /**
     * Types of goals the AI can guide users towards.
     */
    public enum GoalType {
        // Critical
        COMPLETE_ONBOARDING("השלם את תהליך ההרשמה"),
        
        // Mandatory
        SET_WEEKLY_GOAL("קבע יעד שבועי"),
        SCHEDULE_QUALITY_TIME("קבע זמן איכות"),
        REVIEW_LAST_WEEK("סכם את השבוע שעבר"),
        
        // Suggested
        ADD_MORE_QUALITY_TIME("הוסף עוד זמני איכות"),
        CHECK_PROGRESS("בדוק את ההתקדמות"),
        GET_ACTIVITY_IDEAS("קבל רעיונות לפעילויות"),
        COMPLETE_PENDING_QUALITY_TIME("השלם זמן איכות שהסתיים"),
        
        // None
        FREE_CHOICE("בחר מה לעשות");
        
        private final String hebrewDescription;
        
        GoalType(String hebrewDescription) {
            this.hebrewDescription = hebrewDescription;
        }
        
        public String getHebrewDescription() {
            return hebrewDescription;
        }
    }
    
    /**
     * A specific goal with context for the AI.
     * 
     * @param type the type of goal
     * @param priority the priority level
     * @param reason why this goal is needed (for AI context)
     * @param suggestedTool the tool the AI should use to address this goal
     * @param contextData additional context data (e.g., child name, target hours)
     */
    public record Goal(
        GoalType type,
        Priority priority,
        String reason,
        String suggestedTool,
        String contextData
    ) {
        /**
         * Creates a critical goal.
         */
        public static Goal critical(GoalType type, String reason, String suggestedTool) {
            return new Goal(type, Priority.CRITICAL, reason, suggestedTool, null);
        }
        
        /**
         * Creates a mandatory goal.
         */
        public static Goal mandatory(GoalType type, String reason, String suggestedTool) {
            return new Goal(type, Priority.MANDATORY, reason, suggestedTool, null);
        }
        
        /**
         * Creates a mandatory goal with context data.
         */
        public static Goal mandatory(GoalType type, String reason, String suggestedTool, String contextData) {
            return new Goal(type, Priority.MANDATORY, reason, suggestedTool, contextData);
        }
        
        /**
         * Creates a suggested goal.
         */
        public static Goal suggested(GoalType type, String reason, String suggestedTool) {
            return new Goal(type, Priority.SUGGESTED, reason, suggestedTool, null);
        }
        
        /**
         * Creates a suggested goal with context data.
         */
        public static Goal suggested(GoalType type, String reason, String suggestedTool, String contextData) {
            return new Goal(type, Priority.SUGGESTED, reason, suggestedTool, contextData);
        }
    }
    
    /**
     * Creates a StateGoal indicating the user is on track with no mandatory pending actions.
     */
    public static StateGoal onTrack(List<Goal> suggestedGoals, String summary) {
        Goal priorityGoal = suggestedGoals.isEmpty() 
            ? new Goal(GoalType.FREE_CHOICE, Priority.NONE, "המשתמש סיים את כל המשימות החובה", "show_help", null)
            : suggestedGoals.get(0);
        return new StateGoal(priorityGoal, suggestedGoals, true, summary);
    }
    
    /**
     * Creates a StateGoal with pending mandatory actions.
     */
    public static StateGoal withPendingGoals(List<Goal> goals, String summary) {
        if (goals.isEmpty()) {
            return onTrack(List.of(), summary);
        }
        return new StateGoal(goals.get(0), goals, false, summary);
    }
    
    /**
     * Check if the priority goal is critical or mandatory.
     */
    public boolean hasMandatoryGoal() {
        return priorityGoal != null && 
               (priorityGoal.priority() == Priority.CRITICAL || priorityGoal.priority() == Priority.MANDATORY);
    }
    
    /**
     * Generate a formatted string for the AI prompt.
     */
    public String generateForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🎯 מטרת השיחה הנוכחית\n\n");
        
        if (userIsOnTrack) {
            sb.append("✅ **המשתמש במסלול!** כל המשימות החובה הושלמו.\n");
            sb.append("אפשר להציע פעולות נוספות או לשאול מה הוא רוצה לעשות.\n\n");
        } else {
            sb.append("⚠️ **יש משימות שהמשתמש צריך להשלים!**\n");
            sb.append("**אל תשאל 'מה אתה רוצה לעשות?'** - הנחה אותו למשימה העיקרית.\n\n");
        }
        
        sb.append("### משימה עיקרית\n");
        sb.append(formatGoal(priorityGoal));
        sb.append("\n");
        
        if (allPendingGoals.size() > 1) {
            sb.append("### משימות נוספות בתור\n");
            for (int i = 1; i < Math.min(allPendingGoals.size(), 4); i++) {
                sb.append(String.format("%d. %s\n", i, allPendingGoals.get(i).type().getHebrewDescription()));
            }
            sb.append("\n");
        }
        
        sb.append("### סיכום מצב\n");
        sb.append(summaryForAi);
        sb.append("\n");
        
        return sb.toString();
    }
    
    private String formatGoal(Goal goal) {
        if (goal == null) {
            return "אין משימה מוגדרת";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("- **סוג:** ").append(goal.type().getHebrewDescription()).append("\n");
        sb.append("- **עדיפות:** ").append(formatPriority(goal.priority())).append("\n");
        sb.append("- **סיבה:** ").append(goal.reason()).append("\n");
        sb.append("- **כלי מומלץ:** `").append(goal.suggestedTool()).append("`\n");
        if (goal.contextData() != null && !goal.contextData().isEmpty()) {
            sb.append("- **מידע נוסף:** ").append(goal.contextData()).append("\n");
        }
        return sb.toString();
    }
    
    private String formatPriority(Priority priority) {
        return switch (priority) {
            case CRITICAL -> "🔴 קריטי - חייב לטפל עכשיו";
            case MANDATORY -> "🟠 חובה - המשתמש צריך לבצע";
            case SUGGESTED -> "🟢 מומלץ - הצעה לשיפור";
            case NONE -> "⚪ רגיל - לפי בחירת המשתמש";
        };
    }
}
