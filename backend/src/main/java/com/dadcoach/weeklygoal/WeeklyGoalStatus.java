package com.dadcoach.weeklygoal;

/**
 * Status of a weekly goal.
 */
public enum WeeklyGoalStatus {
    
    /**
     * Goal is being set up (SET_WEEKLY_GOAL / DISTRIBUTE_GOAL / SCHEDULE_WEEK states).
     */
    PENDING,
    
    /**
     * Goal is active for the current week.
     */
    ACTIVE,
    
    /**
     * Week ended and goal was met (actual >= target).
     */
    COMPLETED,
    
    /**
     * Week ended and goal was not met (actual < target).
     */
    MISSED,
    
    /**
     * Goal was cancelled mid-week.
     */
    CANCELLED
}
