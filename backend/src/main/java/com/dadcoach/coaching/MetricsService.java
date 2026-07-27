package com.dadcoach.coaching;

import org.springframework.stereotype.Service;

/**
 * Stateless computation service for coaching metrics.
 *
 * Computes:
 * - Mission Completion Rate (Req 9.3): (missions_completed / missions_assigned) × 100 over 30-day window, 0 if none assigned
 * - Relationship Progress per child (Req 9.4): (avg(outcome_rating) / 5) × 100 over 30 days, 50 if none completed
 * - Consistency Score (Req 9.6): (days_with_interaction / 30) × 100 over 30-day window
 */
@Service
public class MetricsService {

    /**
     * Computes the Mission Completion Rate over a 30-day window.
     * Formula: (missions_completed / missions_assigned) × 100
     * Returns 0 if no missions were assigned.
     *
     * @param missionsCompleted number of missions completed in the 30-day window (non-negative)
     * @param missionsAssigned  number of missions assigned in the 30-day window (non-negative)
     * @return completion rate as a percentage [0, 100]
     */
    public double computeMissionCompletionRate(int missionsCompleted, int missionsAssigned) {
        if (missionsCompleted < 0 || missionsAssigned < 0) {
            throw new IllegalArgumentException("Missions counts must be non-negative");
        }
        if (missionsCompleted > missionsAssigned) {
            throw new IllegalArgumentException("Completed missions cannot exceed assigned missions");
        }
        if (missionsAssigned == 0) {
            return 0.0;
        }
        return ((double) missionsCompleted / missionsAssigned) * 100.0;
    }

    /**
     * Computes the Relationship Progress for a child over a 30-day window.
     * Formula: (avg(outcome_rating) / 5) × 100
     * Returns 50 if no missions were completed.
     *
     * @param averageOutcomeRating the average outcome rating of completed missions for that child (1.0-5.0)
     * @param hasCompletedMissions whether the child has any completed missions in the 30-day window
     * @return relationship progress as a percentage [0, 100]
     */
    public double computeRelationshipProgress(double averageOutcomeRating, boolean hasCompletedMissions) {
        if (!hasCompletedMissions) {
            return 50.0;
        }
        if (averageOutcomeRating < 1.0 || averageOutcomeRating > 5.0) {
            throw new IllegalArgumentException("Average outcome rating must be between 1.0 and 5.0, got: " + averageOutcomeRating);
        }
        return (averageOutcomeRating / 5.0) * 100.0;
    }

    /**
     * Computes the Consistency Score over a 30-day window.
     * Formula: (days_with_interaction / 30) × 100
     *
     * @param daysWithInteraction number of days with at least one interaction in the 30-day window (0-30)
     * @return consistency score as a percentage [0, 100]
     */
    public double computeConsistencyScore(int daysWithInteraction) {
        if (daysWithInteraction < 0 || daysWithInteraction > 30) {
            throw new IllegalArgumentException("Days with interaction must be between 0 and 30, got: " + daysWithInteraction);
        }
        return ((double) daysWithInteraction / 30.0) * 100.0;
    }
}
