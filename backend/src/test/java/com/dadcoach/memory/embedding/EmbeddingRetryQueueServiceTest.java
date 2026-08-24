package com.dadcoach.memory.embedding;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemorySubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for EmbeddingRetryQueueService.
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * <p>Key behaviors tested:
 * <ul>
 *   <li>Queuing memories for retry</li>
 *   <li>Finding entries ready for processing</li>
 *   <li>Recording success and failure results</li>
 *   <li>Cleanup of old entries</li>
 *   <li>Metrics and monitoring</li>
 * </ul>
 *
 * @see EmbeddingRetryQueueService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingRetryQueueService Tests")
class EmbeddingRetryQueueServiceTest {

    private static final UUID TEST_MEMORY_ID = UUID.randomUUID();
    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final String TEST_CONTENT = "Lucas loves dinosaurs";

    @Mock
    private EmbeddingRetryRepository retryRepository;

    @Mock
    private MemoryRepository memoryRepository;

    private EmbeddingRetryQueueService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingRetryQueueService(retryRepository, memoryRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Queue for Retry
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Queue for Retry Tests")
    class QueueForRetryTests {

        @Test
        @DisplayName("Should create new entry when queuing memory")
        void shouldCreateNewEntryWhenQueuing() {
            // Arrange
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.empty());
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            EmbeddingRetryEntry result = service.queueForRetry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
            assertThat(result.getContent()).isEqualTo(TEST_CONTENT);
            assertThat(result.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);

            verify(retryRepository).save(any(EmbeddingRetryEntry.class));
        }

        @Test
        @DisplayName("Should not create duplicate when already queued")
        void shouldNotCreateDuplicateWhenAlreadyQueued() {
            // Arrange
            EmbeddingRetryEntry existing = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.of(existing));

            // Act
            EmbeddingRetryEntry result = service.queueForRetry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(result).isSameAs(existing);
            verify(retryRepository, never()).save(any()); // Should not save again
        }

        @Test
        @DisplayName("Should not re-queue completed entry")
        void shouldNotRequeueCompletedEntry() {
            // Arrange
            EmbeddingRetryEntry completed = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            completed.markCompleted();
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.of(completed));

            // Act
            EmbeddingRetryEntry result = service.queueForRetry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(result.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.COMPLETED);
            verify(retryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not re-queue permanently failed entry")
        void shouldNotRequeuePermanentlyFailedEntry() {
            // Arrange
            EmbeddingRetryEntry failed = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            for (int i = 0; i < 3; i++) {
                failed.recordFailure("ERROR", "Fail");
            }
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.of(failed));

            // Act
            EmbeddingRetryEntry result = service.queueForRetry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(result.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED);
            verify(retryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should queue using memory lookup")
        void shouldQueueUsingMemoryLookup() {
            // Arrange
            Memory memory = createTestMemory();
            when(memoryRepository.findById(TEST_MEMORY_ID)).thenReturn(Optional.of(memory));
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.empty());
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            Optional<EmbeddingRetryEntry> result = service.queueForRetry(TEST_MEMORY_ID);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getContent()).isEqualTo(TEST_CONTENT);
        }

