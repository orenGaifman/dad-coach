package com.dadcoach.memory.extraction;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditLog;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for audit integration in {@link MemoryExtractionService}.
 *
 * <p>These tests verify that audit entries are created when memories are created,
 * as defined in SPEC-004 REQ-24 and Task 4.7:
 * <ul>
 *   <li>Every memory creation SHALL produce an audit record</li>
 *   <li>Audit entry contains: event_type=CREATE, actor_type=AI</li>
 *   <li>Audit entry includes state_after snapshot</li>
 *   <li>Audit failures do not block memory creation</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements REQ-24, Task 4.7</strong>
 *
 * @see MemoryExtractionService
 * @see MemoryAuditService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryExtractionService Audit Integration Tests")
class MemoryExtractionServiceAuditTest {

    @Mock
    private DuplicateDetector duplicateDetector;

    @Mock
    private ExtractionValidator extractionValidator;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryCapacityManager capacityManager;

    @Mock
    private MemoryAuditService auditService;

    private MemoryExtractionService extractionService;

    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final UUID TEST_CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        extractionService = new MemoryExtractionService(
                duplicateDetector, extractionValidator, memoryRepository, 
                capacityManager, auditService);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Audit Entry Created on Memory Creation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audit Entry Creation Tests")
    class AuditEntryCreationTests {

