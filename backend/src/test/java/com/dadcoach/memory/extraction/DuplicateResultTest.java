package com.dadcoach.memory.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Tests for DuplicateResult sealed interface and its implementations.
 *
 * <p>These tests verify the duplicate detection result types defined in SPEC-004 Requirement 9:
 * <ul>
 *   <li>DUPLICATE: similarity > 0.85</li>
 *   <li>POTENTIAL_UPDATE: similarity 0.70-0.85</li>
 *   <li>DISTINCT: similarity < 0.70 or no matches</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 9</strong>
 *
 * @see DuplicateResult
 */
@DisplayName("DuplicateResult Tests")
class DuplicateResultTest {

    private static final UUID TEST_MEMORY_ID = UUID.randomUUID();

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Threshold Constants
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Threshold Constant Tests")
    class ThresholdConstantTests {

        @Test
        @DisplayName("DUPLICATE_THRESHOLD should be 0.85")
        void duplicateThresholdShouldBe085() {
            assertThat(DuplicateResult.DUPLICATE_THRESHOLD).isEqualTo(0.85);
        }

        @Test
        @DisplayName("POTENTIAL_UPDATE_THRESHOLD should be 0.70")
        void potentialUpdateThresholdShouldBe070() {
            assertThat(DuplicateResult.POTENTIAL_UPDATE_THRESHOLD).isEqualTo(0.70);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Duplicate Record
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Duplicate Record Tests")
    class DuplicateRecordTests {

        @Test
        @DisplayName("Should create valid Duplicate with similarity > 0.85")
        void shouldCreateValidDuplicate() {
            // Act
            DuplicateResult.Duplicate result = new DuplicateResult.Duplicate(TEST_MEMORY_ID, 0.90);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
            assertThat(result.existingMemoryId()).contains(TEST_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(0.90);
        }

        @ParameterizedTest(name = "similarity={0} should be valid for DUPLICATE")
        @ValueSource(doubles = {0.851, 0.86, 0.90, 0.95, 0.99, 1.0})
        @DisplayName("Should accept valid similarity values > 0.85")
        void shouldAcceptValidSimilarityAbove085(double similarity) {
            // Act & Assert - should not throw
            DuplicateResult.Duplicate result = new DuplicateResult.Duplicate(TEST_MEMORY_ID, similarity);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should throw when similarity is exactly 0.85")
        void shouldThrowWhenSimilarityIs085() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DuplicateResult.Duplicate(TEST_MEMORY_ID, 0.85))
                    .withMessageContaining("must be > 0.85");
        }

        @Test
        @DisplayName("Should throw when similarity is below 0.85")
        void shouldThrowWhenSimilarityBelow085() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DuplicateResult.Duplicate(TEST_MEMORY_ID, 0.70))
                    .withMessageContaining("must be > 0.85");
        }

        @Test
        @DisplayName("Should throw when memoryId is null")
        void shouldThrowWhenMemoryIdIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DuplicateResult.Duplicate(null, 0.90))
                    .withMessageContaining("existingMemoryId cannot be null");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: PotentialUpdate Record
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PotentialUpdate Record Tests")
    class PotentialUpdateRecordTests {

        @Test
        @DisplayName("Should create valid PotentialUpdate with similarity 0.70-0.85")
        void shouldCreateValidPotentialUpdate() {
            // Act
            DuplicateResult.PotentialUpdate result = new DuplicateResult.PotentialUpdate(TEST_MEMORY_ID, 0.78);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
            assertThat(result.existingMemoryId()).contains(TEST_MEMORY_ID);
            assertThat(result.similarity()).isEqualTo(0.78);
        }

        @ParameterizedTest(name = "similarity={0} should be valid for POTENTIAL_UPDATE")
        @ValueSource(doubles = {0.70, 0.75, 0.80, 0.85})
        @DisplayName("Should accept valid similarity values 0.70-0.85")
        void shouldAcceptValidSimilarityInRange(double similarity) {
            // Act & Assert - should not throw
            DuplicateResult.PotentialUpdate result = new DuplicateResult.PotentialUpdate(TEST_MEMORY_ID, similarity);
            assertThat(result.similarity()).isEqualTo(similarity);
        }

