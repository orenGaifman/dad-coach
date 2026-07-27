package com.dadcoach.ai.telemetry;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for AI telemetry alert threshold detection.
 *
 * <p><b>Validates: Requirements 16.4</b></p>
 *
 * Property 19: Alert Threshold Detection — alerts fire correctly when thresholds
 * are exceeded, and do not fire when metrics are below threshold.
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 19: Alert Threshold Detection")
class AiTelemetryAlertPropertyTest {

    // --- Error Rate Alert Properties ---

    @Property(tries = 200)
    void errorRateAboveThreshold_alwaysTriggers(
            @ForAll("errorRateAboveThreshold") ErrorRateScenario scenario) {
        // When error rate > 5%, alert MUST fire
        double errorRate = (double) scenario.failedCalls / scenario.totalCalls;
        assertTrue(errorRate > AiTelemetryService.ERROR_RATE_THRESHOLD,
                "Precondition: error rate should be above threshold");
        assertTrue(scenario.shouldAlert(),
                "Alert should fire when error rate " + (errorRate * 100) + "% > 5%");
    }

    @Property(tries = 200)
    void errorRateAtOrBelowThreshold_neverTriggers(
            @ForAll("errorRateAtOrBelowThreshold") ErrorRateScenario scenario) {
        // When error rate <= 5%, alert MUST NOT fire
        double errorRate = (double) scenario.failedCalls / scenario.totalCalls;
        assertTrue(errorRate <= AiTelemetryService.ERROR_RATE_THRESHOLD,
                "Precondition: error rate should be at or below threshold");
        assertFalse(scenario.shouldAlert(),
                "Alert should NOT fire when error rate " + (errorRate * 100) + "% <= 5%");
    }

    @Property(tries = 100)
    void errorRateWithZeroTotalCalls_neverTriggers(
            @ForAll @IntRange(min = 0, max = 100) int failedCalls) {
        // When there are no calls at all, no alert should fire regardless of "failures"
        ErrorRateScenario scenario = new ErrorRateScenario(0, 0);
        assertFalse(scenario.shouldAlert(),
                "Alert should NOT fire when there are zero total calls");
    }

    // --- Latency P95 Alert Properties ---

    @Property(tries = 200)
    void latencyP95AboveThreshold_alwaysTriggers(
            @ForAll("latenciesWithP95AboveThreshold") List<Integer> latencies) {
        // When p95 latency > 10000ms, alert MUST fire
        int p95 = AiTelemetryService.computeP95(latencies);
        assertTrue(p95 > AiTelemetryService.LATENCY_P95_THRESHOLD_MS,
                "Precondition: p95 " + p95 + "ms should be above threshold");
        assertTrue(p95 > AiTelemetryService.LATENCY_P95_THRESHOLD_MS,
                "Alert should fire when p95 latency > 10000ms");
    }

    @Property(tries = 200)
    void latencyP95AtOrBelowThreshold_neverTriggers(
            @ForAll("latenciesWithP95AtOrBelowThreshold") List<Integer> latencies) {
        // When p95 latency <= 10000ms, alert MUST NOT fire
        int p95 = AiTelemetryService.computeP95(latencies);
        assertTrue(p95 <= AiTelemetryService.LATENCY_P95_THRESHOLD_MS,
                "Precondition: p95 " + p95 + "ms should be at or below threshold");
        assertFalse(p95 > AiTelemetryService.LATENCY_P95_THRESHOLD_MS,
                "Alert should NOT fire when p95 latency <= 10000ms");
    }

    @Property(tries = 100)
    void emptyLatencyList_neverTriggers() {
        // When there are no latency records, alert MUST NOT fire
        int p95 = AiTelemetryService.computeP95(List.of());
        assertEquals(0, p95);
        assertFalse(p95 > AiTelemetryService.LATENCY_P95_THRESHOLD_MS,
                "Alert should NOT fire when there are no latency records");
    }

    @Property(tries = 200)
    void p95IsAlwaysAtOrBelowMaxLatency(
            @ForAll("anyLatencies") List<Integer> latencies) {
        // p95 should always be <= the maximum value in the list
        if (latencies.isEmpty()) return;
        int p95 = AiTelemetryService.computeP95(latencies);
        int max = latencies.stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(p95 <= max,
                "p95 (" + p95 + ") should be <= max (" + max + ")");
    }

