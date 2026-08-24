package com.dadcoach.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for EmbeddingRetryProcessor.
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * <p>Key behaviors tested:
 * <ul>
 *   <li>Processing retry queue entries</li>
 *   <li>Handling successful embedding generation</li>
 *   <li>Handling failed embedding generation</li>
 *   <li>Reset stuck processing entries</li>
 *   <li>Cleanup of old entries</li>
 * </ul>
 *
 * @see EmbeddingRetryProcessor
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingRetryProcessor Tests")
class EmbeddingRetryProcessorTest {

    private static final String TEST_CONTENT = "Lucas loves dinosaurs";
    private static final int EMBEDDING_DIMENSION = 1536;

    @Mock
    private EmbeddingRetryQueueService retryQueueService;

    @Mock
    private EmbeddingService embeddingService;

    private EmbeddingRetryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EmbeddingRetryProcessor(retryQueueService, embeddingService);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Process Retry Queue
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Process Retry Queue Tests")
    class ProcessRetryQueueTests {

        @Test
        @DisplayName("Should process entries and record success")
        void shouldProcessEntriesAndRecordSuccess() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            float[] embedding = createTestEmbedding();
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT)).thenReturn(embedding);

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).markProcessing(entry);
            verify(embeddingService).generateEmbedding(TEST_CONTENT);
            verify(retryQueueService).recordSuccess(entry, embedding);
        }

        @Test
        @DisplayName("Should process entries and record failure")
        void shouldProcessEntriesAndRecordFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, "Timed out"));

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).markProcessing(entry);
            verify(retryQueueService).recordFailure(eq(entry), eq("TIMEOUT"), anyString());
        }

        @Test
        @DisplayName("Should reset stuck processing entries before processing")
        void shouldResetStuckProcessingFirst() {
            // Arrange
            when(retryQueueService.resetStuckProcessing()).thenReturn(2);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of());

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).resetStuckProcessing();
        }

        @Test
        @DisplayName("Should do nothing when no entries ready")
        void shouldDoNothingWhenNoEntriesReady() {
            // Arrange
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of());

            // Act
            processor.processRetryQueue();

            // Assert
            verify(embeddingService, never()).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should handle unexpected exceptions")
        void shouldHandleUnexpectedExceptions() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // Act - Should not throw
            assertThatCode(() -> processor.processRetryQueue()).doesNotThrowAnyException();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry), eq("UNEXPECTED_ERROR"), anyString());
        }

        @Test
        @DisplayName("Should process multiple entries")
        void shouldProcessMultipleEntries() {
            // Arrange
            EmbeddingRetryEntry entry1 = new EmbeddingRetryEntry(UUID.randomUUID(), "content1");
            EmbeddingRetryEntry entry2 = new EmbeddingRetryEntry(UUID.randomUUID(), "content2");
            float[] embedding = createTestEmbedding();
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry1, entry2));
            when(embeddingService.generateEmbedding(anyString())).thenReturn(embedding);

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService, times(2)).markProcessing(any());
            verify(retryQueueService, times(2)).recordSuccess(any(), any());
        }

        @Test
        @DisplayName("Should continue processing after one failure")
        void shouldContinueProcessingAfterOneFailure() {
            // Arrange
            EmbeddingRetryEntry entry1 = new EmbeddingRetryEntry(UUID.randomUUID(), "content1");
            EmbeddingRetryEntry entry2 = new EmbeddingRetryEntry(UUID.randomUUID(), "content2");
            float[] embedding = createTestEmbedding();
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry1, entry2));
            when(embeddingService.generateEmbedding("content1"))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, "Timeout"));
            when(embeddingService.generateEmbedding("content2")).thenReturn(embedding);

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry1), anyString(), anyString());
            verify(retryQueueService).recordSuccess(entry2, embedding);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Error Type Handling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Type Handling Tests")
    class ErrorTypeHandlingTests {

        @Test
        @DisplayName("Should handle RATE_LIMIT error")
        void shouldHandleRateLimitError() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.RATE_LIMIT, 429, "Rate limited"));

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry), eq("RATE_LIMIT"), anyString());
        }

        @Test
        @DisplayName("Should handle NETWORK_ERROR")
        void shouldHandleNetworkError() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.NETWORK_ERROR, "Network error"));

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry), eq("NETWORK_ERROR"), anyString());
        }

        @Test
        @DisplayName("Should handle SERVER_ERROR")
        void shouldHandleServerError() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 500, "Server error"));

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry), eq("SERVER_ERROR"), anyString());
        }

        @Test
        @DisplayName("Should handle AUTHENTICATION_ERROR")
        void shouldHandleAuthenticationError() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.AUTHENTICATION_ERROR, 401, "Invalid API key"));

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry), eq("AUTHENTICATION_ERROR"), anyString());
        }

        @Test
        @DisplayName("Should handle CIRCUIT_OPEN")
        void shouldHandleCircuitOpen() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            
            when(retryQueueService.resetStuckProcessing()).thenReturn(0);
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.CIRCUIT_OPEN, "Circuit open"));

            // Act
            processor.processRetryQueue();

            // Assert
            verify(retryQueueService).recordFailure(eq(entry), eq("CIRCUIT_OPEN"), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("Should cleanup old entries")
        void shouldCleanupOldEntries() {
            // Arrange
            when(retryQueueService.cleanupCompleted(7)).thenReturn(5);
            when(retryQueueService.cleanupFailed(30)).thenReturn(3);

            // Act
            processor.cleanupOldEntries();

            // Assert
            verify(retryQueueService).cleanupCompleted(7);
            verify(retryQueueService).cleanupFailed(30);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Manual Trigger
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Manual Trigger Tests")
    class ManualTriggerTests {

        @Test
        @DisplayName("Should support manual trigger")
        void shouldSupportManualTrigger() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(UUID.randomUUID(), TEST_CONTENT);
            float[] embedding = createTestEmbedding();
            
            when(retryQueueService.findReadyForProcessing(anyInt())).thenReturn(List.of(entry));
            when(embeddingService.generateEmbedding(TEST_CONTENT)).thenReturn(embedding);

            // Act
            int processed = processor.triggerProcessing();

            // Assert
            assertThat(processed).isEqualTo(1);
            verify(retryQueueService).recordSuccess(entry, embedding);
        }

        @Test
        @DisplayName("Should support manual cleanup trigger")
        void shouldSupportManualCleanupTrigger() {
            // Arrange
            when(retryQueueService.cleanupCompleted(anyInt())).thenReturn(0);
            when(retryQueueService.cleanupFailed(anyInt())).thenReturn(0);

            // Act
            processor.triggerCleanup();

            // Assert
            verify(retryQueueService).cleanupCompleted(anyInt());
            verify(retryQueueService).cleanupFailed(anyInt());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Metrics Logging
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Metrics Logging Tests")
    class MetricsLoggingTests {

        @Test
        @DisplayName("Should log queue metrics")
        void shouldLogQueueMetrics() {
            // Arrange
            when(retryQueueService.countPending()).thenReturn(5L);
            when(retryQueueService.countPermanentlyFailed()).thenReturn(2L);
            when(retryQueueService.countByStatus(EmbeddingRetryEntry.Status.PROCESSING)).thenReturn(1L);

            // Act - Should not throw
            assertThatCode(() -> processor.logQueueMetrics()).doesNotThrowAnyException();

            // Assert
            verify(retryQueueService).countPending();
            verify(retryQueueService).countPermanentlyFailed();
            verify(retryQueueService).countByStatus(EmbeddingRetryEntry.Status.PROCESSING);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private float[] createTestEmbedding() {
        float[] embedding = new float[EMBEDDING_DIMENSION];
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            embedding[i] = 0.1f + (i * 0.0001f);
        }
        return embedding;
    }
}
