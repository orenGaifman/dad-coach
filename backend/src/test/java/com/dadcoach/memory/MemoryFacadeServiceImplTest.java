package com.dadcoach.memory;

import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditService;
import com.dadcoach.memory.dto.MemoryCapacityDto;
import com.dadcoach.memory.dto.MemoryDto;
import com.dadcoach.memory.dto.RetrievalResultDto;
import com.dadcoach.memory.extraction.MemoryExtractionService;
import com.dadcoach.memory.lifecycle.MemoryLifecycleService;
import com.dadcoach.memory.retrieval.MemoryRetriever;
import com.dadcoach.memory.retrieval.RetrievalMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryFacadeServiceImpl.
 *
 * <p>Validates: SPEC-004 Design - MemoryService Public Interface
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>retrieveRanked delegates to MemoryRetriever correctly</li>
 *   <li>triggerExtraction delegates to MemoryExtractionService correctly</li>
 *   <li>recordInjection/recordReference update access tracking and create audit entries</li>
 *   <li>confirmMemory delegates to MemoryLifecycleService correctly</li>
 *   <li>supersedeMemory delegates to MemoryLifecycleService correctly</li>
 *   <li>deleteMemory delegates to MemoryLifecycleService correctly</li>
 *   <li>deleteAllForFather performs GDPR erasure for all memories</li>
 *   <li>getCapacity returns correct capacity information</li>
 *   <li>Graceful handling when services are unavailable</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryFacadeServiceImpl Tests")
class MemoryFacadeServiceImplTest {

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();
    private static final UUID MEMORY_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final String TOPIC = "bedtime routine";
    private static final String CONTENT = "Lucas loves dinosaurs";
    private static final String TRANSCRIPT = "Dad: How was bedtime? AI: Let's discuss...";

    @Mock
    private MemoryRetriever memoryRetriever;

    @Mock
    private MemoryExtractionService extractionService;

    @Mock
    private MemoryLifecycleService lifecycleService;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryAuditService auditService;

    private MemoryFacadeServiceImpl facadeService;

    @BeforeEach
    void setUp() {
        facadeService = new MemoryFacadeServiceImpl(
                memoryRetriever,
                extractionService,
                lifecycleService,
                memoryRepository,
                auditService
        );
    }

