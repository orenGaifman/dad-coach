package com.dadcoach.ai.evaluation;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Property-based tests for QualityScorer.
 * 
 * <p><b>Validates: Requirements 12.2</b>
 *
 * <p>Property 17: Quality Score Formula Correctness —
 * For any set of quality signals, the composite score SHALL equal
 * (mcr × 0.3) + (nor × 0.25) + (ccr × 0.25) + (nsd × 0.2)
 * and SHALL always be in the range [0, 100].
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 17: Quality Score Formula Correctness")
class QualityScorerPropertyTest {

    private final QualityScorer scorer = new QualityScorer();

    /**
     * Property 17a: The composite score is always in [0, 100] for any normalized inputs.
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Property(tries = 200)
    void scoreAlwaysInRange0To100(
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double mcr,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double nor,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double ccr,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double nsd
    ) {
        QualitySignals signals = new QualitySignals(mcr, nor, ccr, nsd);
        double score = scorer.computeScore(signals);

        assertThat(score).isBetween(0.0, 100.0);
    }

    /**
     * Property 17b: The formula correctly computes
     * (mcr × 0.3) + (nor × 0.25) + (ccr × 0.25) + (nsd × 0.2)
     * for inputs already in [0, 100].
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Property(tries = 200)
    void formulaCorrectlyComputed(
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double mcr,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double nor,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double ccr,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double nsd
    ) {
        QualitySignals signals = new QualitySignals(mcr, nor, ccr, nsd);
        double score = scorer.computeScore(signals);

        double expected = (mcr * 0.30) + (nor * 0.25) + (ccr * 0.25) + (nsd * 0.20);

        assertThat(score).isCloseTo(expected, within(1e-10));
    }

    /**
     * Property 17c: Even with extreme or out-of-range inputs,
     * the score is still clamped to [0, 100].
     * Inputs beyond [0, 100] are normalized (clamped) before the formula.
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Property(tries = 200)
    void scoreClampedForOutOfRangeInputs(
            @ForAll @DoubleRange(min = -1000.0, max = 1000.0) double mcr,
            @ForAll @DoubleRange(min = -1000.0, max = 1000.0) double nor,
            @ForAll @DoubleRange(min = -1000.0, max = 1000.0) double ccr,
            @ForAll @DoubleRange(min = -1000.0, max = 1000.0) double nsd
    ) {
        QualitySignals signals = new QualitySignals(mcr, nor, ccr, nsd);
        double score = scorer.computeScore(signals);

        assertThat(score).isBetween(0.0, 100.0);
    }

    /**
     * Property 17d: When all components are the same value v in [0,100],
     * the score equals v (since weights sum to 1.0).
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Property(tries = 200)
    void uniformInputsProduceUniformScore(
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double uniformValue
    ) {
        QualitySignals signals = new QualitySignals(uniformValue, uniformValue, uniformValue, uniformValue);
        double score = scorer.computeScore(signals);

        // weights sum to 1.0, so v*0.3 + v*0.25 + v*0.25 + v*0.2 = v*1.0 = v
        assertThat(score).isCloseTo(uniformValue, within(1e-10));
    }

    /**
     * Property 17e: Normalization correctly clamps values below 0 to 0 and above 100 to 100.
     * The formula applied after normalization matches the expected computation.
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Property(tries = 200)
    void normalizedFormulaMatchesExpected(
            @ForAll @DoubleRange(min = -500.0, max = 500.0) double mcr,
            @ForAll @DoubleRange(min = -500.0, max = 500.0) double nor,
            @ForAll @DoubleRange(min = -500.0, max = 500.0) double ccr,
            @ForAll @DoubleRange(min = -500.0, max = 500.0) double nsd
    ) {
        QualitySignals signals = new QualitySignals(mcr, nor, ccr, nsd);
        double score = scorer.computeScore(signals);

        // Manually normalize
        double normMcr = Math.max(0.0, Math.min(100.0, mcr));
        double normNor = Math.max(0.0, Math.min(100.0, nor));
        double normCcr = Math.max(0.0, Math.min(100.0, ccr));
        double normNsd = Math.max(0.0, Math.min(100.0, nsd));

        double expected = (normMcr * 0.30) + (normNor * 0.25) + (normCcr * 0.25) + (normNsd * 0.20);
        // Clamp the final result
        expected = Math.max(0.0, Math.min(100.0, expected));

        assertThat(score).isCloseTo(expected, within(1e-10));
    }

    /**
     * Property 17f: All-zero inputs produce a score of 0.
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Example
    void allZerosProduceZero() {
        QualitySignals signals = QualitySignals.zero();
        double score = scorer.computeScore(signals);
        assertThat(score).isEqualTo(0.0);
    }

    /**
     * Property 17g: All-maximum inputs produce a score of 100.
     *
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Example
    void allMaxProduceHundred() {
        QualitySignals signals = QualitySignals.max();
        double score = scorer.computeScore(signals);
        assertThat(score).isEqualTo(100.0);
    }
}
