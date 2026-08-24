package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditContentErasureService;
import com.dadcoach.memory.audit.MemoryAuditLog;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryErasureJob}.
 *
 * <p>Tests cover SPEC-004 Requirement 2 Criteria 7:
 * <ul>
 *   <li>Content erasure within 72 hours of DELETED state</li>
 *   <li>Memory content field set to "[ERASED]"</li>
 *   <li>Embedding vector removal</li>
 *   <li>Version history erasure from audit logs</li>
 *   <li>Audit entry creation for erasure</li>
 *   <li>Error handling and partial failure recovery</li>
 *   <li>GDPR bulk erasure support</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 2.7 (SPEC-004)</strong>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MemoryErasureJob Tests")
class MemoryErasureJobTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryAuditService auditService;

    @Mock
    private MemoryAuditContentErasureService auditContentErasureService;

    private MemoryErasureJob erasureJob;

    @Captor
    private ArgumentCaptor<Memory> memoryCaptor;

    private UUID fatherId;
    private UUID memoryId;
    private Instant now;

    @BeforeEach
    void setUp() {
        erasureJob = new MemoryErasureJob(memoryRepository, auditService, auditContentErasureService);
        fatherId = UUID.randomUUID();
        memoryId = UUID.randomUUID();
        now = Instant.now();
        
        // Default mock behavior
        when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");
        when(auditContentErasureService.eraseAuditContentForMemory(any(UUID.class))).thenReturn(3);
        when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    private Memory createDeletedMemory(int daysInDeletedState) {
        Memory memory = new Memory(
                fatherId,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                "Lucas loves dinosaurs and space",
                5,
                new BigDecimal("0.80"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(memoryId);
        memory.setState(MemoryState.DELETED);
        memory.setLastUpdatedAt(now.minus(daysInDeletedState, ChronoUnit.DAYS));
        memory.setEmbedding(new float[1536]); // Simulated embedding
        return memory;
    }

    private Memory createAlreadyErasedMemory() {
        Memory memory = createDeletedMemory(1);
        memory.setContent(MemoryErasureJob.ERASED_CONTENT_PLACEHOLDER);
        memory.setEmbedding(null);
        return memory;
    }

    // ─── Content Erasure Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Content Erasure")
    class ContentErasureTests {

        @Test
        @DisplayName("Should erase content from DELETED memory")
        void shouldEraseContentFromDeletedMemory() {
            // Given: A DELETED memory with content
            Memory memory = createDeletedMemory(1);
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseDeletedMemoryContent(now);

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            Memory savedMemory = memoryCaptor.getValue();
            
            assertThat(savedMemory.getContent()).isEqualTo(MemoryErasureJob.ERASED_CONTENT_PLACEHOLDER);
            assertThat(savedMemory.getEmbedding()).isNull();
            
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesErased()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should erase embedding vector")
        void shouldEraseEmbeddingVector() {
            // Given: A DELETED memory with embedding
            Memory memory = createDeletedMemory(1);
            assertThat(memory.getEmbedding()).isNotNull();
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            erasureJob.eraseDeletedMemoryContent(now);

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getEmbedding()).isNull();
        }

        @Test
        @DisplayName("Should erase version history from audit log")
        void shouldEraseVersionHistoryFromAuditLog() {
            // Given: A DELETED memory
            Memory memory = createDeletedMemory(1);
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            erasureJob.eraseDeletedMemoryContent(now);

            // Then
            verify(auditContentErasureService).eraseAuditContentForMemory(memoryId);
        }

        @Test
        @DisplayName("Should create ERASE audit entry")
        void shouldCreateEraseAuditEntry() {
            // Given: A DELETED memory
            Memory memory = createDeletedMemory(1);
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            erasureJob.eraseDeletedMemoryContent(now);

            // Then
            verify(auditService).createAuditEntryWithStateTransition(
                    any(Memory.class),
                    eq(EventType.ERASE),
                    eq(MemoryState.DELETED),
                    eq(MemoryState.DELETED),
                    eq(ActorType.SYSTEM),
                    eq("SYSTEM:erasure_job"),
                    any()
            );
        }
    }

    // ─── Skip Behavior Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Skip Behavior")
    class SkipBehaviorTests {

        @Test
        @DisplayName("Should not process already erased memories")
        void shouldNotProcessAlreadyErasedMemories() {
            // Given: Empty list (already erased memories have null content)
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseDeletedMemoryContent(now);

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            verify(auditContentErasureService, never()).eraseAuditContentForMemory(any());
            
            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesErased()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip memories that changed since job start (race condition protection)")
        void shouldSkipMemoriesThatChangedSinceJobStart() {
            // Given: A memory that was updated after the job started
            Memory memory = createDeletedMemory(0);
            memory.setLastUpdatedAt(now.plus(1, ChronoUnit.SECONDS)); // Updated after job start
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory));

            // When
            Instant jobStartTime = now;
            MemoryErasureJob.ErasureResult result = erasureJob.eraseDeletedMemoryContent(jobStartTime);

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesErased()).isEqualTo(0);
        }
    }

    // ─── Error Handling Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should continue processing other memories when one fails")
        void shouldContinueProcessingWhenOneFails() {
            // Given: Two memories, one that will fail
            Memory memory1 = createDeletedMemory(1);
            memory1.setId(UUID.randomUUID());
            
            Memory memory2 = createDeletedMemory(2);
            memory2.setId(UUID.randomUUID());
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory1, memory2));
            
            // First memory save fails, second succeeds
            when(memoryRepository.save(memory1))
                    .thenThrow(new RuntimeException("Database error"));
            when(memoryRepository.save(memory2))
                    .thenReturn(memory2);

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseDeletedMemoryContent(now);

            // Then
            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesErased()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject erasure of non-DELETED memory")
        void shouldRejectErasureOfNonDeletedMemory() {
            // Given: A memory not in DELETED state
            Memory memory = new Memory(
                    fatherId,
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    "Lucas loves dinosaurs",
                    5,
                    new BigDecimal("0.80"),
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
            memory.setId(memoryId);
            memory.setState(MemoryState.ACTIVE); // Not DELETED

            // When/Then
            assertThatThrownBy(() -> erasureJob.eraseMemoryContent(memory))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("non-DELETED");
        }
    }

    // ─── Individual Memory Erasure Tests ─────────────────────────────────────

    @Nested
    @DisplayName("Individual Memory Erasure")
    class IndividualMemoryErasureTests {

        @Test
        @DisplayName("Should erase memory by ID")
        void shouldEraseMemoryById() {
            // Given: A DELETED memory
            Memory memory = createDeletedMemory(1);
            
            when(memoryRepository.findById(memoryId)).thenReturn(Optional.of(memory));

            // When
            MemoryErasureJob.ErasureDetails result = erasureJob.eraseMemoryById(memoryId);

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            Memory savedMemory = memoryCaptor.getValue();
            
            assertThat(savedMemory.getContent()).isEqualTo(MemoryErasureJob.ERASED_CONTENT_PLACEHOLDER);
            assertThat(savedMemory.getEmbedding()).isNull();
            assertThat(result.auditEntriesErased()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should skip already erased memory by ID")
        void shouldSkipAlreadyErasedMemoryById() {
            // Given: An already erased memory
            Memory memory = createAlreadyErasedMemory();
            
            when(memoryRepository.findById(memoryId)).thenReturn(Optional.of(memory));

            // When
            MemoryErasureJob.ErasureDetails result = erasureJob.eraseMemoryById(memoryId);

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            assertThat(result.auditEntriesErased()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should throw when memory not found")
        void shouldThrowWhenMemoryNotFound() {
            // Given
            when(memoryRepository.findById(memoryId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> erasureJob.eraseMemoryById(memoryId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw when memory not in DELETED state")
        void shouldThrowWhenMemoryNotDeleted() {
            // Given: An ACTIVE memory
            Memory memory = createDeletedMemory(1);
            memory.setState(MemoryState.ACTIVE);
            
            when(memoryRepository.findById(memoryId)).thenReturn(Optional.of(memory));

            // When/Then
            assertThatThrownBy(() -> erasureJob.eraseMemoryById(memoryId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DELETED state");
        }
    }

    // ─── Bulk Erasure Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Bulk Erasure for Father (GDPR)")
    class BulkErasureTests {

        @Test
        @DisplayName("Should erase all content for a father")
        void shouldEraseAllContentForFather() {
            // Given: Multiple DELETED memories for a father
            Memory memory1 = createDeletedMemory(1);
            memory1.setId(UUID.randomUUID());
            
            Memory memory2 = createDeletedMemory(2);
            memory2.setId(UUID.randomUUID());
            
            when(memoryRepository.findByFatherIdAndState(fatherId, MemoryState.DELETED))
                    .thenReturn(List.of(memory1, memory2));

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseAllContentForFather(fatherId);

            // Then
            verify(memoryRepository, times(2)).save(any(Memory.class));
            verify(auditContentErasureService, times(2)).eraseAuditContentForMemory(any());
            
            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesErased()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip already erased memories in bulk erasure")
        void shouldSkipAlreadyErasedMemoriesInBulkErasure() {
            // Given: One erased and one not erased
            Memory erasedMemory = createAlreadyErasedMemory();
            erasedMemory.setId(UUID.randomUUID());
            
            Memory notErasedMemory = createDeletedMemory(1);
            notErasedMemory.setId(UUID.randomUUID());
            
            when(memoryRepository.findByFatherIdAndState(fatherId, MemoryState.DELETED))
                    .thenReturn(List.of(erasedMemory, notErasedMemory));

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseAllContentForFather(fatherId);

            // Then
            verify(memoryRepository, times(1)).save(any(Memory.class));
            
            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesErased()).isEqualTo(1); // Only the not-erased one
        }

        @Test
        @DisplayName("Should handle empty list for father with no deleted memories")
        void shouldHandleEmptyListForFatherWithNoDeletedMemories() {
            // Given
            when(memoryRepository.findByFatherIdAndState(fatherId, MemoryState.DELETED))
                    .thenReturn(Collections.emptyList());

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseAllContentForFather(fatherId);

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            
            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesErased()).isEqualTo(0);
        }
    }

    // ─── Result Tracking Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Result Tracking")
    class ResultTrackingTests {

        @Test
        @DisplayName("Should track audit entries erased count")
        void shouldTrackAuditEntriesErasedCount() {
            // Given: A DELETED memory with 5 audit entries
            Memory memory = createDeletedMemory(1);
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory));
            when(auditContentErasureService.eraseAuditContentForMemory(memoryId)).thenReturn(5);

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseDeletedMemoryContent(now);

            // Then
            assertThat(result.auditEntriesErased()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should aggregate audit entries erased across multiple memories")
        void shouldAggregateAuditEntriesErasedAcrossMultipleMemories() {
            // Given: Multiple DELETED memories
            Memory memory1 = createDeletedMemory(1);
            memory1.setId(UUID.randomUUID());
            
            Memory memory2 = createDeletedMemory(2);
            memory2.setId(UUID.randomUUID());
            
            when(memoryRepository.findDeletedForErasure(eq(MemoryState.DELETED), any(Instant.class)))
                    .thenReturn(List.of(memory1, memory2));
            when(auditContentErasureService.eraseAuditContentForMemory(memory1.getId())).thenReturn(3);
            when(auditContentErasureService.eraseAuditContentForMemory(memory2.getId())).thenReturn(4);

            // When
            MemoryErasureJob.ErasureResult result = erasureJob.eraseDeletedMemoryContent(now);

            // Then
            assertThat(result.auditEntriesErased()).isEqualTo(7);
        }
    }
}
