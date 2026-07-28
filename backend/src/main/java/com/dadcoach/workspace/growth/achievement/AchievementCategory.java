package com.dadcoach.workspace.growth.achievement;

/**
 * Categories for grouping achievements in the Father Growth System.
 *
 * <p>Each category represents a different dimension of fathering engagement
 * that achievements can recognize.</p>
 *
 * @see Achievement
 */
public enum AchievementCategory {

    /** Achievements related to completing missions. */
    MISSIONS("Achievements earned through mission completion"),

    /** Achievements related to maintaining engagement streaks. */
    CONSISTENCY("Achievements earned through consistent daily engagement"),

    /** Achievements related to belt progression and overall growth. */
    GROWTH("Achievements earned through belt progression and score milestones"),

    /** Achievements related to meaningful conversations with children. */
    CONVERSATIONS("Achievements earned through quality conversations"),

    /** Achievements related to goal setting and completion. */
    GOALS("Achievements earned through goal completion"),

    /** Special one-time or event-based achievements. */
    SPECIAL("Special achievements for unique accomplishments");

    private final String description;

    AchievementCategory(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this achievement category.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
