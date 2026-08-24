package com.dadcoach.memory.audit;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryAuditService}.
 *
 * <p>These tests verify the audit logging functionality defined in SPEC-004:
 * <ul>
 *   <li>REQ-24: Every memory lifecycle event SHALL produce a durable audit record</li>
 *   <li>REQ-2 Criteria 9: Audit entries contain: operation_type, from_state, to_state, trigger_type, triggered_by</li>
 *   <li>Before/after state snapshots are captured</li>
 *   <li>Audit service handles failures gracefully</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements REQ-24, REQ-2, Task 10</strong>
 *
 * @see MemoryAuditService
 * @see MemoryAuditLog
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryAuditService Tests")
class MemoryAuditServiceTest {

    @Mock
    private MemoryAuditRepository auditRepository;

    private ObjectMapper objectMapper;
    private MemoryAuditService auditService;

    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final UUID TEST_MEMORY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditService = new MemoryAuditService(auditRepository, objectMapper);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Create Audit Entry for CREATE Event
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create Audit Entry Tests")
    class CreateAuditEntryTests {

        @Test
        @DisplayName("Should create audit entry for new memory with AI actor")
        void shouldCreateAuditEntryForNewMemory() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            
            MemoryAuditLog savedEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");
            savedEntry.setId(UUID.randomUUID());
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenReturn(savedEntry);

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForCreate(memory, ActorType.AI);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getOperationType()).isEqualTo(EventType.CREATE);
            assertThat(result.get().getTriggerType()).isEqualTo(ActorType.AI);
            
            // Verify the saved entry has correct fields
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
            assertThat(captured.getFatherId()).isEqualTo(TEST_FATHER_ID);
            assertThat(captured.getOperationType()).isEqualTo(EventType.CREATE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.AI);
            assertThat(captured.getStateBefore()).isNull(); // CREATE has no before state
            assertThat(captured.getStateAfter()).isNotNull();
            assertThat(captured.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should include state snapshot in audit entry")
        void shouldIncludeStateSnapshotInAuditEntry() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Lucas loves dinosaurs");
            memory.setImportanceScore(6);
            memory.setConfidenceScore(new BigDecimal("0.85"));
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForCreate(memory, ActorType.AI);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            String stateAfter = captor.getValue().getStateAfter();
            assertThat(stateAfter).contains("\"content\":\"Lucas loves dinosaurs\"");
            assertThat(stateAfter).contains("\"importance_score\":6");
            assertThat(stateAfter).contains("\"confidence_score\":0.85");
            assertThat(stateAfter).contains("\"category\":\"PREFERENCE\"");
            assertThat(stateAfter).contains("\"state\":\"ACTIVE\"");
        }

        @Test
        @DisplayName("Should return empty when repository is null")
        void shouldReturnEmptyWhenRepositoryIsNull() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            // Act
            Optional<MemoryAuditLog> result = serviceWithoutRepo.createAuditEntryForCreate(memory, ActorType.AI);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for null memory")
        void shouldReturnEmptyForNullMemory() {
            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForCreate(null, ActorType.AI);

            // Assert
            assertThat(result).isEmpty();
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return empty for memory without ID")
        void shouldReturnEmptyForMemoryWithoutId() {
            // Arrange
            Memory memory = createTestMemory();
            // memory.setId() not called - ID is null

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForCreate(memory, ActorType.AI);

            // Assert
            assertThat(result).isEmpty();
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle repository exception gracefully")
        void shouldHandleRepositoryExceptionGracefully() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            
            when(auditRepository.save(any())).thenThrow(new RuntimeException("Database error"));

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForCreate(memory, ActorType.AI);

            // Assert
            assertThat(result).isEmpty();
            // No exception should propagate
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Create Audit Entry with Before/After States
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audit Entry with State Snapshots Tests")
    class AuditEntryWithStateSnapshotsTests {

        @Test
        @DisplayName("Should create UPDATE audit entry with before and after states")
        void shouldCreateUpdateAuditEntryWithStates() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            String stateBefore = "{\"confidence_score\":0.5}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForUpdate(
                    memory, ActorType.USER, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getOperationType()).isEqualTo(EventType.UPDATE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
            assertThat(captured.getStateBefore()).isEqualTo(stateBefore);
            assertThat(captured.getStateAfter()).isNotNull();
        }

        @Test
        @DisplayName("Should create CONFIRM audit entry with USER actor")
        void shouldCreateConfirmAuditEntryWithUserActor() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.CONFIRMED);
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForConfirm(memory, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getOperationType()).isEqualTo(EventType.CONFIRM);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
        }

