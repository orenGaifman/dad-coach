package com.dadcoach.ai.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiTelemetryService behavior including alert detection
 * and async recording.
 */
@ExtendWith(MockitoExtension.class)
class AiTelemetryServiceTest {

    @Mock
    private AiTelemetryRepository repository;

    private AiTelemetryService service;

    @BeforeEach
    void setUp() {
        service = new AiTelemetryService(repository);
    }

    // --- Async recording tests ---

    @Test
    void recordAsync_savesRecordToRepository() {
        AiTelemetryRecord record = createBasicRecord();
        when(repository.save(record)).thenReturn(record);

        CompletableFuture<AiTelemetryRecord> future = service.recordAsync(record);

        assertNotNull(future);
        assertFalse(future.isCompletedExceptionally());
        assertEquals(record, future.join());
        verify(repository).save(record);
    }

    @Test
    void recordAsync_returnsFailedFutureOnException() {
        AiTelemetryRecord record = createBasicRecord();
        when(repository.save(record)).thenThrow(new RuntimeException("DB connection lost"));

        CompletableFuture<AiTelemetryRecord> future = service.recordAsync(record);

        assertTrue(future.isCompletedExceptionally());
    }

    // --- Error rate alert tests ---

    @Test
    void checkErrorRateAlert_returnsFalseWhenNoRecords() {
        Instant now = Instant.now();
        when(repository.countRecordsSince(any())).thenReturn(0L);

        assertFalse(service.checkErrorRateAlert(now));
    }

    @Test
    void checkErrorRateAlert_returnsFalseWhenErrorRateBelow5Percent() {
        Instant now = Instant.now();
        when(repository.countRecordsSince(any())).thenReturn(100L);
        when(repository.countFailedRecordsSince(any())).thenReturn(4L); // 4% < 5%

        assertFalse(service.checkErrorRateAlert(now));
    }

    @Test
    void checkErrorRateAlert_returnsFalseWhenErrorRateExactly5Percent() {
        Instant now = Instant.now();
        when(repository.countRecordsSince(any())).thenReturn(100L);
        when(repository.countFailedRecordsSince(any())).thenReturn(5L); // 5% == 5%

        assertFalse(service.checkErrorRateAlert(now));
    }

    @Test
    void checkErrorRateAlert_returnsTrueWhenErrorRateAbove5Percent() {
        Instant now = Instant.now();
        when(repository.countRecordsSince(any())).thenReturn(100L);
        when(repository.countFailedRecordsSince(any())).thenReturn(6L); // 6% > 5%

        assertTrue(service.checkErrorRateAlert(now));
    }

    @Test
    void checkErrorRateAlert_usesCorrectTimeWindow() {
        Instant now = Instant.parse("2024-06-01T12:00:00Z");
        Instant expectedWindowStart = now.minus(AiTelemetryService.ERROR_RATE_WINDOW);
        when(repository.countRecordsSince(expectedWindowStart)).thenReturn(50L);
        when(repository.countFailedRecordsSince(expectedWindowStart)).thenReturn(10L); // 20% > 5%

        assertTrue(service.checkErrorRateAlert(now));

        verify(repository).countRecordsSince(expectedWindowStart);
        verify(repository).countFailedRecordsSince(expectedWindowStart);
    }

    // --- Latency p95 alert tests ---

    @Test
    void checkLatencyP95Alert_returnsFalseWhenNoRecords() {
        Instant now = Instant.now();
        when(repository.findLatenciesSince(any())).thenReturn(List.of());

        assertFalse(service.checkLatencyP95Alert(now));
    }

    @Test
    void checkLatencyP95Alert_returnsFalseWhenP95BelowThreshold() {
        Instant now = Instant.now();
        // 20 values all below 10000ms
        List<Integer> latencies = List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000);
        when(repository.findLatenciesSince(any())).thenReturn(latencies);

        assertFalse(service.checkLatencyP95Alert(now));
    }

    @Test
    void checkLatencyP95Alert_returnsTrueWhenP95AboveThreshold() {
        Instant now = Instant.now();
        // 20 values where p95 (index 18) = 15000ms > 10000ms threshold
        List<Integer> latencies = List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 15000, 20000);
        when(repository.findLatenciesSince(any())).thenReturn(latencies);

        assertTrue(service.checkLatencyP95Alert(now));
    }

    @Test
    void checkLatencyP95Alert_usesCorrectTimeWindow() {
        Instant now = Instant.parse("2024-06-01T12:00:00Z");
        Instant expectedWindowStart = now.minus(AiTelemetryService.LATENCY_P95_WINDOW);
        when(repository.findLatenciesSince(expectedWindowStart)).thenReturn(List.of(100, 200));

        service.checkLatencyP95Alert(now);

        verify(repository).findLatenciesSince(expectedWindowStart);
    }

    // --- P95 computation tests ---

    @Test
    void computeP95_emptyList_returnsZero() {
        assertEquals(0, AiTelemetryService.computeP95(List.of()));
    }

    @Test
    void computeP95_singleValue_returnsThatValue() {
        assertEquals(5000, AiTelemetryService.computeP95(List.of(5000)));
    }

    @Test
    void computeP95_twoValues_returnsHigher() {
        assertEquals(9000, AiTelemetryService.computeP95(List.of(1000, 9000)));
    }

    @Test
    void computeP95_twentyValues_returnsCorrectPercentile() {
        // 20 values: p95 is index ceil(20*0.95)-1 = ceil(19)-1 = 18 (0-indexed)
        List<Integer> latencies = List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000);
        assertEquals(1900, AiTelemetryService.computeP95(latencies));
    }

    @Test
    void computeP95_hundredValues_returnsCorrectPercentile() {
        // 100 values 1..100: p95 at index ceil(100*0.95)-1 = 94
        List<Integer> latencies = java.util.stream.IntStream.rangeClosed(1, 100)
                .boxed().toList();
        assertEquals(95, AiTelemetryService.computeP95(latencies));
    }

    // --- Helper ---

    private AiTelemetryRecord createBasicRecord() {
        return AiTelemetryRecord.builder()
                .requestId(UUID.randomUUID())
                .fatherId(UUID.randomUUID())
                .interactionType("coaching")
                .modelProvider("openai")
                .modelName("gpt-4o")
                .inputTokens(500)
                .outputTokens(200)
                .totalLatencyMs(1500)
                .validationPassed(true)
                .build();
    }
}
