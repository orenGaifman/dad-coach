package com.dadcoach.workflow.message;

/**
 * Enumeration of all message types in the deterministic workflow engine.
 * 
 * <p>Each message type corresponds to a specific message template used by the
 * {@link MessageGenerator}. Templates are stored in the message_templates table
 * and used as fallback when AI message generation fails or times out.</p>
 * 
 * <p>The message generator receives a MessageType along with contextual data
 * and produces natural language text. The AI component only generates text;
 * it does NOT make decisions about state transitions or business logic.</p>
 * 
 * <p>Implements Requirement 10.2 from the deterministic-workflow-engine spec.</p>
 * 
 * @see MessageGenerator
 * @see MessageTemplate
 * @see MessageContext
 */
public enum MessageType {
    
    // ─── WELCOME State Messages ─────────────────────────────────────────────
    
    /**
     * Initial greeting message for new fathers entering the WELCOME state.
     * Greets the father by name and explains the core concept of Dad Coach.
     * 
     * <p>Context required: fatherName</p>
     * 
     * @see WorkflowState#WELCOME
     */
    WELCOME_GREETING("welcome_greeting"),
    
    /**
     * Explanation message when father requests more information in WELCOME state.
     * Provides details about Quality Time, belt progression, and how the system works.
     * 
     * <p>Context required: fatherName</p>
     * 
     * @see WorkflowState#WELCOME
     */
    WELCOME_EXPLAIN("welcome_explain"),
    
    // ─── SCHEDULE_QUALITY_TIME State Messages ───────────────────────────────
    
    /**
     * Message presenting available time slots for Quality Time scheduling.
     * Shows 3-5 numbered time slots for the father to choose from.
     * 
     * <p>Context required: fatherName, childName, timeSlots, timezone</p>
     * 
     * @see WorkflowState#SCHEDULE_QUALITY_TIME
     */
    SCHEDULE_SLOTS("schedule_slots"),
    
    /**
     * Confirmation message after successfully scheduling Quality Time.
     * Includes event details and encouragement.
     * 
     * <p>Context required: fatherName, childName, scheduledStart, scheduledEnd</p>
     * 
     * @see WorkflowState#SCHEDULE_QUALITY_TIME
     */
    SCHEDULE_CONFIRM("schedule_confirm"),
    
    /**
     * Message when no available time slots are found in the father's calendar.
     * Suggests connecting calendar or trying different time ranges.
     * 
     * <p>Context required: fatherName</p>
     * 
     * @see WorkflowState#SCHEDULE_QUALITY_TIME
     */
    SCHEDULE_NO_SLOTS("schedule_no_slots"),
    
    // ─── WAITING State Messages ─────────────────────────────────────────────
    
    /**
     * Morning reminder message sent on the day of scheduled Quality Time.
     * Sent at 8:00 AM in the father's local timezone.
     * 
     * <p>Context required: fatherName, childName, scheduledTime</p>
     * 
     * @see WorkflowState#WAITING
     */
    WAITING_REMINDER("waiting_reminder"),
    
    /**
     * Response when father inquires about their schedule while in WAITING state.
     * Confirms the date, time, and child for the upcoming Quality Time.
     * 
     * <p>Context required: fatherName, childName, scheduledStart, scheduledEnd</p>
     * 
     * @see WorkflowState#WAITING
     */
    WAITING_SCHEDULE_INFO("waiting_schedule_info"),
    
    // ─── QUALITY_TIME_FOLLOW_UP State Messages ──────────────────────────────
    
    /**
     * Question message asking if the father completed their Quality Time.
     * Presents clear yes/no options.
     * 
     * <p>Context required: fatherName, childName</p>
     * 
     * @see WorkflowState#QUALITY_TIME_FOLLOW_UP
     */
    FOLLOW_UP_QUESTION("follow_up_question"),
    
    /**
     * Celebration message when father confirms Quality Time completion.
     * Includes streak update and possibly belt progression news.
     * 
     * <p>Context required: fatherName, childName, newStreak, beltEarned (optional)</p>
     * 
     * @see WorkflowState#QUALITY_TIME_FOLLOW_UP
     */
    FOLLOW_UP_COMPLETED("follow_up_completed"),
    
