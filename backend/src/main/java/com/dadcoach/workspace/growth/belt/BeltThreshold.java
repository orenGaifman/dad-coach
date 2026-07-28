package com.dadcoach.workspace.growth.belt;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration class mapping each {@link BeltLevel} to its min/max score thresholds.
 *
 * <p>Score thresholds per Requirement 10.2:</p>
 * <table>
 *   <tr><th>Belt</th><th>Min Score</th><th>Max Score</th></tr>
 *   <tr><td>WHITE</td><td>0</td><td>99</td></tr>
 *   <tr><td>YELLOW</td><td>100</td><td>249</td></tr>
 *   <tr><td>ORANGE</td><td>250</td><td>449</td></tr>
 *   <tr><td>GREEN</td><td>450</td><td>699</td></tr>
 *   <tr><td>BLUE</td><td>700</td><td>899</td></tr>
 *   <tr><td>PURPLE</td><td>900</td><td>1049</td></tr>
 *   <tr><td>BROWN</td><td>1050</td><td>1199</td></tr>
 *   <tr><td>BLACK</td><td>1200</td><td>∞ (Integer.MAX_VALUE)</td></tr>
 * </table>
 *
 * <p>This is a final utility class with a private constructor — instantiation is not allowed.
 * Threshold data is stored in an unmodifiable {@link EnumMap} for O(1) lookup.</p>
 *
 * @see BeltLevel
 */
public final class BeltThreshold {

    /**
     * Immutable record holding the min and max score for a single belt level.
     */
    public record Threshold(int minScore, int maxScore) {

        /**
         * Creates a threshold with validated bounds.
         *
         * @param minScore minimum score (inclusive)
         * @param maxScore maximum score (inclusive)
         * @throws IllegalArgumentException if minScore is negative or maxScore < minScore
         */
        public Threshold {
            if (minScore < 0) {
                throw new IllegalArgumentException("minScore must not be negative: " + minScore);
            }
            if (maxScore < minScore) {
                throw new IllegalArgumentException(
                        "maxScore (" + maxScore + ") must be >= minScore (" + minScore + ")");
            }
        }

        /**
         * Returns whether the given score falls within this threshold range (inclusive).
         *
         * @param score the score to test
         * @return true if minScore <= score <= maxScore
         */
        public boolean contains(int score) {
            return score >= minScore && score <= maxScore;
        }

        /**
         * Returns the total range span of this threshold (maxScore - minScore + 1).
         *
         * @return the number of discrete score values in this range
         */
        public int range() {
            // Guard against overflow for BLACK belt (Integer.MAX_VALUE)
            if (maxScore == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return maxScore - minScore + 1;
        }
    }

    private static final Map<BeltLevel, Threshold> THRESHOLDS;

    static {
        EnumMap<BeltLevel, Threshold> map = new EnumMap<>(BeltLevel.class);
        map.put(BeltLevel.WHITE, new Threshold(0, 99));
        map.put(BeltLevel.YELLOW, new Threshold(100, 249));
        map.put(BeltLevel.ORANGE, new Threshold(250, 449));
        map.put(BeltLevel.GREEN, new Threshold(450, 699));
        map.put(BeltLevel.BLUE, new Threshold(700, 899));
        map.put(BeltLevel.PURPLE, new Threshold(900, 1049));
        map.put(BeltLevel.BROWN, new Threshold(1050, 1199));
        map.put(BeltLevel.BLACK, new Threshold(1200, Integer.MAX_VALUE));
        THRESHOLDS = Collections.unmodifiableMap(map);
    }

    private BeltThreshold() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Returns the belt level corresponding to the given score.
     *
     * <p>Iterates through belt levels from highest (BLACK) to lowest (WHITE) and
     * returns the first belt whose minimum score the given score meets or exceeds.</p>
     *
     * @param score the growth score (must be non-negative)
     * @return the corresponding belt level
     * @throws IllegalArgumentException if score is negative
     */
    public static BeltLevel beltForScore(int score) {
        if (score < 0) {
            throw new IllegalArgumentException("Score must not be negative: " + score);
        }
        // Iterate from highest belt downward to find the first match
        BeltLevel[] levels = BeltLevel.values();
        for (int i = levels.length - 1; i >= 0; i--) {
            if (score >= THRESHOLDS.get(levels[i]).minScore()) {
                return levels[i];
            }
        }
        // Should never reach here since WHITE starts at 0
        return BeltLevel.WHITE;
    }

