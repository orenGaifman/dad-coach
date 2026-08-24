package com.dadcoach.memory.extraction;

import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for DuplicateDetector.
 *
 * <p>These tests verify the duplicate detection logic defined in SPEC-004 Requirement 9:
 * <ul>
 *   <li>Cosine similarity > 0.85 → DUPLICATE (reject creation)</li>
 *   <li>Cosine similarity 0.70-0.85 → POTENTIAL_UPDATE (consider supersession)</li>
 *   <li>Cosine similarity < 0.70 → DISTINCT (allow creation)</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 9</strong>
 *
 * @see DuplicateDetector
 * @see DuplicateResult
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DuplicateDetector Tests")
class DuplicateDetectorTest {

    // ─── Test Constants ──────────────────────────────────────────────────

    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final UUID EXISTING_MEMORY_ID = UUID.randomUUID();
    private static final MemoryCategory TEST_CATEGORY = MemoryCategory.IDENTITY;
    private static final MemorySubjectType TEST_SUBJECT_TYPE = MemorySubjectType.FATHER;
    private static final float[] TEST_EMBEDDING = createTestEmbedding();

    @Mock
    private MemoryRepository memoryRepository;

    private DuplicateDetector duplicateDetector;

    @BeforeEach
    void setUp() {
        duplicateDetector = new DuplicateDetector(memoryRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: DUPLICATE Detection (similarity > 0.85)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DUPLICATE Detection Tests (similarity > 0.85)")
    class DuplicateDetectionTests {

        @Test
        @DisplayName("Should return DUPLICATE when similarity is 0.86")
        void shouldReturnDuplicateWhenSimilarityIs086() {
            // Arrange
            double similarity = 0.86;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should return DUPLICATE when similarity is 0.90")
        void shouldReturnDuplicateWhenSimilarityIs090() {
            // Arrange
            double similarity = 0.90;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should return DUPLICATE when similarity is 0.99 (near identical)")
        void shouldReturnDuplicateWhenSimilarityIs099() {
            // Arrange
            double similarity = 0.99;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should return DUPLICATE when similarity is exactly 1.0 (perfect match)")
        void shouldReturnDuplicateWhenSimilarityIs1() {
            // Arrange
            double similarity = 1.0;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("DUPLICATE result should indicate creation should be rejected")
        void duplicateResultShouldIndicateRejection() {
            // Arrange
            mockRepositoryReturnsSimilarity(0.90);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result).isInstanceOf(DuplicateResult.Duplicate.class);
            assertThat(result.existingMemoryId()).isPresent();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: POTENTIAL_UPDATE Detection (similarity 0.70-0.85)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POTENTIAL_UPDATE Detection Tests (similarity 0.70-0.85)")
    class PotentialUpdateDetectionTests {

        @Test
        @DisplayName("Should return POTENTIAL_UPDATE when similarity is exactly 0.70")
        void shouldReturnPotentialUpdateWhenSimilarityIs070() {
            // Arrange
            double similarity = 0.70;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should return POTENTIAL_UPDATE when similarity is 0.78")
        void shouldReturnPotentialUpdateWhenSimilarityIs078() {
            // Arrange
            double similarity = 0.78;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should return POTENTIAL_UPDATE when similarity is exactly 0.85")
        void shouldReturnPotentialUpdateWhenSimilarityIs085() {
            // Arrange
            double similarity = 0.85;
            mockRepositoryReturnsSimilarity(similarity);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
            assertThat(result.existingMemoryId()).contains(EXISTING_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("POTENTIAL_UPDATE result should indicate supersession consideration")
        void potentialUpdateResultShouldIndicateSupersessionConsideration() {
            // Arrange
            mockRepositoryReturnsSimilarity(0.78);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result).isInstanceOf(DuplicateResult.PotentialUpdate.class);
            assertThat(result.existingMemoryId()).isPresent();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: DISTINCT Detection (similarity < 0.70 or no matches)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DISTINCT Detection Tests (similarity < 0.70)")
    class DistinctDetectionTests {

        @Test
        @DisplayName("Should return DISTINCT when no similar memories found")
        void shouldReturnDistinctWhenNoSimilarMemoriesFound() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result.existingMemoryId()).isEmpty();
            assertThat(result.similarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return DISTINCT when embedding is null")
        void shouldReturnDistinctWhenEmbeddingIsNull() {
            // Act - no repository interaction expected
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, null);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result.existingMemoryId()).isEmpty();
            
            // Verify repository was never called
            verifyNoInteractions(memoryRepository);
        }

        @Test
        @DisplayName("Should return DISTINCT when embedding is empty array")
        void shouldReturnDistinctWhenEmbeddingIsEmpty() {
            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, new float[0]);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result.existingMemoryId()).isEmpty();
            
            // Verify repository was never called
            verifyNoInteractions(memoryRepository);
        }

        @Test
        @DisplayName("DISTINCT result should indicate creation is allowed")
        void distinctResultShouldIndicateCreationAllowed() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result).isInstanceOf(DuplicateResult.Distinct.class);
            assertThat(result.existingMemoryId()).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Boundary Conditions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Boundary Condition Tests")
    class BoundaryConditionTests {

        @Test
        @DisplayName("Similarity exactly at 0.85 should be POTENTIAL_UPDATE, not DUPLICATE")
        void similarityAt085ShouldBePotentialUpdate() {
            // Arrange - exactly at boundary
            mockRepositoryReturnsSimilarity(0.85);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert - should be POTENTIAL_UPDATE (not DUPLICATE)
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
        }

        @Test
        @DisplayName("Similarity just above 0.85 (0.851) should be DUPLICATE")
        void similarityJustAbove085ShouldBeDuplicate() {
            // Arrange - just above boundary
            mockRepositoryReturnsSimilarity(0.851);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
        }

        @Test
        @DisplayName("Similarity exactly at 0.70 should be POTENTIAL_UPDATE")
        void similarityAt070ShouldBePotentialUpdate() {
            // Arrange
            mockRepositoryReturnsSimilarity(0.70);

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Query Parameters
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Parameter Tests")
    class QueryParameterTests {

        @Test
        @DisplayName("Should query with correct father ID")
        void shouldQueryWithCorrectFatherId() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            duplicateDetector.check(TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            verify(memoryRepository).findSimilarForDuplicateDetection(
                    eq(TEST_FATHER_ID), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("Should query with correct category")
        void shouldQueryWithCorrectCategory() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            duplicateDetector.check(TEST_FATHER_ID, MemoryCategory.RELATIONSHIP, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            verify(memoryRepository).findSimilarForDuplicateDetection(
                    any(), eq("RELATIONSHIP"), anyString(), anyList(), anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("Should query with correct subject type")
        void shouldQueryWithCorrectSubjectType() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            duplicateDetector.check(TEST_FATHER_ID, TEST_CATEGORY, MemorySubjectType.CHILD, TEST_EMBEDDING);

            // Assert
            verify(memoryRepository).findSimilarForDuplicateDetection(
                    any(), anyString(), eq("CHILD"), anyList(), anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("Should query only ACTIVE and CONFIRMED states")
        void shouldQueryOnlyActiveAndConfirmedStates() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            duplicateDetector.check(TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            verify(memoryRepository).findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), 
                    eq(List.of("ACTIVE", "CONFIRMED")), 
                    anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("Should query with minimum similarity threshold of 0.70")
        void shouldQueryWithMinSimilarityThreshold() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // Act
            duplicateDetector.check(TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert
            verify(memoryRepository).findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), 
                    eq(0.70), anyInt());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Error Handling (Graceful Degradation)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return DISTINCT when repository throws exception (graceful degradation)")
        void shouldReturnDistinctWhenRepositoryThrowsException() {
            // Arrange
            when(memoryRepository.findSimilarForDuplicateDetection(
                    any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                    .thenThrow(new RuntimeException("pgvector unavailable"));

            // Act
            DuplicateResult result = duplicateDetector.check(
                    TEST_FATHER_ID, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

            // Assert - graceful degradation: allow creation
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result.existingMemoryId()).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when fatherId is null")
        void shouldThrowExceptionWhenFatherIdIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> duplicateDetector.check(
                            null, TEST_CATEGORY, TEST_SUBJECT_TYPE, TEST_EMBEDDING))
                    .withMessage("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when category is null")
        void shouldThrowExceptionWhenCategoryIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> duplicateDetector.check(
                            TEST_FATHER_ID, null, TEST_SUBJECT_TYPE, TEST_EMBEDDING))
                    .withMessage("category cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when subjectType is null")
        void shouldThrowExceptionWhenSubjectTypeIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> duplicateDetector.check(
                            TEST_FATHER_ID, TEST_CATEGORY, null, TEST_EMBEDDING))
                    .withMessage("subjectType cannot be null");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: All Categories
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Category Tests")
    class CategoryTests {

        @Test
        @DisplayName("Should work with all memory categories")
        void shouldWorkWithAllCategories() {
            for (MemoryCategory category : MemoryCategory.values()) {
                // Arrange
                when(memoryRepository.findSimilarForDuplicateDetection(
                        any(), eq(category.name()), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                        .thenReturn(Collections.emptyList());

                // Act
                DuplicateResult result = duplicateDetector.check(
                        TEST_FATHER_ID, category, TEST_SUBJECT_TYPE, TEST_EMBEDDING);

                // Assert
                assertThat(result.status())
                        .as("Category %s should return DISTINCT when no matches", category)
                        .isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: All Subject Types
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Subject Type Tests")
    class SubjectTypeTests {

        @Test
        @DisplayName("Should work with all subject types")
        void shouldWorkWithAllSubjectTypes() {
            for (MemorySubjectType subjectType : MemorySubjectType.values()) {
                // Arrange
                when(memoryRepository.findSimilarForDuplicateDetection(
                        any(), anyString(), eq(subjectType.name()), anyList(), anyString(), anyDouble(), anyInt()))
                        .thenReturn(Collections.emptyList());

                // Act
                DuplicateResult result = duplicateDetector.check(
                        TEST_FATHER_ID, TEST_CATEGORY, subjectType, TEST_EMBEDDING);

                // Assert
                assertThat(result.status())
                        .as("Subject type %s should return DISTINCT when no matches", subjectType)
                        .isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a mock embedding vector for testing.
     */
    private static float[] createTestEmbedding() {
        float[] embedding = new float[1536];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = 0.1f; // Simple test embedding
        }
        return embedding;
    }

    /**
     * Mocks the repository to return a result with the specified similarity.
     */
    private void mockRepositoryReturnsSimilarity(double similarity) {
        // Create mock result: [memoryId, ..., cosine_similarity]
        Object[] mockResult = new Object[2];
        mockResult[0] = EXISTING_MEMORY_ID;
        mockResult[1] = similarity;

        List<Object[]> results = new java.util.ArrayList<>();
        results.add(mockResult);
        
        when(memoryRepository.findSimilarForDuplicateDetection(
                any(), anyString(), anyString(), anyList(), anyString(), anyDouble(), anyInt()))
                .thenReturn(results);
    }
}