    /**
     * Encouraging message when father reports they didn't complete Quality Time.
     * Non-judgmental, invites them to try again.
     * 
     * <p>Context required: fatherName, childName</p>
     * 
     * @see WorkflowState#QUALITY_TIME_FOLLOW_UP
     */
    FOLLOW_UP_MISSED("follow_up_missed"),
    
    // ─── ACTIVITY_IDEAS State Messages ──────────────────────────────────────
    
    /**
     * Message presenting activity ideas for Quality Time.
     * Includes 3 numbered ideas with title, description, and duration.
     * 
     * <p>Context required: fatherName, childName, childAge, activityIdeas</p>
     * 
     * @see WorkflowState#ACTIVITY_IDEAS
     */
    ACTIVITY_IDEAS("activity_ideas"),
    
    // ─── DASHBOARD Messages ─────────────────────────────────────────────────
    
    /**
     * Text summary of dashboard metrics for WhatsApp delivery.
     * Includes belt, streak, achievements, and deep link to web dashboard.
     * 
     * <p>Context required: fatherName, currentBelt, currentStreak, 
     * totalCompletions, dashboardUrl</p>
     * 
     * @see WorkflowState#DASHBOARD
     */
    DASHBOARD_SUMMARY("dashboard_summary"),
    
    // ─── General Messages ───────────────────────────────────────────────────
    
    /**
     * Clarification message when user input doesn't match expected patterns.
     * Provides explicit options for valid responses in the current state.
     * 
     * <p>Context required: fatherName, validOptions</p>
     */
    CLARIFICATION("clarification"),
    
    /**
     * Generic error message when something goes wrong.
     * Apologizes and asks father to try again.
     * 
     * <p>Context required: fatherName</p>
     */
    ERROR_GENERIC("error_generic"),
    
    // ─── State-Specific Error Messages ──────────────────────────────────────
    
    /**
     * Error message specific to SCHEDULE_QUALITY_TIME state.
     * Provides context about slot finding issues and actionable guidance.
     * 
     * <p>Used when an error occurs during the scheduling flow, such as
     * calendar API timeouts or slot generation failures.</p>
     * 
     * <p>Context required: fatherName</p>
     * 
     * @see WorkflowState#SCHEDULE_QUALITY_TIME
     */
    ERROR_SCHEDULE_QUALITY_TIME("error_schedule_quality_time"),
    
    /**
     * Error message specific to QUALITY_TIME_FOLLOW_UP state.
     * Provides context about follow-up issues and prompts for QT completion status.
     * 
     * <p>Used when an error occurs during follow-up processing, such as
     * database errors when recording completion status.</p>
     * 
     * <p>Context required: fatherName, childName</p>
     * 
     * @see WorkflowState#QUALITY_TIME_FOLLOW_UP
     */
    ERROR_QUALITY_TIME_FOLLOW_UP("error_quality_time_follow_up"),
    
    /**
     * Error message specific to WAITING state.
     * Provides context about the waiting state and available actions.
     * 
     * <p>Used when an error occurs while processing messages in the WAITING
     * state, guiding the user on what they can do next.</p>
     * 
     * <p>Context required: fatherName</p>
     * 
     * @see WorkflowState#WAITING
     */
    ERROR_WAITING("error_waiting"),
    
    /**
     * Processing message sent when response takes longer than 30 seconds.
     * Lets the father know the system is still working on their request.
     * 
     * <p>Implements Requirement 11.2: THE Workflow_Engine SHALL respond to every 
     * WhatsApp message within 30 seconds. If processing takes longer, send a 
     * "processing" message immediately and follow up with the real response.</p>
     * 
     * <p>Context required: fatherName</p>
     */
    PROCESSING("processing"),
    
    // ─── Frustration Acknowledgment Messages ────────────────────────────────────
    
