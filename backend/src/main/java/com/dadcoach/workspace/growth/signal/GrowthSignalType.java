package com.dadcoach.workspace.growth.signal;

/**
 * Enumeration of all recognized growth signal types in the Father Growth System.
 *
 * <p>Each type represents a distinct action or milestone that contributes positively
 * to a father's growth score. Signal types are extensible — new types can be added
 * without modifying existing scoring logic or requiring data migration.</p>
 *
 * @see SignalWeight
 */
public enum GrowthSignalType {

    /** Mission transitions to COMPLETED status. */
    MISSION_COMPLETED("Mission completed by the father"),

    /** Mission transitions to REFLECTED status (bonus on top of completion). */
    MISSION_REFLECTED("Mission reflected upon after completion"),

    /** A goal advances by at least 10% progress. */
    GOAL_PROGRESS("Goal progress advanced by at least 10%"),

    /** A goal reaches 100% completion. */
    GOAL_COMPLETED("Goal reached 100% completion"),

    /** A coaching conversation exceeds 5 exchanges with quality rating above 0.6. */
    MEANINGFUL_CONVERSATION("Meaningful coaching conversation completed"),

    /** At least one qualifying interaction on a calendar day. */
    DAILY_ENGAGEMENT("Daily qualifying engagement recorded"),

    /** Father's current streak reaches exactly 7 consecutive days. */
    STREAK_BONUS_7("7-day streak milestone reached"),

    /** Father's current streak reaches exactly 14 consecutive days. */
    STREAK_BONUS_14("14-day streak milestone reached"),

    /** Father's current streak reaches exactly 21 consecutive days. */
    STREAK_BONUS_21("21-day streak milestone reached"),

    /** Father's current streak reaches exactly 30 consecutive days. */
    STREAK_BONUS_30("30-day streak milestone reached"),

    /** Father's current streak reaches exactly 60 consecutive days. */
    STREAK_BONUS_60("60-day streak milestone reached"),

    /** Father's current streak reaches exactly 90 consecutive days. */
    STREAK_BONUS_90("90-day streak milestone reached"),

    /** Father's current streak reaches exactly 180 consecutive days. */
    STREAK_BONUS_180("180-day streak milestone reached"),

    /** Father's current streak reaches exactly 365 consecutive days. */
    STREAK_BONUS_365("365-day streak milestone reached"),

    /** Father reports quality time with a child (minimum 15 minutes). */
    QUALITY_TIME_REPORTED("Quality time with child reported"),

    /** Father reports a positive parenting activity (praise, shared activity, teaching moment). */
    POSITIVE_ACTIVITY("Positive parenting activity reported");

    private final String description;

    GrowthSignalType(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this signal type.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns whether this signal type is a streak bonus.
     *
     * @return true if this is a streak bonus signal type
     */
    public boolean isStreakBonus() {
        return name().startsWith("STREAK_BONUS_");
    }

    /**
     * Parses a string value into a {@link GrowthSignalType}, case-insensitively.
     *
     * @param value the string to parse
     * @return the matching signal type
     * @throws IllegalArgumentException if value is null, blank, or unrecognized
     */
    public static GrowthSignalType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GrowthSignalType must not be null or blank");
        }
        String trimmed = value.trim().toUpperCase();
        try {
            return GrowthSignalType.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown GrowthSignalType: " + trimmed);
        }
    }
}
