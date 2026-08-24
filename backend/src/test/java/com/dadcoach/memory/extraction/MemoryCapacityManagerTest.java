package com.dadcoach.memory.extraction;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.extraction.MemoryCapacityManager.CapacityStatus;
import com.dadcoach.memory.extraction.MemoryCapacityManager.EnsureCapacityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryCapacityManager}.
 *
 * <p>These tests verify the capacity management logic defined in SPEC-004:
 * <ul>
 *   <li>REQ-6: Maximum 500 active memories per father</li>
 *   <li>REQ-6: When at capacity, archive memory with lowest composite score</li>
 *   <li>Requirement 15: Only ACTIVE and CONFIRMED states count toward limit</li>
 *   <li>Task 4.6: 500-memory capacity checked before creation</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements REQ-6, Req 15, Task 4.6</strong>
 *
 * @see MemoryCapacityManager
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryCapacityManager Tests")
class MemoryCapacityManagerTest {

    @Mock
    private MemoryRepository memoryRepository;

    private MemoryCapacityManager capacityManager;

    private static final UUID TEST_FATHER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        capacityManager = new MemoryCapacityManager(memoryRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Count Active Memories
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Count Active Memories Tests")
    class CountActiveMemoriesTests {

        @Test
        @DisplayName("Should count only ACTIVE and CONFIRMED memories")
        void shouldCountOnlyActiveAndConfirmedMemories() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(
                    eq(TEST_FATHER_ID),
                    eq(MemoryCapacityManager.CAPACITY_COUNTING_STATES)))
                    .thenReturn(250L);

            // Act
            long count = capacityManager.countActiveMemories(TEST_FATHER_ID);

            // Assert
            assertThat(count).isEqualTo(250);
            verify(memoryRepository).countByFatherIdAndStateIn(
                    TEST_FATHER_ID,
                    MemoryCapacityManager.CAPACITY_COUNTING_STATES);
        }

        @Test
        @DisplayName("Should return 0 when no memories exist")
        void shouldReturnZeroWhenNoMemoriesExist() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(0L);

            // Act
            long count = capacityManager.countActiveMemories(TEST_FATHER_ID);

            // Assert
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null fatherId")
        void shouldThrowForNullFatherId() {
            // Act & Assert
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> capacityManager.countActiveMemories(null))
                    .withMessage("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should return 0 when repository is null")
        void shouldReturnZeroWhenRepositoryIsNull() {
            // Arrange
            MemoryCapacityManager managerWithoutRepo = new MemoryCapacityManager(null);

            // Act
            long count = managerWithoutRepo.countActiveMemories(TEST_FATHER_ID);

            // Assert
            assertThat(count).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Is At Capacity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Is At Capacity Tests")
    class IsAtCapacityTests {

        @Test
        @DisplayName("Should return true when count equals 500")
        void shouldReturnTrueWhenCountEqualsLimit() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(500L);

            // Act
            boolean atCapacity = capacityManager.isAtCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(atCapacity).isTrue();
        }

        @Test
        @DisplayName("Should return true when count exceeds 500")
        void shouldReturnTrueWhenCountExceedsLimit() {
            // Arrange (edge case - shouldn't happen but handle gracefully)
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(501L);

            // Act
            boolean atCapacity = capacityManager.isAtCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(atCapacity).isTrue();
        }

        @Test
        @DisplayName("Should return false when count is below 500")
        void shouldReturnFalseWhenCountIsBelowLimit() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(499L);

            // Act
            boolean atCapacity = capacityManager.isAtCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(atCapacity).isFalse();
        }

        @Test
        @DisplayName("Should return false when count is 0")
        void shouldReturnFalseWhenCountIsZero() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(0L);

            // Act
            boolean atCapacity = capacityManager.isAtCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(atCapacity).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Get Capacity Status
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Capacity Status Tests")
    class GetCapacityStatusTests {

        @Test
        @DisplayName("Should return correct status when below capacity")
        void shouldReturnCorrectStatusBelowCapacity() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(300L);

            // Act
            CapacityStatus status = capacityManager.getCapacityStatus(TEST_FATHER_ID);

            // Assert
            assertThat(status.currentCount()).isEqualTo(300);
            assertThat(status.limit()).isEqualTo(500);
            assertThat(status.atCapacity()).isFalse();
            assertThat(status.remainingCapacity()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should return correct status when at capacity")
        void shouldReturnCorrectStatusAtCapacity() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(500L);

            // Act
            CapacityStatus status = capacityManager.getCapacityStatus(TEST_FATHER_ID);

            // Assert
            assertThat(status.currentCount()).isEqualTo(500);
            assertThat(status.limit()).isEqualTo(500);
            assertThat(status.atCapacity()).isTrue();
            assertThat(status.remainingCapacity()).isZero();
        }