    /**
     * Empathetic acknowledgment message when user frustration is detected.
     * Prepended to the normal workflow response when frustration patterns match.
     * 
     * <p>Implements Requirements 2.13, 2.14, 2.15 from chatbot-conversation-bugs spec:
     * Detects frustration indicators like "why again", "already said", "כבר אמרתי"
     * and responds with empathy before continuing with normal workflow.</p>
     * 
     * <p>Context required: none (standalone empathy prefix)</p>
     * 
     * @see WorkflowAction#ACKNOWLEDGE_FRUSTRATION
     */
    FRUSTRATION_ACKNOWLEDGMENT("frustration_acknowledgment"),
    
    // ─── Belt Promotion Messages ────────────────────────────────────────────
    
    /**
     * Celebration message when father earns a new belt.
     * Sent after Quality Time completion when progress threshold is reached.
     * 
     * <p>Context required: fatherName, beltEarned, currentBelt</p>
     */
    BELT_PROMOTION("belt_promotion"),
    
    // ─── Pre-QT Reminder State Messages ─────────────────────────────────────
    
    /**
     * Reminder message sent approximately 1 hour before scheduled Quality Time.
     * Includes activity suggestions and confirmation of the upcoming event.
     * 
     * <p>Context required: fatherName, childName, scheduledStart</p>
     * 
     * @see WorkflowState#QUALITY_TIME_REMINDER
     */
    QUALITY_TIME_REMINDER("quality_time_reminder"),
    
    // ─── Inactivity Messages ────────────────────────────────────────────────
    
    /**
     * Gentle re-engagement message sent after 3 days of inactivity.
     * Supportive tone, reminds about any scheduled Quality Time.
     * 
     * <p>Context required: fatherName, childName (optional), scheduledStart (optional)</p>
     * 
     * @see WorkflowState#INACTIVITY_NUDGE
     */
    INACTIVITY_NUDGE("inactivity_nudge"),
    
    /**
     * Message sent when father has been inactive for 7+ days and coaching is paused.
     * Supportive tone, leaves door open for return.
     * 
     * <p>Context required: fatherName</p>
     */
    COACHING_PAUSED("coaching_paused"),
    
    // ─── Weekly Goal Messages ───────────────────────────────────────────────
    
    /**
     * Sunday morning prompt asking father to set their weekly Quality Time goal.
     * Sent at 8:00 AM Israel time to fathers who don't have a goal for the new week.
     * 
     * <p>Context required: fatherName, childName (optional)</p>
     * 
     * @see WorkflowState#SET_WEEKLY_GOAL
     */
    WEEKLY_GOAL_PROMPT("weekly_goal_prompt");
    
    private final String templateKey;
    
    /**
     * Creates a MessageType with the corresponding template key.
     * 
     * @param templateKey the key used to look up the message template in the database
     */
    MessageType(String templateKey) {
        this.templateKey = templateKey;
    }
    
    /**
     * Returns the template key used to look up the message template in the database.
     * 
     * <p>The template key matches the message_type column in the message_templates table,
     * allowing fallback messages to be loaded when AI generation fails.</p>
     * 
     * @return the template key for database lookup
     */
    public String getTemplateKey() {
        return templateKey;
    }
    
    /**
     * Finds a MessageType by its template key.
     * 
     * <p>Performs case-insensitive matching to handle both uppercase values
     * from the database (e.g., 'WELCOME_GREETING') and lowercase template keys
     * (e.g., 'welcome_greeting').</p>
     * 
     * @param templateKey the template key to search for
     * @return the matching MessageType
     * @throws IllegalArgumentException if no MessageType matches the template key
     */
    public static MessageType fromTemplateKey(String templateKey) {
        if (templateKey == null) {
            throw new IllegalArgumentException("Template key cannot be null");
        }
        String normalizedKey = templateKey.toLowerCase();
        for (MessageType type : values()) {
            if (type.templateKey.equalsIgnoreCase(normalizedKey)) {
                return type;
            }
        }
        // Also try matching by enum name (for uppercase database values)
        try {
            return MessageType.valueOf(templateKey.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Fall through to throw more descriptive error
        }
        throw new IllegalArgumentException("Unknown message type template key: " + templateKey);
    }
}