        @Test
        @DisplayName("Should throw when similarity is above 0.85")
        void shouldThrowWhenSimilarityAbove085() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DuplicateResult.PotentialUpdate(TEST_MEMORY_ID, 0.86))
                    .withMessageContaining("must be between 0.7 and 0.85");
        }

        @Test
        @DisplayName("Should throw when similarity is below 0.70")
        void shouldThrowWhenSimilarityBelow070() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DuplicateResult.PotentialUpdate(TEST_MEMORY_ID, 0.69))
                    .withMessageContaining("must be between 0.7 and 0.85");
        }

        @Test
        @DisplayName("Should throw when memoryId is null")
        void shouldThrowWhenMemoryIdIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DuplicateResult.PotentialUpdate(null, 0.78))
                    .withMessageContaining("existingMemoryId cannot be null");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Distinct Record
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Distinct Record Tests")
    class DistinctRecordTests {

        @Test
        @DisplayName("Should create valid Distinct result")
        void shouldCreateValidDistinct() {
            // Act
            DuplicateResult.Distinct result = new DuplicateResult.Distinct();

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result.existingMemoryId()).isEmpty();
            assertThat(result.similarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Distinct should always return empty existingMemoryId")
        void distinctShouldAlwaysReturnEmptyMemoryId() {
            DuplicateResult.Distinct result = new DuplicateResult.Distinct();
            assertThat(result.existingMemoryId()).isEmpty();
        }

        @Test
        @DisplayName("Distinct should always return 0.0 similarity")
        void distinctShouldAlwaysReturnZeroSimilarity() {
            DuplicateResult.Distinct result = new DuplicateResult.Distinct();
            assertThat(result.similarity()).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Factory Method - DuplicateResult.of()
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Factory Method Tests - DuplicateResult.of()")
    class FactoryMethodTests {

        @ParameterizedTest(name = "similarity={0} should create DUPLICATE")
        @ValueSource(doubles = {0.851, 0.86, 0.90, 0.95, 1.0})
        @DisplayName("Should create DUPLICATE when similarity > 0.85")
        void shouldCreateDuplicateWhenSimilarityAbove085(double similarity) {
            // Act
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, similarity);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
            assertThat(result).isInstanceOf(DuplicateResult.Duplicate.class);
        }

        @ParameterizedTest(name = "similarity={0} should create POTENTIAL_UPDATE")
        @ValueSource(doubles = {0.70, 0.75, 0.80, 0.85})
        @DisplayName("Should create POTENTIAL_UPDATE when similarity 0.70-0.85")
        void shouldCreatePotentialUpdateWhenSimilarityInRange(double similarity) {
            // Act
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, similarity);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
            assertThat(result).isInstanceOf(DuplicateResult.PotentialUpdate.class);
        }

        @ParameterizedTest(name = "similarity={0} should create DISTINCT")
        @ValueSource(doubles = {0.0, 0.30, 0.50, 0.69})
        @DisplayName("Should create DISTINCT when similarity < 0.70")
        void shouldCreateDistinctWhenSimilarityBelow070(double similarity) {
            // Act
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, similarity);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result).isInstanceOf(DuplicateResult.Distinct.class);
        }

        @Test
        @DisplayName("Should create DISTINCT when memoryId is null")
        void shouldCreateDistinctWhenMemoryIdIsNull() {
            // Act
            DuplicateResult result = DuplicateResult.of(null, 0.90);

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
            assertThat(result).isInstanceOf(DuplicateResult.Distinct.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Factory Method - DuplicateResult.distinct()
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Factory Method Tests - DuplicateResult.distinct()")
    class DistinctFactoryMethodTests {

        @Test
        @DisplayName("Should create DISTINCT result")
        void shouldCreateDistinctResult() {
            // Act
            DuplicateResult result = DuplicateResult.distinct();

            // Assert
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
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
        @DisplayName("Boundary: 0.8500001 should be DUPLICATE")
        void boundaryJustAbove085ShouldBeDuplicate() {
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, 0.8500001);
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DUPLICATE);
        }

        @Test
        @DisplayName("Boundary: 0.85 exactly should be POTENTIAL_UPDATE")
        void boundaryExactly085ShouldBePotentialUpdate() {
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, 0.85);
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
        }

        @Test
        @DisplayName("Boundary: 0.70 exactly should be POTENTIAL_UPDATE")
        void boundaryExactly070ShouldBePotentialUpdate() {
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, 0.70);
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE);
        }

        @Test
        @DisplayName("Boundary: 0.6999999 should be DISTINCT")
        void boundaryJustBelow070ShouldBeDistinct() {
            DuplicateResult result = DuplicateResult.of(TEST_MEMORY_ID, 0.6999999);
            assertThat(result.status()).isEqualTo(DuplicateResult.DuplicateStatus.DISTINCT);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Status Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DuplicateStatus Enum Tests")
    class DuplicateStatusEnumTests {

        @Test
        @DisplayName("Should have exactly 3 status values")
        void shouldHaveExactlyThreeStatusValues() {
            assertThat(DuplicateResult.DuplicateStatus.values()).hasSize(3);
        }

        @Test
        @DisplayName("Should contain DUPLICATE, POTENTIAL_UPDATE, and DISTINCT")
        void shouldContainAllExpectedValues() {
            assertThat(DuplicateResult.DuplicateStatus.values())
                    .containsExactlyInAnyOrder(
                            DuplicateResult.DuplicateStatus.DUPLICATE,
                            DuplicateResult.DuplicateStatus.POTENTIAL_UPDATE,
                            DuplicateResult.DuplicateStatus.DISTINCT
                    );
        }
    }
}
