package com.dadcoach.father;

/**
 * Coaching phases based on days since activation.
 * Each phase defines a day range and progressively increases coaching depth.
 */
public enum CoachingPhase {
    FOUNDATION(1, 14),    // Days 1-14
    BUILDING(15, 42),     // Days 15-42
    DEEPENING(43, 84),    // Days 43-84
    MASTERY(85, Integer.MAX_VALUE);  // Day 85+

    private final int startDay;
    private final int endDay;

    CoachingPhase(int startDay, int endDay) {
        this.startDay = startDay;
        this.endDay = endDay;
    }

    public int getStartDay() {
        return startDay;
    }

    public int getEndDay() {
        return endDay;
    }

    /**
     * Determines the coaching phase for a given number of days since activation.
     *
     * @param daysSinceActivation number of days since the father was activated
     * @return the corresponding coaching phase
     * @throws IllegalArgumentException if daysSinceActivation is less than 1
     */
    public static CoachingPhase forDay(int daysSinceActivation) {
        if (daysSinceActivation < 1) {
            throw new IllegalArgumentException("Days since activation must be at least 1, got: " + daysSinceActivation);
        }
        for (CoachingPhase phase : values()) {
            if (daysSinceActivation >= phase.startDay && daysSinceActivation <= phase.endDay) {
                return phase;
            }
        }
        // Should never reach here given MASTERY covers up to MAX_VALUE
        return MASTERY;
    }

    /**
     * Checks whether the given day falls within this phase's range.
     */
    public boolean containsDay(int day) {
        return day >= startDay && day <= endDay;
    }
}
