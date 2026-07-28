package com.dadcoach.workspace.growth.belt;

import java.util.Optional;

/**
 * Enumeration of the eight progression belt levels in the Father Growth System.
 *
 * <p>Belts are ordered from beginner ({@link #WHITE}) to mastery ({@link #BLACK}).
 * Belt progression is monotonic — once a father reaches a belt, they retain it
 * permanently (Design Decision AD-8).</p>
 *
 * <p>Thresholds for each belt are defined in {@link BeltThreshold}.</p>
 *
 * @see BeltThreshold
 */
public enum BeltLevel {

    /** Getting Started — the default belt for all new fathers. */
    WHITE("Getting Started"),

    /** Building Habits — early engagement patterns forming. */
    YELLOW("Building Habits"),

    /** Finding Rhythm — consistent participation emerging. */
    ORANGE("Finding Rhythm"),

    /** Growing Strong — solid engagement and growth. */
    GREEN("Growing Strong"),

    /** Deep Connection — advanced engagement with children. */
    BLUE("Deep Connection"),

    /** Advanced Father — high-level consistency and quality. */
    PURPLE("Advanced Father"),

    /** Near Mastery — approaching the highest level. */
    BROWN("Near Mastery"),

    /** Master Father — the highest achievable belt level. */
    BLACK("Master Father");

    private final String description;

    BeltLevel(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this belt level.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns whether this belt level is strictly higher than the given belt.
     *
     * <p>Comparison is based on ordinal position in the enum declaration order.</p>
     *
     * @param other the belt level to compare against
     * @return true if this belt is higher than the given belt
     * @throws IllegalArgumentException if other is null
     */
    public boolean isHigherThan(BeltLevel other) {
        if (other == null) {
            throw new IllegalArgumentException("Belt level to compare must not be null");
        }
        return this.ordinal() > other.ordinal();
    }

    /**
     * Returns whether this belt level is the highest achievable belt (BLACK).
     *
     * @return true if this is the BLACK belt
     */
    public boolean isMaxLevel() {
        return this == BLACK;
    }

    /**
     * Returns whether this belt level is the highest achievable belt (BLACK).
     * Alias for {@link #isMaxLevel()} for readability in certain contexts.
     *
     * @return true if this is the BLACK belt
     */
    public boolean isMaxBelt() {
        return this == BLACK;
    }

    /**
     * Returns the next belt level above this one, or empty if this is BLACK.
     *
     * @return an Optional containing the next belt level, or empty for BLACK
     */
    public Optional<BeltLevel> next() {
        if (isMaxLevel()) {
            return Optional.empty();
        }
        return Optional.of(values()[this.ordinal() + 1]);
    }

    /**
     * Returns the next belt level above this one, or {@code null} if this is BLACK.
     *
     * @return the next belt level, or null for BLACK
     */
    public BeltLevel nextBelt() {
        if (isMaxBelt()) {
            return null;
        }
        return values()[this.ordinal() + 1];
    }

    /**
     * Parses a string value into a {@link BeltLevel}, case-insensitively.
     *
     * @param value the string to parse
     * @return the matching belt level
     * @throws IllegalArgumentException if value is null, blank, or unrecognized
     */
    public static BeltLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BeltLevel must not be null or blank");
        }
        String trimmed = value.trim().toUpperCase();
        try {
            return BeltLevel.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown BeltLevel: " + trimmed);
        }
    }
}
