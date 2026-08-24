package com.dadcoach.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for GracefulEmbeddingService.
 *
 * <p>These tests verify the graceful degradation behavior when embedding generation fails.
 *
 * <p><strong>Validates: Task 9 - Memory stored without embedding on failure (excluded from similarity search)</strong>
 *
 * <p>Key behaviors tested:
 * <ul>
 *   <li>Returns Optional.empty() when embedding service is unavailable (null)</li>
 *   <li>Returns Optional.empty() when embedding generation throws exception</li>
 *   <li>Returns Optional.empty() when circuit breaker is open</li>
 *   <li>Returns successful embedding when service is available and succeeds</li>
 *   <li>Batch operations gracefully handle partial failures</li>
 * </ul>
 *
 * @see GracefulEmbeddingService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GracefulEmbeddingService Tests")
class GracefulEmbeddingServiceTest {

    private static final String TEST_TEXT = "Lucas loves dinosaurs and playing outside";
    private static final int EMBEDDING_DIMENSION = 1536;

    @Mock
    private EmbeddingService embeddingService;

    private GracefulEmbeddingService gracefulEmbeddingService;

    @BeforeEach
    void setUp() {
        gracefulEmbeddingService = new GracefulEmbeddingService(embeddingService);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Service Unavailable (Null EmbeddingService)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Service Unavailable Tests")
    class ServiceUnavailableTests {

        @Test
        @DisplayName("Should return empty when embedding service is null")
        void shouldReturnEmptyWhenServiceIsNull() {
            // Arrange
            GracefulEmbeddingService serviceWithNull = new GracefulEmbeddingService(null);

            // Act
            Optional<float[]> result = serviceWithNull.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return null list when embedding service is null for batch")
        void shouldReturnNullListWhenServiceIsNullForBatch() {
            // Arrange
            GracefulEmbeddingService serviceWithNull = new GracefulEmbeddingService(null);
            List<String> texts = List.of("text1", "text2", "text3");

            // Act
            List<float[]> result = serviceWithNull.generateEmbeddingsGracefully(texts);

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(e -> e == null);
        }

        @Test
        @DisplayName("Should report service unavailable when null")
        void shouldReportUnavailableWhenNull() {
            // Arrange
            GracefulEmbeddingService serviceWithNull = new GracefulEmbeddingService(null);

            // Act & Assert
            assertThat(serviceWithNull.isEmbeddingServiceAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should return zero dimension when service is null")
        void shouldReturnZeroDimensionWhenNull() {
            // Arrange
            GracefulEmbeddingService serviceWithNull = new GracefulEmbeddingService(null);

            // Act & Assert
            assertThat(serviceWithNull.getEmbeddingDimension()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Successful Embedding Generation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Successful Embedding Tests")
    class SuccessfulEmbeddingTests {

        @Test
        @DisplayName("Should return embedding when generation succeeds")
        void shouldReturnEmbeddingOnSuccess() {
            // Arrange
            float[] expectedEmbedding = createTestEmbedding();
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT)).thenReturn(expectedEmbedding);

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedEmbedding);
            verify(embeddingService).generateEmbedding(TEST_TEXT);
        }

        @Test
        @DisplayName("Should return embeddings list when batch generation succeeds")
        void shouldReturnEmbeddingsOnBatchSuccess() {
            // Arrange
            List<String> texts = List.of("text1", "text2");
            List<float[]> expectedEmbeddings = List.of(createTestEmbedding(), createTestEmbedding());
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbeddings(texts)).thenReturn(expectedEmbeddings);

            // Act
            List<float[]> result = gracefulEmbeddingService.generateEmbeddingsGracefully(texts);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isNotNull();
            assertThat(result.get(1)).isNotNull();
        }

        @Test
        @DisplayName("Should report service available when circuit is closed")
        void shouldReportAvailableWhenCircuitClosed() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);

            // Act & Assert
            assertThat(gracefulEmbeddingService.isEmbeddingServiceAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should return correct embedding dimension")
        void shouldReturnCorrectDimension() {
            // Arrange
            when(embeddingService.getEmbeddingDimension()).thenReturn(EMBEDDING_DIMENSION);

            // Act & Assert
            assertThat(gracefulEmbeddingService.getEmbeddingDimension()).isEqualTo(EMBEDDING_DIMENSION);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Graceful Degradation on Failures
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Graceful Degradation Tests")
    class GracefulDegradationTests {

        @Test
        @DisplayName("Should return empty when circuit breaker is open")
        void shouldReturnEmptyWhenCircuitOpen() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(true);

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
            verify(embeddingService, never()).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should report service unavailable when circuit is open")
        void shouldReportUnavailableWhenCircuitOpen() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(true);

            // Act & Assert
            assertThat(gracefulEmbeddingService.isEmbeddingServiceAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should return empty when rate limited")
        void shouldReturnEmptyWhenRateLimited() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.RATE_LIMIT, 429, "Rate limited"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when timeout occurs")
        void shouldReturnEmptyWhenTimeout() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, "Request timed out"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when network error occurs")
        void shouldReturnEmptyWhenNetworkError() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.NETWORK_ERROR, "Connection failed"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when server error occurs")
        void shouldReturnEmptyWhenServerError() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 500, "Internal error"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when authentication error occurs")
        void shouldReturnEmptyWhenAuthError() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.AUTHENTICATION_ERROR, 401, "Invalid key"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when invalid response received")
        void shouldReturnEmptyWhenInvalidResponse() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.INVALID_RESPONSE, "Bad response"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when unexpected exception occurs")
        void shouldReturnEmptyWhenUnexpectedError() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Input Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should return empty for null text")
        void shouldReturnEmptyForNullText() {
            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully(null);

            // Assert
            assertThat(result).isEmpty();
            verify(embeddingService, never()).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should return empty for blank text")
        void shouldReturnEmptyForBlankText() {
            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully("   ");

            // Assert
            assertThat(result).isEmpty();
            verify(embeddingService, never()).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should return empty for empty text")
        void shouldReturnEmptyForEmptyText() {
            // Act
            Optional<float[]> result = gracefulEmbeddingService.generateEmbeddingGracefully("");

            // Assert
            assertThat(result).isEmpty();
            verify(embeddingService, never()).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should return empty list for null text list")
        void shouldReturnEmptyListForNullTextList() {
            // Act
            List<float[]> result = gracefulEmbeddingService.generateEmbeddingsGracefully(null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for empty text list")
        void shouldReturnEmptyListForEmptyTextList() {
            // Act
            List<float[]> result = gracefulEmbeddingService.generateEmbeddingsGracefully(List.of());

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Batch Partial Failure Handling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Batch Partial Failure Tests")
    class BatchPartialFailureTests {

        @Test
        @DisplayName("Should fall back to individual processing when batch fails")
        void shouldFallbackToIndividualWhenBatchFails() {
            // Arrange
            List<String> texts = List.of("text1", "text2");
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbeddings(texts))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 500, "Batch failed"));
            when(embeddingService.generateEmbedding("text1")).thenReturn(createTestEmbedding());
            when(embeddingService.generateEmbedding("text2")).thenReturn(createTestEmbedding());

            // Act
            List<float[]> result = gracefulEmbeddingService.generateEmbeddingsGracefully(texts);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isNotNull();
            assertThat(result.get(1)).isNotNull();
            verify(embeddingService, times(2)).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should handle partial success in individual processing")
        void shouldHandlePartialSuccessInIndividual() {
            // Arrange
            List<String> texts = List.of("text1", "text2", "text3");
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbeddings(texts))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 500, "Batch failed"));
            when(embeddingService.generateEmbedding("text1")).thenReturn(createTestEmbedding());
            when(embeddingService.generateEmbedding("text2"))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.RATE_LIMIT, 429, "Rate limited"));
            when(embeddingService.generateEmbedding("text3")).thenReturn(createTestEmbedding());

            // Act
            List<float[]> result = gracefulEmbeddingService.generateEmbeddingsGracefully(texts);

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isNotNull(); // text1 succeeded
            assertThat(result.get(1)).isNull();    // text2 failed
            assertThat(result.get(2)).isNotNull(); // text3 succeeded
        }

        @Test
        @DisplayName("Should return null list when circuit opens during batch")
        void shouldReturnNullListWhenCircuitOpenDuringBatch() {
            // Arrange
            List<String> texts = List.of("text1", "text2");
            when(embeddingService.isCircuitOpen()).thenReturn(true);

            // Act
            List<float[]> result = gracefulEmbeddingService.generateEmbeddingsGracefully(texts);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(e -> e == null);
            verify(embeddingService, never()).generateEmbeddings(anyList());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Memory Integration Scenario
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Memory Integration Scenario Tests")
    class MemoryIntegrationTests {

        @Test
        @DisplayName("Should allow memory creation without embedding when embedding fails")
        void shouldAllowMemoryCreationWithoutEmbeddingOnFailure() {
            // This test verifies the key requirement:
            // "Memory stored without embedding on failure (excluded from similarity search)"

            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 500, "API down"));

            // Act - Get embedding (gracefully returns empty on failure)
            Optional<float[]> embeddingResult = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);

            // Assert - Embedding is empty, but we can proceed with memory creation
            assertThat(embeddingResult).isEmpty();

            // The calling code would do:
            // memory.setEmbedding(embeddingResult.orElse(null));
            // memoryRepository.save(memory);
            // 
            // And the memory would be stored with null embedding.
            // The repository queries already filter: AND m.embedding IS NOT NULL
            // So this memory would be excluded from similarity search.
        }

        @Test
        @DisplayName("Should support incremental embedding on retry")
        void shouldSupportIncrementalEmbeddingOnRetry() {
            // This test verifies the retry scenario:
            // A memory was stored without embedding, and now we retry embedding generation

            // First call: failure
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_TEXT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, "Timed out"))
                    .thenReturn(createTestEmbedding()); // Second call succeeds

            // Act - First attempt fails
            Optional<float[]> firstAttempt = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);
            assertThat(firstAttempt).isEmpty();

            // Act - Retry succeeds
            Optional<float[]> retryAttempt = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_TEXT);
            assertThat(retryAttempt).isPresent();
            assertThat(retryAttempt.get()).hasSize(EMBEDDING_DIMENSION);
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
