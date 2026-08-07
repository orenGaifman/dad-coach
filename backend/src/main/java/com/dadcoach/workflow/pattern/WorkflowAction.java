package com.dadcoach.workflow.pattern;

/**
 * Defines the actions that can be taken in response to a matched pattern.
 * 
 * <p>These actions are returned by the PatternMatcher when a user message 
 * matches a defined pattern. The StateHandler then executes the appropriate
 * business logic for each action.</p>
 * 
 * <p>Implements Requirement 11.3 - Pattern-based message processing without AI interpretation.</p>
 */
public enum WorkflowAction {

    // ===== WELCOME State Actions =====
    
    /**
     * Transition from WELCOME to SCHEDULE_QUALITY_TIME state.
     * Triggered when father responds affirmatively to welcome message.
     */
    TRANSITION_TO_SCHEDULE,
    
    /**
     * Send explanation about Dad Coach and re-prompt for readiness.
     * Triggered when father asks for more information in WELCOME state.
     */
    EXPLAIN_AND_REPROMPT,

    // ===== SCHEDULE_QUALITY_TIME State Actions =====
    
    /**
     * Select a time slot by number (1-9).
     * Verifies slot availability and creates calendar event.
     */
    SELECT_SLOT,
    
    /**
     * Postpone scheduling for later.
     * Sets a reminder to re-prompt in 24 hours.
     */
    POSTPONE_SCHEDULING,
    
    /**
     * Show additional available time slots.
     * Presents the next batch of available slots.
     */
    SHOW_MORE_SLOTS,
    
    /**
     * Parse a natural language time expression.
     * Validates and converts to specific slot selection.
     */
    PARSE_TIME,

    // ===== QUALITY_TIME_FOLLOW_UP State Actions =====
    
    /**
     * Mark Quality Time as completed.
     * Updates dashboard metrics, increments streak, checks belt progression.
     */
    MARK_COMPLETED,
    
    /**
     * Mark Quality Time as missed.
     * Sends encouraging message without updating metrics.
     */
    MARK_MISSED,

    // ===== ACTIVITY_IDEAS State Actions =====
    
    /**
     * Show details for a specific activity idea (1-3).
     * Provides expanded description and tips.
     */
    SHOW_IDEA_DETAILS,
    
    /**
     * Generate a new set of activity ideas.
     * Uses AI to create fresh suggestions.
     */
    GENERATE_MORE_IDEAS,
    
    /**
     * Exit ACTIVITY_IDEAS and return to previous workflow state.
     * Triggered by "thanks", "done", etc.
     */
    RETURN_TO_PREVIOUS,

    // ===== WAITING State Actions =====
    
    /**
     * Transition to ACTIVITY_IDEAS state.
     * Triggered when father explicitly requests activity suggestions.
     */
    TRANSITION_TO_ACTIVITY_IDEAS,
    
    /**
     * Initiate rescheduling of current Quality Time.
     * Cancels existing event and transitions to SCHEDULE_QUALITY_TIME.
     */
    RESCHEDULE,
    
    /**
     * Show current schedule information.
     * Displays next scheduled Quality Time details.
     */
    SHOW_SCHEDULE,
    
    /**
     * Show dashboard summary via WhatsApp.
     * Sends text summary with deep link to web dashboard.
     */
    SHOW_DASHBOARD_SUMMARY,
    
    /**
     * Acknowledge schedule confirmation.
     * Triggered when father sends "ok", "thanks", "אוקי", etc. after scheduling.
     * Responds with a brief reminder about the scheduled time.
     */
    ACKNOWLEDGE_SCHEDULE,
    
    /**
     * Handle "already scheduled" message.
     * Triggered when father says "כבר קבענו" (already scheduled) or similar.
     * Shows the current scheduled Quality Time as confirmation.
     */
    ALREADY_SCHEDULED,
    
    /**
     * Reset to WELCOME state and send fresh greeting.
     * Triggered when father sends a greeting (היי, hello) in any state.
     * Useful when father wants to start fresh.
     */
    RESET_TO_WELCOME
}
