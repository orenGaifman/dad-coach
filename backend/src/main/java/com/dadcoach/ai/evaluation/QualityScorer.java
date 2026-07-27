package com.dadcoach.ai.evaluation;

import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Automated response quality scorer.
 * Computes a composite AI Quality Score from four normalized signals:
 * <ul>
 *   <li>Mission Completion Rate (mcr) — weight 0.30</li>
 *   <li>Normalized Outcome Rating (nor) — weight 0.25</li>
 *   <li>Conversation Continuation Rate (ccr) — weight 0.25</li>
 *   <li>Normalized Streak Days (nsd) — weight 0.20</li>
 * </ul>
 *
 * <p>Formula: {@code (mcr × 0.3) + (nor × 0.25) + (ccr × 0.25) + (nsd × 0.2)}
 *
 * <p>Each component is normalized to [0, 100] before applying the formula.
 * The result is always clamped to [0, 100].
 *
 * @see <a href="Property 17: Quality Score Formula Correctness">Design Spec</a>
 */
@Component
public class QualityScorer {

    static final double WEIGHT_MCR = 0.30;
    static final double WEIGHT_NOR = 0.25;
    static final double WEIGHT_CCR = 0.25;
    static final double WEIGHT_NSD = 0.20;

    /**
     * Compute the composite quality score from raw signal values.
     * Each input is normalized to [0, 100] before applying the weighted formula.
     *
     * @param signals the quality signals containing raw component values
     * @return the composite quality score in range [0, 100]
     * @throws NullPointerException if signals is null
     */
    public double computeScore(QualitySignals signals) {
        Objects.requireNonNull(signals, "signals must not be null");

        double mcr = normalize(signals.missionCompletionRate());
        double nor = normalize(signals.normalizedOutcomeRating());
        double ccr = normalize(signals.conversationContinuationRate());
        double nsd = normalize(signals.normalizedStreakDays());

        double score = (mcr * WEIGHT_MCR)
                + (nor * WEIGHT_NOR)
                + (ccr * WEIGHT_CCR)
                + (nsd * WEIGHT_NSD);

        return clamp(score);
    }

    /**
     * Normalize a value to the range [0, 100].
     * Values below 0 are clamped to 0; values above 100 are clamped to 100.
     *
     * @param value the raw input value
     * @return the normalized value in [0, 100]
     */
    static double normalize(double value) {
        return clamp(value);
    }

    /**
     * Clamp a value to the range [0, 100].
     *
     * @param value the input value
     * @return the clamped value
     */
    static double clamp(double value) {
        if (value < 0.0) return 0.0;
        if (value > 100.0) return 100.0;
        return value;
    }
}
