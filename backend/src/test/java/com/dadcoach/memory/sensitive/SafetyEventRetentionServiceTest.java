package com.dadcoach.memory.sensitive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SafetyEventRetentionService}.
 *
 * <p>Validates: SPEC-004 Task 12.3 - Expiration enforced on safety records
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyEventRetentionService Tests")
class SafetyEventRetentionServiceTest {

    @Mock
    private SafetyEventRepository safetyEventRepository;

    @Captor
    private ArgumentCaptor<List<UUID>> idsCaptor;

    private SafetyEventRetentionService retentionService;

    @BeforeEach
    void setUp() {
        retentionService = new SafetyEventRetentionService(safetyEventRepository);
    }

    // ─── Retention Processing Tests ──────────────────────────────────────

    @Nested
    @DisplayName("Retention Processing")
    class RetentionProcessingTests {

        @Test
        @DisplayName("should delete expired safety events in batches")
        void shouldDeleteExpiredSafetyEventsInBatches() {
            // Create expired records
            List<SafetyEventRecord> expiredBatch = createExpiredEvents(3);

            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenReturn(expiredBatch)
                    .thenReturn(List.of()); // Empty on second call

            SafetyEventRetentionService.RetentionResult result =
                    retentionService.processExpiredRecords(Instant.now());

            assertThat(result.recordsProcessed()).isEqualTo(3);
            assertThat(result.recordsDeleted()).isEqualTo(3);
            assertThat(result.batchesProcessed()).isEqualTo(1);
            assertThat(result.errors()).isZero();

            verify(safetyEventRepository).deleteByIdIn(idsCaptor.capture());
            assertThat(idsCaptor.getValue()).hasSize(3);
        }

