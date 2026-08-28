package com.dadcoach.workflow;

/**
 * Progressive steps within the WELCOME workflow state.
 * Enforces a structured onboarding flow:
 * 
 * 1. INTRO - Explain the program, show what we'll do together
 * 2. CONNECT_CALENDAR - Guide user to connect Google Calendar
 * 3. SET_WEEKLY_GOAL - Help user set their first weekly goal
 * 4. SCHEDULE_FIRST_QUALITY_TIME - Schedule first quality time(s) based on goal
 * 5. DASHBOARD_TOUR - Show the dashboard and explain features
 * 
 * User cannot proceed to next step until current step is completed.
 */
public enum WelcomeStep {
    
    /**
     * Initial step: Explain the program.
     * Message explains:
     * - What Dad Coach does
     * - The 3 things we'll set up together
     * - Ask for confirmation to proceed
     * 
     * Next: CONNECT_CALENDAR
     */
    INTRO,
    
    /**
     * Step 1: Connect Google Calendar.
     * Required so we can:
     * - Send reminders
     * - Sync quality times to calendar
     * - Check available times
     * 
     * User must click the link and complete OAuth.
     * Next: SET_WEEKLY_GOAL
     */
    CONNECT_CALENDAR,
    
    /**
     * Step 2: Set weekly goal.
     * Ask how many quality times this week.
     * If mid-week, goal is until Saturday (Shabbat).
     * 
     * Next: SCHEDULE_FIRST_QUALITY_TIME
     */
    SET_WEEKLY_GOAL,
    
    /**
     * Step 3: Schedule quality times.
     * Based on weekly goal, schedule the sessions.
     * Uses calendar availability to suggest times.
     * 
     * Next: DASHBOARD_TOUR
     */
    SCHEDULE_FIRST_QUALITY_TIME,
    
    /**
     * Step 4: Dashboard tour.
     * Show link to dashboard and explain:
     * - Progress tracking
     * - Belt system
     * - Weekly history
     * 
     * After completion: Transition to WAITING state
     */
    DASHBOARD_TOUR,
    
    /**
     * Welcome flow completed.
     * Father is ready for normal operation.
     */
    COMPLETED;
    
    /**
     * Get the next step in the welcome flow.
     * @return the next step, or null if this is the last step
     */
    public WelcomeStep next() {
        return switch (this) {
            case INTRO -> CONNECT_CALENDAR;
            case CONNECT_CALENDAR -> SET_WEEKLY_GOAL;
            case SET_WEEKLY_GOAL -> SCHEDULE_FIRST_QUALITY_TIME;
            case SCHEDULE_FIRST_QUALITY_TIME -> DASHBOARD_TOUR;
            case DASHBOARD_TOUR -> COMPLETED;
            case COMPLETED -> null;
        };
    }
    
    /**
     * Check if this step can be skipped.
     * Currently only CONNECT_CALENDAR can be skipped (user can connect later).
     */
    public boolean isSkippable() {
        return this == CONNECT_CALENDAR;
    }
    
    /**
     * Get the step number (1-based for display).
     */
    public int getStepNumber() {
        return switch (this) {
            case INTRO -> 0;
            case CONNECT_CALENDAR -> 1;
            case SET_WEEKLY_GOAL -> 2;
            case SCHEDULE_FIRST_QUALITY_TIME -> 3;
            case DASHBOARD_TOUR -> 4;
            case COMPLETED -> 5;
        };
    }
    
    /**
     * Get total number of steps (excluding INTRO and COMPLETED).
     */
    public static int getTotalSteps() {
        return 4;
    }
}
