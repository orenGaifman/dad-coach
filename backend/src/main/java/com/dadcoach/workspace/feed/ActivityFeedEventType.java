package com.dadcoach.workspace.feed;

/**
 * Enumeration of all event types that can appear in the activity feed.
 *
 * <p>Each type represents a distinct user-facing event that is surfaced in
 * the father's activity feed timeline.</p>
 *
 * @see ActivityFeedItem
 */
public enum ActivityFeedEventType {

    /** A new mission has been assigned to the father. */
    MISSION_ASSIGNED("A new mission was assigned"),

    /** A mission was completed by the father. */
    MISSION_COMPLETED("A mission was completed"),

    /** A new goal was created. */
    GOAL_CREATED("A new goal was created"),

    /** A goal's progress was updated. */
    GOAL_PROGRESS_UPDATE("Goal progress was updated"),

    /** A coaching conversation was completed. */
    CONVERSATION_COMPLETED("A conversation was completed"),

    /** An achievement was earned. */
    ACHIEVEMENT_EARNED("An achievement was earned"),

    /** A milestone was reached. */
    MILESTONE_REACHED("A milestone was reached"),

    /** The father's belt level increased. */
    BELT_LEVEL_UP("Belt level increased"),

    /** A streak milestone was reached. */
    STREAK_MILESTONE("A streak milestone was reached"),

    /** A child's birthday is approaching or today. */
    CHILD_BIRTHDAY("A child's birthday is approaching");

    private final String description;

    ActivityFeedEventType(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this event type.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