        @Test
        @DisplayName("Should create audit entry when new memory is created (DISTINCT)")
        void shouldCreateAuditEntryWhenMemoryCreated() {
            // Arrange
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
            when(capacityManager.ensureCapacity(any()))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });
            when(auditService.createAuditEntryForCreate(any(Memory.class), eq(ActorType.AI)))
                    .thenReturn(Optional.of(new MemoryAuditLog(
                            UUID.randomUUID(), TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}")));

            // Act
            Optional<Memory> result = extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert
            assertThat(result).isPresent();
            
            // Verify audit service was called with correct parameters
            ArgumentCaptor<Memory> memoryCaptor = ArgumentCaptor.forClass(Memory.class);
            verify(auditService).createAuditEntryForCreate(memoryCaptor.capture(), eq(ActorType.AI));
            
            Memory capturedMemory = memoryCaptor.getValue();
            assertThat(capturedMemory.getId()).isNotNull();
            assertThat(capturedMemory.getContent()).isEqualTo("Lucas loves dinosaurs");
            assertThat(capturedMemory.getCategory()).isEqualTo(MemoryCategory.PREFERENCE);
        }

        @Test
        @DisplayName("Should create audit entry when memory created via POTENTIAL_UPDATE")
        void shouldCreateAuditEntryOnPotentialUpdate() {
            // Arrange
            UUID existingMemoryId = UUID.randomUUID();
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas now prefers T-Rex dinosaurs")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(6)
                    .confidenceScore(0.85)
                    .build();

            Memory existingMemory = new Memory(
                    TEST_FATHER_ID,
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    "Lucas loves dinosaurs",
                    5,
                    new BigDecimal("0.70"),
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
            existingMemory.setId(existingMemoryId);
            
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
            when(auditService.createAuditEntryForCreate(any(Memory.class), eq(ActorType.AI)))
                    .thenReturn(Optional.empty());

            // Act
            Optional<Memory> result = extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert
            assertThat(result).isPresent();
            
            // Verify audit service was called for the NEW memory (the one created)
            verify(auditService).createAuditEntryForCreate(any(Memory.class), eq(ActorType.AI));
        }

        @Test
        @DisplayName("Should use ActorType.AI for extraction-based memory creation")
        void shouldUseActorTypeAiForExtraction() {
            // Arrange
            float[] embedding = new float[1536];
            
            AiMemoryRecommendation recommendation = AiMemoryRecommendation.builder()
                    .content("Lucas's favorite color is blue")
                    .category("PREFERENCE")
                    .subjectType("CHILD")
                    .sourceType("CONVERSATION_EXTRACTION")
                    .importanceScore(5)
                    .confidenceScore(0.75)
                    .build();

            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(DuplicateResult.distinct());
            when(capacityManager.ensureCapacity(any()))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert - verify ActorType.AI is used
            verify(auditService).createAuditEntryForCreate(any(Memory.class), eq(ActorType.AI));
        }

        @Test
        @DisplayName("Should not create audit entry for DUPLICATE (no new memory created)")
        void shouldNotCreateAuditEntryForDuplicate() {
            // Arrange
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
                    TEST_FATHER_ID,
                    MemoryCategory.PREFERENCE,
                    MemorySubjectType.CHILD,
                    "Lucas loves dinosaurs",
                    5,
                    new BigDecimal("0.70"),
                    MemorySourceType.CONVERSATION_EXTRACTION
            );
            existingMemory.setId(existingMemoryId);
            
            when(duplicateDetector.check(any(), any(), any(), any()))
                    .thenReturn(new DuplicateResult.Duplicate(existingMemoryId, 0.92));
            when(memoryRepository.findById(existingMemoryId))
                    .thenReturn(Optional.of(existingMemory));
            when(memoryRepository.save(existingMemory)).thenReturn(existingMemory);

            // Act
            Optional<Memory> result = extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert - no new memory, no audit entry for CREATE
            assertThat(result).isEmpty();
            verify(auditService, never()).createAuditEntryForCreate(any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Audit Failure Handling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audit Failure Handling Tests")
    class AuditFailureHandlingTests {

        @Test
        @DisplayName("Should not block memory creation when audit service is unavailable")
        void shouldNotBlockMemoryCreationWhenAuditUnavailable() {
            // Arrange - service without audit service
            MemoryExtractionService serviceNoAudit = new MemoryExtractionService(
                    duplicateDetector, extractionValidator, memoryRepository, capacityManager, null);
            
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
            when(capacityManager.ensureCapacity(any()))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // Act
            Optional<Memory> result = serviceNoAudit.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert - memory still created despite no audit service
            assertThat(result).isPresent();
            assertThat(result.get().getContent()).isEqualTo("Lucas loves dinosaurs");
        }

        @Test
        @DisplayName("Should not block memory creation when audit service throws exception")
        void shouldNotBlockMemoryCreationWhenAuditThrows() {
            // Arrange
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
            when(capacityManager.ensureCapacity(any()))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });
            // Audit service throws exception
            when(auditService.createAuditEntryForCreate(any(Memory.class), any()))
                    .thenThrow(new RuntimeException("Audit database unavailable"));

            // Act
            Optional<Memory> result = extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert - memory still created despite audit failure
            assertThat(result).isPresent();
            assertThat(result.get().getContent()).isEqualTo("Lucas loves dinosaurs");
        }

        @Test
        @DisplayName("Should continue processing when audit service returns empty")
        void shouldContinueWhenAuditReturnsEmpty() {
            // Arrange
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
            when(capacityManager.ensureCapacity(any()))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });
            // Audit service returns empty (failed silently)
            when(auditService.createAuditEntryForCreate(any(Memory.class), any()))
                    .thenReturn(Optional.empty());

            // Act
            Optional<Memory> result = extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert - memory still created
            assertThat(result).isPresent();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Audit Entry Timing
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audit Entry Timing Tests")
    class AuditEntryTimingTests {

        @Test
        @DisplayName("Should create audit entry AFTER memory is persisted (has ID)")
        void shouldCreateAuditAfterMemoryPersisted() {
            // Arrange
            float[] embedding = new float[1536];
            final UUID generatedMemoryId = UUID.randomUUID();
            
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
            when(capacityManager.ensureCapacity(any()))
                    .thenReturn(MemoryCapacityManager.EnsureCapacityResult.capacityAvailable());
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(generatedMemoryId);
                return m;
            });

            // Act
            extractionService.processValidatedRecommendation(
                    recommendation, TEST_FATHER_ID, TEST_CONVERSATION_ID, embedding);

            // Assert - audit was called with memory that has an ID
            ArgumentCaptor<Memory> memoryCaptor = ArgumentCaptor.forClass(Memory.class);
            verify(auditService).createAuditEntryForCreate(memoryCaptor.capture(), eq(ActorType.AI));
            
            Memory capturedMemory = memoryCaptor.getValue();
            assertThat(capturedMemory.getId()).isEqualTo(generatedMemoryId);
        }
    }
}