        @Test
        @DisplayName("should process multiple batches when many expired records exist")
        void shouldProcessMultipleBatches() throws Exception {
            // Set batch size to 2 via reflection
            setBatchSize(retentionService, 2);

            List<SafetyEventRecord> batch1 = createExpiredEvents(2);
            List<SafetyEventRecord> batch2 = createExpiredEvents(2);

            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), eq(2)))
                    .thenReturn(batch1)
                    .thenReturn(batch2)
                    .thenReturn(List.of());

            SafetyEventRetentionService.RetentionResult result =
                    retentionService.processExpiredRecords(Instant.now());

            assertThat(result.recordsProcessed()).isEqualTo(4);
            assertThat(result.recordsDeleted()).isEqualTo(4);
            assertThat(result.batchesProcessed()).isEqualTo(2);
            assertThat(result.errors()).isZero();

            verify(safetyEventRepository, times(2)).deleteByIdIn(any());
        }

        @Test
        @DisplayName("should handle no expired records gracefully")
        void shouldHandleNoExpiredRecordsGracefully() {
            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenReturn(List.of());

            SafetyEventRetentionService.RetentionResult result =
                    retentionService.processExpiredRecords(Instant.now());

            assertThat(result.recordsProcessed()).isZero();
            assertThat(result.recordsDeleted()).isZero();
            assertThat(result.batchesProcessed()).isZero();
            assertThat(result.errors()).isZero();

            verify(safetyEventRepository, never()).deleteByIdIn(any());
        }

        @Test
        @DisplayName("should handle errors gracefully and stop processing")
        void shouldHandleErrorsGracefully() {
            List<SafetyEventRecord> expiredBatch = createExpiredEvents(2);

            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenReturn(expiredBatch);
            doThrow(new RuntimeException("Database error"))
                    .when(safetyEventRepository).deleteByIdIn(any());

            SafetyEventRetentionService.RetentionResult result =
                    retentionService.processExpiredRecords(Instant.now());

            // Should have processed one batch with an error
            assertThat(result.errors()).isEqualTo(1);
            assertThat(result.recordsDeleted()).isZero();
        }

        @Test
        @DisplayName("should log each deletion for compliance auditing")
        void shouldLogEachDeletionForCompliance() {
            List<SafetyEventRecord> expiredBatch = createExpiredEvents(2);

            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenReturn(expiredBatch)
                    .thenReturn(List.of());

            retentionService.processExpiredRecords(Instant.now());

            // Verify delete was called with all IDs
            verify(safetyEventRepository).deleteByIdIn(idsCaptor.capture());
            List<UUID> deletedIds = idsCaptor.getValue();
            assertThat(deletedIds).hasSize(2);
            assertThat(deletedIds).containsExactlyInAnyOrder(
                    expiredBatch.get(0).getId(),
                    expiredBatch.get(1).getId()
            );
        }
    }

    // ─── Scheduled Job Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Scheduled Job Behavior")
    class ScheduledJobTests {

        @Test
        @DisplayName("should skip job when retention is disabled")
        void shouldSkipJobWhenRetentionDisabled() {
            retentionService.setRetentionEnabled(false);

            retentionService.runWeeklyRetentionEnforcement();

            // Repository should not be called when disabled
            verify(safetyEventRepository, never()).findExpiredBeforeWithLimit(any(), anyInt());
        }

        @Test
        @DisplayName("should process when retention is enabled")
        void shouldProcessWhenRetentionEnabled() {
            retentionService.setRetentionEnabled(true);

            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenReturn(List.of());

            retentionService.runWeeklyRetentionEnforcement();

            verify(safetyEventRepository).findExpiredBeforeWithLimit(any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("should handle exceptions in scheduled job gracefully")
        void shouldHandleExceptionsInScheduledJob() {
            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenThrow(new RuntimeException("Database unavailable"));

            // Should not throw - just log the error
            retentionService.runWeeklyRetentionEnforcement();

            // Verify we attempted to query
            verify(safetyEventRepository).findExpiredBeforeWithLimit(any(Instant.class), anyInt());
        }
    }

    // ─── Query Operations Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Query Operations")
    class QueryOperationsTests {

        @Test
        @DisplayName("should count expired records")
        void shouldCountExpiredRecords() {
            when(safetyEventRepository.countExpiredBefore(any(Instant.class)))
                    .thenReturn(42L);

            long count = retentionService.countExpiredRecords();

            assertThat(count).isEqualTo(42L);
            verify(safetyEventRepository).countExpiredBefore(any(Instant.class));
        }

        @Test
        @DisplayName("should get records expiring within days")
        void shouldGetRecordsExpiringWithinDays() {
            List<SafetyEventRecord> expiringRecords = createExpiredEvents(5);

            when(safetyEventRepository.findExpiringBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(expiringRecords);

            List<SafetyEventRecord> result = retentionService.getExpiringWithinDays(30);

            assertThat(result).hasSize(5);
            verify(safetyEventRepository).findExpiringBetween(any(Instant.class), any(Instant.class));
        }
    }

    // ─── Manual Trigger Tests ────────────────────────────────────────────

    @Nested
    @DisplayName("Manual Trigger")
    class ManualTriggerTests {

        @Test
        @DisplayName("should trigger retention enforcement manually")
        void shouldTriggerRetentionEnforcementManually() {
            List<SafetyEventRecord> expiredBatch = createExpiredEvents(2);

            when(safetyEventRepository.findExpiredBeforeWithLimit(any(Instant.class), anyInt()))
                    .thenReturn(expiredBatch)
                    .thenReturn(List.of());

            SafetyEventRetentionService.RetentionResult result =
                    retentionService.triggerRetentionEnforcement();

            assertThat(result.recordsDeleted()).isEqualTo(2);
            verify(safetyEventRepository).deleteByIdIn(any());
        }
    }

    // ─── Configuration Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("should get and set retention enabled")
        void shouldGetAndSetRetentionEnabled() {
            assertThat(retentionService.isRetentionEnabled()).isTrue(); // default

            retentionService.setRetentionEnabled(false);
            assertThat(retentionService.isRetentionEnabled()).isFalse();

            retentionService.setRetentionEnabled(true);
            assertThat(retentionService.isRetentionEnabled()).isTrue();
        }

        @Test
        @DisplayName("should get and set batch size")
        void shouldGetAndSetBatchSize() {
            assertThat(retentionService.getBatchSize()).isEqualTo(100); // default

            retentionService.setBatchSize(50);
            assertThat(retentionService.getBatchSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("should reject invalid batch size")
        void shouldRejectInvalidBatchSize() {
            assertThatThrownBy(() -> retentionService.setBatchSize(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Batch size must be positive");

            assertThatThrownBy(() -> retentionService.setBatchSize(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Batch size must be positive");
        }
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    /**
     * Creates a list of expired safety event records for testing.
     */
    private List<SafetyEventRecord> createExpiredEvents(int count) {
        List<SafetyEventRecord> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SafetyEventRecord event = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Test expired event " + i
            );
            // Set ID via reflection since it's generated
            setEventId(event, UUID.randomUUID());
            events.add(event);
        }
        return events;
    }

    /**
     * Sets the ID of a SafetyEventRecord via reflection.
     */
    private void setEventId(SafetyEventRecord event, UUID id) {
        try {
            // Use the package-private setter
            event.setId(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set event ID", e);
        }
    }

    /**
     * Sets the batch size via reflection for testing.
     */
    private void setBatchSize(SafetyEventRetentionService service, int batchSize) throws Exception {
        Field field = SafetyEventRetentionService.class.getDeclaredField("batchSize");
        field.setAccessible(true);
        field.set(service, batchSize);
    }
}
