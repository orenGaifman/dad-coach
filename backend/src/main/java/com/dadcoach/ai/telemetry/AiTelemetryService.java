package com.dadcoach.ai.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for structured telemetry emission of every AI call.
 * Records input/output tokens, latency, cost, model, validation status,
 * quality score, and A/B test group.
 *
 * <p>Telemetry writes are async (non-blocking) to avoid impacting response delivery.
 * Alert triggers fire when thresholds are exceeded:
 * <ul>
 *   <li>error_rate > 5% over 30 minutes</li>
 *   <li>latency p95 > 10s (10000ms) over 15 minutes</li>
 * </ul>
 */
@Service
public class AiTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(AiTelemetryService.class);

    /** Error rate threshold: 5% */
    static final double ERROR_RATE_THRESHOLD = 0.05;

    /** Error rate evaluation window: 30 minutes */
    static final Duration ERROR_RATE_WINDOW = Duration.ofMinutes(30);

    /** Latency p95 threshold: 10 seconds (10000ms) */
    static final int LATENCY_P95_THRESHOLD_MS = 10_000;

    /** Latency evaluation window: 15 minutes */
    static final Duration LATENCY_P95_WINDOW = Duration.ofMinutes(15);

    private final AiTelemetryRepository repository;

    public AiTelemetryService(AiTelemetryRepository repository) {
        this.repository = repository;
    }

    /**
     * Record an AI call telemetry event asynchronously.
     * Does not block the caller — fire and forget with error logging.
     *
     * @param record the telemetry record to persist
     * @return a CompletableFuture that completes when the write finishes
     */
    @Async
    public CompletableFuture<AiTelemetryRecord> recordAsync(AiTelemetryRecord record) {
        try {
            AiTelemetryRecord saved = repository.save(record);
            log.debug("AI telemetry recorded: requestId={}, model={}, latency={}ms, tokens_in={}, tokens_out={}",
                    saved.getRequestId(), saved.getModelName(),
                    saved.getTotalLatencyMs(), saved.getInputTokens(), saved.getOutputTokens());
            return CompletableFuture.completedFuture(saved);
        } catch (Exception e) {
            log.error("Failed to persist AI telemetry record: requestId={}, error={}",
                    record.getRequestId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Check if the error rate alert should fire.
     * Alert triggers when error_rate > 5% over the last 30 minutes.
     *
     * @return true if the error rate exceeds the threshold
     */
    public boolean checkErrorRateAlert() {
        return checkErrorRateAlert(Instant.now());
    }

    /**
     * Check error rate alert relative to a reference time.
     * Useful for testing with controlled timestamps.
     *
     * @param now the reference point for the time window
     * @return true if error_rate > 5% over the 30-minute window ending at 'now'
     */
    public boolean checkErrorRateAlert(Instant now) {
        Instant windowStart = now.minus(ERROR_RATE_WINDOW);
        long totalCalls = repository.countRecordsSince(windowStart);
        if (totalCalls == 0) {
            return false;
        }
        long failedCalls = repository.countFailedRecordsSince(windowStart);
        double errorRate = (double) failedCalls / totalCalls;
        if (errorRate > ERROR_RATE_THRESHOLD) {
            log.warn("AI ALERT: Error rate {}% exceeds threshold {}% over last 30 minutes (total={}, failed={})",
                    String.format("%.2f", errorRate * 100), ERROR_RATE_THRESHOLD * 100, totalCalls, failedCalls);
            return true;
        }
        return false;
    }

    /**
     * Check if the latency p95 alert should fire.
     * Alert triggers when p95 latency > 10s over the last 15 minutes.
     *
     * @return true if p95 latency exceeds the threshold
     */
    public boolean checkLatencyP95Alert() {
        return checkLatencyP95Alert(Instant.now());
    }

    /**
     * Check latency p95 alert relative to a reference time.
     * Useful for testing with controlled timestamps.
     *
     * @param now the reference point for the time window
     * @return true if latency p95 > 10s over the 15-minute window ending at 'now'
     */
    public boolean checkLatencyP95Alert(Instant now) {
        Instant windowStart = now.minus(LATENCY_P95_WINDOW);
        List<Integer> latencies = repository.findLatenciesSince(windowStart);
        if (latencies.isEmpty()) {
            return false;
        }
        int p95Latency = computeP95(latencies);
        if (p95Latency > LATENCY_P95_THRESHOLD_MS) {
            log.warn("AI ALERT: Latency p95 {}ms exceeds threshold {}ms over last 15 minutes (sample_size={})",
                    p95Latency, LATENCY_P95_THRESHOLD_MS, latencies.size());
            return true;
        }
        return false;
    }

    /**
     * Compute the p95 value from a sorted list of latencies.
     * The list must be sorted in ascending order.
     *
     * @param sortedLatencies ascending-sorted latency values in milliseconds
     * @return the p95 latency value
     */
    static int computeP95(List<Integer> sortedLatencies) {
        if (sortedLatencies.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(sortedLatencies.size() * 0.95) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }
}
