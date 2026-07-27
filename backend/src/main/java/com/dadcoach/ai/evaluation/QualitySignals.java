package com.dadcoach.ai.evaluation;

/**
 * Input signals for quality score computation.
 * Each value represents a raw metric that will be normalized to [0, 100]
 * before applying the quality formula.
 *
 * @param missionCompletionRate    percentage of missions completed (0-100 expected)
 * @param normalizedOutcomeRating  normalized outcome rating from user feedback (0-100 expected)
 * @param conversationContinuationRate percentage of conversations that continue (0-100 expected)
 * @param normalizedStreakDays     normalized streak days metric (0-100 expected)
 */
public record QualitySignals(
        double missionCompletionRate,
        double normalizedOutcomeRating,
        double conversationContinuationRate,
        double normalizedStreakDays
) {

    /**
     * Create quality signals with all values set to zero.
     */
    public static QualitySignals zero() {
        return new QualitySignals(0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Create quality signals with all values set to 100 (maximum).
     */
    public static QualitySignals max() {
        return new QualitySignals(100.0, 100.0, 100.0, 100.0);
    }
}