        @Test
        @DisplayName("Should create ARCHIVE audit entry with SYSTEM actor")
        void shouldCreateArchiveAuditEntryWithSystemActor() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.ARCHIVED);
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForArchive(
                    memory, ActorType.SYSTEM, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getOperationType()).isEqualTo(EventType.ARCHIVE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.SYSTEM);
        }

        @Test
        @DisplayName("Should create EXPIRE audit entry with SYSTEM actor")
        void shouldCreateExpireAuditEntryWithSystemActor() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.EXPIRED);
            String stateBefore = "{\"state\":\"ACTIVE\",\"confidence_score\":0.4}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForExpire(memory, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getOperationType()).isEqualTo(EventType.EXPIRE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.SYSTEM);
        }

        @Test
        @DisplayName("Should create DELETE audit entry")
        void shouldCreateDeleteAuditEntry() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.DELETED);
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForDelete(
                    memory, ActorType.USER, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getOperationType()).isEqualTo(EventType.DELETE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
        }

        @Test
        @DisplayName("Should create SUPERSEDE audit entry")
        void shouldCreateSupersedeAuditEntry() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.SUPERSEDED);
            memory.setSupersededBy(UUID.randomUUID());
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForSupersede(
                    memory, ActorType.AI, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            assertThat(captured.getOperationType()).isEqualTo(EventType.SUPERSEDE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.AI);
            assertThat(captured.getStateAfter()).contains("superseded_by");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Full State Transition Tracking (SPEC-004 Req 2 Criteria 9)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Transition Tracking Tests (Req 2 Criteria 9)")
    class StateTransitionTrackingTests {

        @Test
        @DisplayName("Should record operation_type, from_state, to_state, trigger_type, triggered_by for CREATE")
        void shouldRecordAllRequiredFieldsForCreate() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.ACTIVE);
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForCreateWithFullTracking(
                    memory, ActorType.AI, "AI:extraction");

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            // Verify all required fields per SPEC-004 Req 2 Criteria 9
            assertThat(captured.getOperationType()).isEqualTo(EventType.CREATE); // operation_type
            assertThat(captured.getFromState()).isNull();                        // from_state (null for CREATE)
            assertThat(captured.getToState()).isEqualTo(MemoryState.ACTIVE);     // to_state
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.AI);       // trigger_type
            assertThat(captured.getTriggeredBy()).isEqualTo("AI:extraction");    // triggered_by
            assertThat(captured.getCreatedAt()).isNotNull();                     // timestamp
        }

        @Test
        @DisplayName("Should record state transition from ACTIVE to CONFIRMED")
        void shouldRecordStateTransitionActiveToConfirmed() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.CONFIRMED);
            String stateBefore = "{\"state\":\"ACTIVE\",\"confidence_score\":0.7}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForConfirmWithFullTracking(
                    memory, MemoryState.ACTIVE, "USER:confirmation", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            assertThat(captured.getOperationType()).isEqualTo(EventType.CONFIRM);
            assertThat(captured.getFromState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(captured.getToState()).isEqualTo(MemoryState.CONFIRMED);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
            assertThat(captured.getTriggeredBy()).isEqualTo("USER:confirmation");
        }

        @Test
        @DisplayName("Should record state transition from ACTIVE to ARCHIVED")
        void shouldRecordStateTransitionActiveToArchived() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.ARCHIVED);
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForArchiveWithFullTracking(
                    memory, MemoryState.ACTIVE, ActorType.SYSTEM, "SYSTEM:capacity_enforcement", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            assertThat(captured.getOperationType()).isEqualTo(EventType.ARCHIVE);
            assertThat(captured.getFromState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(captured.getToState()).isEqualTo(MemoryState.ARCHIVED);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.SYSTEM);
            assertThat(captured.getTriggeredBy()).isEqualTo("SYSTEM:capacity_enforcement");
        }

        @Test
        @DisplayName("Should record state transition from ACTIVE to SUPERSEDED")
        void shouldRecordStateTransitionActiveToSuperseded() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.SUPERSEDED);
            memory.setSupersededBy(UUID.randomUUID());
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForSupersedeWithFullTracking(
                    memory, MemoryState.ACTIVE, ActorType.USER, "USER:correction", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            assertThat(captured.getOperationType()).isEqualTo(EventType.SUPERSEDE);
            assertThat(captured.getFromState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(captured.getToState()).isEqualTo(MemoryState.SUPERSEDED);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
            assertThat(captured.getTriggeredBy()).isEqualTo("USER:correction");
        }

        @Test
        @DisplayName("Should record state transition from ACTIVE to EXPIRED")
        void shouldRecordStateTransitionActiveToExpired() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.EXPIRED);
            String stateBefore = "{\"state\":\"ACTIVE\",\"confidence_score\":0.4}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForExpireWithFullTracking(
                    memory, MemoryState.ACTIVE, "SYSTEM:decay_job", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            assertThat(captured.getOperationType()).isEqualTo(EventType.EXPIRE);
            assertThat(captured.getFromState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(captured.getToState()).isEqualTo(MemoryState.EXPIRED);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.SYSTEM);
            assertThat(captured.getTriggeredBy()).isEqualTo("SYSTEM:decay_job");
        }

        @Test
        @DisplayName("Should record state transition from ACTIVE to DELETED")
        void shouldRecordStateTransitionActiveToDeleted() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.DELETED);
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForDeleteWithFullTracking(
                    memory, MemoryState.ACTIVE, ActorType.USER, "USER:deletion_request", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            assertThat(captured.getOperationType()).isEqualTo(EventType.DELETE);
            assertThat(captured.getFromState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(captured.getToState()).isEqualTo(MemoryState.DELETED);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
            assertThat(captured.getTriggeredBy()).isEqualTo("USER:deletion_request");
        }

        @Test
        @DisplayName("Should record state transition from ARCHIVED to ACTIVE (reactivation)")
        void shouldRecordStateTransitionArchivedToActive() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.ACTIVE);
            String stateBefore = "{\"state\":\"ARCHIVED\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForReactivateWithFullTracking(
                    memory, MemoryState.ARCHIVED, ActorType.USER, "USER:re_reference", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            assertThat(captured.getOperationType()).isEqualTo(EventType.REACTIVATE);
            assertThat(captured.getFromState()).isEqualTo(MemoryState.ARCHIVED);
            assertThat(captured.getToState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(captured.getTriggerType()).isEqualTo(ActorType.USER);
            assertThat(captured.getTriggeredBy()).isEqualTo("USER:re_reference");
        }

        @Test
        @DisplayName("Should record triggered_by with system job reference")
        void shouldRecordTriggeredByWithSystemJobReference() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setState(MemoryState.EXPIRED);
            String stateBefore = "{\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            MemoryAuditLog result = auditService.createAuditEntryForExpireWithFullTracking(
                    memory, MemoryState.ACTIVE, "SYSTEM:expiration_job", stateBefore);

            // Assert
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            assertThat(captor.getValue().getTriggeredBy()).isEqualTo("SYSTEM:expiration_job");
        }

        @Test
        @DisplayName("Should throw exception when triggeredBy is blank")
        void shouldThrowExceptionWhenTriggeredByIsBlank() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.createAuditEntryForCreateWithFullTracking(memory, ActorType.AI, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("triggeredBy is required");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Serialize Memory State
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Serialize Memory State Tests")
    class SerializeMemoryStateTests {

        @Test
        @DisplayName("Should serialize all key memory fields including state for transition tracking")
        void shouldSerializeAllKeyMemoryFields() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Test content");
            memory.setImportanceScore(7);
            memory.setConfidenceScore(new BigDecimal("0.90"));

            // Act
            String state = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(state).contains("\"content\":\"Test content\"");
            assertThat(state).contains("\"category\":\"PREFERENCE\"");
            assertThat(state).contains("\"state\":\"ACTIVE\""); // Important for from_state extraction
            assertThat(state).contains("\"importance_score\":7");
            assertThat(state).contains("\"confidence_score\":0.9");
            assertThat(state).contains("\"subject_type\":\"CHILD\"");
        }

        @Test
        @DisplayName("Should handle null memory")
        void shouldHandleNullMemory() {
            // Act
            String state = auditService.serializeMemoryState(null);

            // Assert
            assertThat(state).isNull();
        }

        @Test
        @DisplayName("Should include child_id when present")
        void shouldIncludeChildIdWhenPresent() {
            // Arrange
            UUID childId = UUID.randomUUID();
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setChildId(childId);

            // Act
            String state = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(state).contains("\"child_id\":\"" + childId + "\"");
        }

        @Test
        @DisplayName("Should include superseded_by when present")
        void shouldIncludeSupersededByWhenPresent() {
            // Arrange
            UUID supersedingId = UUID.randomUUID();
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setSupersededBy(supersedingId);

            // Act
            String state = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(state).contains("\"superseded_by\":\"" + supersedingId + "\"");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Query Methods
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("Should get audit history for memory")
        void shouldGetAuditHistoryForMemory() {
            // Arrange
            MemoryAuditLog entry1 = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");
            MemoryAuditLog entry2 = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.UPDATE, ActorType.USER, "{}", "{}");
            
            when(auditRepository.findByMemoryIdOrderByCreatedAtAsc(TEST_MEMORY_ID))
                    .thenReturn(List.of(entry1, entry2));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(history).hasSize(2);
            assertThat(history.get(0).getOperationType()).isEqualTo(EventType.CREATE);
            assertThat(history.get(1).getOperationType()).isEqualTo(EventType.UPDATE);
        }

        @Test
        @DisplayName("Should get audit history for father")
        void shouldGetAuditHistoryForFather() {
            // Arrange
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");
            
            when(auditRepository.findByFatherIdOrderByCreatedAtDesc(TEST_FATHER_ID))
                    .thenReturn(List.of(entry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForFather(TEST_FATHER_ID);

            // Assert
            assertThat(history).hasSize(1);
            assertThat(history.get(0).getFatherId()).isEqualTo(TEST_FATHER_ID);
        }

        @Test
        @DisplayName("Should return empty list when repository is null")
        void shouldReturnEmptyListWhenRepositoryIsNull() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);

            // Act
            List<MemoryAuditLog> memoryHistory = serviceWithoutRepo.getAuditHistoryForMemory(TEST_MEMORY_ID);
            List<MemoryAuditLog> fatherHistory = serviceWithoutRepo.getAuditHistoryForFather(TEST_FATHER_ID);

            // Assert
            assertThat(memoryHistory).isEmpty();
            assertThat(fatherHistory).isEmpty();
        }

        @Test
        @DisplayName("Should count audit entries for memory")
        void shouldCountAuditEntriesForMemory() {
            // Arrange
            when(auditRepository.countByMemoryId(TEST_MEMORY_ID)).thenReturn(5L);

            // Act
            long count = auditService.countAuditEntriesForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return 0 count when repository is null")
        void shouldReturnZeroCountWhenRepositoryIsNull() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);

            // Act
            long count = serviceWithoutRepo.countAuditEntriesForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(count).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: MemoryAuditLog Entity Field Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MemoryAuditLog Entity Tests")
    class MemoryAuditLogEntityTests {

        @Test
        @DisplayName("Should create audit log with all required fields")
        void shouldCreateAuditLogWithAllRequiredFields() {
            // Arrange & Act
            MemoryAuditLog auditLog = new MemoryAuditLog(
                    TEST_MEMORY_ID,
                    TEST_FATHER_ID,
                    EventType.CONFIRM,
                    MemoryState.ACTIVE,      // from_state
                    MemoryState.CONFIRMED,   // to_state
                    ActorType.USER,          // trigger_type
                    "USER:confirmation",     // triggered_by
                    "{\"state\":\"ACTIVE\"}", // state_before
                    "{\"state\":\"CONFIRMED\"}" // state_after
            );

            // Assert
            assertThat(auditLog.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
            assertThat(auditLog.getFatherId()).isEqualTo(TEST_FATHER_ID);
            assertThat(auditLog.getOperationType()).isEqualTo(EventType.CONFIRM);
            assertThat(auditLog.getFromState()).isEqualTo(MemoryState.ACTIVE);
            assertThat(auditLog.getToState()).isEqualTo(MemoryState.CONFIRMED);
            assertThat(auditLog.getTriggerType()).isEqualTo(ActorType.USER);
            assertThat(auditLog.getTriggeredBy()).isEqualTo("USER:confirmation");
            assertThat(auditLog.getStateBefore()).contains("ACTIVE");
            assertThat(auditLog.getStateAfter()).contains("CONFIRMED");
            assertThat(auditLog.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should provide backward-compatible getEventType() and getActorType() methods")
        void shouldProvideBackwardCompatibleMethods() {
            // Arrange
            MemoryAuditLog auditLog = new MemoryAuditLog(
                    TEST_MEMORY_ID,
                    TEST_FATHER_ID,
                    EventType.CREATE,
                    null,
                    MemoryState.ACTIVE,
                    ActorType.AI,
                    "AI:extraction",
                    null,
                    "{}"
            );

            // Assert - backward compatible getters should work
            assertThat(auditLog.getEventType()).isEqualTo(EventType.CREATE);  // Deprecated alias
            assertThat(auditLog.getActorType()).isEqualTo(ActorType.AI);      // Deprecated alias
            assertThat(auditLog.getOperationType()).isEqualTo(EventType.CREATE); // New name
            assertThat(auditLog.getTriggerType()).isEqualTo(ActorType.AI);       // New name
        }

        @Test
        @DisplayName("Should have correct toString representation")
        void shouldHaveCorrectToStringRepresentation() {
            // Arrange
            MemoryAuditLog auditLog = new MemoryAuditLog(
                    TEST_MEMORY_ID,
                    TEST_FATHER_ID,
                    EventType.DELETE,
                    MemoryState.ACTIVE,
                    MemoryState.DELETED,
                    ActorType.USER,
                    "USER:deletion_request",
                    "{}",
                    "{}"
            );

            // Act
            String toString = auditLog.toString();

            // Assert
            assertThat(toString).contains("operationType=DELETE");
            assertThat(toString).contains("fromState=ACTIVE");
            assertThat(toString).contains("toState=DELETED");
            assertThat(toString).contains("triggerType=USER");
            assertThat(toString).contains("triggeredBy='USER:deletion_request'");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Version History Snapshots (SPEC-004 Task 14 - Audit & Observability)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests for verifying that version history snapshots capture content, confidence_score,
     * and importance_score at each change.
     *
     * <p><strong>Validates: Task 14 - Audit & Observability</strong>
     * <p>The audit log's state_before and state_after JSON fields should capture key memory fields
     * including content, confidence_score, and importance_score.
     */
    @Nested
    @DisplayName("Version History Snapshots Tests (Task 14)")
    class VersionHistorySnapshotsTests {

        @Test
        @DisplayName("Should capture content in state_after JSON snapshot")
        void shouldCaptureContentInStateAfterJson() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Lucas loves playing with dinosaurs");

            // Act
            String stateAfter = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(stateAfter).isNotNull();
            assertThat(stateAfter).contains("\"content\":\"Lucas loves playing with dinosaurs\"");
        }

        @Test
        @DisplayName("Should capture confidence_score in state_after JSON snapshot")
        void shouldCaptureConfidenceScoreInStateAfterJson() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setConfidenceScore(new BigDecimal("0.85"));

            // Act
            String stateAfter = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(stateAfter).isNotNull();
            assertThat(stateAfter).contains("\"confidence_score\":0.85");
        }

        @Test
        @DisplayName("Should capture importance_score in state_after JSON snapshot")
        void shouldCaptureImportanceScoreInStateAfterJson() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setImportanceScore(8);

            // Act
            String stateAfter = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(stateAfter).isNotNull();
            assertThat(stateAfter).contains("\"importance_score\":8");
        }

        @Test
        @DisplayName("Should capture all three key fields (content, confidence_score, importance_score) in snapshot")
        void shouldCaptureAllThreeKeyFieldsInSnapshot() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Dad prefers morning missions");
            memory.setConfidenceScore(new BigDecimal("0.95"));
            memory.setImportanceScore(7);

            // Act
            String stateAfter = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(stateAfter).isNotNull();
            // Verify all three key fields are present
            assertThat(stateAfter).contains("\"content\":\"Dad prefers morning missions\"");
            assertThat(stateAfter).contains("\"confidence_score\":0.95");
            assertThat(stateAfter).contains("\"importance_score\":7");
        }

        @Test
        @DisplayName("Should capture state_before with original values in UPDATE audit entry")
        void shouldCaptureStateBeforeWithOriginalValuesInUpdateAuditEntry() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Updated content");
            memory.setConfidenceScore(new BigDecimal("0.90"));
            memory.setImportanceScore(6);
            
            String stateBefore = "{\"content\":\"Original content\",\"confidence_score\":0.70,\"importance_score\":5}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForUpdate(
                    memory, ActorType.USER, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            // Verify state_before captures original values
            assertThat(captured.getStateBefore()).contains("\"content\":\"Original content\"");
            assertThat(captured.getStateBefore()).contains("\"confidence_score\":0.70");
            assertThat(captured.getStateBefore()).contains("\"importance_score\":5");
            
            // Verify state_after captures new values
            assertThat(captured.getStateAfter()).contains("\"content\":\"Updated content\"");
            assertThat(captured.getStateAfter()).contains("\"confidence_score\":0.9");
            assertThat(captured.getStateAfter()).contains("\"importance_score\":6");
        }

        @Test
        @DisplayName("Should track confidence_score change from 0.70 to 1.0 on CONFIRM")
        void shouldTrackConfidenceScoreChangeOnConfirm() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Lucas likes dinosaurs");
            memory.setConfidenceScore(new BigDecimal("1.0")); // After confirmation
            memory.setImportanceScore(6);
            memory.setState(MemoryState.CONFIRMED);
            
            String stateBefore = "{\"content\":\"Lucas likes dinosaurs\",\"confidence_score\":0.70,\"importance_score\":6,\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForConfirm(memory, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            // state_before shows confidence was 0.70
            assertThat(captured.getStateBefore()).contains("\"confidence_score\":0.70");
            // state_after shows confidence is now 1.0
            assertThat(captured.getStateAfter()).contains("\"confidence_score\":1.0");
        }

        @Test
        @DisplayName("Should track importance_score increase from 5 to 7")
        void shouldTrackImportanceScoreIncrease() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Family routine pattern");
            memory.setConfidenceScore(new BigDecimal("0.80"));
            memory.setImportanceScore(7); // Increased due to repeated references
            
            String stateBefore = "{\"content\":\"Family routine pattern\",\"confidence_score\":0.80,\"importance_score\":5}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForUpdate(
                    memory, ActorType.SYSTEM, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            // state_before shows importance was 5
            assertThat(captured.getStateBefore()).contains("\"importance_score\":5");
            // state_after shows importance is now 7
            assertThat(captured.getStateAfter()).contains("\"importance_score\":7");
        }

        @Test
        @DisplayName("Should preserve content during state transitions")
        void shouldPreserveContentDuringStateTransitions() {
            // Arrange
            String originalContent = "Lucas enjoys reading before bed";
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent(originalContent);
            memory.setConfidenceScore(new BigDecimal("0.40")); // Low confidence
            memory.setImportanceScore(4);
            memory.setState(MemoryState.EXPIRED);
            
            String stateBefore = "{\"content\":\"" + originalContent + "\",\"confidence_score\":0.45,\"importance_score\":4,\"state\":\"ACTIVE\"}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForExpire(memory, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            // Both before and after should contain the same content
            assertThat(captured.getStateBefore()).contains("\"content\":\"" + originalContent + "\"");
            assertThat(captured.getStateAfter()).contains("\"content\":\"" + originalContent + "\"");
        }

        @Test
        @DisplayName("Should handle null confidence_score gracefully in serialization")
        void shouldHandleNullConfidenceScoreGracefully() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Test content");
            memory.setConfidenceScore(null);
            memory.setImportanceScore(5);

            // Act
            String stateAfter = auditService.serializeMemoryState(memory);

            // Assert
            assertThat(stateAfter).isNotNull();
            assertThat(stateAfter).contains("\"content\":\"Test content\"");
            assertThat(stateAfter).contains("\"importance_score\":5");
            // confidence_score should be null in the JSON
            assertThat(stateAfter).contains("\"confidence_score\":null");
        }

        @Test
        @DisplayName("Should handle edge case importance scores (1 and 10)")
        void shouldHandleEdgeCaseImportanceScores() {
            // Arrange - minimum importance
            Memory memoryMin = createTestMemory();
            memoryMin.setId(TEST_MEMORY_ID);
            memoryMin.setImportanceScore(1);

            // Arrange - maximum importance
            Memory memoryMax = createTestMemory();
            memoryMax.setId(UUID.randomUUID());
            memoryMax.setImportanceScore(10);

            // Act
            String stateMin = auditService.serializeMemoryState(memoryMin);
            String stateMax = auditService.serializeMemoryState(memoryMax);

            // Assert
            assertThat(stateMin).contains("\"importance_score\":1");
            assertThat(stateMax).contains("\"importance_score\":10");
        }

        @Test
        @DisplayName("Should handle edge case confidence scores (0.0 and 1.0)")
        void shouldHandleEdgeCaseConfidenceScores() {
            // Arrange - minimum confidence
            Memory memoryMin = createTestMemory();
            memoryMin.setId(TEST_MEMORY_ID);
            memoryMin.setConfidenceScore(new BigDecimal("0.00"));

            // Arrange - maximum confidence
            Memory memoryMax = createTestMemory();
            memoryMax.setId(UUID.randomUUID());
            memoryMax.setConfidenceScore(new BigDecimal("1.00"));

            // Act
            String stateMin = auditService.serializeMemoryState(memoryMin);
            String stateMax = auditService.serializeMemoryState(memoryMax);

            // Assert
            assertThat(stateMin).contains("\"confidence_score\":0.0");
            assertThat(stateMax).contains("\"confidence_score\":1.0");
        }

        @Test
        @DisplayName("Should track content change in SUPERSEDE operation")
        void shouldTrackContentChangeInSupersedeOperation() {
            // Arrange
            UUID supersedingId = UUID.randomUUID();
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Lucas's favorite color is blue");
            memory.setConfidenceScore(new BigDecimal("1.0"));
            memory.setImportanceScore(6);
            memory.setState(MemoryState.SUPERSEDED);
            memory.setSupersededBy(supersedingId);
            
            String stateBefore = "{\"content\":\"Lucas's favorite color is red\",\"confidence_score\":0.70,\"importance_score\":6}";
            
            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForSupersede(
                    memory, ActorType.USER, stateBefore);

            // Assert
            assertThat(result).isPresent();
            
            ArgumentCaptor<MemoryAuditLog> captor = ArgumentCaptor.forClass(MemoryAuditLog.class);
            verify(auditRepository).save(captor.capture());
            
            MemoryAuditLog captured = captor.getValue();
            
            // state_before shows old content
            assertThat(captured.getStateBefore()).contains("\"content\":\"Lucas's favorite color is red\"");
            // state_after shows superseded_by reference (not new content as memory is superseded)
            assertThat(captured.getStateAfter()).contains("\"superseded_by\":\"" + supersedingId + "\"");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Version History Retrieval (SPEC-004 Task 14 - Audit & Observability)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests for verifying that version history can be retrieved and shows changes over time.
     *
     * <p><strong>Validates: Task 14 - Audit & Observability</strong>
     */
    @Nested
    @DisplayName("Version History Retrieval Tests (Task 14)")
    class VersionHistoryRetrievalTests {

        @Test
        @DisplayName("Should retrieve version history showing confidence changes over time")
        void shouldRetrieveVersionHistoryShowingConfidenceChangesOverTime() {
            // Arrange
            MemoryAuditLog createEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI,
                    null, // no state before
                    "{\"content\":\"Lucas likes soccer\",\"confidence_score\":0.70,\"importance_score\":5}"
            );
            
            MemoryAuditLog updateEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.UPDATE, ActorType.USER,
                    "{\"content\":\"Lucas likes soccer\",\"confidence_score\":0.70,\"importance_score\":5}",
                    "{\"content\":\"Lucas likes soccer\",\"confidence_score\":0.85,\"importance_score\":5}"
            );
            
            MemoryAuditLog confirmEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CONFIRM, ActorType.USER,
                    "{\"content\":\"Lucas likes soccer\",\"confidence_score\":0.85,\"importance_score\":5}",
                    "{\"content\":\"Lucas likes soccer\",\"confidence_score\":1.0,\"importance_score\":5}"
            );
            
            when(auditRepository.findByMemoryIdOrderByCreatedAtAsc(TEST_MEMORY_ID))
                    .thenReturn(List.of(createEntry, updateEntry, confirmEntry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(history).hasSize(3);
            
            // First entry: CREATE with initial confidence 0.70
            assertThat(history.get(0).getOperationType()).isEqualTo(EventType.CREATE);
            assertThat(history.get(0).getStateBefore()).isNull();
            assertThat(history.get(0).getStateAfter()).contains("\"confidence_score\":0.70");
            
            // Second entry: UPDATE with confidence increased to 0.85
            assertThat(history.get(1).getOperationType()).isEqualTo(EventType.UPDATE);
            assertThat(history.get(1).getStateBefore()).contains("\"confidence_score\":0.70");
            assertThat(history.get(1).getStateAfter()).contains("\"confidence_score\":0.85");
            
            // Third entry: CONFIRM with confidence at 1.0
            assertThat(history.get(2).getOperationType()).isEqualTo(EventType.CONFIRM);
            assertThat(history.get(2).getStateBefore()).contains("\"confidence_score\":0.85");
            assertThat(history.get(2).getStateAfter()).contains("\"confidence_score\":1.0");
        }

        @Test
        @DisplayName("Should retrieve version history showing importance score changes over time")
        void shouldRetrieveVersionHistoryShowingImportanceChangesOverTime() {
            // Arrange
            MemoryAuditLog createEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI,
                    null,
                    "{\"content\":\"Father's parenting goal\",\"confidence_score\":1.0,\"importance_score\":7}"
            );
            
            MemoryAuditLog updateEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.UPDATE, ActorType.SYSTEM,
                    "{\"content\":\"Father's parenting goal\",\"confidence_score\":1.0,\"importance_score\":7}",
                    "{\"content\":\"Father's parenting goal\",\"confidence_score\":1.0,\"importance_score\":8}"
            );
            
            when(auditRepository.findByMemoryIdOrderByCreatedAtAsc(TEST_MEMORY_ID))
                    .thenReturn(List.of(createEntry, updateEntry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(history).hasSize(2);
            
            // First entry: CREATE with importance 7
            assertThat(history.get(0).getStateAfter()).contains("\"importance_score\":7");
            
            // Second entry: UPDATE with importance increased to 8
            assertThat(history.get(1).getStateBefore()).contains("\"importance_score\":7");
            assertThat(history.get(1).getStateAfter()).contains("\"importance_score\":8");
        }

        @Test
        @DisplayName("Should retrieve version history showing content changes over time")
        void shouldRetrieveVersionHistoryShowingContentChangesOverTime() {
            // Arrange
            MemoryAuditLog createEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI,
                    null,
                    "{\"content\":\"Lucas enjoys art class\",\"confidence_score\":0.80,\"importance_score\":5}"
            );
            
            MemoryAuditLog supersedeEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.SUPERSEDE, ActorType.USER,
                    "{\"content\":\"Lucas enjoys art class\",\"confidence_score\":0.80,\"importance_score\":5}",
                    "{\"content\":\"Lucas prefers music over art\",\"confidence_score\":1.0,\"importance_score\":5,\"superseded_by\":\"new-memory-id\"}"
            );
            
            when(auditRepository.findByMemoryIdOrderByCreatedAtAsc(TEST_MEMORY_ID))
                    .thenReturn(List.of(createEntry, supersedeEntry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(history).hasSize(2);
            
            // First entry: Original content
            assertThat(history.get(0).getStateAfter()).contains("\"content\":\"Lucas enjoys art class\"");
            
            // Second entry: Content correction
            assertThat(history.get(1).getStateBefore()).contains("\"content\":\"Lucas enjoys art class\"");
            assertThat(history.get(1).getStateAfter()).contains("\"content\":\"Lucas prefers music over art\"");
        }

        @Test
        @DisplayName("Should retrieve complete lifecycle history from CREATE to DELETE")
        void shouldRetrieveCompleteLifecycleHistory() {
            // Arrange
            MemoryAuditLog createEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI,
                    null,
                    "{\"content\":\"Temporary context\",\"confidence_score\":0.50,\"importance_score\":3,\"state\":\"ACTIVE\"}"
            );
            
            MemoryAuditLog expireEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.EXPIRE, ActorType.SYSTEM,
                    "{\"content\":\"Temporary context\",\"confidence_score\":0.40,\"importance_score\":3,\"state\":\"ACTIVE\"}",
                    "{\"content\":\"Temporary context\",\"confidence_score\":0.40,\"importance_score\":3,\"state\":\"EXPIRED\"}"
            );
            
            MemoryAuditLog deleteEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.DELETE, ActorType.SYSTEM,
                    "{\"content\":\"Temporary context\",\"confidence_score\":0.40,\"importance_score\":3,\"state\":\"EXPIRED\"}",
                    "{\"content\":null,\"confidence_score\":null,\"importance_score\":3,\"state\":\"DELETED\"}"
            );
            
            when(auditRepository.findByMemoryIdOrderByCreatedAtAsc(TEST_MEMORY_ID))
                    .thenReturn(List.of(createEntry, expireEntry, deleteEntry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(history).hasSize(3);
            
            // Verify lifecycle progression
            assertThat(history.get(0).getOperationType()).isEqualTo(EventType.CREATE);
            assertThat(history.get(1).getOperationType()).isEqualTo(EventType.EXPIRE);
            assertThat(history.get(2).getOperationType()).isEqualTo(EventType.DELETE);
            
            // Verify confidence decay visible in history
            assertThat(history.get(0).getStateAfter()).contains("\"confidence_score\":0.50");
            assertThat(history.get(1).getStateBefore()).contains("\"confidence_score\":0.40");
        }

        @Test
        @DisplayName("Should retrieve version history for multiple memories of same father")
        void shouldRetrieveVersionHistoryForMultipleMemoriesOfSameFather() {
            // Arrange
            UUID memoryId1 = UUID.randomUUID();
            UUID memoryId2 = UUID.randomUUID();
            
            MemoryAuditLog entry1 = new MemoryAuditLog(
                    memoryId1, TEST_FATHER_ID, EventType.CREATE, ActorType.AI,
                    null,
                    "{\"content\":\"Memory 1\",\"confidence_score\":0.80,\"importance_score\":5}"
            );
            
            MemoryAuditLog entry2 = new MemoryAuditLog(
                    memoryId2, TEST_FATHER_ID, EventType.CREATE, ActorType.AI,
                    null,
                    "{\"content\":\"Memory 2\",\"confidence_score\":0.90,\"importance_score\":7}"
            );
            
            MemoryAuditLog entry3 = new MemoryAuditLog(
                    memoryId1, TEST_FATHER_ID, EventType.CONFIRM, ActorType.USER,
                    "{\"content\":\"Memory 1\",\"confidence_score\":0.80,\"importance_score\":5}",
                    "{\"content\":\"Memory 1\",\"confidence_score\":1.0,\"importance_score\":5}"
            );
            
            when(auditRepository.findByFatherIdOrderByCreatedAtDesc(TEST_FATHER_ID))
                    .thenReturn(List.of(entry3, entry2, entry1));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForFather(TEST_FATHER_ID);

            // Assert
            assertThat(history).hasSize(3);
            // Should contain entries from both memories
            assertThat(history.stream().filter(e -> e.getMemoryId().equals(memoryId1)).count()).isEqualTo(2);
            assertThat(history.stream().filter(e -> e.getMemoryId().equals(memoryId2)).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty history for memory with no audit entries")
        void shouldReturnEmptyHistoryForMemoryWithNoAuditEntries() {
            // Arrange
            when(auditRepository.findByMemoryIdOrderByCreatedAtAsc(TEST_MEMORY_ID))
                    .thenReturn(List.of());

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForMemory(TEST_MEMORY_ID);

            // Assert
            assertThat(history).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: JSON Snapshot Structure Validation (SPEC-004 Task 14)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests for verifying the JSON structure of state snapshots.
     *
     * <p><strong>Validates: Task 14 - Audit & Observability</strong>
     */
    @Nested
    @DisplayName("JSON Snapshot Structure Validation Tests (Task 14)")
    class JsonSnapshotStructureValidationTests {

        @Test
        @DisplayName("Should produce valid JSON that can be parsed")
        void shouldProduceValidJsonThatCanBeParsed() throws Exception {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Test content with special chars: \"quotes\" and 'apostrophes'");
            memory.setConfidenceScore(new BigDecimal("0.75"));
            memory.setImportanceScore(6);

            // Act
            String json = auditService.serializeMemoryState(memory);

            // Assert - should be parseable JSON
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(json);
            
            assertThat(node.get("content").asText()).isEqualTo("Test content with special chars: \"quotes\" and 'apostrophes'");
            assertThat(node.get("confidence_score").asDouble()).isEqualTo(0.75);
            assertThat(node.get("importance_score").asInt()).isEqualTo(6);
        }

        @Test
        @DisplayName("Should include all required fields in JSON snapshot")
        void shouldIncludeAllRequiredFieldsInJsonSnapshot() throws Exception {
            // Arrange
            UUID childId = UUID.randomUUID();
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Complete memory");
            memory.setCategory(MemoryCategory.RELATIONSHIP);
            memory.setState(MemoryState.CONFIRMED);
            memory.setSubjectType(MemorySubjectType.CHILD);
            memory.setChildId(childId);
            memory.setConfidenceScore(new BigDecimal("0.95"));
            memory.setImportanceScore(8);

            // Act
            String json = auditService.serializeMemoryState(memory);

            // Assert
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(json);
            
            // All required fields for version history
            assertThat(node.has("content")).isTrue();
            assertThat(node.has("confidence_score")).isTrue();
            assertThat(node.has("importance_score")).isTrue();
            
            // Additional context fields
            assertThat(node.has("category")).isTrue();
            assertThat(node.has("state")).isTrue();
            assertThat(node.has("subject_type")).isTrue();
            assertThat(node.has("child_id")).isTrue();
            
            // Verify values
            assertThat(node.get("content").asText()).isEqualTo("Complete memory");
            assertThat(node.get("confidence_score").asDouble()).isEqualTo(0.95);
            assertThat(node.get("importance_score").asInt()).isEqualTo(8);
            assertThat(node.get("category").asText()).isEqualTo("RELATIONSHIP");
            assertThat(node.get("state").asText()).isEqualTo("CONFIRMED");
            assertThat(node.get("subject_type").asText()).isEqualTo("CHILD");
            assertThat(node.get("child_id").asText()).isEqualTo(childId.toString());
        }

        @Test
        @DisplayName("Should handle special characters in content field")
        void shouldHandleSpecialCharactersInContentField() throws Exception {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent("Content with newline\nand tab\tand unicode: 🎉 emojis");
            memory.setConfidenceScore(new BigDecimal("0.80"));
            memory.setImportanceScore(5);

            // Act
            String json = auditService.serializeMemoryState(memory);

            // Assert - should be parseable and preserve content
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(json);
            
            assertThat(node.get("content").asText()).contains("newline");
            assertThat(node.get("content").asText()).contains("tab");
            assertThat(node.get("content").asText()).contains("🎉");
        }

        @Test
        @DisplayName("Should handle long content up to 500 characters")
        void shouldHandleLongContentUpTo500Characters() throws Exception {
            // Arrange
            String longContent = "A".repeat(500);
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            memory.setContent(longContent);
            memory.setConfidenceScore(new BigDecimal("0.70"));
            memory.setImportanceScore(4);

            // Act
            String json = auditService.serializeMemoryState(memory);

            // Assert
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(json);
            
            assertThat(node.get("content").asText()).hasSize(500);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Query by Father ID and Time Range (Task 14.5)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests for querying audit entries by father_id and time range.
     *
     * <p>From SPEC-004 Task 10 Acceptance Criteria:
     * "Queryable by father_id and time range" - supports compliance and troubleshooting.
     *
     * <p><strong>Validates: Task 14.5 - Audit Query Support for Compliance and Troubleshooting</strong>
     */
    @Nested
    @DisplayName("Query by Father ID and Time Range Tests (Task 14.5)")
    class QueryByFatherIdAndTimeRangeTests {

        @Test
        @DisplayName("Should query audit entries by father_id and time range")
        void shouldQueryAuditEntriesByFatherIdAndTimeRange() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");
            
            MemoryAuditLog entry1 = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, null, "{}");
            MemoryAuditLog entry2 = new MemoryAuditLog(
                    UUID.randomUUID(), TEST_FATHER_ID, EventType.UPDATE, ActorType.USER, "{}", "{}");
            
            when(auditRepository.findByFatherIdAndTimeRange(TEST_FATHER_ID, startTime, endTime))
                    .thenReturn(List.of(entry1, entry2));

            // Act
            List<MemoryAuditLog> result = auditService.getAuditHistoryForFatherInTimeRange(
                    TEST_FATHER_ID, startTime, endTime);

            // Assert
            assertThat(result).hasSize(2);
            verify(auditRepository).findByFatherIdAndTimeRange(TEST_FATHER_ID, startTime, endTime);
        }

        @Test
        @DisplayName("Should return empty list when no entries in time range")
        void shouldReturnEmptyListWhenNoEntriesInTimeRange() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");
            
            when(auditRepository.findByFatherIdAndTimeRange(TEST_FATHER_ID, startTime, endTime))
                    .thenReturn(List.of());

            // Act
            List<MemoryAuditLog> result = auditService.getAuditHistoryForFatherInTimeRange(
                    TEST_FATHER_ID, startTime, endTime);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when fatherId is null")
        void shouldThrowExceptionWhenFatherIdIsNull() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryForFatherInTimeRange(null, startTime, endTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when startTime is null")
        void shouldThrowExceptionWhenStartTimeIsNull() {
            // Arrange
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryForFatherInTimeRange(TEST_FATHER_ID, null, endTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime and endTime cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when endTime is null")
        void shouldThrowExceptionWhenEndTimeIsNull() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryForFatherInTimeRange(TEST_FATHER_ID, startTime, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime and endTime cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when startTime is after endTime")
        void shouldThrowExceptionWhenStartTimeIsAfterEndTime() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-31T23:59:59Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-01T00:00:00Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryForFatherInTimeRange(TEST_FATHER_ID, startTime, endTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime must not be after endTime");
        }

        @Test
        @DisplayName("Should accept same startTime and endTime (empty range)")
        void shouldAcceptSameStartTimeAndEndTime() {
            // Arrange
            java.time.Instant sameTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
            
            when(auditRepository.findByFatherIdAndTimeRange(TEST_FATHER_ID, sameTime, sameTime))
                    .thenReturn(List.of());

            // Act
            List<MemoryAuditLog> result = auditService.getAuditHistoryForFatherInTimeRange(
                    TEST_FATHER_ID, sameTime, sameTime);

            // Assert
            assertThat(result).isEmpty();
            verify(auditRepository).findByFatherIdAndTimeRange(TEST_FATHER_ID, sameTime, sameTime);
        }

        @Test
        @DisplayName("Should return empty list when repository is null")
        void shouldReturnEmptyListWhenRepositoryIsNull() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");

            // Act
            List<MemoryAuditLog> result = serviceWithoutRepo.getAuditHistoryForFatherInTimeRange(
                    TEST_FATHER_ID, startTime, endTime);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should support compliance query for user activity in a period")
        void shouldSupportComplianceQueryForUserActivityInPeriod() {
            // Arrange - Compliance scenario: "Show all memory operations for user X in Q1 2024"
            java.time.Instant q1Start = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant q1End = java.time.Instant.parse("2024-04-01T00:00:00Z");
            
            MemoryAuditLog createEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, 
                    null, MemoryState.ACTIVE, 
                    ActorType.AI, "AI:extraction", 
                    null, "{\"content\":\"Lucas likes trains\"}");
            MemoryAuditLog confirmEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CONFIRM,
                    MemoryState.ACTIVE, MemoryState.CONFIRMED,
                    ActorType.USER, "USER:confirmation",
                    "{\"state\":\"ACTIVE\"}", "{\"state\":\"CONFIRMED\"}");
            MemoryAuditLog deleteEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.DELETE,
                    MemoryState.CONFIRMED, MemoryState.DELETED,
                    ActorType.USER, "USER:deletion_request",
                    "{\"state\":\"CONFIRMED\"}", "{\"state\":\"DELETED\"}");
            
            when(auditRepository.findByFatherIdAndTimeRange(TEST_FATHER_ID, q1Start, q1End))
                    .thenReturn(List.of(deleteEntry, confirmEntry, createEntry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForFatherInTimeRange(
                    TEST_FATHER_ID, q1Start, q1End);

            // Assert - Full audit trail available for compliance
            assertThat(history).hasSize(3);
            assertThat(history).extracting(MemoryAuditLog::getOperationType)
                    .containsExactly(EventType.DELETE, EventType.CONFIRM, EventType.CREATE);
            assertThat(history).extracting(MemoryAuditLog::getFatherId)
                    .allMatch(id -> id.equals(TEST_FATHER_ID));
        }

        @Test
        @DisplayName("Should support troubleshooting query for specific day")
        void shouldSupportTroubleshootingQueryForSpecificDay() {
            // Arrange - Troubleshooting scenario: "What happened to this user's memories on Jan 15?"
            java.time.Instant dayStart = java.time.Instant.parse("2024-01-15T00:00:00Z");
            java.time.Instant dayEnd = java.time.Instant.parse("2024-01-16T00:00:00Z");
            
            MemoryAuditLog expireEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.EXPIRE,
                    MemoryState.ACTIVE, MemoryState.EXPIRED,
                    ActorType.SYSTEM, "SYSTEM:decay_job",
                    "{\"confidence_score\":0.4}", "{\"confidence_score\":0.3}");
            
            when(auditRepository.findByFatherIdAndTimeRange(TEST_FATHER_ID, dayStart, dayEnd))
                    .thenReturn(List.of(expireEntry));

            // Act
            List<MemoryAuditLog> history = auditService.getAuditHistoryForFatherInTimeRange(
                    TEST_FATHER_ID, dayStart, dayEnd);

            // Assert - Can investigate what happened on a specific day
            assertThat(history).hasSize(1);
            assertThat(history.get(0).getOperationType()).isEqualTo(EventType.EXPIRE);
            assertThat(history.get(0).getTriggeredBy()).isEqualTo("SYSTEM:decay_job");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Query by Time Range Only (System-Wide)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests for querying audit entries by time range only (system-wide, no father filter).
     *
     * <p><strong>Validates: Task 14.5 - Audit Query Support for System-Wide Monitoring</strong>
     */
    @Nested
    @DisplayName("Query by Time Range Only (System-Wide) Tests")
    class QueryByTimeRangeOnlyTests {

        @Test
        @DisplayName("Should query all audit entries within time range")
        void shouldQueryAllAuditEntriesWithinTimeRange() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");
            
            UUID fatherId1 = UUID.randomUUID();
            UUID fatherId2 = UUID.randomUUID();
            
            MemoryAuditLog entry1 = new MemoryAuditLog(
                    UUID.randomUUID(), fatherId1, EventType.CREATE, ActorType.AI, null, "{}");
            MemoryAuditLog entry2 = new MemoryAuditLog(
                    UUID.randomUUID(), fatherId2, EventType.CREATE, ActorType.AI, null, "{}");
            MemoryAuditLog entry3 = new MemoryAuditLog(
                    UUID.randomUUID(), fatherId1, EventType.CONFIRM, ActorType.USER, "{}", "{}");
            
            when(auditRepository.findByTimeRange(startTime, endTime))
                    .thenReturn(List.of(entry1, entry2, entry3));

            // Act
            List<MemoryAuditLog> result = auditService.getAuditHistoryInTimeRange(startTime, endTime);

            // Assert - Should include entries from multiple fathers
            assertThat(result).hasSize(3);
            assertThat(result.stream().map(MemoryAuditLog::getFatherId).distinct().count())
                    .isEqualTo(2); // Two different fathers
            verify(auditRepository).findByTimeRange(startTime, endTime);
        }

        @Test
        @DisplayName("Should return empty list when no entries in system-wide time range")
        void shouldReturnEmptyListWhenNoEntriesInSystemWideTimeRange() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");
            
            when(auditRepository.findByTimeRange(startTime, endTime))
                    .thenReturn(List.of());

            // Act
            List<MemoryAuditLog> result = auditService.getAuditHistoryInTimeRange(startTime, endTime);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when startTime is null for system-wide query")
        void shouldThrowExceptionWhenStartTimeIsNullForSystemWideQuery() {
            // Arrange
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryInTimeRange(null, endTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime and endTime cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when endTime is null for system-wide query")
        void shouldThrowExceptionWhenEndTimeIsNullForSystemWideQuery() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryInTimeRange(startTime, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime and endTime cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when startTime is after endTime for system-wide query")
        void shouldThrowExceptionWhenStartTimeIsAfterEndTimeForSystemWideQuery() {
            // Arrange
            java.time.Instant startTime = java.time.Instant.parse("2024-01-31T23:59:59Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-01T00:00:00Z");

            // Act & Assert
            assertThatThrownBy(() -> 
                auditService.getAuditHistoryInTimeRange(startTime, endTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime must not be after endTime");
        }

        @Test
        @DisplayName("Should return empty list when repository is null for system-wide query")
        void shouldReturnEmptyListWhenRepositoryIsNullForSystemWideQuery() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);
            java.time.Instant startTime = java.time.Instant.parse("2024-01-01T00:00:00Z");
            java.time.Instant endTime = java.time.Instant.parse("2024-01-31T23:59:59Z");

            // Act
            List<MemoryAuditLog> result = serviceWithoutRepo.getAuditHistoryInTimeRange(startTime, endTime);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should support system monitoring query for operations in last hour")
        void shouldSupportSystemMonitoringQueryForOperationsInLastHour() {
            // Arrange - System monitoring scenario: "What memory operations happened in the last hour?"
            java.time.Instant oneHourAgo = java.time.Instant.now().minus(java.time.Duration.ofHours(1));
            java.time.Instant now = java.time.Instant.now();
            
            UUID fatherId1 = UUID.randomUUID();
            UUID fatherId2 = UUID.randomUUID();
            UUID fatherId3 = UUID.randomUUID();
            
            MemoryAuditLog createEntry1 = new MemoryAuditLog(
                    UUID.randomUUID(), fatherId1, EventType.CREATE, 
                    null, MemoryState.ACTIVE, ActorType.AI, "AI:extraction", null, "{}");
            MemoryAuditLog createEntry2 = new MemoryAuditLog(
                    UUID.randomUUID(), fatherId2, EventType.CREATE, 
                    null, MemoryState.ACTIVE, ActorType.AI, "AI:extraction", null, "{}");
            MemoryAuditLog expireEntry = new MemoryAuditLog(
                    UUID.randomUUID(), fatherId3, EventType.EXPIRE,
                    MemoryState.ACTIVE, MemoryState.EXPIRED, ActorType.SYSTEM, "SYSTEM:decay_job", "{}", "{}");
            
            when(auditRepository.findByTimeRange(any(java.time.Instant.class), any(java.time.Instant.class)))
                    .thenReturn(List.of(expireEntry, createEntry2, createEntry1));

            // Act
            List<MemoryAuditLog> recentActivity = auditService.getAuditHistoryInTimeRange(oneHourAgo, now);

            // Assert - System-wide view of recent activity
            assertThat(recentActivity).hasSize(3);
            assertThat(recentActivity.stream().map(MemoryAuditLog::getFatherId).distinct().count())
                    .isEqualTo(3); // Three different fathers
        }

        @Test
        @DisplayName("Should support incident investigation for outage window")
        void shouldSupportIncidentInvestigationForOutageWindow() {
            // Arrange - Incident investigation: "What happened during the 2-hour outage window?"
            java.time.Instant outageStart = java.time.Instant.parse("2024-01-15T14:00:00Z");
            java.time.Instant outageEnd = java.time.Instant.parse("2024-01-15T16:00:00Z");
            
            // Simulate entries showing system behavior during outage
            MemoryAuditLog failedEntry = new MemoryAuditLog(
                    UUID.randomUUID(), UUID.randomUUID(), EventType.UPDATE,
                    MemoryState.ACTIVE, MemoryState.ACTIVE, ActorType.SYSTEM, "SYSTEM:consolidation_job", "{}", "{}");
            
            when(auditRepository.findByTimeRange(outageStart, outageEnd))
                    .thenReturn(List.of(failedEntry));

            // Act
            List<MemoryAuditLog> outageActivity = auditService.getAuditHistoryInTimeRange(outageStart, outageEnd);

            // Assert - Can investigate what was happening during outage
            assertThat(outageActivity).hasSize(1);
            assertThat(outageActivity.get(0).getTriggeredBy()).contains("SYSTEM");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Count Audit Entries
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Count Audit Entries Tests")
    class CountAuditEntriesTests {

        @Test
        @DisplayName("Should count audit entries for father")
        void shouldCountAuditEntriesForFather() {
            // Arrange
            when(auditRepository.countByFatherId(TEST_FATHER_ID)).thenReturn(42L);

            // Act
            long count = auditService.countAuditEntriesForFather(TEST_FATHER_ID);

            // Assert
            assertThat(count).isEqualTo(42);
            verify(auditRepository).countByFatherId(TEST_FATHER_ID);
        }

        @Test
        @DisplayName("Should return zero when no audit entries for father")
        void shouldReturnZeroWhenNoAuditEntriesForFather() {
            // Arrange
            when(auditRepository.countByFatherId(TEST_FATHER_ID)).thenReturn(0L);

            // Act
            long count = auditService.countAuditEntriesForFather(TEST_FATHER_ID);

            // Assert
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Should return zero when fatherId is null")
        void shouldReturnZeroWhenFatherIdIsNull() {
            // Act
            long count = auditService.countAuditEntriesForFather(null);

            // Assert
            assertThat(count).isZero();
            verify(auditRepository, never()).countByFatherId(any());
        }

        @Test
        @DisplayName("Should return zero when repository is null for count query")
        void shouldReturnZeroWhenRepositoryIsNullForCountQuery() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);

            // Act
            long count = serviceWithoutRepo.countAuditEntriesForFather(TEST_FATHER_ID);

            // Assert
            assertThat(count).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private Memory createTestMemory() {
        return new Memory(
                TEST_FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                "Test memory content",
                5,
                new BigDecimal("0.70"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
    }
}
