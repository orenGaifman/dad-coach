package com.dadcoach.goal;

/**
 * Categories of parenting goals, each with an estimated number of missions to complete.
 */
public enum GoalCategory {
    CONNECTION(15),
    COMMUNICATION(20),
    DISCIPLINE(25),
    EDUCATION(20),
    HEALTH(15),
    EMOTIONAL(20),
    INDEPENDENCE(15),
    FUN(10),
    ROUTINE(30),
    CUSTOM(20);

    private final int estimatedMissions;

    GoalCategory(int estimatedMissions) {
        this.estimatedMissions = estimatedMissions;
    }

    /**
     * Returns the estimated number of missions required to achieve a goal in this category.
     */
    public int getEstimatedMissions() {
        return estimatedMissions;
    }
}