    /**
     * Returns the minimum score required to achieve the given belt level.
     *
     * @param belt the belt level
     * @return the minimum score (inclusive)
     * @throws IllegalArgumentException if belt is null
     */
    public static int getMinScore(BeltLevel belt) {
        if (belt == null) {
            throw new IllegalArgumentException("Belt level must not be null");
        }
        return THRESHOLDS.get(belt).minScore();
    }

    /**
     * Returns the maximum score for the given belt level.
     *
     * <p>For BLACK belt, returns {@link Integer#MAX_VALUE} since there is no upper bound.</p>
     *
     * @param belt the belt level
     * @return the maximum score (inclusive)
     * @throws IllegalArgumentException if belt is null
     */
    public static int getMaxScore(BeltLevel belt) {
        if (belt == null) {
            throw new IllegalArgumentException("Belt level must not be null");
        }
        return THRESHOLDS.get(belt).maxScore();
    }

    /**
     * Returns the number of points remaining to reach the next belt from the current score.
     *
     * <p>For BLACK belt (the maximum), always returns 0 since there is no higher belt.</p>
     *
     * @param currentBelt  the father's current belt level
     * @param currentScore the father's current growth score
     * @return points needed to reach the next belt, or 0 for BLACK
     * @throws IllegalArgumentException if currentBelt is null or currentScore is negative
     */
    public static int getPointsToNextBelt(BeltLevel currentBelt, int currentScore) {
        if (currentBelt == null) {
            throw new IllegalArgumentException("Current belt must not be null");
        }
        if (currentScore < 0) {
            throw new IllegalArgumentException("Current score must not be negative: " + currentScore);
        }
        if (currentBelt.isMaxLevel()) {
            return 0;
        }
        BeltLevel nextBelt = currentBelt.next().orElseThrow();
        int nextMinScore = THRESHOLDS.get(nextBelt).minScore();
        return Math.max(0, nextMinScore - currentScore);
    }

    /**
     * Returns the progress percentage within the current belt toward the next belt.
     *
     * <p>The progress is calculated as how far the score has advanced within the current
     * belt's range toward the next belt's minimum threshold.</p>
     *
     * <p>For BLACK belt (the maximum), always returns 100 since there is no higher belt.</p>
     *
     * @param currentBelt  the father's current belt level
     * @param currentScore the father's current growth score
     * @return progress percentage (0-100)
     * @throws IllegalArgumentException if currentBelt is null or currentScore is negative
     */
    public static int getProgressPercentage(BeltLevel currentBelt, int currentScore) {
        if (currentBelt == null) {
            throw new IllegalArgumentException("Current belt must not be null");
        }
        if (currentScore < 0) {
            throw new IllegalArgumentException("Current score must not be negative: " + currentScore);
        }
        if (currentBelt.isMaxLevel()) {
            return 100;
        }
        int currentMin = THRESHOLDS.get(currentBelt).minScore();
        BeltLevel nextBelt = currentBelt.next().orElseThrow();
        int nextMin = THRESHOLDS.get(nextBelt).minScore();
        int totalRange = nextMin - currentMin;
        int progress = currentScore - currentMin;

        if (totalRange <= 0) {
            return 100;
        }
        int percentage = (int) ((long) progress * 100 / totalRange);
        return Math.max(0, Math.min(100, percentage));
    }

    /**
     * Returns the threshold data for the given belt level.
     *
     * @param belt the belt level
     * @return the threshold record with min and max scores
     * @throws IllegalArgumentException if belt is null
     */
    public static Threshold getThreshold(BeltLevel belt) {
        if (belt == null) {
            throw new IllegalArgumentException("Belt level must not be null");
        }
        return THRESHOLDS.get(belt);
    }

    /**
     * Returns an unmodifiable view of all belt level to threshold mappings.
     *
     * @return unmodifiable map of belt levels to their thresholds
     */
    public static Map<BeltLevel, Threshold> getAllThresholds() {
        return THRESHOLDS;
    }
}
