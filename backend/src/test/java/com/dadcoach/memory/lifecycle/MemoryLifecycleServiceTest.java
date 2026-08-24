package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.*;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.MemoryAuditLog;
import com.dadcoach.memory.audit.MemoryAuditService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryLifecycleService.
 *
 * <p>Validates: REQ-7 (Memory Lifecycle States), REQ-24 (Audit Logging)
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>confirmMemory transitions ACTIVE → CONFIRMED correctly</li>
 *   <li>confirmMemory increments confirmation_count</li>
 *   <li>confirmMemory sets last_confirmed_at timestamp</li>
 *   <li>confirmMemory boosts confidence to max(current, 0.9)</li>
 *   <li>confirmMemory creates audit entries</li>
 *   <li>confirmMemory throws EntityNotFoundException for missing memory</li>
 *   <li>confirmMemory throws IllegalStateException for invalid state transitions</li>
 *   <li>supersedeMemory creates new memory with updated content</li>
 *   <li>supersedeMemory marks old memory as SUPERSEDED with superseded_by link</li>
 *   <li>supersedeMemory creates audit entries for both old and new memory</li>
 *   <li>supersedeMemory validates inputs (content, confidence)</li>
 *   <li>supersedeMemory throws for invalid state transitions</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryLifecycleService Tests")
class MemoryLifecycleServiceTest {

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID MEMORY_ID = UUID.randomUUID();
    private static final String CONTENT = "Lucas loves dinosaurs";
    private static final int IMPORTANCE_SCORE = 6;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryAuditService auditService;

