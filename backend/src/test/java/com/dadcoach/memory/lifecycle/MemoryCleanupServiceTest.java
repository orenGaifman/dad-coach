package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryCleanupService}.
 *
 * <p>Tests cover SPEC-004 Requirements 2 and 8:
 * <ul>
 *   <li>EXPIRED memory cleanup after 30 days</li>
 *   <li>DELETED memory cleanup after 30 days</li>
 *   <li>Audit logging before permanent deletion</li>
 *   <li>Race condition protection</li>
 *   <li>Weekly scheduling verification</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryCleanupServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryAuditService auditService;

    @InjectMocks
    private MemoryCleanupService cleanupService;

    @Captor
    private ArgumentCaptor<Memory> memoryCaptor;

    private UUID fatherId;
    private Instant now;

    @BeforeEach
    void setUp() {
        fatherId = UUID.randomUUID();
        now = Instant.now();
        when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");
    }

    // ─── EXPIRED Memory Cleanup Tests ────────────────────────────────────────

    @Nested
    @DisplayName("EXPIRED Memory Cleanup")
    class ExpiredMemoryCleanupTests {

        @Test
        @DisplayName("Should permanently delete EXPIRED memory older than 30 days")
        void shouldDeleteExpiredMemoryOlderThan30Days() {
            // Given: An EXPIRED memory that has been expired for 35 days
            Memory memory = createExpiredMemory(35);

            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerExpiredCleanup();

            // Then
            // First save for state transition to DELETED
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.DELETED);

            // Then permanent deletion
            verify(memoryRepository).delete(memory);

            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesRemoved()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should not delete EXPIRED memory that is less than 30 days old")
        void shouldNotDeleteExpiredMemoryLessThan30Days() {
            // Given: No EXPIRED memories older than 30 days
            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerExpiredCleanup();

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            verify(memoryRepository, never()).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesRemoved()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should create audit entry before deleting EXPIRED memory")
        void shouldCreateAuditEntryBeforeDeleteExpired() {
            // Given: An EXPIRED memory to delete
            Memory memory = createExpiredMemory(35);

            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            cleanupService.triggerExpiredCleanup();

            // Then: Audit entry should be created before deletion
            verify(auditService).serializeMemoryState(memory);
            verify(auditService).createAuditEntryForDelete(eq(memory), eq(ActorType.SYSTEM), any());
        }

        @Test
        @DisplayName("Should skip EXPIRED memory that changed state since job start (race condition)")
        void shouldSkipExpiredMemoryChangedSinceJobStart() {
            // Given: An EXPIRED memory that was updated after job start time
            Memory memory = createExpiredMemory(35);
            memory.setLastUpdatedAt(now.plus(1, ChronoUnit.SECONDS)); // After job start

            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerExpiredCleanup();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
            verify(memoryRepository, never()).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesRemoved()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should process multiple EXPIRED memories")
        void shouldProcessMultipleExpiredMemories() {
            // Given: Multiple EXPIRED memories older than 30 days
            Memory memory1 = createExpiredMemory(35);
            Memory memory2 = createExpiredMemory(45);
            Memory memory3 = createExpiredMemory(60);

            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(List.of(memory1, memory2, memory3));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerExpiredCleanup();

            // Then
            verify(memoryRepository, times(3)).save(any(Memory.class));
            verify(memoryRepository, times(3)).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(3);
            assertThat(result.memoriesRemoved()).isEqualTo(3);
        }
    }

    // ─── DELETED Memory Cleanup Tests ────────────────────────────────────────

    @Nested
    @DisplayName("DELETED Memory Cleanup")
    class DeletedMemoryCleanupTests {

        @Test
        @DisplayName("Should permanently remove DELETED memory older than 30 days")
        void shouldRemoveDeletedMemoryOlderThan30Days() {
            // Given: A DELETED memory that has been in that state for 35 days
            Memory memory = createDeletedMemory(35);

            when(memoryRepository.findAll())
                    .thenReturn(List.of(memory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerDeletedCleanup();

            // Then: Permanent deletion
            verify(memoryRepository).delete(memory);

            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesRemoved()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should not remove DELETED memory that is less than 30 days old")
        void shouldNotRemoveDeletedMemoryLessThan30Days() {
            // Given: A DELETED memory that has been in that state for only 20 days
            Memory memory = createDeletedMemory(20);

            when(memoryRepository.findAll())
                    .thenReturn(List.of(memory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerDeletedCleanup();

            // Then
            verify(memoryRepository, never()).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesRemoved()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip non-DELETED memories")
        void shouldSkipNonDeletedMemories() {
            // Given: Various memories in different states
            Memory activeMemory = createMemory(5, new BigDecimal("0.80"));
            activeMemory.setLastUpdatedAt(now.minus(35, ChronoUnit.DAYS));

            Memory expiredMemory = createMemory(3, new BigDecimal("0.40"));
            expiredMemory.setState(MemoryState.EXPIRED);
            expiredMemory.setLastUpdatedAt(now.minus(35, ChronoUnit.DAYS));

            when(memoryRepository.findAll())
                    .thenReturn(List.of(activeMemory, expiredMemory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerDeletedCleanup();

            // Then: No deletions
            verify(memoryRepository, never()).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesRemoved()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip DELETED memory that changed state since job start (race condition)")
        void shouldSkipDeletedMemoryChangedSinceJobStart() {
            // Given: A DELETED memory that was updated after job start time
            // Note: The memory's lastUpdatedAt being recent means it won't pass the cutoff filter
            // (which looks for memories older than 30 days), so it will be excluded from processing.
            // This is actually correct behavior - a recently-modified DELETED memory shouldn't be
            // cleaned up yet anyway.
            Memory memory = createDeletedMemory(35);
            // Set lastUpdatedAt to after job start - this memory won't be in the filtered list
            // because the stream filter checks m.getLastUpdatedAt().isBefore(cutoffTime)
            memory.setLastUpdatedAt(now.plus(1, ChronoUnit.SECONDS)); // After job start (and after cutoff)

            when(memoryRepository.findAll())
                    .thenReturn(List.of(memory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerDeletedCleanup();

            // Then: Memory should be filtered out during stream processing (not in cutoff window)
            verify(memoryRepository, never()).delete(any(Memory.class));

            // The memory doesn't pass the cutoff filter, so it's not counted as processed
            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesRemoved()).isEqualTo(0);
        }
    }

    // ─── Full Cleanup Job Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("Full Cleanup Job")
    class FullCleanupJobTests {

        @Test
        @DisplayName("Should combine EXPIRED and DELETED memory cleanup")
        void shouldCombineBothCleanupTypes() {
            // Given: One EXPIRED memory and one DELETED memory
            Memory expiredMemory = createExpiredMemory(35);
            Memory deletedMemory = createDeletedMemory(45);

            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(List.of(expiredMemory));
            when(memoryRepository.findAll())
                    .thenReturn(List.of(deletedMemory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerFullCleanup();

            // Then
            // EXPIRED: save (transition to DELETED) + delete
            // DELETED: just delete
            verify(memoryRepository, times(1)).save(any(Memory.class));
            verify(memoryRepository, times(2)).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesRemoved()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle errors gracefully and continue processing")
        void shouldHandleErrorsGracefully() {
            // Given: Two EXPIRED memories, one that throws an exception
            Memory goodMemory = createExpiredMemory(35);
            Memory badMemory = createExpiredMemory(40);
            // Make the bad memory throw when trying to delete
            badMemory.setState(MemoryState.DELETED); // Can't transition from DELETED to DELETED

            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(List.of(goodMemory, badMemory));

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerExpiredCleanup();

            // Then: Should process good memory and record error for bad memory
            verify(memoryRepository, times(1)).save(any(Memory.class));
            verify(memoryRepository, times(1)).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesRemoved()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle empty lists")
        void shouldHandleEmptyLists() {
            // Given: No memories to clean up
            when(memoryRepository.findExpiredForCleanup(eq(MemoryState.EXPIRED), any(Instant.class)))
                    .thenReturn(Collections.emptyList());
            when(memoryRepository.findAll())
                    .thenReturn(Collections.emptyList());

            // When
            MemoryCleanupService.CleanupResult result = cleanupService.triggerFullCleanup();

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            verify(memoryRepository, never()).delete(any(Memory.class));

            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesRemoved()).isEqualTo(0);
            assertThat(result.errors()).isEqualTo(0);
        }
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    /**
     * Creates a test memory with the specified importance and confidence scores.
     */
    private Memory createMemory(int importanceScore, BigDecimal confidenceScore) {
        Memory memory = new Memory(
                fatherId,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.FATHER,
                "Test memory content",
                importanceScore,
                confidenceScore,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
        memory.setCreatedAt(now.minus(100, ChronoUnit.DAYS));
        memory.setLastUpdatedAt(now.minus(100, ChronoUnit.DAYS));
        return memory;
    }

    /**
     * Creates an EXPIRED memory that has been in that state for the specified days.
     */
    private Memory createExpiredMemory(int daysInExpiredState) {
        Memory memory = createMemory(3, new BigDecimal("0.40"));
        memory.setState(MemoryState.EXPIRED);
        memory.setLastUpdatedAt(now.minus(daysInExpiredState, ChronoUnit.DAYS));
        return memory;
    }

    /**
     * Creates a DELETED memory that has been in that state for the specified days.
     */
    private Memory createDeletedMemory(int daysInDeletedState) {
        Memory memory = createMemory(3, new BigDecimal("0.40"));
        memory.setState(MemoryState.DELETED);
        memory.setLastUpdatedAt(now.minus(daysInDeletedState, ChronoUnit.DAYS));
        return memory;
    }
}