        @Test
        @DisplayName("Should return empty when memory not found")
        void shouldReturnEmptyWhenMemoryNotFound() {
            // Arrange
            when(memoryRepository.findById(TEST_MEMORY_ID)).thenReturn(Optional.empty());

            // Act
            Optional<EmbeddingRetryEntry> result = service.queueForRetry(TEST_MEMORY_ID);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should not queue memory that already has embedding")
        void shouldNotQueueMemoryWithEmbedding() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setEmbedding(new float[1536]); // Has embedding
            when(memoryRepository.findById(TEST_MEMORY_ID)).thenReturn(Optional.of(memory));

            // Act
            Optional<EmbeddingRetryEntry> result = service.queueForRetry(TEST_MEMORY_ID);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Find Ready for Processing
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Find Ready for Processing Tests")
    class FindReadyForProcessingTests {

        @Test
        @DisplayName("Should find entries ready for processing")
        void shouldFindEntriesReadyForProcessing() {
            // Arrange
            EmbeddingRetryEntry entry1 = new EmbeddingRetryEntry(UUID.randomUUID(), "content1");
            EmbeddingRetryEntry entry2 = new EmbeddingRetryEntry(UUID.randomUUID(), "content2");
            when(retryRepository.findReadyForProcessingNative(any(Instant.class), eq(10)))
                    .thenReturn(List.of(entry1, entry2));

            // Act
            List<EmbeddingRetryEntry> result = service.findReadyForProcessing(10);

            // Assert
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should use default batch size")
        void shouldUseDefaultBatchSize() {
            // Arrange
            when(retryRepository.findReadyForProcessingNative(any(Instant.class), 
                    eq(EmbeddingRetryQueueService.DEFAULT_BATCH_SIZE)))
                    .thenReturn(List.of());

            // Act
            service.findReadyForProcessing();

            // Assert
            verify(retryRepository).findReadyForProcessingNative(
                    any(Instant.class), 
                    eq(EmbeddingRetryQueueService.DEFAULT_BATCH_SIZE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Record Success
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Record Success Tests")
    class RecordSuccessTests {

        @Test
        @DisplayName("Should update memory with embedding on success")
        void shouldUpdateMemoryWithEmbeddingOnSuccess() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            Memory memory = createTestMemory();
            float[] embedding = new float[1536];
            
            when(memoryRepository.findById(TEST_MEMORY_ID)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            service.recordSuccess(entry, embedding);

            // Assert
            verify(memoryRepository).save(argThat(m -> m.getEmbedding() == embedding));
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.COMPLETED);
        }

        @Test
        @DisplayName("Should mark entry as completed on success")
        void shouldMarkEntryAsCompletedOnSuccess() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            when(memoryRepository.findById(TEST_MEMORY_ID)).thenReturn(Optional.empty());
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            service.recordSuccess(entry, new float[1536]);

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.COMPLETED);
            verify(retryRepository).save(entry);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Record Failure
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Record Failure Tests")
    class RecordFailureTests {

        @Test
        @DisplayName("Should record failure and schedule retry")
        void shouldRecordFailureAndScheduleRetry() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            service.recordFailure(entry, "TIMEOUT", "Request timed out");

            // Assert
            assertThat(entry.getAttemptCount()).isEqualTo(1);
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);
            assertThat(entry.getNextAttemptAt()).isNotNull();
            verify(retryRepository).save(entry);
        }

        @Test
        @DisplayName("Should mark as permanently failed after max attempts")
        void shouldMarkAsPermanentlyFailedAfterMaxAttempts() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.recordFailure("ERROR", "Fail 1");
            entry.recordFailure("ERROR", "Fail 2");
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            service.recordFailure(entry, "ERROR", "Fail 3");

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Reset Stuck Processing
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reset Stuck Processing Tests")
    class ResetStuckProcessingTests {

        @Test
        @DisplayName("Should reset stuck processing entries")
        void shouldResetStuckProcessingEntries() {
            // Arrange
            EmbeddingRetryEntry stuck = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            stuck.markProcessing();
            stuck.setUpdatedAt(Instant.now().minusSeconds(600)); // 10 minutes ago

            when(retryRepository.findStuckProcessing(
                    eq(EmbeddingRetryEntry.Status.PROCESSING), any(Instant.class)))
                    .thenReturn(List.of(stuck));
            when(retryRepository.save(any(EmbeddingRetryEntry.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            int count = service.resetStuckProcessing();

            // Assert
            assertThat(count).isEqualTo(1);
            assertThat(stuck.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);
        }

        @Test
        @DisplayName("Should return 0 when no stuck entries")
        void shouldReturn0WhenNoStuckEntries() {
            // Arrange
            when(retryRepository.findStuckProcessing(any(), any()))
                    .thenReturn(List.of());

            // Act
            int count = service.resetStuckProcessing();

            // Assert
            assertThat(count).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("Should cleanup old completed entries")
        void shouldCleanupOldCompletedEntries() {
            // Arrange
            when(retryRepository.deleteCompletedOlderThan(
                    eq(EmbeddingRetryEntry.Status.COMPLETED), any(Instant.class)))
                    .thenReturn(5);

            // Act
            int deleted = service.cleanupCompleted(7);

            // Assert
            assertThat(deleted).isEqualTo(5);
        }

        @Test
        @DisplayName("Should cleanup old failed entries")
        void shouldCleanupOldFailedEntries() {
            // Arrange
            when(retryRepository.deleteFailedOlderThan(
                    eq(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED), any(Instant.class)))
                    .thenReturn(3);

            // Act
            int deleted = service.cleanupFailed(30);

            // Assert
            assertThat(deleted).isEqualTo(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Metrics Tests")
    class MetricsTests {

        @Test
        @DisplayName("Should count pending entries")
        void shouldCountPendingEntries() {
            // Arrange
            when(retryRepository.countByStatus(EmbeddingRetryEntry.Status.PENDING))
                    .thenReturn(10L);

            // Act
            long count = service.countPending();

            // Assert
            assertThat(count).isEqualTo(10);
        }

        @Test
        @DisplayName("Should count permanently failed entries")
        void shouldCountPermanentlyFailedEntries() {
            // Arrange
            when(retryRepository.countByStatus(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED))
                    .thenReturn(5L);

            // Act
            long count = service.countPermanentlyFailed();

            // Assert
            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Should check for pending retry")
        void shouldCheckForPendingRetry() {
            // Arrange
            EmbeddingRetryEntry pending = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.of(pending));

            // Act
            boolean hasPending = service.hasPendingRetry(TEST_MEMORY_ID);

            // Assert
            assertThat(hasPending).isTrue();
        }

        @Test
        @DisplayName("Should return false for no pending retry")
        void shouldReturnFalseForNoPendingRetry() {
            // Arrange
            when(retryRepository.findByMemoryId(TEST_MEMORY_ID)).thenReturn(Optional.empty());

            // Act
            boolean hasPending = service.hasPendingRetry(TEST_MEMORY_ID);

            // Assert
            assertThat(hasPending).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private Memory createTestMemory() {
        Memory memory = new Memory(
                TEST_FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                TEST_CONTENT,
                6,
                new BigDecimal("0.80"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(TEST_MEMORY_ID);
        return memory;
    }
}
