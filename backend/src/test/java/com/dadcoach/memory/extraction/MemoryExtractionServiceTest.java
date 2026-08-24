package com.dadcoach.memory.extraction;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryExtractionService}.
 *
 * <p>These tests verify the async processing behavior defined in SPEC-004:
 * <ul>
 *   <li>AD-2: Memory extraction processes asynchronously, never blocking conversation responses</li>
 *   <li>Errors are logged but don't propagate to caller</li>
 *   <li>Extraction starts and completes logging for observability</li>
 *   <li>Task 4.4: Each extracted memory is checked for duplicates before creation</li>
 * </ul>
 *
 * <p><strong>Validates: Design AD-2, Task 4.1, Task 4.4</strong>
 *
 * @see MemoryExtractionService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryExtractionService Tests")
class MemoryExtractionServiceTest {

    // ─── Test Constants ──────────────────────────────────────────────────

    private static final UUID TEST_CONVERSATION_ID = UUID.randomUUID();
    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final String TEST_TRANSCRIPT = "User: Hello, my son Lucas loves dinosaurs.";

    private MemoryExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new MemoryExtractionService();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Async Behavior
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Async Processing Tests")
    class AsyncProcessingTests {

        @Test
        @DisplayName("Should return CompletableFuture immediately (non-blocking)")
        void shouldReturnCompletableFutureImmediately() {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, TEST_TRANSCRIPT);

            // Assert - method returns immediately without blocking
            assertThat(future).isNotNull();
            assertThat(future).isInstanceOf(CompletableFuture.class);
        }

        @Test
        @DisplayName("Should complete future successfully on valid input")
        void shouldCompleteFutureSuccessfullyOnValidInput() throws Exception {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, TEST_TRANSCRIPT);

