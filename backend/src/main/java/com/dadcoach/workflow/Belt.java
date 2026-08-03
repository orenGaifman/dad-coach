package com.dadcoach.workflow;

/**
 * Belt progression levels based on Quality Time completion count.
 * Gamification element that motivates fathers through visible progression.
 *
 * <p>Belt thresholds per Requirement 8.5:
 * <ul>
 *   <li>WHITE: 0-2 Quality Times</li>
 *   <li>YELLOW: 3-9 Quality Times</li>
 *   <li>ORANGE: 10-24 Quality Times</li>
 *   <li>GREEN: 25-49 Quality Times</li>
 *   <li>BLUE: 50-99 Quality Times</li>
 *   <li>BROWN: 100-199 Quality Times</li>
 *   <li>BLACK: 200+ Quality Times</li>
 * </ul>
 */
public enum Belt {
    WHITE(0, 2),
    YELLOW(3, 9),
    ORANGE(10, 24),
    GREEN(25, 49),
    BLUE(50, 99),
    BROWN(100, 199),
    BLACK(200, Integer.MAX_VALUE);

    private final int minCompletions;
    private final int maxCompletions;

    Belt(int minCompletions, int maxCompletions) {
        this.minCompletions = minCompletions;
        this.maxCompletions = maxCompletions;
    }

    /**
     * Returns the minimum number of Quality Time completions required for this belt.
     *
     * @return the minimum completion threshold
     */
    public int getMinCompletions() {
        return minCompletions;
    }

    /**
     * Returns the maximum number of Quality Time completions for this belt.
     *
     * @return the maximum completion threshold
     */
    public int getMaxCompletions() {
        return maxCompletions;
    }

    /**
     * Determines the belt level for a given Quality Time completion count.
     * Completion counts above 200 are classified as BLACK belt.
     *
     * @param count the number of completed Quality Times
     * @return the matching belt level
     * @throws IllegalArgumentException if count is negative
     */
    public static Belt fromCompletionCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Completion count cannot be negative: " + count);
        }
        for (Belt belt : values()) {
            if (count >= belt.minCompletions && count <= belt.maxCompletions) {
                return belt;
            }
        }
        // Counts above Integer.MAX_VALUE are already covered by BLACK's range,
        // but this is a safety fallback
        return BLACK;
    }

    /**
     * Returns the next belt in the progression, or null if this is BLACK (the highest).
     *
     * @return the next belt level, or null if already at BLACK
     */
    public Belt getNextBelt() {
        int ordinal = this.ordinal();
        if (ordinal >= values().length - 1) {
            return null; // BLACK is the highest belt
        }
        return values()[ordinal + 1];
    }

    /**
     * Returns a human-readable display name for the belt.
     * 
     * @return the display name (e.g., "White Belt", "Yellow Belt")
     */
    public String getDisplayName() {
        String name = this.name();
        return name.charAt(0) + name.substring(1).toLowerCase() + " Belt";
    }

    /**
     * Returns a localized display name for the belt.
     * 
     * @param locale the locale code ("en" or "he")
     * @return the localized display name
     */
    public String getDisplayName(String locale) {
        if ("he".equals(locale)) {
            return switch (this) {
                case WHITE -> "חגורה לבנה";
                case YELLOW -> "חגורה צהובה";
                case ORANGE -> "חגורה כתומה";
                case GREEN -> "חגורה ירוקה";
                case BLUE -> "חגורה כחולה";
                case BROWN -> "חגורה חומה";
                case BLACK -> "חגורה שחורה";
            };
        }
        return getDisplayName();
    }
}
