package com.dadcoach.workflow;

/**
 * Triggers that can cause workflow state transitions.
 * 
 * <p>These triggers are used by the {@link WorkflowEngine} to determine
 * the reason for a state transition, particularly for scheduler-initiated
 * transitions.</p>
 * 
 * <p>Implements Requirement 12.1 from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowEngine#triggerTransition(java.util.UUID, WorkflowTrigger)
 */
public enum WorkflowTrigger {
    
    /**
     * Triggered when a user sends a WhatsApp message.
     * This is the most common trigger for state transitions.
     */
    USER_MESSAGE,
    
    /**
     * Triggered by the scheduler when a scheduled Quality Time's end time has passed.
     * Causes transition from WAITING to QUALITY_TIME_FOLLOW_UP.
     */
    QUALITY_TIME_ENDED,
    
    /**
     * Triggered by the scheduler when a father hasn't responded to follow-up within 24 hours.
     * Causes transition from QUALITY_TIME_FOLLOW_UP to SCHEDULE_QUALITY_TIME,
     * marking the Quality Time as MISSED.
     */
    FOLLOW_UP_TIMEOUT,
    
    /**
     * Triggered by the scheduler for morning reminders on Quality Time day.
     * Sent at 8 AM in the father's local timezone.
     */
    SCHEDULER_REMINDER,
    
    /**
     * Triggered by the scheduler approximately 1 hour before Quality Time starts.
     * Causes transition from WAITING to QUALITY_TIME_REMINDER state.
     * Sends activity ideas and reminder about the upcoming event.
     */
    QUALITY_TIME_APPROACHING
}
