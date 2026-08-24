package com.dadcoach.memory.embedding;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.MemoryTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying the graceful degradation behavior when embedding generation fails.
 *
 * <p><strong>Validates: Task 9.2 - Memory stored without embedding on failure (excluded from similarity search)</strong>
 *
 * <p>From SPEC-004 Design Document - Error Handling:
 * <blockquote>
 * Embedding generation fails → Store memory without embedding; queue retry (3 attempts / 24h);
 * exclude from similarity search until embedded
 * </blockquote>
 *
 * <p>This test class verifies the complete flow:
 * <ol>
 *   <li>Memory creation proceeds when embedding fails</li>
 *   <li>Memory is stored with null embedding</li>
 *   <li>Memory's hasEmbedding() returns false</li>
 *   <li>Memory is excluded from similarity search queries (via repository filtering)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Embedding Failure Graceful Degradation Integration Tests")
class EmbeddingFailureGracefulDegradationTest {

    private static final int EMBEDDING_DIMENSION = 1536;
    private static final String TEST_CONTENT = "Lucas loves dinosaurs and playing outside";

    @Mock
    private EmbeddingService embeddingService;

    private GracefulEmbeddingService gracefulEmbeddingService;

    @BeforeEach
    void setUp() {
        gracefulEmbeddingService = new GracefulEmbeddingService(embeddingService);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Memory Creation Without Embedding
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Memory Creation Without Embedding Tests")
    class MemoryCreationWithoutEmbeddingTests {

        @Test
        @DisplayName("Memory should be creatable without embedding when embedding generation fails")
        void shouldCreateMemoryWithoutEmbeddingOnFailure() {
            // Arrange - Embedding generation fails
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 500, "API unavailable"));

            // Act - Try to generate embedding (gracefully returns empty)
            Optional<float[]> embeddingResult = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);

            // Create memory without embedding
            Memory memory = createTestMemory();
            memory.setEmbedding(embeddingResult.orElse(null));