        @Test
        @DisplayName("Should return 0 remaining capacity when over limit")
        void shouldReturnZeroRemainingWhenOverLimit() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(550L);

            // Act
            CapacityStatus status = capacityManager.getCapacityStatus(TEST_FATHER_ID);

            // Assert
            assertThat(status.atCapacity()).isTrue();
            assertThat(status.remainingCapacity()).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Find Lowest Scoring Memory
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Find Lowest Scoring Memory Tests")
    class FindLowestScoringMemoryTests {

        @Test
        @DisplayName("Should return memory with lowest combined score")
        void shouldReturnMemoryWithLowestCombinedScore() {
            // Arrange
            Memory lowestScoring = createTestMemory(3, new BigDecimal("0.30")); // Score: 0.9
            Memory higherScoring = createTestMemory(7, new BigDecimal("0.80")); // Score: 5.6
            
            when(memoryRepository.findByFatherIdAndStateInOrderByCombinedScoreAsc(
                    eq(TEST_FATHER_ID),
                    eq(MemoryCapacityManager.CAPACITY_COUNTING_STATES)))
                    .thenReturn(List.of(lowestScoring, higherScoring));

            // Act
            var result = capacityManager.findLowestScoringMemory(TEST_FATHER_ID);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(lowestScoring);
        }

