package com.dadcoach.ai.mission;

import java.util.List;

/**
 * Calculates mission difficulty based on coaching phase, phase progress, and mission history.
 *
 * <p>Difficulty formula (from design spec):
 * <pre>
 *   base = phase_min + floor((phase_day / phase_duration) × (phase_max - phase_min))
 *   adjustment: +1 if last 3 missions avg rating >= 4; -1 if avg <= 2 OR 2+ expired/skipped
 *   Hard bounds: never exceed phase max, never go below 1
 * </pre>
 */
public class DifficultyCalculator {

    /**
     * Coaching phases with their difficulty bounds.
     */
    public enum Phase {
        FOUNDATION(1, 2, 30),
        BUILDING(2, 3, 30),
        DEEPENING(3, 4, 30),
        MASTERY(4, 5, 30);

        private final int min;
        private final int max;
        private final int durationDays;

        Phase(int min, int max, int durationDays) {
            this.min = min;
            this.max = max;
            this.durationDays = durationDays;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public int durationDays() {
            return durationDays;
        }
    }

    /**
     * A completed mission outcome for difficulty adjustment calculations.
     *
     * @param rating   the outcome rating (1-5), or -1 if expired/skipped
     * @param expired  true if the mission expired or was skipped
     */
    public record MissionOutcome(int rating, boolean expired) {
        public MissionOutcome {
            if (!expired && (rating < 1 || rating > 5)) {
                throw new IllegalArgumentException("rating must be 1-5 for completed missions, was: " + rating);
            }
        }

        /**
         * Creates an expired/skipped outcome.
         */
        public static MissionOutcome expiredOutcome() {
            return new MissionOutcome(-1, true);
        }

        /**
         * Creates a completed outcome with the given rating.
         */
        public static MissionOutcome completed(int rating) {
            return new MissionOutcome(rating, false);
        }
    }

    /**
     * Calculates the difficulty level for the next mission.
     *
     * @param phase          the current coaching phase
     * @param phaseDay       the day within the current phase (1-based)
     * @param recentOutcomes the last 3 mission outcomes (most recent first); may have fewer than 3
     * @return the calculated difficulty, guaranteed within [phase.min(), phase.max()] and >= 1
     */
    public int calculate(Phase phase, int phaseDay, List<MissionOutcome> recentOutcomes) {
        int base = calculateBase(phase, phaseDay);
        int adjustment = calculateAdjustment(recentOutcomes);
        int result = base + adjustment;

        // Hard bounds: never exceed phase max, never go below phase min, never go below 1
        result = Math.max(phase.min(), result);
        result = Math.max(1, result);
        result = Math.min(phase.max(), result);

        return result;
    }

    /**
     * Calculates base difficulty from phase progress.
     * Formula: phase_min + floor((phase_day / phase_duration) × (phase_max - phase_min))
     *
     * @param phase    the current phase
     * @param phaseDay the day within the phase (clamped to [1, duration])
     * @return the base difficulty level
     */
    int calculateBase(Phase phase, int phaseDay) {
        int clampedDay = Math.max(1, Math.min(phaseDay, phase.durationDays()));
        double progress = (double) clampedDay / phase.durationDays();
        int base = phase.min() + (int) Math.floor(progress * (phase.max() - phase.min()));
        return Math.max(phase.min(), Math.min(base, phase.max()));
    }

    /**
     * Calculates the difficulty adjustment from recent mission outcomes.
     *
     * <p>Rules:
     * <ul>
     *   <li>+1 if last 3 missions average rating >= 4</li>
     *   <li>-1 if last 3 missions average rating <= 2 OR 2+ were expired/skipped</li>
     *   <li>0 otherwise (including when fewer than 3 outcomes available)</li>
     * </ul>
     *
     * @param recentOutcomes the last 3 mission outcomes (most recent first)
     * @return -1, 0, or +1
     */
    int calculateAdjustment(List<MissionOutcome> recentOutcomes) {
        if (recentOutcomes == null || recentOutcomes.size() < 3) {
            return 0;
        }

        List<MissionOutcome> lastThree = recentOutcomes.subList(0, Math.min(3, recentOutcomes.size()));

        long expiredCount = lastThree.stream().filter(MissionOutcome::expired).count();
        if (expiredCount >= 2) {
            return -1;
        }

        // Calculate average rating (only from completed missions)
        List<MissionOutcome> completed = lastThree.stream()
            .filter(o -> !o.expired())
            .toList();

        if (completed.isEmpty()) {
            return -1; // All expired = avg effectively 0
        }

        double avgRating = completed.stream()
            .mapToInt(MissionOutcome::rating)
            .average()
            .orElse(0);

        if (avgRating >= 4.0) {
            return 1;
        } else if (avgRating <= 2.0) {
            return -1;
        }

        return 0;
    }
}