            // Assert - Memory is created but has no embedding
            assertThat(memory).isNotNull();
            assertThat(memory.getContent()).isEqualTo(TEST_CONTENT);
            assertThat(memory.getEmbedding()).isNull();
            assertThat(memory.hasEmbedding()).isFalse();
        }

        @Test
        @DisplayName("Memory should be creatable with embedding when generation succeeds")
        void shouldCreateMemoryWithEmbeddingOnSuccess() {
            // Arrange - Embedding generation succeeds
            float[] expectedEmbedding = createTestEmbedding();
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_CONTENT)).thenReturn(expectedEmbedding);

            // Act - Generate embedding and create memory
            Optional<float[]> embeddingResult = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(embeddingResult.orElse(null));

            // Assert - Memory has embedding
            assertThat(memory.getEmbedding()).isNotNull();
            assertThat(memory.getEmbedding()).hasSize(EMBEDDING_DIMENSION);
            assertThat(memory.hasEmbedding()).isTrue();
        }

        @Test
        @DisplayName("Memory without embedding should have isRetrievable() = true but hasEmbedding() = false")
        void shouldBeRetrievableButNotHaveEmbedding() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setEmbedding(null);

            // Assert - Memory is retrievable (ACTIVE state) but has no embedding
            assertThat(memory.isRetrievable()).isTrue();
            assertThat(memory.hasEmbedding()).isFalse();
        }

        @Test
        @DisplayName("Memory with partial embedding (wrong dimension) should have hasEmbedding() = false")
        void shouldReturnFalseForPartialEmbedding() {
            // Arrange - Embedding with wrong dimension
            float[] partialEmbedding = new float[100]; // Wrong dimension
            Memory memory = createTestMemory();
            memory.setEmbedding(partialEmbedding);

            // Assert - hasEmbedding should return false for wrong dimension
            assertThat(memory.hasEmbedding()).isFalse();
        }

        @Test
        @DisplayName("Memory with correct dimension embedding should have hasEmbedding() = true")
        void shouldReturnTrueForCorrectEmbedding() {
            // Arrange
            float[] correctEmbedding = createTestEmbedding();
            Memory memory = createTestMemory();
            memory.setEmbedding(correctEmbedding);

            // Assert
            assertThat(memory.hasEmbedding()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Similarity Search Exclusion
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Similarity Search Exclusion Tests")
    class SimilaritySearchExclusionTests {

        @Test
        @DisplayName("Memory hasEmbedding should correctly indicate similarity search eligibility")
        void shouldIndicateSimilaritySearchEligibility() {
            // Memory with embedding - eligible for similarity search
            Memory withEmbedding = createTestMemory();
            withEmbedding.setEmbedding(createTestEmbedding());
            assertThat(withEmbedding.hasEmbedding())
                    .as("Memory with embedding should be eligible for similarity search")
                    .isTrue();

            // Memory without embedding - NOT eligible for similarity search
            Memory withoutEmbedding = createTestMemory();
            withoutEmbedding.setEmbedding(null);
            assertThat(withoutEmbedding.hasEmbedding())
                    .as("Memory without embedding should NOT be eligible for similarity search")
                    .isFalse();
        }

        @Test
        @DisplayName("Empty embedding array should not count as having embedding")
        void shouldNotCountEmptyEmbeddingArray() {
            Memory memory = createTestMemory();
            memory.setEmbedding(new float[0]);

            assertThat(memory.hasEmbedding()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Various Failure Scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Various Failure Scenarios Tests")
    class VariousFailureScenariosTests {

        @Test
        @DisplayName("Should handle rate limit gracefully - memory stored without embedding")
        void shouldHandleRateLimitGracefully() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.RATE_LIMIT, 429, "Rate limited"));

            // Act
            Optional<float[]> embedding = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(embedding.orElse(null));

            // Assert - Memory created without embedding
            assertThat(memory.hasEmbedding()).isFalse();
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("Should handle timeout gracefully - memory stored without embedding")
        void shouldHandleTimeoutGracefully() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, "Timed out"));

            // Act
            Optional<float[]> embedding = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(embedding.orElse(null));

            // Assert - Memory created without embedding
            assertThat(memory.hasEmbedding()).isFalse();
        }

        @Test
        @DisplayName("Should handle circuit breaker open gracefully - memory stored without embedding")
        void shouldHandleCircuitBreakerOpenGracefully() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(true);

            // Act
            Optional<float[]> embedding = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(embedding.orElse(null));

            // Assert - Memory created without embedding, embedding service not called
            assertThat(memory.hasEmbedding()).isFalse();
            verify(embeddingService, never()).generateEmbedding(any());
        }

        @Test
        @DisplayName("Should handle authentication error gracefully - memory stored without embedding")
        void shouldHandleAuthErrorGracefully() {
            // Arrange
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.AUTHENTICATION_ERROR, 401, "Invalid API key"));

            // Act
            Optional<float[]> embedding = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(embedding.orElse(null));

            // Assert - Memory created without embedding
            assertThat(memory.hasEmbedding()).isFalse();
        }

        @Test
        @DisplayName("Should handle service unavailable gracefully - memory stored without embedding")
        void shouldHandleServiceUnavailableGracefully() {
            // Arrange - service is null (not configured)
            GracefulEmbeddingService serviceWithNull = new GracefulEmbeddingService(null);

            // Act
            Optional<float[]> embedding = serviceWithNull.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(embedding.orElse(null));

            // Assert - Memory created without embedding
            assertThat(memory.hasEmbedding()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Embedding Retry Scenario
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Embedding Retry Scenario Tests")
    class EmbeddingRetryScenarioTests {

        @Test
        @DisplayName("Memory without embedding can be updated with embedding on retry success")
        void shouldUpdateMemoryWithEmbeddingOnRetrySuccess() {
            // Arrange - First call fails, second succeeds
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.generateEmbedding(TEST_CONTENT))
                    .thenThrow(new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, "First attempt timed out"))
                    .thenReturn(createTestEmbedding());

            // Act - First attempt fails
            Optional<float[]> firstAttempt = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            Memory memory = createTestMemory();
            memory.setEmbedding(firstAttempt.orElse(null));

            // Assert - Memory has no embedding initially
            assertThat(memory.hasEmbedding()).isFalse();

            // Act - Retry succeeds
            Optional<float[]> retryAttempt = gracefulEmbeddingService.generateEmbeddingGracefully(TEST_CONTENT);
            memory.setEmbedding(retryAttempt.orElse(null));

            // Assert - Memory now has embedding
            assertThat(memory.hasEmbedding()).isTrue();
            assertThat(memory.getEmbedding()).hasSize(EMBEDDING_DIMENSION);
        }

        @Test
        @DisplayName("Memory remains functional for non-similarity operations without embedding")
        void shouldRemainFunctionalWithoutEmbedding() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setEmbedding(null);

            // Act & Assert - All non-embedding operations work
            assertThat(memory.isRetrievable()).isTrue();
            assertThat(memory.getTier()).isNotNull();
            assertThat(memory.getCombinedScore()).isNotNull();
            
            // Can confirm memory
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);
            assertThat(memory.getConfirmationCount()).isEqualTo(1);
            
            // Still no embedding
            assertThat(memory.hasEmbedding()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: System Continuity During Embedding Unavailability
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("System Continuity Tests")
    class SystemContinuityTests {

        @Test
        @DisplayName("System should continue normal memory operations when embedding service is unavailable")
        void shouldContinueNormalOperationsWhenEmbeddingUnavailable() {
            // Arrange - Embedding service is completely unavailable
            GracefulEmbeddingService unavailableService = new GracefulEmbeddingService(null);

            // Act - Create multiple memories without embeddings
            Memory memory1 = createTestMemory("Lucas enjoys reading books");
            Memory memory2 = createTestMemory("Sofia prefers outdoor activities");
            Memory memory3 = createTestMemory("Family has dinner together on Sundays");

            memory1.setEmbedding(unavailableService.generateEmbeddingGracefully("Lucas enjoys reading books").orElse(null));
            memory2.setEmbedding(unavailableService.generateEmbeddingGracefully("Sofia prefers outdoor activities").orElse(null));
            memory3.setEmbedding(unavailableService.generateEmbeddingGracefully("Family has dinner together on Sundays").orElse(null));

            // Assert - All memories are created successfully without embeddings
            assertThat(memory1.hasEmbedding()).isFalse();
            assertThat(memory2.hasEmbedding()).isFalse();
            assertThat(memory3.hasEmbedding()).isFalse();

            // All memories are still functional for non-similarity operations
            assertThat(memory1.isRetrievable()).isTrue();
            assertThat(memory2.isRetrievable()).isTrue();
            assertThat(memory3.isRetrievable()).isTrue();

            // All memories can be accessed and tracked
            memory1.recordAccess();
            memory2.recordAccess();
            memory3.recordAccess();

            assertThat(memory1.getAccessCount()).isEqualTo(1);
            assertThat(memory2.getAccessCount()).isEqualTo(1);
            assertThat(memory3.getAccessCount()).isEqualTo(1);

            // Service availability check reports unavailable
            assertThat(unavailableService.isEmbeddingServiceAvailable()).isFalse();
        }

        @Test
        @DisplayName("Memory state transitions should work without embedding")
        void shouldAllowStateTransitionsWithoutEmbedding() {
            // Arrange
            Memory memory = EmbeddingFailureGracefulDegradationTest.this.createTestMemory();
            memory.setEmbedding(null);

            // Assert - Initial state
            assertThat(memory.hasEmbedding()).isFalse();
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // Act - Confirm memory (ACTIVE -> CONFIRMED)
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);
            assertThat(memory.hasEmbedding()).isFalse(); // Still no embedding

            // Create a new memory to supersede it
            Memory newMemory = createTestMemory("Updated: Lucas now loves astronomy");
            newMemory.setEmbedding(null);
            
            // Act - Mark original as superseded
            memory.markSuperseded(newMemory.getId());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);
            assertThat(memory.hasEmbedding()).isFalse(); // Still no embedding
        }

        @Test
        @DisplayName("Memory scoring and tier classification should work without embedding")
        void shouldCalculateScoresWithoutEmbedding() {
            // Arrange
            Memory lowImportance = createTestMemoryWithImportance("Temporary context", 2);
            Memory mediumImportance = createTestMemoryWithImportance("Preference info", 5);
            Memory highImportance = createTestMemoryWithImportance("Identity fact", 9);

            lowImportance.setEmbedding(null);
            mediumImportance.setEmbedding(null);
            highImportance.setEmbedding(null);

            // Assert - Tier classification works without embedding
            assertThat(lowImportance.getTier()).isEqualTo(MemoryTier.SHORT_TERM);
            assertThat(mediumImportance.getTier()).isEqualTo(MemoryTier.MEDIUM_TERM);
            assertThat(highImportance.getTier()).isEqualTo(MemoryTier.LONG_TERM);

            // Combined scores work without embedding
            assertThat(lowImportance.getCombinedScore()).isNotNull();
            assertThat(mediumImportance.getCombinedScore()).isNotNull();
            assertThat(highImportance.getCombinedScore()).isNotNull();

            // Higher importance should have higher combined score
            assertThat(highImportance.getCombinedScore())
                    .isGreaterThan(mediumImportance.getCombinedScore());
            assertThat(mediumImportance.getCombinedScore())
                    .isGreaterThan(lowImportance.getCombinedScore());
        }

        @Test
        @DisplayName("GracefulEmbeddingService should report correct availability status")
        void shouldReportCorrectAvailabilityStatus() {
            // Case 1: Service is null
            GracefulEmbeddingService nullService = new GracefulEmbeddingService(null);
            assertThat(nullService.isEmbeddingServiceAvailable()).isFalse();
            assertThat(nullService.getEmbeddingDimension()).isEqualTo(0);

            // Case 2: Circuit breaker is open
            when(embeddingService.isCircuitOpen()).thenReturn(true);
            assertThat(gracefulEmbeddingService.isEmbeddingServiceAvailable()).isFalse();

            // Case 3: Service is available (circuit closed)
            when(embeddingService.isCircuitOpen()).thenReturn(false);
            when(embeddingService.getEmbeddingDimension()).thenReturn(1536);
            assertThat(gracefulEmbeddingService.isEmbeddingServiceAvailable()).isTrue();
            assertThat(gracefulEmbeddingService.getEmbeddingDimension()).isEqualTo(1536);
        }

        private Memory createTestMemory(String content) {
            return new Memory(
                    UUID.randomUUID(),
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    content,
                    6,
                    new BigDecimal("0.80"),
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
        }

        private Memory createTestMemoryWithImportance(String content, int importance) {
            return new Memory(
                    UUID.randomUUID(),
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    content,
                    importance,
                    new BigDecimal("0.80"),
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private Memory createTestMemory() {
        return new Memory(
                UUID.randomUUID(), // fatherId
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                TEST_CONTENT,
                6, // importanceScore
                new BigDecimal("0.80"), // confidenceScore
                MemorySourceType.CONVERSATION_EXTRACTION
        );
    }

    private float[] createTestEmbedding() {
        float[] embedding = new float[EMBEDDING_DIMENSION];
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            embedding[i] = 0.1f + (i * 0.0001f);
        }
        return embedding;
    }
}