        @Test
        @DisplayName("Should return empty when no memories exist")
        void shouldReturnEmptyWhenNoMemoriesExist() {
            // Arrange
            when(memoryRepository.findByFatherIdAndStateInOrderByCombinedScoreAsc(any(), any()))
                    .thenReturn(Collections.emptyList());

            // Act
            var result = capacityManager.findLowestScoringMemory(TEST_FATHER_ID);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null fatherId")
        void shouldThrowForNullFatherId() {
            // Act & Assert
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> capacityManager.findLowestScoringMemory(null))
                    .withMessage("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should return empty when repository is null")
        void shouldReturnEmptyWhenRepositoryIsNull() {
            // Arrange
            MemoryCapacityManager managerWithoutRepo = new MemoryCapacityManager(null);

            // Act
            var result = managerWithoutRepo.findLowestScoringMemory(TEST_FATHER_ID);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Ensure Capacity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ensure Capacity Tests")
    class EnsureCapacityTests {

        @Test
        @DisplayName("Should return CapacityAvailable when below limit")
        void shouldReturnCapacityAvailableWhenBelowLimit() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(400L);

            // Act
            EnsureCapacityResult result = capacityManager.ensureCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(result).isInstanceOf(EnsureCapacityResult.CapacityAvailable.class);
            assertThat(result.isSuccess()).isTrue();
            
            // Should not try to archive anything
            verify(memoryRepository, never()).findByFatherIdAndStateInOrderByCombinedScoreAsc(any(), any());
        }

        @Test
        @DisplayName("Should archive lowest-scoring memory when at capacity")
        void shouldArchiveLowestScoringMemoryWhenAtCapacity() {
            // Arrange
            Memory lowestScoring = createTestMemory(2, new BigDecimal("0.30"));
            lowestScoring.setId(UUID.randomUUID());
            
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(500L);
            when(memoryRepository.findByFatherIdAndStateInOrderByCombinedScoreAsc(any(), any()))
                    .thenReturn(List.of(lowestScoring));
            when(memoryRepository.save(any(Memory.class))).thenReturn(lowestScoring);

            // Act
            EnsureCapacityResult result = capacityManager.ensureCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(result).isInstanceOf(EnsureCapacityResult.MemoryArchived.class);
            assertThat(result.isSuccess()).isTrue();
            
            EnsureCapacityResult.MemoryArchived archived = (EnsureCapacityResult.MemoryArchived) result;
            assertThat(archived.archivedMemoryId()).isEqualTo(lowestScoring.getId());
            
            // Verify the memory was saved after archiving
            verify(memoryRepository).save(lowestScoring);
            assertThat(lowestScoring.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("Should return NoArchivableMemory when at capacity but no memories found")
        void shouldReturnNoArchivableMemoryWhenNoneFound() {
            // Arrange
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(500L);
            when(memoryRepository.findByFatherIdAndStateInOrderByCombinedScoreAsc(any(), any()))
                    .thenReturn(Collections.emptyList());

            // Act
            EnsureCapacityResult result = capacityManager.ensureCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(result).isInstanceOf(EnsureCapacityResult.NoArchivableMemory.class);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null fatherId")
        void shouldThrowForNullFatherId() {
            // Act & Assert
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> capacityManager.ensureCapacity(null))
                    .withMessage("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should return CapacityAvailable when repository is null")
        void shouldReturnCapacityAvailableWhenRepositoryIsNull() {
            // Arrange
            MemoryCapacityManager managerWithoutRepo = new MemoryCapacityManager(null);

            // Act
            EnsureCapacityResult result = managerWithoutRepo.ensureCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(result).isInstanceOf(EnsureCapacityResult.CapacityAvailable.class);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return ArchiveFailed when memory cannot be archived")
        void shouldReturnArchiveFailedWhenMemoryCannotBeArchived() {
            // Arrange - Create a memory that's already in DELETED state (terminal state)
            // Note: DELETED is a terminal state and cannot transition to ARCHIVED
            // (SUPERSEDED → ARCHIVED is valid per design doc for cleanup jobs)
            Memory deletedMemory = createTestMemory(2, new BigDecimal("0.30"));
            deletedMemory.setId(UUID.randomUUID());
            deletedMemory.setState(MemoryState.DELETED); // Cannot transition to ARCHIVED
            
            when(memoryRepository.countByFatherIdAndStateIn(any(), any())).thenReturn(500L);
            when(memoryRepository.findByFatherIdAndStateInOrderByCombinedScoreAsc(any(), any()))
                    .thenReturn(List.of(deletedMemory));

            // Act
            EnsureCapacityResult result = capacityManager.ensureCapacity(TEST_FATHER_ID);

            // Assert
            assertThat(result).isInstanceOf(EnsureCapacityResult.ArchiveFailed.class);
            assertThat(result.isSuccess()).isFalse();
            
            EnsureCapacityResult.ArchiveFailed failed = (EnsureCapacityResult.ArchiveFailed) result;
            assertThat(failed.memoryId()).isEqualTo(deletedMemory.getId());
            assertThat(failed.errorMessage()).contains("Cannot transition");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Capacity Counting States
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Capacity Counting States Tests")
    class CapacityCountingStatesTests {

        @Test
        @DisplayName("Should include ACTIVE state in capacity counting")
        void shouldIncludeActiveState() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .contains(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("Should include CONFIRMED state in capacity counting")
        void shouldIncludeConfirmedState() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .contains(MemoryState.CONFIRMED);
        }

        @Test
        @DisplayName("Should exclude ARCHIVED state from capacity counting")
        void shouldExcludeArchivedState() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .doesNotContain(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("Should exclude SUPERSEDED state from capacity counting")
        void shouldExcludeSupersededState() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .doesNotContain(MemoryState.SUPERSEDED);
        }

        @Test
        @DisplayName("Should exclude EXPIRED state from capacity counting")
        void shouldExcludeExpiredState() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .doesNotContain(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("Should exclude DELETED state from capacity counting")
        void shouldExcludeDeletedState() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .doesNotContain(MemoryState.DELETED);
        }

        @Test
        @DisplayName("Should have exactly 2 states counting toward capacity")
        void shouldHaveExactlyTwoCountingStates() {
            assertThat(MemoryCapacityManager.CAPACITY_COUNTING_STATES)
                    .hasSize(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: EnsureCapacityResult
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EnsureCapacityResult Tests")
    class EnsureCapacityResultTests {

        @Test
        @DisplayName("CapacityAvailable should be success")
        void capacityAvailableShouldBeSuccess() {
            EnsureCapacityResult result = EnsureCapacityResult.capacityAvailable();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("MemoryArchived should be success")
        void memoryArchivedShouldBeSuccess() {
            UUID archivedId = UUID.randomUUID();
            EnsureCapacityResult result = EnsureCapacityResult.memoryArchived(archivedId);
            assertThat(result.isSuccess()).isTrue();
            assertThat(((EnsureCapacityResult.MemoryArchived) result).archivedMemoryId())
                    .isEqualTo(archivedId);
        }

        @Test
        @DisplayName("NoArchivableMemory should not be success")
        void noArchivableMemoryShouldNotBeSuccess() {
            EnsureCapacityResult result = EnsureCapacityResult.noArchivableMemory();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("ArchiveFailed should not be success")
        void archiveFailedShouldNotBeSuccess() {
            UUID memoryId = UUID.randomUUID();
            EnsureCapacityResult result = EnsureCapacityResult.archiveFailed(memoryId, "Test error");
            assertThat(result.isSuccess()).isFalse();
            
            EnsureCapacityResult.ArchiveFailed failed = (EnsureCapacityResult.ArchiveFailed) result;
            assertThat(failed.memoryId()).isEqualTo(memoryId);
            assertThat(failed.errorMessage()).isEqualTo("Test error");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private Memory createTestMemory(int importance, BigDecimal confidence) {
        return new Memory(
                TEST_FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                "Test memory content",
                importance,
                confidence,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
    }
}