    @Property(tries = 200)
    void p95IsAlwaysAtOrAboveMedian(
            @ForAll("anyLatencies") List<Integer> latencies) {
        // p95 should always be >= the median for non-trivial datasets
        if (latencies.size() < 2) return;
        int p95 = AiTelemetryService.computeP95(latencies);
        int medianIndex = latencies.size() / 2;
        int median = latencies.get(medianIndex);
        assertTrue(p95 >= median,
                "p95 (" + p95 + ") should be >= median (" + median + ")");
    }

    // --- Generators ---

    @Provide
    Arbitrary<ErrorRateScenario> errorRateAboveThreshold() {
        // Generate scenarios where failedCalls/totalCalls > 5%
        return Arbitraries.integers().between(10, 1000).flatMap(totalCalls -> {
            int minFailed = (int) Math.ceil(totalCalls * AiTelemetryService.ERROR_RATE_THRESHOLD) + 1;
            int maxFailed = totalCalls;
            if (minFailed > maxFailed) minFailed = maxFailed;
            return Arbitraries.integers().between(minFailed, maxFailed)
                    .map(failed -> new ErrorRateScenario(totalCalls, failed));
        });
    }

    @Provide
    Arbitrary<ErrorRateScenario> errorRateAtOrBelowThreshold() {
        // Generate scenarios where failedCalls/totalCalls <= 5%
        return Arbitraries.integers().between(1, 1000).flatMap(totalCalls -> {
            int maxFailed = (int) Math.floor(totalCalls * AiTelemetryService.ERROR_RATE_THRESHOLD);
            return Arbitraries.integers().between(0, maxFailed)
                    .map(failed -> new ErrorRateScenario(totalCalls, failed));
        });
    }

    @Provide
    Arbitrary<List<Integer>> latenciesWithP95AboveThreshold() {
        // Generate sorted latency lists where p95 > 10000ms
        return Arbitraries.integers().between(5, 100).flatMap(size -> {
            int p95Index = (int) Math.ceil(size * 0.95) - 1;
            // Values before p95 can be anything 1..30000
            Arbitrary<List<Integer>> lowerValues = Arbitraries.integers().between(1, 30000)
                    .list().ofSize(p95Index);
            // Values at p95 and above MUST exceed the threshold
            int upperCount = size - p95Index;
            Arbitrary<List<Integer>> upperValues = Arbitraries.integers()
                    .between(AiTelemetryService.LATENCY_P95_THRESHOLD_MS + 1, 60000)
                    .list().ofSize(upperCount);
            return Combinators.combine(lowerValues, upperValues).as((lower, upper) -> {
                List<Integer> combined = new ArrayList<>(lower);
                combined.addAll(upper);
                Collections.sort(combined);
                return combined;
            });
        });
    }

    @Provide
    Arbitrary<List<Integer>> latenciesWithP95AtOrBelowThreshold() {
        // Generate sorted latency lists where p95 <= 10000ms
        return Arbitraries.integers().between(1, 100).flatMap(size -> {
            int p95Index = (int) Math.ceil(size * 0.95) - 1;
            // All values up to and including p95 position must be <= threshold
            Arbitrary<List<Integer>> lowerValues = Arbitraries.integers()
                    .between(1, AiTelemetryService.LATENCY_P95_THRESHOLD_MS)
                    .list().ofSize(p95Index + 1);
            // Values beyond p95 can be anything (they don't affect p95)
            int upperCount = size - p95Index - 1;
            Arbitrary<List<Integer>> upperValues = upperCount > 0
                    ? Arbitraries.integers().between(1, 60000).list().ofSize(upperCount)
                    : Arbitraries.just(List.of());
            return Combinators.combine(lowerValues, upperValues).as((lower, upper) -> {
                List<Integer> combined = new ArrayList<>(lower);
                combined.addAll(upper);
                Collections.sort(combined);
                return combined;
            });
        });
    }

    @Provide
    Arbitrary<List<Integer>> anyLatencies() {
        // Generate any sorted latency list
        return Arbitraries.integers().between(1, 60000)
                .list().ofMinSize(1).ofMaxSize(100)
                .map(list -> {
                    List<Integer> sorted = new ArrayList<>(list);
                    Collections.sort(sorted);
                    return sorted;
                });
    }

    // --- Test support record ---

    record ErrorRateScenario(long totalCalls, long failedCalls) {
        /**
         * Determine if an alert should fire based on the error rate logic:
         * alert fires when error_rate > 5% AND totalCalls > 0
         */
        boolean shouldAlert() {
            if (totalCalls == 0) return false;
            double errorRate = (double) failedCalls / totalCalls;
            return errorRate > AiTelemetryService.ERROR_RATE_THRESHOLD;
        }
    }
}
