package com.dadcoach.workflow;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The six workflow states in the deterministic state machine.
 * 
 * <p>Each state defines its valid transitions to other states.
 * The workflow follows this general flow:</p>
 * 
 * <pre>
 * WELCOME → SCHEDULE_QUALITY_TIME → WAITING → QUALITY_TIME_FOLLOW_UP → (back to SCHEDULE_QUALITY_TIME)
 * 
 * Overlay states (accessible from multiple states):
 * - ACTIVITY_IDEAS: On-demand, returns to previous state
 * - DASHBOARD: Frontend-only display state
 * </pre>
 * 
 * <p>Implements Requirements 1.1 and 1.3 from the deterministic-workflow-engine spec.</p>
 * 
 * @see <a href="Requirements 1.1">Workflow State Definitions</a>
 * @see <a href="Requirements 1.3">State Transitions</a>
 */
public enum WorkflowState {
    
    /**
     * Initial state for new fathers. Explains Dad Coach, guides to first Quality Time.
     * Valid transitions: SCHEDULE_QUALITY_TIME
     */
    WELCOME,
    
    /**
     * Active scheduling state. Reads Google Calendar, suggests slots, creates events.
     * Valid transitions: WAITING, ACTIVITY_IDEAS
     */
    SCHEDULE_QUALITY_TIME,
    
    /**
     * Passive waiting state. Daily morning reminder if Quality Time exists today.
     * Valid transitions: QUALITY_TIME_FOLLOW_UP, SCHEDULE_QUALITY_TIME, ACTIVITY_IDEAS
     */
    WAITING,
    
    /**
     * Post-event state. Asks if father completed Quality Time, updates dashboard.
     * Valid transitions: SCHEDULE_QUALITY_TIME
     */
    QUALITY_TIME_FOLLOW_UP,
    
    /**
     * On-demand state. Triggered only when father explicitly asks for ideas.
     * Returns to previous state when done.
     * Valid transitions: WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP
     */
    ACTIVITY_IDEAS,
    
    /**
     * Frontend-only state for dashboard display. Not persisted in WhatsApp flow.
     * Valid transitions: none (terminal display state)
     */
    DASHBOARD;
    
    /**
     * Returns the set of valid target states that can be transitioned to from this state.
     * 
     * @return an unmodifiable set of valid target states
     */
    public Set<WorkflowState> getValidTransitions() {
        return switch (this) {
            case WELCOME -> Collections.unmodifiableSet(
                EnumSet.of(SCHEDULE_QUALITY_TIME)
            );
            case SCHEDULE_QUALITY_TIME -> Collections.unmodifiableSet(
                EnumSet.of(WAITING, ACTIVITY_IDEAS)
            );
            case WAITING -> Collections.unmodifiableSet(
                EnumSet.of(QUALITY_TIME_FOLLOW_UP, SCHEDULE_QUALITY_TIME, ACTIVITY_IDEAS)
            );
            case QUALITY_TIME_FOLLOW_UP -> Collections.unmodifiableSet(
                EnumSet.of(SCHEDULE_QUALITY_TIME)
            );
            case ACTIVITY_IDEAS -> Collections.unmodifiableSet(
                EnumSet.of(WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP)
            );
            case DASHBOARD -> Collections.emptySet();
        };
    }
    
    /**
     * Checks if a transition from this state to the target state is valid.
     * 
     * @param target the target state to transition to
     * @return true if the transition is valid, false otherwise
     */
    public boolean canTransitionTo(WorkflowState target) {
        return getValidTransitions().contains(target);
    }
}
