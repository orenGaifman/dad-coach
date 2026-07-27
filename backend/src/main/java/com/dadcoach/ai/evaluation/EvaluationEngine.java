package com.dadcoach.ai.evaluation;

import com.dadcoach.ai.prompt.AbTestAssigner;
import com.dadcoach.ai.telemetry.AiTelemetryRecord;
import com.dadcoach.ai.telemetry.AiTelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Evaluation engine for metrics correlation and A/B test analysis.
 * Computes quality scores, compares A/B groups, and persists scores
 * into telemetry records.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Score quality using the {@link QualityScorer} formula</li>
 *   <li>Compare quality scores between A/B test groups</li>
 *   <li>Persist quality scores in telemetry records</li>
 * </ul>
 */
@Service
public class EvaluationEngine {

    private static final Logger log = LoggerFactory.getLogger(EvaluationEngine.class);

    private final QualityScorer qualityScorer;
    private final AiTelemetryService telemetryService;

    public EvaluationEngine(QualityScorer qualityScorer, AiTelemetryService telemetryService) {
        this.qualityScorer = qualityScorer;
        this.telemetryService = telemetryService;
    }

    /**
     * Evaluate quality signals and produce a score.
     *
     * @param signals the quality signals to evaluate
     * @return the composite quality score in [0, 100]
     */
    public double evaluate(QualitySignals signals) {
        return qualityScorer.computeScore(signals);
    }

    /**
     * Evaluate quality signals and persist the score in a telemetry record.
     * The quality score is stored in the telemetry record via the builder pattern.
     *
     * @param signals   the quality signals to evaluate
     * @param fatherId  the father ID for telemetry and A/B group assignment
     * @param requestId the request ID to associate with the telemetry record
     * @param modelProvider the provider name for the telemetry record
     * @param modelName the model name for the telemetry record
     * @param interactionType the interaction type for the telemetry record
     * @param inputTokens the number of input tokens
     * @param outputTokens the number of output tokens
     * @param latencyMs the total latency in milliseconds
     * @return the computed quality score in [0, 100]
     */
    public double evaluateAndPersist(QualitySignals signals, UUID fatherId, UUID requestId,
                                     String modelProvider, String modelName, String interactionType,
                                     int inputTokens, int outputTokens, int latencyMs) {
        double score = qualityScorer.computeScore(signals);
        String abGroup = AbTestAssigner.assignGroup(fatherId);

        AiTelemetryRecord record = AiTelemetryRecord.builder()
                .requestId(requestId)
                .fatherId(fatherId)
                .interactionType(interactionType)
                .modelProvider(modelProvider)
                .modelName(modelName)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalLatencyMs(latencyMs)
                .validationPassed(true)
                .fallbackUsed(false)
                .retryCount(0)
                .qualityScore((float) score)
                .abTestGroup(abGroup)
                .build();

        telemetryService.recordAsync(record);

        log.debug("Quality score computed and persisted: fatherId={}, score={}, abGroup={}",
                fatherId, String.format("%.2f", score), abGroup);

        return score;
    }

    /**
     * Compare quality scores between A/B test groups.
     * Groups the scores by A/B assignment and computes average score per group.
     *
     * @param scoredEntries list of scored entries with their father IDs
     * @return comparison result containing average scores per group and the difference
     */
    public AbTestComparison compareAbGroups(List<ScoredEntry> scoredEntries) {
        Objects.requireNonNull(scoredEntries, "scoredEntries must not be null");

        double sumA = 0.0;
        int countA = 0;
        double sumB = 0.0;
        int countB = 0;

        for (ScoredEntry entry : scoredEntries) {
            String group = AbTestAssigner.assignGroup(entry.fatherId());
            if (AbTestAssigner.GROUP_A.equals(group)) {
                sumA += entry.qualityScore();
                countA++;
            } else {
                sumB += entry.qualityScore();
                countB++;
            }
        }

        double avgA = countA > 0 ? sumA / countA : 0.0;
        double avgB = countB > 0 ? sumB / countB : 0.0;
        double difference = avgA - avgB;

        log.info("A/B test comparison: groupA(n={}, avg={}), groupB(n={}, avg={}), diff={}",
                countA, String.format("%.2f", avgA), countB, String.format("%.2f", avgB), String.format("%.2f", difference));

        return new AbTestComparison(avgA, countA, avgB, countB, difference);
    }

    /**
     * A scored entry representing a father's quality score for A/B comparison.
     *
     * @param fatherId     the father's unique identifier (used for group assignment)
     * @param qualityScore the computed quality score for this father
     */
    public record ScoredEntry(UUID fatherId, double qualityScore) {}

    /**
     * Result of an A/B test comparison.
     *
     * @param averageScoreA average quality score for group A
     * @param countA        number of entries in group A
     * @param averageScoreB average quality score for group B
     * @param countB        number of entries in group B
     * @param difference    difference (A - B); positive means A is better
     */
    public record AbTestComparison(
            double averageScoreA, int countA,
            double averageScoreB, int countB,
            double difference
    ) {}
}