    private MemoryLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new MemoryLifecycleService(memoryRepository, auditService);
    }

    private Memory createActiveMemory(BigDecimal confidenceScore) {
        Memory memory = new Memory(
                FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                CONTENT,
                IMPORTANCE_SCORE,
                confidenceScore,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(MEMORY_ID);
        return memory;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // confirmMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confirmMemory() method")
    class ConfirmMemoryTests {

        @Test
        @DisplayName("confirmMemory transitions state from ACTIVE to CONFIRMED")
        void confirmMemoryTransitionsState() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.80"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"ACTIVE\"}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.CONFIRMED);
        }

        @Test
        @DisplayName("confirmMemory increments confirmation_count by 1")
        void confirmMemoryIncrementsConfirmationCount() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.80"));
            assertThat(activeMemory.getConfirmationCount()).isEqualTo(0);
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getConfirmationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("confirmMemory sets last_confirmed_at timestamp")
        void confirmMemorySetsLastConfirmedAt() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.80"));
            assertThat(activeMemory.getLastConfirmedAt()).isNull();
            Instant beforeConfirm = Instant.now();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getLastConfirmedAt()).isNotNull();
            assertThat(result.getLastConfirmedAt()).isAfterOrEqualTo(beforeConfirm);
        }

        @Test
        @DisplayName("confirmMemory boosts confidence to 0.9 when current is lower")
        void confirmMemoryBoostsConfidenceToNinetyWhenLower() {
            // Given - confidence 0.70 should be boosted to 0.90
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.90"));
        }

        @Test
        @DisplayName("confirmMemory preserves confidence when already above 0.9")
        void confirmMemoryPreservesHighConfidence() {
            // Given - confidence 0.95 should remain 0.95
            Memory activeMemory = createActiveMemory(new BigDecimal("0.95"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.95"));
        }

        @Test
        @DisplayName("confirmMemory creates audit entry for the transition")
        void confirmMemoryCreatesAuditEntry() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.80"));
            String stateBeforeJson = "{\"state\":\"ACTIVE\",\"confidence_score\":0.80}";
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(stateBeforeJson);

            // When
            lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            verify(auditService).createAuditEntryForConfirm(any(Memory.class), eq(stateBeforeJson));
        }

        @Test
        @DisplayName("confirmMemory persists the updated memory")
        void confirmMemoryPersistsMemory() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.80"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            
            Memory savedMemory = captor.getValue();
            assertThat(savedMemory.getState()).isEqualTo(MemoryState.CONFIRMED);
        }

        @Test
        @DisplayName("confirmMemory throws EntityNotFoundException for missing memory")
        void confirmMemoryThrowsWhenMemoryNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(memoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> lifecycleService.confirmMemory(nonExistentId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Memory not found: " + nonExistentId);
        }

        @Test
        @DisplayName("confirmMemory throws IllegalStateException when memory is not ACTIVE")
        void confirmMemoryThrowsWhenNotActive() {
            // Given - memory already CONFIRMED
            Memory confirmedMemory = createActiveMemory(new BigDecimal("0.80"));
            confirmedMemory.confirm(); // Now CONFIRMED
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(confirmedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CONFIRMED to CONFIRMED");
        }

        @Test
        @DisplayName("confirmMemory throws IllegalStateException when memory is SUPERSEDED")
        void confirmMemoryThrowsWhenSuperseded() {
            // Given
            Memory supersededMemory = createActiveMemory(new BigDecimal("0.80"));
            supersededMemory.markSuperseded(UUID.randomUUID());
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(supersededMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to CONFIRMED");
        }

        @Test
        @DisplayName("confirmMemory throws IllegalStateException when memory is ARCHIVED")
        void confirmMemoryThrowsWhenArchived() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.80"));
            archivedMemory.archive();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to CONFIRMED");
        }

        @Test
        @DisplayName("confirmMemory throws IllegalStateException when memory is EXPIRED")
        void confirmMemoryThrowsWhenExpired() {
            // Given
            Memory expiredMemory = createActiveMemory(new BigDecimal("0.80"));
            expiredMemory.expire();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(expiredMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from EXPIRED to CONFIRMED");
        }

        @Test
        @DisplayName("confirmMemory throws IllegalStateException when memory is DELETED")
        void confirmMemoryThrowsWhenDeleted() {
            // Given
            Memory deletedMemory = createActiveMemory(new BigDecimal("0.80"));
            deletedMemory.delete();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(deletedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.confirmMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to CONFIRMED");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence Score Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence score edge cases")
    class ConfidenceScoreEdgeCases {

        @Test
        @DisplayName("confirmMemory boosts confidence exactly at 0.9 boundary")
        void confirmMemoryAtExactlyNinetyBoundary() {
            // Given - confidence exactly 0.90 should remain 0.90
            Memory activeMemory = createActiveMemory(new BigDecimal("0.90"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.90"));
        }

        @Test
        @DisplayName("confirmMemory handles minimum confidence (0.3)")
        void confirmMemoryWithMinimumConfidence() {
            // Given - minimum confidence 0.30 should be boosted to 0.90
            Memory activeMemory = createActiveMemory(new BigDecimal("0.30"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.90"));
        }

        @Test
        @DisplayName("confirmMemory preserves maximum confidence (1.0)")
        void confirmMemoryWithMaximumConfidence() {
            // Given - maximum confidence 1.00 should remain 1.00
            Memory activeMemory = createActiveMemory(new BigDecimal("1.00"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.confirmMemory(MEMORY_ID);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("1.00"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // supersedeMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("supersedeMemory() method")
    class SupersedeMemoryTests {

        private static final String NEW_CONTENT = "Actually, Lucas prefers dinosaurs over robots";
        private static final UUID NEW_MEMORY_ID = UUID.randomUUID();

        @Test
        @DisplayName("supersedeMemory creates a new memory with updated content")
        void supersedeMemoryCreatesNewMemory() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            BigDecimal newConfidence = new BigDecimal("1.00");
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, newConfidence);

            // Then
            assertThat(result.getContent()).isEqualTo(NEW_CONTENT);
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(newConfidence);
            assertThat(result.getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("supersedeMemory marks old memory as SUPERSEDED")
        void supersedeMemoryMarksOldAsSuperseded() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(oldMemory.getState()).isEqualTo(MemoryState.SUPERSEDED);
        }

        @Test
        @DisplayName("supersedeMemory sets superseded_by link to new memory ID")
        void supersedeMemorySetsSupersededByLink() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory newMemory = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(oldMemory.getSupersededBy()).isEqualTo(newMemory.getId());
        }

        @Test
        @DisplayName("supersedeMemory preserves category from old memory")
        void supersedeMemoryPreservesCategory() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(result.getCategory()).isEqualTo(oldMemory.getCategory());
        }

        @Test
        @DisplayName("supersedeMemory preserves fatherId from old memory")
        void supersedeMemoryPreservesFatherId() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(result.getFatherId()).isEqualTo(oldMemory.getFatherId());
        }

        @Test
        @DisplayName("supersedeMemory sets sourceType to FATHER_CORRECTION")
        void supersedeMemorySetsSourceTypeFatherCorrection() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(result.getSourceType()).isEqualTo(MemorySourceType.FATHER_CORRECTION);
        }

        @Test
        @DisplayName("supersedeMemory creates audit entry for new memory creation")
        void supersedeMemoryCreatesAuditEntryForNewMemory() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            verify(auditService).createAuditEntryForCreate(any(Memory.class), eq(ActorType.USER));
        }

        @Test
        @DisplayName("supersedeMemory creates audit entry for old memory supersession")
        void supersedeMemoryCreatesAuditEntryForSupersession() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            String stateBeforeJson = "{\"state\":\"ACTIVE\",\"confidence_score\":0.70}";
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(stateBeforeJson);

            // When
            lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            verify(auditService).createAuditEntryForSupersede(any(Memory.class), eq(ActorType.USER), eq(stateBeforeJson));
        }

        @Test
        @DisplayName("supersedeMemory saves both old and new memories")
        void supersedeMemorySavesBothMemories() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then - save called twice (new memory + old memory)
            verify(memoryRepository, times(2)).save(any(Memory.class));
        }

        @Test
        @DisplayName("supersedeMemory works for CONFIRMED state memory")
        void supersedeMemoryWorksForConfirmedState() {
            // Given - a CONFIRMED memory can also be superseded (explicit correction)
            Memory confirmedMemory = createActiveMemory(new BigDecimal("0.90"));
            confirmedMemory.confirm(); // Now CONFIRMED
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(confirmedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(result.getContent()).isEqualTo(NEW_CONTENT);
            assertThat(confirmedMemory.getState()).isEqualTo(MemoryState.SUPERSEDED);
        }

        @Test
        @DisplayName("supersedeMemory throws EntityNotFoundException for missing memory")
        void supersedeMemoryThrowsWhenMemoryNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(memoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(nonExistentId, NEW_CONTENT, new BigDecimal("1.00")))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Memory not found: " + nonExistentId);
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalStateException when memory is SUPERSEDED")
        void supersedeMemoryThrowsWhenAlreadySuperseded() {
            // Given
            Memory supersededMemory = createActiveMemory(new BigDecimal("0.70"));
            supersededMemory.markSuperseded(UUID.randomUUID());
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(supersededMemory));

            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to SUPERSEDED");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalStateException when memory is ARCHIVED")
        void supersedeMemoryThrowsWhenArchived() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));

            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to SUPERSEDED");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalStateException when memory is EXPIRED")
        void supersedeMemoryThrowsWhenExpired() {
            // Given
            Memory expiredMemory = createActiveMemory(new BigDecimal("0.70"));
            expiredMemory.expire();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(expiredMemory));

            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from EXPIRED to SUPERSEDED");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalStateException when memory is DELETED")
        void supersedeMemoryThrowsWhenDeleted() {
            // Given
            Memory deletedMemory = createActiveMemory(new BigDecimal("0.70"));
            deletedMemory.delete();
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(deletedMemory));

            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to SUPERSEDED");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalArgumentException for null content")
        void supersedeMemoryThrowsForNullContent() {
            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, null, new BigDecimal("1.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("New content cannot be null or empty");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalArgumentException for empty content")
        void supersedeMemoryThrowsForEmptyContent() {
            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, "  ", new BigDecimal("1.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("New content cannot be null or empty");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalArgumentException for null confidence")
        void supersedeMemoryThrowsForNullConfidence() {
            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Confidence score must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalArgumentException for negative confidence")
        void supersedeMemoryThrowsForNegativeConfidence() {
            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("-0.1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Confidence score must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("supersedeMemory throws IllegalArgumentException for confidence above 1.0")
        void supersedeMemoryThrowsForConfidenceAboveOne() {
            // When/Then
            assertThatThrownBy(() -> lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Confidence score must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("supersedeMemory preserves childId from old memory")
        void supersedeMemoryPreservesChildId() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            UUID childId = UUID.randomUUID();
            oldMemory.setChildId(childId);
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(result.getChildId()).isEqualTo(childId);
        }

        @Test
        @DisplayName("supersedeMemory preserves importanceScore from old memory")
        void supersedeMemoryPreservesImportanceScore() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, new BigDecimal("1.00"));

            // Then
            assertThat(result.getImportanceScore()).isEqualTo(oldMemory.getImportanceScore());
        }

        @Test
        @DisplayName("supersedeMemory accepts boundary confidence value 0.0")
        void supersedeMemoryAcceptsZeroConfidence() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, BigDecimal.ZERO);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("supersedeMemory accepts boundary confidence value 1.0")
        void supersedeMemoryAcceptsOneConfidence() {
            // Given
            Memory oldMemory = createActiveMemory(new BigDecimal("0.70"));
            
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(oldMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(NEW_MEMORY_ID);
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.supersedeMemory(MEMORY_ID, NEW_CONTENT, BigDecimal.ONE);

            // Then
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // archiveMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("archiveMemory() method")
    class ArchiveMemoryTests {

        @Test
        @DisplayName("archiveMemory transitions state from ACTIVE to ARCHIVED")
        void archiveMemoryTransitionsFromActiveState() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"ACTIVE\"}");

            // When
            Memory result = lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archiveMemory transitions state from CONFIRMED to ARCHIVED")
        void archiveMemoryTransitionsFromConfirmedState() {
            // Given
            Memory confirmedMemory = createActiveMemory(new BigDecimal("0.90"));
            confirmedMemory.confirm(); // Now CONFIRMED
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(confirmedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"CONFIRMED\"}");

            // When
            Memory result = lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archiveMemory creates audit entry with before/after state snapshots")
        void archiveMemoryCreatesAuditEntry() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            String stateBeforeJson = "{\"state\":\"ACTIVE\",\"confidence_score\":0.70}";
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(stateBeforeJson);

            // When
            lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            verify(auditService).createAuditEntryForArchive(any(Memory.class), eq(ActorType.SYSTEM), eq(stateBeforeJson));
        }

        @Test
        @DisplayName("archiveMemory persists the updated memory")
        void archiveMemoryPersistsMemory() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archiveMemory throws EntityNotFoundException for missing memory")
        void archiveMemoryThrowsWhenMemoryNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(memoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> lifecycleService.archiveMemory(nonExistentId, ActorType.SYSTEM))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Memory not found: " + nonExistentId);
        }

        @Test
        @DisplayName("archiveMemory throws IllegalStateException when memory is already ARCHIVED")
        void archiveMemoryThrowsWhenAlreadyArchived() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to ARCHIVED");
        }

        @Test
        @DisplayName("archiveMemory throws IllegalStateException when memory is DELETED")
        void archiveMemoryThrowsWhenDeleted() {
            // Given
            Memory deletedMemory = createActiveMemory(new BigDecimal("0.70"));
            deletedMemory.delete();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(deletedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to ARCHIVED");
        }

        @Test
        @DisplayName("archiveMemory works for SUPERSEDED memory (valid per state machine)")
        void archiveMemoryWorksWhenSuperseded() {
            // Given - SUPERSEDED → ARCHIVED is valid per SPEC-004 state machine
            Memory supersededMemory = createActiveMemory(new BigDecimal("0.70"));
            supersededMemory.markSuperseded(UUID.randomUUID());
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(supersededMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            Memory result = lifecycleService.archiveMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archiveMemory supports USER actor type for manual archive")
        void archiveMemorySupportsUserActorType() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.archiveMemory(MEMORY_ID, ActorType.USER);

            // Then
            verify(auditService).createAuditEntryForArchive(any(Memory.class), eq(ActorType.USER), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // expireMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("expireMemory() method")
    class ExpireMemoryTests {

        @Test
        @DisplayName("expireMemory transitions state from ACTIVE to EXPIRED")
        void expireMemoryTransitionsState() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.40"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"ACTIVE\"}");

            // When
            Memory result = lifecycleService.expireMemory(MEMORY_ID);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("expireMemory creates audit entry with SYSTEM actor type")
        void expireMemoryCreatesAuditEntry() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.40"));
            String stateBeforeJson = "{\"state\":\"ACTIVE\",\"confidence_score\":0.40}";
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(stateBeforeJson);

            // When
            lifecycleService.expireMemory(MEMORY_ID);

            // Then
            verify(auditService).createAuditEntryForExpire(any(Memory.class), eq(stateBeforeJson));
        }

        @Test
        @DisplayName("expireMemory persists the updated memory")
        void expireMemoryPersistsMemory() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.40"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.expireMemory(MEMORY_ID);

            // Then
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("expireMemory throws EntityNotFoundException for missing memory")
        void expireMemoryThrowsWhenMemoryNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(memoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> lifecycleService.expireMemory(nonExistentId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Memory not found: " + nonExistentId);
        }

        @Test
        @DisplayName("expireMemory throws IllegalStateException when memory is CONFIRMED")
        void expireMemoryThrowsWhenConfirmed() {
            // Given
            Memory confirmedMemory = createActiveMemory(new BigDecimal("0.90"));
            confirmedMemory.confirm();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(confirmedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.expireMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CONFIRMED to EXPIRED");
        }

        @Test
        @DisplayName("expireMemory throws IllegalStateException when memory is already EXPIRED")
        void expireMemoryThrowsWhenAlreadyExpired() {
            // Given
            Memory expiredMemory = createActiveMemory(new BigDecimal("0.40"));
            expiredMemory.expire();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(expiredMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.expireMemory(MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from EXPIRED to EXPIRED");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // deleteMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteMemory() method")
    class DeleteMemoryTests {

        @Test
        @DisplayName("deleteMemory transitions state from ACTIVE to DELETED")
        void deleteMemoryTransitionsFromActiveState() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"ACTIVE\"}");

            // When
            Memory result = lifecycleService.deleteMemory(MEMORY_ID, ActorType.USER);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteMemory transitions state from ARCHIVED to DELETED")
        void deleteMemoryTransitionsFromArchivedState() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"ARCHIVED\"}");

            // When
            Memory result = lifecycleService.deleteMemory(MEMORY_ID, ActorType.USER);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteMemory transitions state from EXPIRED to DELETED")
        void deleteMemoryTransitionsFromExpiredState() {
            // Given
            Memory expiredMemory = createActiveMemory(new BigDecimal("0.40"));
            expiredMemory.expire();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(expiredMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"EXPIRED\"}");

            // When
            Memory result = lifecycleService.deleteMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteMemory transitions state from SUPERSEDED to DELETED")
        void deleteMemoryTransitionsFromSupersededState() {
            // Given
            Memory supersededMemory = createActiveMemory(new BigDecimal("0.70"));
            supersededMemory.markSuperseded(UUID.randomUUID());
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(supersededMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"SUPERSEDED\"}");

            // When
            Memory result = lifecycleService.deleteMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteMemory creates audit entry with before/after state snapshots")
        void deleteMemoryCreatesAuditEntry() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            String stateBeforeJson = "{\"state\":\"ACTIVE\",\"confidence_score\":0.70}";
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(stateBeforeJson);

            // When
            lifecycleService.deleteMemory(MEMORY_ID, ActorType.USER);

            // Then
            verify(auditService).createAuditEntryForDelete(any(Memory.class), eq(ActorType.USER), eq(stateBeforeJson));
        }

        @Test
        @DisplayName("deleteMemory throws EntityNotFoundException for missing memory")
        void deleteMemoryThrowsWhenMemoryNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(memoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> lifecycleService.deleteMemory(nonExistentId, ActorType.USER))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Memory not found: " + nonExistentId);
        }

        @Test
        @DisplayName("deleteMemory throws IllegalStateException when memory is already DELETED")
        void deleteMemoryThrowsWhenAlreadyDeleted() {
            // Given
            Memory deletedMemory = createActiveMemory(new BigDecimal("0.70"));
            deletedMemory.delete();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(deletedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.deleteMemory(MEMORY_ID, ActorType.USER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to DELETED");
        }

        @Test
        @DisplayName("deleteMemory supports SYSTEM actor type for cleanup jobs")
        void deleteMemorySupportsSystemActorType() {
            // Given
            Memory expiredMemory = createActiveMemory(new BigDecimal("0.40"));
            expiredMemory.expire();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(expiredMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.deleteMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            verify(auditService).createAuditEntryForDelete(any(Memory.class), eq(ActorType.SYSTEM), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // reactivateMemory() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("reactivateMemory() method")
    class ReactivateMemoryTests {

        @Test
        @DisplayName("reactivateMemory transitions state from ARCHIVED to ACTIVE")
        void reactivateMemoryFromArchivedState() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"ARCHIVED\"}");

            // When
            Memory result = lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("reactivateMemory transitions state from EXPIRED to ACTIVE")
        void reactivateMemoryFromExpiredState() {
            // Given
            Memory expiredMemory = createActiveMemory(new BigDecimal("0.40"));
            expiredMemory.expire();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(expiredMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{\"state\":\"EXPIRED\"}");

            // When
            Memory result = lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER);

            // Then
            assertThat(result.getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("reactivateMemory creates audit entry with before/after state snapshots")
        void reactivateMemoryCreatesAuditEntry() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            String stateBeforeJson = "{\"state\":\"ARCHIVED\",\"confidence_score\":0.70}";
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(stateBeforeJson);

            // When
            lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER);

            // Then
            verify(auditService).createAuditEntryForReactivate(any(Memory.class), eq(ActorType.USER), eq(stateBeforeJson));
        }

        @Test
        @DisplayName("reactivateMemory persists the updated memory")
        void reactivateMemoryPersistsMemory() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER);

            // Then
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("reactivateMemory throws EntityNotFoundException for missing memory")
        void reactivateMemoryThrowsWhenMemoryNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(memoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> lifecycleService.reactivateMemory(nonExistentId, ActorType.USER))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Memory not found: " + nonExistentId);
        }

        @Test
        @DisplayName("reactivateMemory throws IllegalStateException when memory is ACTIVE")
        void reactivateMemoryThrowsWhenAlreadyActive() {
            // Given
            Memory activeMemory = createActiveMemory(new BigDecimal("0.70"));
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(activeMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ACTIVE to ACTIVE");
        }

        @Test
        @DisplayName("reactivateMemory throws IllegalStateException when memory is CONFIRMED")
        void reactivateMemoryThrowsWhenConfirmed() {
            // Given
            Memory confirmedMemory = createActiveMemory(new BigDecimal("0.90"));
            confirmedMemory.confirm();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(confirmedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CONFIRMED to ACTIVE");
        }

        @Test
        @DisplayName("reactivateMemory throws IllegalStateException when memory is SUPERSEDED")
        void reactivateMemoryThrowsWhenSuperseded() {
            // Given
            Memory supersededMemory = createActiveMemory(new BigDecimal("0.70"));
            supersededMemory.markSuperseded(UUID.randomUUID());
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(supersededMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to ACTIVE");
        }

        @Test
        @DisplayName("reactivateMemory throws IllegalStateException when memory is DELETED")
        void reactivateMemoryThrowsWhenDeleted() {
            // Given
            Memory deletedMemory = createActiveMemory(new BigDecimal("0.70"));
            deletedMemory.delete();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(deletedMemory));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When/Then
            assertThatThrownBy(() -> lifecycleService.reactivateMemory(MEMORY_ID, ActorType.USER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to ACTIVE");
        }

        @Test
        @DisplayName("reactivateMemory supports SYSTEM actor type for automatic reactivation")
        void reactivateMemorySupportsSystemActorType() {
            // Given
            Memory archivedMemory = createActiveMemory(new BigDecimal("0.70"));
            archivedMemory.archive();
            when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(archivedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.reactivateMemory(MEMORY_ID, ActorType.SYSTEM);

            // Then
            verify(auditService).createAuditEntryForReactivate(any(Memory.class), eq(ActorType.SYSTEM), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Audit Entry Verification Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("All state changes create audit entries")
    class AuditEntryVerificationTests {

        /**
         * Validates: Task 5 acceptance criteria - "All state changes create version history entries"
         */
        @Test
        @DisplayName("All lifecycle transitions create audit entries - verify complete coverage")
        void allLifecycleTransitionsCreateAuditEntries() {
            // This test verifies that every state transition method creates an audit entry
            // by checking that the appropriate audit method is called

            // Setup common mocks
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(UUID.randomUUID());
                }
                return m;
            });
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // Test 1: confirmMemory creates audit
            Memory activeForConfirm = createActiveMemory(new BigDecimal("0.70"));
            activeForConfirm.setId(UUID.randomUUID());
            when(memoryRepository.findById(activeForConfirm.getId())).thenReturn(Optional.of(activeForConfirm));
            lifecycleService.confirmMemory(activeForConfirm.getId());
            verify(auditService).createAuditEntryForConfirm(any(), any());

            // Test 2: archiveMemory creates audit
            Memory activeForArchive = createActiveMemory(new BigDecimal("0.70"));
            activeForArchive.setId(UUID.randomUUID());
            when(memoryRepository.findById(activeForArchive.getId())).thenReturn(Optional.of(activeForArchive));
            lifecycleService.archiveMemory(activeForArchive.getId(), ActorType.SYSTEM);
            verify(auditService).createAuditEntryForArchive(any(), any(), any());

            // Test 3: expireMemory creates audit
            Memory activeForExpire = createActiveMemory(new BigDecimal("0.40"));
            activeForExpire.setId(UUID.randomUUID());
            when(memoryRepository.findById(activeForExpire.getId())).thenReturn(Optional.of(activeForExpire));
            lifecycleService.expireMemory(activeForExpire.getId());
            verify(auditService).createAuditEntryForExpire(any(), any());

            // Test 4: deleteMemory creates audit
            Memory activeForDelete = createActiveMemory(new BigDecimal("0.70"));
            activeForDelete.setId(UUID.randomUUID());
            when(memoryRepository.findById(activeForDelete.getId())).thenReturn(Optional.of(activeForDelete));
            lifecycleService.deleteMemory(activeForDelete.getId(), ActorType.USER);
            verify(auditService).createAuditEntryForDelete(any(), any(), any());

            // Test 5: reactivateMemory creates audit
            Memory archivedForReactivate = createActiveMemory(new BigDecimal("0.70"));
            archivedForReactivate.setId(UUID.randomUUID());
            archivedForReactivate.archive();
            when(memoryRepository.findById(archivedForReactivate.getId())).thenReturn(Optional.of(archivedForReactivate));
            lifecycleService.reactivateMemory(archivedForReactivate.getId(), ActorType.USER);
            verify(auditService).createAuditEntryForReactivate(any(), any(), any());

            // Test 6: supersedeMemory creates audit for both old and new memory
            Memory activeForSupersede = createActiveMemory(new BigDecimal("0.70"));
            activeForSupersede.setId(UUID.randomUUID());
            when(memoryRepository.findById(activeForSupersede.getId())).thenReturn(Optional.of(activeForSupersede));
            lifecycleService.supersedeMemory(activeForSupersede.getId(), "Updated content", new BigDecimal("1.00"));
            verify(auditService, atLeastOnce()).createAuditEntryForCreate(any(), eq(ActorType.USER));
            verify(auditService).createAuditEntryForSupersede(any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // deleteAllForFather() Tests - GDPR Erasure
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteAllForFather() method - GDPR Erasure")
    class DeleteAllForFatherTests {

        /**
         * Helper to create a memory with a specific state for a given father.
         */
        private Memory createMemoryWithState(UUID fatherId, MemoryState state, BigDecimal confidence) {
            Memory memory = new Memory(
                    fatherId,
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    "Test content",
                    IMPORTANCE_SCORE,
                    confidence,
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
            memory.setId(UUID.randomUUID());

            // Transition to the desired state
            switch (state) {
                case CONFIRMED:
                    memory.confirm();
                    break;
                case SUPERSEDED:
                    memory.markSuperseded(UUID.randomUUID());
                    break;
                case ARCHIVED:
                    memory.archive();
                    break;
                case EXPIRED:
                    memory.expire();
                    break;
                case DELETED:
                    memory.delete();
                    break;
                case ACTIVE:
                default:
                    // Already ACTIVE by default
                    break;
            }
            return memory;
        }

        @Test
        @DisplayName("deleteAllForFather returns 0 when father has no memories")
        void deleteAllForFatherReturnsZeroWhenNoMemories() {
            // Given
            UUID fatherId = UUID.randomUUID();
            when(memoryRepository.findByFatherId(fatherId)).thenReturn(java.util.List.of());

            // When
            int result = lifecycleService.deleteAllForFather(fatherId);

            // Then
            assertThat(result).isEqualTo(0);
            verify(memoryRepository, never()).save(any(Memory.class));
            verify(auditService, never()).createAuditEntryForDeleteWithFullTracking(
                    any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("deleteAllForFather deletes all ACTIVE memories")
        void deleteAllForFatherDeletesActiveMemories() {
            // Given
            Memory memory1 = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.80"));
            Memory memory2 = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.70"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(memory1, memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(2);
            assertThat(memory1.getState()).isEqualTo(MemoryState.DELETED);
            assertThat(memory2.getState()).isEqualTo(MemoryState.DELETED);
            verify(memoryRepository, times(2)).save(any(Memory.class));
        }

        @Test
        @DisplayName("deleteAllForFather deletes CONFIRMED memories")
        void deleteAllForFatherDeletesConfirmedMemories() {
            // Given
            Memory confirmedMemory = createMemoryWithState(FATHER_ID, MemoryState.CONFIRMED, new BigDecimal("0.90"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(confirmedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(1);
            assertThat(confirmedMemory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteAllForFather deletes SUPERSEDED memories")
        void deleteAllForFatherDeletesSupersededMemories() {
            // Given
            Memory supersededMemory = createMemoryWithState(FATHER_ID, MemoryState.SUPERSEDED, new BigDecimal("0.70"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(supersededMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(1);
            assertThat(supersededMemory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteAllForFather deletes ARCHIVED memories")
        void deleteAllForFatherDeletesArchivedMemories() {
            // Given
            Memory archivedMemory = createMemoryWithState(FATHER_ID, MemoryState.ARCHIVED, new BigDecimal("0.70"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(archivedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(1);
            assertThat(archivedMemory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteAllForFather deletes EXPIRED memories")
        void deleteAllForFatherDeletesExpiredMemories() {
            // Given
            Memory expiredMemory = createMemoryWithState(FATHER_ID, MemoryState.EXPIRED, new BigDecimal("0.40"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(expiredMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(1);
            assertThat(expiredMemory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("deleteAllForFather skips already DELETED memories")
        void deleteAllForFatherSkipsAlreadyDeletedMemories() {
            // Given
            Memory activeMemory = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.80"));
            Memory deletedMemory = createMemoryWithState(FATHER_ID, MemoryState.DELETED, new BigDecimal("0.70"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(activeMemory, deletedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(1); // Only the active memory was deleted
            assertThat(activeMemory.getState()).isEqualTo(MemoryState.DELETED);
            verify(memoryRepository, times(1)).save(any(Memory.class)); // Only one save
        }

        @Test
        @DisplayName("deleteAllForFather handles mix of all states correctly")
        void deleteAllForFatherHandlesMixOfAllStates() {
            // Given - one memory of each state
            Memory activeMemory = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.80"));
            Memory confirmedMemory = createMemoryWithState(FATHER_ID, MemoryState.CONFIRMED, new BigDecimal("0.90"));
            Memory supersededMemory = createMemoryWithState(FATHER_ID, MemoryState.SUPERSEDED, new BigDecimal("0.70"));
            Memory archivedMemory = createMemoryWithState(FATHER_ID, MemoryState.ARCHIVED, new BigDecimal("0.70"));
            Memory expiredMemory = createMemoryWithState(FATHER_ID, MemoryState.EXPIRED, new BigDecimal("0.40"));
            Memory deletedMemory = createMemoryWithState(FATHER_ID, MemoryState.DELETED, new BigDecimal("0.30"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(
                            activeMemory, confirmedMemory, supersededMemory,
                            archivedMemory, expiredMemory, deletedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then - 5 were deleted, 1 was already deleted
            assertThat(result).isEqualTo(5);
            assertThat(activeMemory.getState()).isEqualTo(MemoryState.DELETED);
            assertThat(confirmedMemory.getState()).isEqualTo(MemoryState.DELETED);
            assertThat(supersededMemory.getState()).isEqualTo(MemoryState.DELETED);
            assertThat(archivedMemory.getState()).isEqualTo(MemoryState.DELETED);
            assertThat(expiredMemory.getState()).isEqualTo(MemoryState.DELETED);
            verify(memoryRepository, times(5)).save(any(Memory.class));
        }

        @Test
        @DisplayName("deleteAllForFather creates audit entry for each deletion")
        void deleteAllForFatherCreatesAuditEntries() {
            // Given
            Memory memory1 = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.80"));
            Memory memory2 = createMemoryWithState(FATHER_ID, MemoryState.CONFIRMED, new BigDecimal("0.90"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(memory1, memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.deleteAllForFather(FATHER_ID);

            // Then - verify audit entries with GDPR erasure context
            verify(auditService, times(2)).createAuditEntryForDeleteWithFullTracking(
                    any(Memory.class),
                    any(MemoryState.class),
                    eq(ActorType.USER),
                    eq("USER:gdpr_erasure"),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("deleteAllForFather captures previous state in audit")
        void deleteAllForFatherCapturesPreviousStateInAudit() {
            // Given
            Memory activeMemory = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.80"));
            Memory confirmedMemory = createMemoryWithState(FATHER_ID, MemoryState.CONFIRMED, new BigDecimal("0.90"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(activeMemory, confirmedMemory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.deleteAllForFather(FATHER_ID);

            // Then - verify audit entries capture the correct previous states
            ArgumentCaptor<MemoryState> stateCaptor = ArgumentCaptor.forClass(MemoryState.class);
            verify(auditService, times(2)).createAuditEntryForDeleteWithFullTracking(
                    any(Memory.class),
                    stateCaptor.capture(),
                    eq(ActorType.USER),
                    eq("USER:gdpr_erasure"),
                    any(String.class)
            );

            java.util.List<MemoryState> capturedStates = stateCaptor.getAllValues();
            assertThat(capturedStates).containsExactlyInAnyOrder(MemoryState.ACTIVE, MemoryState.CONFIRMED);
        }

        @Test
        @DisplayName("deleteAllForFather throws IllegalArgumentException for null fatherId")
        void deleteAllForFatherThrowsForNullFatherId() {
            // When/Then
            assertThatThrownBy(() -> lifecycleService.deleteAllForFather(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fatherId cannot be null");

            // Verify no repository calls
            verify(memoryRepository, never()).findByFatherId(any());
        }

        @Test
        @DisplayName("deleteAllForFather is atomic - verifies transactional annotation")
        void deleteAllForFatherIsAtomic() throws NoSuchMethodException {
            // Given - verify the method has @Transactional annotation
            var method = MemoryLifecycleService.class.getDeclaredMethod("deleteAllForFather", UUID.class);
            var transactionalAnnotation = method.getAnnotation(
                    org.springframework.transaction.annotation.Transactional.class);

            // Then
            assertThat(transactionalAnnotation).isNotNull();
        }

        @Test
        @DisplayName("deleteAllForFather returns 0 when all memories are already DELETED")
        void deleteAllForFatherReturnsZeroWhenAllAlreadyDeleted() {
            // Given
            Memory deleted1 = createMemoryWithState(FATHER_ID, MemoryState.DELETED, new BigDecimal("0.70"));
            Memory deleted2 = createMemoryWithState(FATHER_ID, MemoryState.DELETED, new BigDecimal("0.80"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(deleted1, deleted2));

            // When
            int result = lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            assertThat(result).isEqualTo(0);
            verify(memoryRepository, never()).save(any(Memory.class));
            verify(auditService, never()).createAuditEntryForDeleteWithFullTracking(
                    any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("deleteAllForFather persists each memory after deletion")
        void deleteAllForFatherPersistsEachMemory() {
            // Given
            Memory memory1 = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.80"));
            Memory memory2 = createMemoryWithState(FATHER_ID, MemoryState.ACTIVE, new BigDecimal("0.70"));
            Memory memory3 = createMemoryWithState(FATHER_ID, MemoryState.CONFIRMED, new BigDecimal("0.90"));

            when(memoryRepository.findByFatherId(FATHER_ID))
                    .thenReturn(java.util.List.of(memory1, memory2, memory3));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            lifecycleService.deleteAllForFather(FATHER_ID);

            // Then
            ArgumentCaptor<Memory> memoryCaptor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository, times(3)).save(memoryCaptor.capture());

            java.util.List<Memory> savedMemories = memoryCaptor.getAllValues();
            assertThat(savedMemories).allSatisfy(m -> 
                    assertThat(m.getState()).isEqualTo(MemoryState.DELETED));
        }
    }
}