    private Memory createTestMemory(UUID memoryId, MemoryState state) {
        Memory memory = new Memory(
                FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                CONTENT,
                6,
                new BigDecimal("0.80"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(memoryId);
        if (state != MemoryState.ACTIVE) {
            // Simulate different states
            switch (state) {
                case CONFIRMED -> memory.confirm();
                case SUPERSEDED -> memory.markSuperseded(UUID.randomUUID());
                case ARCHIVED -> memory.archive();
                case EXPIRED -> memory.expire();
                case DELETED -> memory.delete();
            }
        }
        return memory;
    }

    private RetrievalResultDto createTestRetrievalResult(UUID memoryId, double compositeScore) {
        MemoryDto dto = new MemoryDto();
        dto.setId(memoryId);
        dto.setFatherId(FATHER_ID);
        dto.setCategory(MemoryCategory.PREFERENCE);
        dto.setContent(CONTENT);
        dto.setImportanceScore(6);
        dto.setConfidenceScore(new BigDecimal("0.80"));
        dto.setState(MemoryState.ACTIVE);

        RetrievalMetadata metadata = new RetrievalMetadata(
                compositeScore,
                6,
                0.80,
                0.9,
                0.5
        );

        return new RetrievalResultDto(dto, metadata);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // retrieveRanked() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("retrieveRanked() method")
    class RetrieveRankedTests {

        @Test
        @DisplayName("retrieveRanked delegates to MemoryRetriever with correct parameters")
        void delegatesToMemoryRetriever() {
            // Given
            List<RetrievalResultDto> expectedResults = List.of(
                    createTestRetrievalResult(MEMORY_ID, 0.85)
            );
            when(memoryRetriever.retrieveRanked(FATHER_ID, TOPIC, CHILD_ID, 15))
                    .thenReturn(expectedResults);

            // When
            List<RetrievalResultDto> results = facadeService.retrieveRanked(FATHER_ID, TOPIC, CHILD_ID, 15);

            // Then
            assertThat(results).isEqualTo(expectedResults);
            verify(memoryRetriever).retrieveRanked(FATHER_ID, TOPIC, CHILD_ID, 15);
        }

        @Test
        @DisplayName("retrieveRanked returns empty list when MemoryRetriever is unavailable")
        void returnsEmptyWhenRetrieverUnavailable() {
            // Given - service created without retriever
            MemoryFacadeServiceImpl serviceWithoutRetriever = new MemoryFacadeServiceImpl(
                    null, extractionService, lifecycleService, memoryRepository, auditService);

            // When
            List<RetrievalResultDto> results = serviceWithoutRetriever.retrieveRanked(FATHER_ID, TOPIC, CHILD_ID, 15);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("retrieveRanked handles null childId (retrieves all memories for father)")
        void handlesNullChildId() {
            // Given
            when(memoryRetriever.retrieveRanked(FATHER_ID, TOPIC, null, 10))
                    .thenReturn(List.of());

            // When
            List<RetrievalResultDto> results = facadeService.retrieveRanked(FATHER_ID, TOPIC, null, 10);

            // Then
            assertThat(results).isEmpty();
            verify(memoryRetriever).retrieveRanked(FATHER_ID, TOPIC, null, 10);
        }

        @Test
        @DisplayName("retrieveRanked handles null topic")
        void handlesNullTopic() {
            // Given
            when(memoryRetriever.retrieveRanked(FATHER_ID, null, CHILD_ID, 10))
                    .thenReturn(List.of());

            // When
            facadeService.retrieveRanked(FATHER_ID, null, CHILD_ID, 10);

            // Then
            verify(memoryRetriever).retrieveRanked(FATHER_ID, null, CHILD_ID, 10);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // triggerExtraction() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("triggerExtraction() method")
    class TriggerExtractionTests {

        @Test
        @DisplayName("triggerExtraction delegates to MemoryExtractionService")
        void delegatesToExtractionService() {
            // Given
            when(extractionService.triggerExtraction(CONVERSATION_ID, FATHER_ID, TRANSCRIPT))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // When
            facadeService.triggerExtraction(CONVERSATION_ID, FATHER_ID, TRANSCRIPT);

            // Then
            verify(extractionService).triggerExtraction(CONVERSATION_ID, FATHER_ID, TRANSCRIPT);
        }

        @Test
        @DisplayName("triggerExtraction handles unavailable extraction service gracefully")
        void handlesUnavailableServiceGracefully() {
            // Given
            MemoryFacadeServiceImpl serviceWithoutExtraction = new MemoryFacadeServiceImpl(
                    memoryRetriever, null, lifecycleService, memoryRepository, auditService);

            // When/Then - should not throw
            assertThatCode(() -> 
                serviceWithoutExtraction.triggerExtraction(CONVERSATION_ID, FATHER_ID, TRANSCRIPT)
            ).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // recordInjection() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("recordInjection() method")
    class RecordInjectionTests {

        @Test
        @DisplayName("recordInjection updates access tracking for each memory")
        void updatesAccessTracking() {
            // Given
            UUID memoryId1 = UUID.randomUUID();
            UUID memoryId2 = UUID.randomUUID();
            Memory memory1 = createTestMemory(memoryId1, MemoryState.ACTIVE);
            Memory memory2 = createTestMemory(memoryId2, MemoryState.ACTIVE);

            when(memoryRepository.findById(memoryId1)).thenReturn(Optional.of(memory1));
            when(memoryRepository.findById(memoryId2)).thenReturn(Optional.of(memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.recordInjection(List.of(memoryId1, memoryId2), CONVERSATION_ID);

            // Then
            verify(memoryRepository, times(2)).save(any(Memory.class));
        }

        @Test
        @DisplayName("recordInjection handles empty list gracefully")
        void handlesEmptyList() {
            // When/Then - should not throw
            assertThatCode(() -> facadeService.recordInjection(List.of(), CONVERSATION_ID))
                    .doesNotThrowAnyException();
            
            verify(memoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("recordInjection handles null list gracefully")
        void handlesNullList() {
            // When/Then - should not throw
            assertThatCode(() -> facadeService.recordInjection(null, CONVERSATION_ID))
                    .doesNotThrowAnyException();
            
            verify(memoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("recordInjection continues processing after error on one memory")
        void continuesProcessingAfterError() {
            // Given
            UUID memoryId1 = UUID.randomUUID();
            UUID memoryId2 = UUID.randomUUID();
            Memory memory2 = createTestMemory(memoryId2, MemoryState.ACTIVE);

            when(memoryRepository.findById(memoryId1)).thenThrow(new RuntimeException("DB error"));
            when(memoryRepository.findById(memoryId2)).thenReturn(Optional.of(memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.recordInjection(List.of(memoryId1, memoryId2), CONVERSATION_ID);

            // Then - second memory should still be processed
            verify(memoryRepository).findById(memoryId2);
            verify(memoryRepository).save(memory2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // recordReference() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("recordReference() method")
    class RecordReferenceTests {

        @Test
        @DisplayName("recordReference updates access tracking for memories")
        void updatesAccessTracking() {
            // Given
            Memory memory = createTestMemory(MEMORY_ID, MemoryState.ACTIVE);
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.recordReference(List.of(MEMORY_ID), CONVERSATION_ID);

            // Then
            verify(memoryRepository).save(memory);
        }

        @Test
        @DisplayName("recordReference handles empty and null lists gracefully")
        void handlesEmptyAndNullLists() {
            // When/Then
            assertThatCode(() -> facadeService.recordReference(List.of(), CONVERSATION_ID))
                    .doesNotThrowAnyException();
            assertThatCode(() -> facadeService.recordReference(null, CONVERSATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // confirmMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confirmMemory() method")
    class ConfirmMemoryTests {

        @Test
        @DisplayName("confirmMemory delegates to MemoryLifecycleService")
        void delegatesToLifecycleService() {
            // Given
            Memory confirmedMemory = createTestMemory(MEMORY_ID, MemoryState.ACTIVE);
            confirmedMemory.confirm();
            when(lifecycleService.confirmMemory(MEMORY_ID)).thenReturn(confirmedMemory);

            // When
            facadeService.confirmMemory(MEMORY_ID);

            // Then
            verify(lifecycleService).confirmMemory(MEMORY_ID);
        }

        @Test
        @DisplayName("confirmMemory throws when MemoryLifecycleService is unavailable")
        void throwsWhenServiceUnavailable() {
            // Given
            MemoryFacadeServiceImpl serviceWithoutLifecycle = new MemoryFacadeServiceImpl(
                    memoryRetriever, extractionService, null, memoryRepository, auditService);

            // When/Then
            assertThatThrownBy(() -> serviceWithoutLifecycle.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MemoryLifecycleService not available");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // supersedeMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("supersedeMemory() method")
    class SupersedeMemoryTests {

        @Test
        @DisplayName("supersedeMemory delegates to MemoryLifecycleService with correct parameters")
        void delegatesToLifecycleService() {
            // Given
            String newContent = "Actually, Lucas prefers dinosaurs over robots";
            double newConfidence = 1.0;
            Memory newMemory = createTestMemory(UUID.randomUUID(), MemoryState.ACTIVE);
            
            when(lifecycleService.supersedeMemory(eq(MEMORY_ID), eq(newContent), any(BigDecimal.class)))
                    .thenReturn(newMemory);

            // When
            facadeService.supersedeMemory(MEMORY_ID, newContent, newConfidence);

            // Then
            ArgumentCaptor<BigDecimal> confidenceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(lifecycleService).supersedeMemory(eq(MEMORY_ID), eq(newContent), confidenceCaptor.capture());
            assertThat(confidenceCaptor.getValue()).isEqualByComparingTo(BigDecimal.valueOf(newConfidence));
        }

        @Test
        @DisplayName("supersedeMemory throws when MemoryLifecycleService is unavailable")
        void throwsWhenServiceUnavailable() {
            // Given
            MemoryFacadeServiceImpl serviceWithoutLifecycle = new MemoryFacadeServiceImpl(
                    memoryRetriever, extractionService, null, memoryRepository, auditService);

            // When/Then
            assertThatThrownBy(() -> serviceWithoutLifecycle.supersedeMemory(MEMORY_ID, "new content", 1.0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MemoryLifecycleService not available");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // deleteMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteMemory() method")
    class DeleteMemoryTests {

        @Test
        @DisplayName("deleteMemory delegates to MemoryLifecycleService")
        void delegatesToLifecycleService() {
            // Given
            Memory deletedMemory = createTestMemory(MEMORY_ID, MemoryState.ACTIVE);
            deletedMemory.delete();
            when(lifecycleService.deleteMemory(MEMORY_ID, ActorType.USER)).thenReturn(deletedMemory);

            // When
            facadeService.deleteMemory(MEMORY_ID, "User requested deletion");

            // Then
            verify(lifecycleService).deleteMemory(MEMORY_ID, ActorType.USER);
        }

        @Test
        @DisplayName("deleteMemory throws when MemoryLifecycleService is unavailable")
        void throwsWhenServiceUnavailable() {
            // Given
            MemoryFacadeServiceImpl serviceWithoutLifecycle = new MemoryFacadeServiceImpl(
                    memoryRetriever, extractionService, null, memoryRepository, auditService);

            // When/Then
            assertThatThrownBy(() -> serviceWithoutLifecycle.deleteMemory(MEMORY_ID, "reason"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MemoryLifecycleService not available");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // deleteAllForFather() (GDPR Erasure) Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteAllForFather() (GDPR Erasure) method")
    class DeleteAllForFatherTests {

        @Test
        @DisplayName("deleteAllForFather transitions all memories to DELETED state")
        void transitionsAllMemoriesToDeleted() {
            // Given
            UUID memoryId1 = UUID.randomUUID();
            UUID memoryId2 = UUID.randomUUID();
            Memory memory1 = createTestMemory(memoryId1, MemoryState.ACTIVE);
            Memory memory2 = createTestMemory(memoryId2, MemoryState.CONFIRMED);
            
            when(memoryRepository.findByFatherId(FATHER_ID)).thenReturn(List.of(memory1, memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(memory1.getState()).isEqualTo(MemoryState.DELETED);
            assertThat(memory2.getState()).isEqualTo(MemoryState.DELETED);
            verify(memoryRepository, times(2)).save(any(Memory.class));
        }

        @Test
        @DisplayName("deleteAllForFather creates audit entries for each deletion")
        void createsAuditEntriesForEachDeletion() {
            // Given
            Memory memory = createTestMemory(MEMORY_ID, MemoryState.ACTIVE);
            when(memoryRepository.findByFatherId(FATHER_ID)).thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.deleteAllForFather(FATHER_ID);

            // Then
            verify(auditService).createAuditEntryForDelete(any(Memory.class), eq(ActorType.USER), anyString());
        }

        @Test
        @DisplayName("deleteAllForFather skips memories already in DELETED state")
        void skipsAlreadyDeletedMemories() {
            // Given
            Memory activeMemory = createTestMemory(MEMORY_ID, MemoryState.ACTIVE);
            Memory deletedMemory = createTestMemory(UUID.randomUUID(), MemoryState.DELETED);
            
            when(memoryRepository.findByFatherId(FATHER_ID)).thenReturn(List.of(activeMemory, deletedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.deleteAllForFather(FATHER_ID);

            // Then - only activeMemory should be saved (not deletedMemory)
            verify(memoryRepository, times(1)).save(any(Memory.class));
        }

        @Test
        @DisplayName("deleteAllForFather handles empty memory list gracefully")
        void handlesEmptyMemoryList() {
            // Given
            when(memoryRepository.findByFatherId(FATHER_ID)).thenReturn(List.of());

            // When/Then - should not throw
            assertThatCode(() -> facadeService.deleteAllForFather(FATHER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deleteAllForFather continues processing after error on one memory")
        void continuesProcessingAfterError() {
            // Given
            UUID memoryId1 = UUID.randomUUID();
            UUID memoryId2 = UUID.randomUUID();
            Memory memory1 = mock(Memory.class);
            Memory memory2 = createTestMemory(memoryId2, MemoryState.ACTIVE);
            
            when(memory1.getId()).thenReturn(memoryId1);
            when(memory1.getState()).thenReturn(MemoryState.ACTIVE);
            doThrow(new RuntimeException("DB error")).when(memory1).delete();
            
            when(memoryRepository.findByFatherId(FATHER_ID)).thenReturn(List.of(memory1, memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            facadeService.deleteAllForFather(FATHER_ID);

            // Then - memory2 should still be processed
            assertThat(memory2.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteAllForFather handles unavailable repository gracefully")
        void handlesUnavailableRepository() {
            // Given
            MemoryFacadeServiceImpl serviceWithoutRepo = new MemoryFacadeServiceImpl(
                    memoryRetriever, extractionService, lifecycleService, null, auditService);

            // When/Then - should not throw
            assertThatCode(() -> serviceWithoutRepo.deleteAllForFather(FATHER_ID))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getCapacity() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCapacity() method")
    class GetCapacityTests {

        @Test
        @DisplayName("getCapacity returns correct capacity information")
        void returnsCorrectCapacity() {
            // Given
            long currentCount = 350;
            when(memoryRepository.countByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(currentCount);

            // When
            MemoryCapacityDto capacity = facadeService.getCapacity(FATHER_ID);

            // Then
            assertThat(capacity.getFatherId()).isEqualTo(FATHER_ID);
            assertThat(capacity.getCurrentCount()).isEqualTo(currentCount);
            assertThat(capacity.getMaxAllowed()).isEqualTo(500);
            assertThat(capacity.getAvailableCapacity()).isEqualTo(150);
            assertThat(capacity.isAtCapacity()).isFalse();
            assertThat(capacity.isNearCapacity()).isFalse();
        }

        @Test
        @DisplayName("getCapacity returns at capacity when count equals max")
        void returnsAtCapacityWhenFull() {
            // Given
            when(memoryRepository.countByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(500L);

            // When
            MemoryCapacityDto capacity = facadeService.getCapacity(FATHER_ID);

            // Then
            assertThat(capacity.isAtCapacity()).isTrue();
            assertThat(capacity.getAvailableCapacity()).isEqualTo(0);
        }

        @Test
        @DisplayName("getCapacity returns near capacity at 90% threshold")
        void returnsNearCapacityAtThreshold() {
            // Given - 450/500 = 90%
            when(memoryRepository.countByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(450L);

            // When
            MemoryCapacityDto capacity = facadeService.getCapacity(FATHER_ID);

            // Then
            assertThat(capacity.isNearCapacity()).isTrue();
            assertThat(capacity.isAtCapacity()).isFalse();
        }

        @Test
        @DisplayName("getCapacity returns zero count when repository unavailable")
        void returnsZeroWhenRepositoryUnavailable() {
            // Given
            MemoryFacadeServiceImpl serviceWithoutRepo = new MemoryFacadeServiceImpl(
                    memoryRetriever, extractionService, lifecycleService, null, auditService);

            // When
            MemoryCapacityDto capacity = serviceWithoutRepo.getCapacity(FATHER_ID);

            // Then
            assertThat(capacity.getCurrentCount()).isEqualTo(0);
            assertThat(capacity.getAvailableCapacity()).isEqualTo(500);
        }

        @Test
        @DisplayName("getCapacity queries only ACTIVE and CONFIRMED states")
        void queriesOnlyActiveStates() {
            // Given
            when(memoryRepository.countByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(100L);

            // When
            facadeService.getCapacity(FATHER_ID);

            // Then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<MemoryState>> statesCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(memoryRepository).countByFatherIdAndStateIn(eq(FATHER_ID), statesCaptor.capture());
            
            Collection<MemoryState> states = statesCaptor.getValue();
            assertThat(states).containsExactlyInAnyOrder(MemoryState.ACTIVE, MemoryState.CONFIRMED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Integration-style Tests (verifying coordination)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Service Coordination Tests")
    class ServiceCoordinationTests {

        @Test
        @DisplayName("All services are optional and facade handles missing dependencies gracefully")
        void handlesAllMissingDependencies() {
            // Given - all dependencies null
            MemoryFacadeServiceImpl minimalService = new MemoryFacadeServiceImpl(
                    null, null, null, null, null);

            // When/Then - operations should handle gracefully
            assertThat(minimalService.retrieveRanked(FATHER_ID, TOPIC, CHILD_ID, 10)).isEmpty();
            assertThatCode(() -> minimalService.triggerExtraction(CONVERSATION_ID, FATHER_ID, TRANSCRIPT))
                    .doesNotThrowAnyException();
            assertThatCode(() -> minimalService.recordInjection(List.of(MEMORY_ID), CONVERSATION_ID))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> minimalService.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> minimalService.deleteAllForFather(FATHER_ID))
                    .doesNotThrowAnyException();
            assertThat(minimalService.getCapacity(FATHER_ID).getCurrentCount()).isEqualTo(0);
        }
    }
}