            // Assert - future should complete without exception
            // Note: In unit tests without Spring @Async, the method executes synchronously
            assertThatCode(() -> future.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should not throw exception to caller even when extraction fails")
        void shouldNotThrowExceptionToCallerWhenExtractionFails() {
            // Arrange - null conversationId would cause validation failure inside processExtraction
            // But the outer try-catch should handle it

            // Act & Assert - caller should not see any exception
            assertThatCode(() -> {
                CompletableFuture<Void> future = extractionService.triggerExtraction(
                        null, TEST_FATHER_ID, TEST_TRANSCRIPT);
                // In synchronous execution (unit test), this should complete without throwing
                future.get(5, TimeUnit.SECONDS);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should complete future even when fatherId is null (error logged, not propagated)")
        void shouldCompleteFutureEvenWhenFatherIdIsNull() {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, null, TEST_TRANSCRIPT);

            // Assert - future completes (error is logged, not thrown)
            assertThatCode(() -> future.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should complete future for empty transcript (skips extraction)")
        void shouldCompleteFutureForEmptyTranscript() throws Exception {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, "");

            // Assert
            assertThat(future.get(5, TimeUnit.SECONDS)).isNull();
        }

        @Test
        @DisplayName("Should complete future for blank transcript (skips extraction)")
        void shouldCompleteFutureForBlankTranscript() throws Exception {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, "   ");

            // Assert
            assertThat(future.get(5, TimeUnit.SECONDS)).isNull();
        }

        @Test
        @DisplayName("Should complete future for null transcript (skips extraction)")
        void shouldCompleteFutureForNullTranscript() throws Exception {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, null);

            // Assert
            assertThat(future.get(5, TimeUnit.SECONDS)).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Error Handling (Graceful Degradation)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should gracefully handle null conversationId (logs error, doesn't propagate)")
        void shouldGracefullyHandleNullConversationId() {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    null, TEST_FATHER_ID, TEST_TRANSCRIPT);

            // Assert - future completes without exception (error logged internally)
            assertThatCode(() -> future.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should gracefully handle null fatherId (logs error, doesn't propagate)")
        void shouldGracefullyHandleNullFatherId() {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, null, TEST_TRANSCRIPT);

            // Assert - future completes without exception
            assertThatCode(() -> future.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should never block caller regardless of extraction outcome")
        void shouldNeverBlockCallerRegardlessOfOutcome() {
            // Multiple scenarios - none should throw to caller
            assertThatCode(() -> {
                // Valid input
                extractionService.triggerExtraction(TEST_CONVERSATION_ID, TEST_FATHER_ID, TEST_TRANSCRIPT)
                        .get(5, TimeUnit.SECONDS);
                
                // Null conversation
                extractionService.triggerExtraction(null, TEST_FATHER_ID, TEST_TRANSCRIPT)
                        .get(5, TimeUnit.SECONDS);
                
                // Null father
                extractionService.triggerExtraction(TEST_CONVERSATION_ID, null, TEST_TRANSCRIPT)
                        .get(5, TimeUnit.SECONDS);
                
                // Empty transcript
                extractionService.triggerExtraction(TEST_CONVERSATION_ID, TEST_FATHER_ID, "")
                        .get(5, TimeUnit.SECONDS);
            }).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Process Extraction (Internal Method)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Process Extraction Tests")
    class ProcessExtractionTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for null conversationId in processExtraction")
        void shouldThrowForNullConversationIdInProcessExtraction() {
            // processExtraction is package-private, test directly
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> extractionService.processExtraction(
                            null, TEST_FATHER_ID, TEST_TRANSCRIPT))
                    .withMessage("conversationId cannot be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null fatherId in processExtraction")
        void shouldThrowForNullFatherIdInProcessExtraction() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> extractionService.processExtraction(
                            TEST_CONVERSATION_ID, null, TEST_TRANSCRIPT))
                    .withMessage("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should skip extraction for null transcript without throwing")
        void shouldSkipExtractionForNullTranscript() {
            // Act & Assert - should not throw
            assertThatCode(() -> extractionService.processExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should skip extraction for empty transcript without throwing")
        void shouldSkipExtractionForEmptyTranscript() {
            assertThatCode(() -> extractionService.processExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, ""))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should skip extraction for blank transcript without throwing")
        void shouldSkipExtractionForBlankTranscript() {
            assertThatCode(() -> extractionService.processExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, "   \t\n   "))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should process valid transcript without throwing")
        void shouldProcessValidTranscriptWithoutThrowing() {
            assertThatCode(() -> extractionService.processExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, TEST_TRANSCRIPT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should process long transcript without throwing")
        void shouldProcessLongTranscriptWithoutThrowing() {
            // Arrange - create a long transcript
            String longTranscript = "User: ".repeat(1000) + TEST_TRANSCRIPT;

            // Act & Assert
            assertThatCode(() -> extractionService.processExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, longTranscript))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Service Annotation Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Annotation Verification Tests")
    class AnnotationVerificationTests {

        @Test
        @DisplayName("Service class should be annotated with @Service")
        void serviceClassShouldBeAnnotatedWithService() {
            assertThat(MemoryExtractionService.class.isAnnotationPresent(
                    org.springframework.stereotype.Service.class))
                    .as("MemoryExtractionService should be annotated with @Service")
                    .isTrue();
        }

        @Test
        @DisplayName("triggerExtraction method should be annotated with @Async")
        void triggerExtractionShouldBeAnnotatedWithAsync() throws NoSuchMethodException {
            var method = MemoryExtractionService.class.getMethod(
                    "triggerExtraction", UUID.class, UUID.class, String.class);
            
            assertThat(method.isAnnotationPresent(org.springframework.scheduling.annotation.Async.class))
                    .as("triggerExtraction should be annotated with @Async")
                    .isTrue();
        }

        @Test
        @DisplayName("@Async annotation should specify sideEffectExecutor")
        void asyncAnnotationShouldSpecifySideEffectExecutor() throws NoSuchMethodException {
            var method = MemoryExtractionService.class.getMethod(
                    "triggerExtraction", UUID.class, UUID.class, String.class);
            var asyncAnnotation = method.getAnnotation(org.springframework.scheduling.annotation.Async.class);
            
            assertThat(asyncAnnotation.value())
                    .as("@Async should specify 'sideEffectExecutor'")
                    .isEqualTo("sideEffectExecutor");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Return Type Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Return Type Tests")
    class ReturnTypeTests {

        @Test
        @DisplayName("Should return CompletableFuture<Void>")
        void shouldReturnCompletableFutureVoid() throws NoSuchMethodException {
            var method = MemoryExtractionService.class.getMethod(
                    "triggerExtraction", UUID.class, UUID.class, String.class);
            
            assertThat(method.getReturnType())
                    .as("Return type should be CompletableFuture")
                    .isEqualTo(CompletableFuture.class);
        }

        @Test
        @DisplayName("Returned future should resolve to null on success")
        void returnedFutureShouldResolveToNullOnSuccess() throws Exception {
            // Act
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    TEST_CONVERSATION_ID, TEST_FATHER_ID, TEST_TRANSCRIPT);

            // Assert
            Void result = future.get(5, TimeUnit.SECONDS);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Returned future should resolve to null even on error (error not propagated)")
        void returnedFutureShouldResolveToNullOnError() throws Exception {
            // Act - with null conversationId which causes internal error
            CompletableFuture<Void> future = extractionService.triggerExtraction(
                    null, TEST_FATHER_ID, TEST_TRANSCRIPT);

            // Assert - still resolves to null (error logged, not thrown)
            Void result = future.get(5, TimeUnit.SECONDS);
            assertThat(result).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Concurrency Safety
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle multiple concurrent extraction requests")
        void shouldHandleMultipleConcurrentExtractionRequests() throws Exception {
            // Arrange
            int requestCount = 10;
            CompletableFuture<?>[] futures = new CompletableFuture[requestCount];

            // Act - trigger multiple extractions
            for (int i = 0; i < requestCount; i++) {
                UUID conversationId = UUID.randomUUID();
                UUID fatherId = UUID.randomUUID();
                futures[i] = extractionService.triggerExtraction(
                        conversationId, fatherId, TEST_TRANSCRIPT + " " + i);
            }

            // Assert - all should complete without exception
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
            assertThatCode(() -> allFutures.get(10, TimeUnit.SECONDS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should be safe to call from multiple threads")
        void shouldBeSafeToCallFromMultipleThreads() throws Exception {
            // Arrange
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();

            // Act - multiple threads calling extraction simultaneously
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                new Thread(() -> {
                    try {
                        startLatch.await();
                        CompletableFuture<Void> future = extractionService.triggerExtraction(
                                UUID.randomUUID(), UUID.randomUUID(), 
                                TEST_TRANSCRIPT + " Thread-" + threadIndex);
                        future.get(5, TimeUnit.SECONDS);
                    } catch (Throwable t) {
                        error.set(t);
                    } finally {
                        completionLatch.countDown();
                    }
                }).start();
            }

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            boolean completed = completionLatch.await(10, TimeUnit.SECONDS);

            // Assert
            assertThat(completed).as("All threads should complete").isTrue();
            assertThat(error.get()).as("No thread should encounter an error").isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Duplicate Detection Integration (Task 4.4)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Duplicate Detection Tests - Task 4.4")
    class DuplicateDetectionTests {

        @Mock
        private DuplicateDetector duplicateDetector;

        @Mock
        private ExtractionValidator extractionValidator;

        @Mock
        private MemoryRepository memoryRepository;

        private MemoryExtractionService serviceWithMocks;

        @BeforeEach
        void setUpMocks() {
            serviceWithMocks = new MemoryExtractionService(
                    duplicateDetector, extractionValidator, memoryRepository);
        }

        @Test
        @DisplayName("Should call DuplicateDetector.checkForDuplicates for each recommendation")
        void shouldCallDuplicateDetectorForEachRecommendation() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            serviceWithMocks.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert
            verify(duplicateDetector).check(
                    eq(fatherId),
                    eq(MemoryCategory.PREFERENCE),
                    eq(MemorySubjectType.CHILD),
                    eq(embedding));
        }

        @Test
        @DisplayName("Should skip memory creation when DUPLICATE detected (similarity > 0.85)")
        void shouldSkipCreationWhenDuplicateDetected() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID existingMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            Memory existingMemory = createTestMemory(existingMemoryId, fatherId);
            
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(new DuplicateResult.Duplicate(existingMemoryId, 0.90));
            when(memoryRepository.findById(existingMemoryId))
                    .thenReturn(Optional.of(existingMemory));
            when(memoryRepository.save(existingMemory)).thenReturn(existingMemory);

            // Act
            Optional<Memory> result = serviceWithMocks.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - no new memory created
            assertThat(result).isEmpty();
            
            // Verify existing memory confidence was boosted
            verify(memoryRepository).findById(existingMemoryId);
            verify(memoryRepository).save(existingMemory);
            
            // Verify no new memory was saved (save called only once for existing memory update)
            verify(memoryRepository, times(1)).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should boost existing memory confidence when DUPLICATE detected")
        void shouldBoostConfidenceWhenDuplicateDetected() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID existingMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            Memory existingMemory = createTestMemory(existingMemoryId, fatherId);
            BigDecimal originalConfidence = existingMemory.getConfidenceScore();
            
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(new DuplicateResult.Duplicate(existingMemoryId, 0.90));
            when(memoryRepository.findById(existingMemoryId))
                    .thenReturn(Optional.of(existingMemory));
            when(memoryRepository.save(existingMemory)).thenReturn(existingMemory);

            // Act
            serviceWithMocks.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - confidence was boosted by 0.20 (from Requirement 5)
            BigDecimal expectedConfidence = originalConfidence.add(new BigDecimal("0.20"));
            assertThat(existingMemory.getConfidenceScore())
                    .isEqualByComparingTo(expectedConfidence.min(BigDecimal.ONE));
        }

        @Test
        @DisplayName("Should supersede existing and create new when POTENTIAL_UPDATE detected (0.70-0.85)")
        void shouldSupersedeWhenPotentialUpdateDetected() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID existingMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas now prefers T-Rex dinosaurs specifically")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.85)
                    .build();

            Memory existingMemory = createTestMemory(existingMemoryId, fatherId);
            
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(new DuplicateResult.PotentialUpdate(existingMemoryId, 0.78));
            when(memoryRepository.findById(existingMemoryId))
                    .thenReturn(Optional.of(existingMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(UUID.randomUUID());
                }
                return m;
            });

            // Act
            Optional<Memory> result = serviceWithMocks.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - new memory was created
            assertThat(result).isPresent();
            assertThat(result.get().getContent()).isEqualTo("Lucas now prefers T-Rex dinosaurs specifically");
            
            // Verify existing memory was marked as superseded
            // findById is called twice: once to supersede, once to update with new memory ID
            verify(memoryRepository, times(2)).findById(existingMemoryId);
            // Three saves: one for superseding, one for new memory, one for updating superseded_by
            verify(memoryRepository, times(3)).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should create new memory when DISTINCT detected (similarity < 0.70)")
        void shouldCreateNewMemoryWhenDistinct() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas started playing soccer")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            Optional<Memory> result = serviceWithMocks.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - new memory was created
            assertThat(result).isPresent();
            assertThat(result.get().getContent()).isEqualTo("Lucas started playing soccer");
            assertThat(result.get().getCategory()).isEqualTo(MemoryCategory.PREFERENCE);
            
            // Verify save was called once for the new memory
            verify(memoryRepository, times(1)).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should fall back to DISTINCT when DuplicateDetector is unavailable")
        void shouldFallBackToDistinctWhenDetectorUnavailable() {
            // Arrange - service without duplicate detector
            MemoryExtractionService serviceNoDetector = new MemoryExtractionService(
                    null, extractionValidator, memoryRepository);

            UUID fatherId = UUID.randomUUID();
            float[] embedding = new float[1536];

            // Act
            DuplicateResult result = serviceNoDetector.checkForDuplicates(
                    fatherId, MemoryCategory.PREFERENCE, MemorySubjectType.CHILD, embedding);

            // Assert - should allow creation
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
        }

        @Test
        @DisplayName("Should process multiple recommendations and check duplicates for each")
        void shouldProcessMultipleRecommendationsWithDuplicateCheck() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            AiMemoryRecommendation rec1 = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            AiMemoryRecommendation rec2 = AiMemoryRecommendation.builder()
                    .content("Father works in tech")
                    .category("IDENTITY")
                    .subjectType("FATHER")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(9)
                    .confidenceScore(0.9)
                    .build();

            List<AiMemoryRecommendation> recommendations = List.of(rec1, rec2);
            List<float[]> embeddings = List.of(new float[1536], new float[1536]);

            when(extractionValidator.validate(any())).thenReturn(ValidationResult.valid());
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            List<Memory> results = serviceWithMocks.processRecommendations(
                    recommendations, fatherId, conversationId, embeddings);

            // Assert - both memories created
            assertThat(results).hasSize(2);
            
            // Verify duplicate detector was called twice
            verify(duplicateDetector, times(2)).check(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should skip invalid recommendations and continue with valid ones")
        void shouldSkipInvalidRecommendationsButContinueWithValid() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            AiMemoryRecommendation invalidRec = AiMemoryRecommendation.builder()
                    .content("") // Invalid - empty content
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            AiMemoryRecommendation validRec = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            List<AiMemoryRecommendation> recommendations = List.of(invalidRec, validRec);
            List<float[]> embeddings = List.of(new float[1536], new float[1536]);

            when(extractionValidator.validate(invalidRec))
                    .thenReturn(ValidationResult.invalid(List.of("Content cannot be empty")));
            when(extractionValidator.validate(validRec))
                    .thenReturn(ValidationResult.valid());
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            List<Memory> results = serviceWithMocks.processRecommendations(
                    recommendations, fatherId, conversationId, embeddings);

            // Assert - only valid memory created
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getContent()).isEqualTo("Lucas loves dinosaurs");
            
            // Verify duplicate detector only called once (for the valid recommendation)
            verify(duplicateDetector, times(1)).check(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return empty list when all recommendations are invalid")
        void shouldReturnEmptyWhenAllInvalid() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            AiMemoryRecommendation invalidRec = AiMemoryRecommendation.builder()
                    .content("")
                    .category("INVALID_CATEGORY")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            List<AiMemoryRecommendation> recommendations = List.of(invalidRec);
            List<float[]> embeddings = List.of(new float[1536]);

            when(extractionValidator.validate(any()))
                    .thenReturn(ValidationResult.invalid(List.of("Content cannot be empty")));

            // Act
            List<Memory> results = serviceWithMocks.processRecommendations(
                    recommendations, fatherId, conversationId, embeddings);

            // Assert
            assertThat(results).isEmpty();
            
            // Verify duplicate detector never called
            verify(duplicateDetector, never()).check(any(), any(), any(), any());
        }

        /**
         * Task 4.5: Invalid AI output discarded; valid memories from same batch still created.
         * This test verifies that multiple invalid recommendations are all discarded while
         * valid ones in the same batch are still processed.
         *
         * <p><strong>Validates: SPEC-004 REQ-4, REQ-25 - AI recommendations as untrusted input</strong>
         */
        @Test
        @DisplayName("Should skip multiple invalid recommendations and continue with valid ones - Task 4.5")
        void shouldSkipMultipleInvalidRecommendationsButContinueWithValid() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            // First invalid: empty content
            AiMemoryRecommendation invalid1 = AiMemoryRecommendation.builder()
                    .content("")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            // Second invalid: invalid category
            AiMemoryRecommendation invalid2 = AiMemoryRecommendation.builder()
                    .content("Some content")
                    .category("NOT_A_VALID_CATEGORY")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            // Third invalid: confidence out of range
            AiMemoryRecommendation invalid3 = AiMemoryRecommendation.builder()
                    .content("More content")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(1.5) // Invalid - above 1.0
                    .build();

            // Valid recommendation
            AiMemoryRecommendation validRec = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            List<AiMemoryRecommendation> recommendations = List.of(invalid1, invalid2, validRec, invalid3);
            List<float[]> embeddings = List.of(new float[1536], new float[1536], new float[1536], new float[1536]);

            // Set up validation results
            when(extractionValidator.validate(invalid1))
                    .thenReturn(ValidationResult.invalid(List.of("Content cannot be empty")));
            when(extractionValidator.validate(invalid2))
                    .thenReturn(ValidationResult.invalid(List.of("Invalid category 'NOT_A_VALID_CATEGORY'")));
            when(extractionValidator.validate(invalid3))
                    .thenReturn(ValidationResult.invalid(List.of("Confidence score 1.50 must be between 0.0 and 1.0")));
            when(extractionValidator.validate(validRec))
                    .thenReturn(ValidationResult.valid());
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            List<Memory> results = serviceWithMocks.processRecommendations(
                    recommendations, fatherId, conversationId, embeddings);

            // Assert - only the one valid memory should be created
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getContent()).isEqualTo("Lucas loves dinosaurs");
            
            // Verify validation was called for all 4 recommendations
            verify(extractionValidator, times(4)).validate(any());
            
            // Verify duplicate detector was only called once (for the valid recommendation)
            verify(duplicateDetector, times(1)).check(any(), any(), any(), any());
            
            // Verify only one memory was saved
            verify(memoryRepository, times(1)).save(any(Memory.class));
        }

        /**
         * Task 4.5: Tests that all recommendations are invalid (returns empty list).
         * 
         * <p><strong>Validates: SPEC-004 REQ-25 - Invalid recommendations discarded</strong>
         */
        @Test
        @DisplayName("Should return empty when all recommendations in batch are invalid - Task 4.5")
        void shouldReturnEmptyWhenAllRecommendationsInBatchAreInvalid() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            AiMemoryRecommendation invalid1 = AiMemoryRecommendation.builder()
                    .content("")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            AiMemoryRecommendation invalid2 = AiMemoryRecommendation.builder()
                    .content("My name is John") // Domain entity data
                    .category("IDENTITY")
                    .subjectType("FATHER")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(9)
                    .confidenceScore(0.9)
                    .build();

            AiMemoryRecommendation invalid3 = AiMemoryRecommendation.builder()
                    .content("Valid content")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(0) // Invalid - below 1
                    .confidenceScore(0.8)
                    .build();

            List<AiMemoryRecommendation> recommendations = List.of(invalid1, invalid2, invalid3);
            List<float[]> embeddings = List.of(new float[1536], new float[1536], new float[1536]);

            when(extractionValidator.validate(any()))
                    .thenReturn(ValidationResult.invalid(List.of("Validation failed")));

            // Act
            List<Memory> results = serviceWithMocks.processRecommendations(
                    recommendations, fatherId, conversationId, embeddings);

            // Assert - no memories created
            assertThat(results).isEmpty();
            
            // Verify validation was called for all recommendations
            verify(extractionValidator, times(3)).validate(any());
            
            // Verify duplicate detector was never called (no valid recommendations)
            verify(duplicateDetector, never()).check(any(), any(), any(), any());
            
            // Verify no memories were saved
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        /**
         * Task 4.5: Tests that validation exceptions are caught and don't abort the batch.
         * Error handling ensures that even if validation throws an exception for one item,
         * other items in the batch continue to be processed.
         *
         * <p><strong>Validates: SPEC-004 Design - Error Handling (extraction errors logged, not propagated)</strong>
         */
        @Test
        @DisplayName("Should handle validation exceptions gracefully without aborting batch - Task 4.5")
        void shouldHandleValidationExceptionsGracefullyWithoutAbortingBatch() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            AiMemoryRecommendation rec1 = AiMemoryRecommendation.builder()
                    .content("First recommendation")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            AiMemoryRecommendation rec2 = AiMemoryRecommendation.builder()
                    .content("Second recommendation")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            List<AiMemoryRecommendation> recommendations = List.of(rec1, rec2);
            List<float[]> embeddings = List.of(new float[1536], new float[1536]);

            // First recommendation throws exception, second is valid
            when(extractionValidator.validate(rec1))
                    .thenThrow(new RuntimeException("Unexpected validation error"));
            when(extractionValidator.validate(rec2))
                    .thenReturn(ValidationResult.valid());
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act - should not throw, should return one memory
            List<Memory> results = serviceWithMocks.processRecommendations(
                    recommendations, fatherId, conversationId, embeddings);

            // Assert - the second valid memory should be created despite first one throwing
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getContent()).isEqualTo("Second recommendation");
        }

        @Test
        @DisplayName("Should handle null embedding gracefully")
        void shouldHandleNullEmbeddingGracefully() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), isNull()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act - passing null embedding
            Optional<Memory> result = serviceWithMocks.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, null);

            // Assert - memory still created
            assertThat(result).isPresent();
            verify(duplicateDetector).check(eq(fatherId), any(), any(), isNull());
        }

        /**
         * Helper method to create a test Memory instance.
         */
        private Memory createTestMemory(UUID memoryId, UUID fatherId) {
            Memory memory = new Memory(
                    fatherId,
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    "Lucas loves dinosaurs",
                    6,
                    new BigDecimal("0.75"),
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
            memory.setId(memoryId);
            return memory;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Capacity Management Integration (Task 4.6)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Capacity Management Integration Tests - Task 4.6")
    class CapacityManagementIntegrationTests {

        @Mock
        private DuplicateDetector duplicateDetector;

        @Mock
        private ExtractionValidator extractionValidator;

        @Mock
        private MemoryRepository memoryRepository;

        @Mock
        private MemoryCapacityManager capacityManager;

        private MemoryExtractionService serviceWithCapacityManager;

        @BeforeEach
        void setUpMocks() {
            serviceWithCapacityManager = new MemoryExtractionService(
                    duplicateDetector, extractionValidator, memoryRepository, capacityManager);
        }

        @Test
        @DisplayName("Should check capacity before creating DISTINCT memory")
        void shouldCheckCapacityBeforeCreatingDistinctMemory() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            Optional<Memory> result = serviceWithCapacityManager.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert
            assertThat(result).isPresent();
            verify(capacityManager).ensureCapacity(fatherId);
            verify(memoryRepository).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should skip memory creation when capacity cannot be ensured")
        void shouldSkipCreationWhenCapacityCannotBeEnsured() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.noArchivableMemory());

            // Act
            Optional<Memory> result = serviceWithCapacityManager.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - no memory created
            assertThat(result).isEmpty();
            verify(capacityManager).ensureCapacity(fatherId);
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should create memory when capacity manager archives a memory")
        void shouldCreateMemoryWhenCapacityManagerArchives() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID archivedMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.memoryArchived(archivedMemoryId));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            Optional<Memory> result = serviceWithCapacityManager.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - memory created after archiving made room
            assertThat(result).isPresent();
            verify(capacityManager).ensureCapacity(fatherId);
            verify(memoryRepository).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should skip memory creation when archive fails")
        void shouldSkipCreationWhenArchiveFails() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID failedMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.archiveFailed(
                            failedMemoryId, "Invalid state transition"));

            // Act
            Optional<Memory> result = serviceWithCapacityManager.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - no memory created
            assertThat(result).isEmpty();
            verify(capacityManager).ensureCapacity(fatherId);
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should allow creation when capacity manager is null")
        void shouldAllowCreationWhenCapacityManagerIsNull() {
            // Arrange - service without capacity manager
            MemoryExtractionService serviceNoCapacityManager = new MemoryExtractionService(
                    duplicateDetector, extractionValidator, memoryRepository);

            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            Optional<Memory> result = serviceNoCapacityManager.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - memory created without capacity check
            assertThat(result).isPresent();
            verify(capacityManager, never()).ensureCapacity(any());
            verify(memoryRepository).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should not check capacity for DUPLICATE result (no new memory created)")
        void shouldNotCheckCapacityForDuplicateResult() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID existingMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas loves dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.8)
                    .build();

            Memory existingMemory = new Memory(
                    fatherId, MemoryCategory.PREFERENCE, MemorySubjectType.CHILD,
                    "Lucas loves dinosaurs", 6, new BigDecimal("0.75"),
                    MemorySourceType.CONVERSATION_EXTRACTION);
            existingMemory.setId(existingMemoryId);

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(new DuplicateResult.Duplicate(existingMemoryId, 0.90));
            when(memoryRepository.findById(existingMemoryId))
                    .thenReturn(Optional.of(existingMemory));
            when(memoryRepository.save(existingMemory)).thenReturn(existingMemory);

            // Act
            Optional<Memory> result = serviceWithCapacityManager.processValidatedRecommendation(
                    recommendation, fatherId, conversationId, embedding);

            // Assert - no capacity check needed since no new memory created
            assertThat(result).isEmpty();
            verify(capacityManager, never()).ensureCapacity(any());
        }

        @Test
        @DisplayName("ensureCapacityForNewMemory should return true when capacity available")
        void ensureCapacityForNewMemoryShouldReturnTrueWhenCapacityAvailable() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());

            // Act
            boolean result = serviceWithCapacityManager.ensureCapacityForNewMemory(fatherId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("ensureCapacityForNewMemory should return true when memory archived")
        void ensureCapacityForNewMemoryShouldReturnTrueWhenMemoryArchived() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.memoryArchived(UUID.randomUUID()));

            // Act
            boolean result = serviceWithCapacityManager.ensureCapacityForNewMemory(fatherId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("ensureCapacityForNewMemory should return false when no archivable memory")
        void ensureCapacityForNewMemoryShouldReturnFalseWhenNoArchivable() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.noArchivableMemory());

            // Act
            boolean result = serviceWithCapacityManager.ensureCapacityForNewMemory(fatherId);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("ensureCapacityForNewMemory should return false when archive fails")
        void ensureCapacityForNewMemoryShouldReturnFalseWhenArchiveFails() {
            // Arrange
            UUID fatherId = UUID.randomUUID();
            when(capacityManager.ensureCapacity(fatherId))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.archiveFailed(
                            UUID.randomUUID(), "Test error"));

            // Act
            boolean result = serviceWithCapacityManager.ensureCapacityForNewMemory(fatherId);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("ensureCapacityForNewMemory should return true when capacity manager is null")
        void ensureCapacityForNewMemoryShouldReturnTrueWhenManagerIsNull() {
            // Arrange - service without capacity manager
            MemoryExtractionService serviceNoManager = new MemoryExtractionService(
                    duplicateDetector, extractionValidator, memoryRepository);
            UUID fatherId = UUID.randomUUID();

            // Act
            boolean result = serviceNoManager.ensureCapacityForNewMemory(fatherId);

            // Assert - allow creation when manager unavailable
            assertThat(result).isTrue();
        }
    }
}
