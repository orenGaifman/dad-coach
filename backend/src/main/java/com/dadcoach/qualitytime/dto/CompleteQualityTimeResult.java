package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.Belt;

import java.util.UUID;

/**
 * Result of completing a Quality Time event.
 * 
 * Contains updated status, streak information, and any belt milestone achieved.
 * Used for confirmation messages and dashboard updates.
 * 
 * Requirements: 7.2, 8.5
 */
public record CompleteQualityTimeResult(
        /**
         * The ID of the completed Quality Time.
         */
        UUID qualityTimeId,

        /**
         * The updated status (should be COMPLETED on success).
         */
        QualityTimeStatus status,

        /**
         * Whether the streak counter was updated.
         */
        boolean streakUpdated,

        /**
         * The new streak count after completion.
         */
        int newStreak,

        /**
         * The belt earned with this completion, or null if no new belt.
         * A new belt is earned when the completion count crosses a belt threshold.
         */
        Belt beltEarned,

        /**
         * The current belt after completion (may be same as before or new).
         */
        Belt currentBelt,

        /**
         * Points awarded for this completion (for gamification display).
         */
        int pointsAwarded
) {

    /**
     * Default points awarded for completing a Quality Time.
     */
    public static final int DEFAULT_POINTS = 10;

    /**
     * Creates a completion result with a new belt earned.
     *
     * @param qualityTimeId the ID of the completed Quality Time
     * @param newStreak     the new streak count
     * @param newBelt       the newly earned belt
     * @return a new CompleteQualityTimeResult
     */
    public static CompleteQualityTimeResult withNewBelt(
            UUID qualityTimeId,
            int newStreak,
            Belt newBelt
    ) {
        return new CompleteQualityTimeResult(
                qualityTimeId,
                QualityTimeStatus.COMPLETED,
                true,
                newStreak,
                newBelt,
                newBelt,
                DEFAULT_POINTS
        );
    }

    /**
     * Creates a completion result without a new belt.
     *
     * @param qualityTimeId the ID of the completed Quality Time
     * @param newStreak     the new streak count
     * @param currentBelt   the father's current belt (unchanged)
     * @return a new CompleteQualityTimeResult
     */
    public static CompleteQualityTimeResult withoutNewBelt(
            UUID qualityTimeId,
            int newStreak,
            Belt currentBelt
    ) {
        return new CompleteQualityTimeResult(
                qualityTimeId,
                QualityTimeStatus.COMPLETED,
                true,
                newStreak,
                null,
                currentBelt,
                DEFAULT_POINTS
        );
    }
}
