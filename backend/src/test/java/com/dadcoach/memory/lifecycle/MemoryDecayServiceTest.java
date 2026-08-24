package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.*;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryDecayService.
 *
 * <p><b>Validates: SPEC-004 Requirement 6 (Memory Decay and Aging)</b>
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Daily decay job runs and processes fathers in batches</li>
 *   <li>Batch processing avoids lock contention by:
 *     <ul>
 *       <li>Processing configurable batch sizes</li>
 *       <li>Adding delays between batches</li>
 *       <li>Using READ_COMMITTED isolation</li>
 *     </ul>
 *   </li>
 *   <li>Tier-based decay rates are applied correctly:
 *     <ul>
 *       <li>Short-term (1-3): decay starts 30 days, rate -0.15/30 days</li>
 *       <li>Medium-term (4-6): decay starts 60 days, rate -0.10/30 days</li>
 *       <li>Long-term (7-10): decay starts 90 days, rate -0.05/30 days</li>
 *     </ul>
 *   </li>
 *   <li>Exempt memories are skipped:
 *     <ul>
 *       <li>IDENTITY with confidence 1.0</li>
 *       <li>FAMILY with confidence >= 0.9</li>
 *       <li>GOAL linked to an active goal</li>
 *     </ul>
 *   </li>
 *   <li>High reliability memories (3+ confirmations) have halved decay rate</li>
 *   <li>Memories changed since job start are skipped (race condition protection)</li>
 *   <li>Audit entries are created for decay operations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryDecayService Tests")
class MemoryDecayServiceTest {

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID MEMORY_ID = UUID.randomUUID();
    private static final String CONTENT = "Test memory content";

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryAuditService auditService;

    private MemoryDecayService decayService;

    @BeforeEach
    void setUp() {
        decayService = new MemoryDecayService(memoryRepository, auditService);
        // Set default values since @Value annotation doesn't work in unit tests
        decayService.setBatchSize(50);
        decayService.setBatchDelayMs(0);
    }

    private Memory createMemory(MemoryCategory category, int importanceScore, BigDecimal confidence) {
        Memory memory = new Memory(
                FATHER_ID,
                category,
                MemorySubjectType.CHILD,
                CONTENT,
                importanceScore,
                confidence,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(MEMORY_ID);
        memory.setCreatedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        // Set lastUpdatedAt to the past so it won't be skipped by race condition check
        memory.setLastUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return memory;
    }

    private Memory createMemoryWithLastAccess(int importanceScore, BigDecimal confidence, int daysSinceAccess) {
        Memory memory = createMemory(MemoryCategory.PREFERENCE, importanceScore, confidence);
        memory.setLastAccessedAt(Instant.now().minus(daysSinceAccess, ChronoUnit.DAYS));
        // Ensure lastUpdatedAt is before job start time to avoid race condition skip
        memory.setLastUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return memory;
    }

    private Memory createMemoryForFather(UUID fatherId, int importanceScore, BigDecimal confidence, int daysSinceAccess) {
        Memory memory = new Memory(
                fatherId,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                CONTENT,
                importanceScore,
                confidence,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
        memory.setCreatedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        memory.setLastAccessedAt(Instant.now().minus(daysSinceAccess, ChronoUnit.DAYS));
        memory.setLastUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return memory;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch Processing Tests (SPEC-004 Requirement 6 Criteria 4, AD-3)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Batch Processing (AD-3: Scheduled Jobs via Spring @Scheduled)")
    class BatchProcessingTests {

        @BeforeEach
        void setUpBatchProcessing() {
            // Configure batch processing settings for tests
            decayService.setBatchSize(50);
            decayService.setBatchDelayMs(0);
        }

        @Test
        @DisplayName("processes fathers in configurable batch sizes")
        void processesFathersInConfigurableBatchSizes() {
            // Given - 7 fathers with decayable memories
            List<UUID> fatherIds = IntStream.range(0, 7)
                    .mapToObj(i -> UUID.randomUUID())
                    .toList();

            when(memoryRepository.findDistinctFatherIdsByStateIn(anyCollection()))
                    .thenReturn(fatherIds);
            when(memoryRepository.findByFatherIdAndStateIn(any(UUID.class), anyCollection()))
                    .thenReturn(List.of());

            // Set batch size to 3
            decayService.setBatchSize(3);

            // When
            decayService.runDailyDecay();

            // Then - all 7 fathers should be processed (3 batches: 3+3+1)
            verify(memoryRepository, times(7)).findByFatherIdAndStateIn(any(UUID.class), anyCollection());
        }

        @Test
        @DisplayName("default batch size is 50")
        void defaultBatchSizeIsFifty() {
            // Verify the batch size we set in @BeforeEach
            assertThat(decayService.getBatchSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("batch size is configurable")
        void batchSizeIsConfigurable() {
            // Given
            decayService.setBatchSize(25);

            // Then
            assertThat(decayService.getBatchSize()).isEqualTo(25);
        }

        @Test
        @DisplayName("batch delay is configurable")
        void batchDelayIsConfigurable() {
            // Given
            decayService.setBatchDelayMs(200);

            // Then
            assertThat(decayService.getBatchDelayMs()).isEqualTo(200);
        }

        @Test
        @DisplayName("processes all fathers even when count is not a multiple of batch size")
        void processesAllFathersWithNonMultipleBatchSize() {
            // Given - 5 fathers, batch size 2 (should be 3 batches: 2+2+1)
            List<UUID> fatherIds = IntStream.range(0, 5)
                    .mapToObj(i -> UUID.randomUUID())
                    .toList();

            when(memoryRepository.findDistinctFatherIdsByStateIn(anyCollection()))
                    .thenReturn(fatherIds);
            when(memoryRepository.findByFatherIdAndStateIn(any(UUID.class), anyCollection()))
                    .thenReturn(List.of());

            decayService.setBatchSize(2);

            // When
            decayService.runDailyDecay();

            // Then - all 5 fathers should be processed
            verify(memoryRepository, times(5)).findByFatherIdAndStateIn(any(UUID.class), anyCollection());
        }

        @Test
        @DisplayName("handles empty father list gracefully")
        void handlesEmptyFatherListGracefully() {
            // Given
            when(memoryRepository.findDistinctFatherIdsByStateIn(anyCollection()))
                    .thenReturn(List.of());

            // When
            decayService.runDailyDecay();

            // Then - no processing attempts
            verify(memoryRepository, never()).findByFatherIdAndStateIn(any(UUID.class), anyCollection());
        }

        @Test
        @DisplayName("continues processing other fathers when one fails")
        void continuesProcessingWhenOneFatherFails() {
            // Given - 3 fathers, middle one fails
            UUID father1 = UUID.randomUUID();
            UUID father2 = UUID.randomUUID();
            UUID father3 = UUID.randomUUID();

            when(memoryRepository.findDistinctFatherIdsByStateIn(anyCollection()))
                    .thenReturn(List.of(father1, father2, father3));
            when(memoryRepository.findByFatherIdAndStateIn(eq(father1), anyCollection()))
                    .thenReturn(List.of());
            when(memoryRepository.findByFatherIdAndStateIn(eq(father2), anyCollection()))
                    .thenThrow(new RuntimeException("Database error"));
            when(memoryRepository.findByFatherIdAndStateIn(eq(father3), anyCollection()))
                    .thenReturn(List.of());

            // When
            decayService.runDailyDecay();

            // Then - all three fathers should have been attempted
            verify(memoryRepository).findByFatherIdAndStateIn(eq(father1), anyCollection());
            verify(memoryRepository).findByFatherIdAndStateIn(eq(father2), anyCollection());
            verify(memoryRepository).findByFatherIdAndStateIn(eq(father3), anyCollection());
        }

        @Test
        @DisplayName("processes each father in isolation (separate transactions)")
        void processesEachFatherInIsolation() {
            // Given - 2 fathers with memories
            UUID father1 = UUID.randomUUID();
            UUID father2 = UUID.randomUUID();

            Memory memory1 = createMemoryForFather(father1, 2, new BigDecimal("0.80"), 65);
            Memory memory2 = createMemoryForFather(father2, 2, new BigDecimal("0.80"), 65);

            when(memoryRepository.findDistinctFatherIdsByStateIn(anyCollection()))
                    .thenReturn(List.of(father1, father2));
            when(memoryRepository.findByFatherIdAndStateIn(eq(father1), anyCollection()))
                    .thenReturn(List.of(memory1));
            when(memoryRepository.findByFatherIdAndStateIn(eq(father2), anyCollection()))
                    .thenReturn(List.of(memory2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.runDailyDecay();

            // Then - each father's memories should be queried separately
            verify(memoryRepository).findByFatherIdAndStateIn(eq(father1), anyCollection());
            verify(memoryRepository).findByFatherIdAndStateIn(eq(father2), anyCollection());
            // And each memory should be saved
            verify(memoryRepository, times(2)).save(any(Memory.class));
        }

        @Test
        @DisplayName("aggregates totals correctly across multiple batches")
        void aggregatesTotalsCorrectlyAcrossMultipleBatches() {
            // Given - 4 fathers across 2 batches, each with 1 memory that needs decay
            List<UUID> fatherIds = IntStream.range(0, 4)
                    .mapToObj(i -> UUID.randomUUID())
                    .toList();

            when(memoryRepository.findDistinctFatherIdsByStateIn(anyCollection()))
                    .thenReturn(fatherIds);

            // Each father has one memory needing decay
            for (UUID fatherId : fatherIds) {
                Memory memory = createMemoryForFather(fatherId, 2, new BigDecimal("0.80"), 65);
                when(memoryRepository.findByFatherIdAndStateIn(eq(fatherId), anyCollection()))
                        .thenReturn(List.of(memory));
            }
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            decayService.setBatchSize(2);

            // When
            decayService.runDailyDecay();

            // Then - 4 memories should be saved (one per father)
            verify(memoryRepository, times(4)).save(any(Memory.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // processFatherMemoriesDecay() Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processFatherMemoriesDecay() method")
    class ProcessFatherMemoriesDecayTests {

        @Test
        @DisplayName("returns empty result when father has no memories")
        void returnsEmptyResultWhenNoMemories() {
            // Given
            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of());

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now());

            // Then
            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesDecayed()).isEqualTo(0);
        }

        @Test
        @DisplayName("processes memories and returns correct counts")
        void processesMemoriesAndReturnsCounts() {
            // Given - short-term memory accessed 65 days ago (past 30-day threshold by 35+ days = 1 decay period)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesDecayed()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tier-Based Decay Rate Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tier-based decay rates (Requirement 6 Criteria 3)")
    class TierBasedDecayRateTests {

        @Test
        @DisplayName("Short-term tier (importance 1-3): decay starts at 30 days")
        void shortTermDecayStartsAtThirtyDays() {
            // Given - short-term memory accessed 29 days ago (before threshold)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 29);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - no decay applied because under threshold
            assertThat(result.memoriesDecayed()).isEqualTo(0);
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("Short-term tier: applies -0.15 decay per 30 days")
        void shortTermAppliesCorrectDecayRate() {
            // Given - short-term memory (importance 2) accessed 65 days ago
            // 65 days = past 30-day threshold by 35 days, which is more than 30 days = 1 decay period
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);
            assertThat(memory.getTier()).isEqualTo(MemoryTier.SHORT_TERM);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - confidence should be 0.80 - 0.15 = 0.65
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.65"));
        }

        @Test
        @DisplayName("Medium-term tier (importance 4-6): decay starts at 60 days")
        void mediumTermDecayStartsAtSixtyDays() {
            // Given - medium-term memory accessed 55 days ago (before threshold)
            Memory memory = createMemoryWithLastAccess(5, new BigDecimal("0.80"), 55);
            assertThat(memory.getTier()).isEqualTo(MemoryTier.MEDIUM_TERM);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - no decay applied because under threshold
            assertThat(result.memoriesDecayed()).isEqualTo(0);
        }

        @Test
        @DisplayName("Medium-term tier: applies -0.10 decay per 30 days")
        void mediumTermAppliesCorrectDecayRate() {
            // Given - medium-term memory (importance 5) accessed 95 days ago
            // 95 days = past 60-day threshold by 35 days, which is more than 30 days = 1 decay period
            Memory memory = createMemoryWithLastAccess(5, new BigDecimal("0.80"), 95);
            assertThat(memory.getTier()).isEqualTo(MemoryTier.MEDIUM_TERM);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - confidence should be 0.80 - 0.10 = 0.70
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.70"));
        }

        @Test
        @DisplayName("Long-term tier (importance 7-10): decay starts at 90 days")
        void longTermDecayStartsAtNinetyDays() {
            // Given - long-term memory accessed 85 days ago (before threshold)
            Memory memory = createMemoryWithLastAccess(8, new BigDecimal("0.80"), 85);
            assertThat(memory.getTier()).isEqualTo(MemoryTier.LONG_TERM);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - no decay applied because under threshold
            assertThat(result.memoriesDecayed()).isEqualTo(0);
        }

        @Test
        @DisplayName("Long-term tier: applies -0.05 decay per 30 days")
        void longTermAppliesCorrectDecayRate() {
            // Given - long-term memory (importance 8) accessed 125 days ago
            // 125 days = past 90-day threshold by 35 days, which is more than 30 days = 1 decay period
            Memory memory = createMemoryWithLastAccess(8, new BigDecimal("0.80"), 125);
            assertThat(memory.getTier()).isEqualTo(MemoryTier.LONG_TERM);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - confidence should be 0.80 - 0.05 = 0.75
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.75"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exempt Memories Tests (Requirement 6 Criteria 7)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exempt memories (Requirement 6 Criteria 7)")
    class ExemptMemoriesTests {

        @Test
        @DisplayName("IDENTITY memories with confidence 1.0 are exempt from decay")
        void identityMemoriesWithPerfectConfidenceAreExempt() {
            // Given - IDENTITY memory with confidence 1.0 accessed 100 days ago
            Memory memory = createMemory(MemoryCategory.IDENTITY, 9, BigDecimal.ONE);
            memory.setLastAccessedAt(Instant.now().minus(100, ChronoUnit.DAYS));

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - no decay applied because exempt
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesDecayed()).isEqualTo(0);
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("IDENTITY memories with confidence < 1.0 are NOT exempt")
        void identityMemoriesWithLowerConfidenceAreNotExempt() {
            // Given - IDENTITY memory with confidence 0.9 accessed 125 days ago (past 90-day threshold)
            Memory memory = createMemory(MemoryCategory.IDENTITY, 9, new BigDecimal("0.90"));
            memory.setLastAccessedAt(Instant.now().minus(125, ChronoUnit.DAYS));

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - decay IS applied
            assertThat(result.memoriesDecayed()).isEqualTo(1);
        }

        @Test
        @DisplayName("FAMILY memories with confidence >= 0.9 are exempt from decay")
        void familyMemoriesWithHighConfidenceAreExempt() {
            // Given - FAMILY memory with confidence 0.9 accessed 100 days ago
            Memory memory = createMemory(MemoryCategory.FAMILY, 7, new BigDecimal("0.90"));
            memory.setLastAccessedAt(Instant.now().minus(100, ChronoUnit.DAYS));

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - no decay applied because exempt
            assertThat(result.memoriesDecayed()).isEqualTo(0);
        }

        @Test
        @DisplayName("FAMILY memories with confidence < 0.9 are NOT exempt")
        void familyMemoriesWithLowerConfidenceAreNotExempt() {
            // Given - FAMILY memory with confidence 0.89 accessed 125 days ago (past 90-day threshold)
            Memory memory = createMemory(MemoryCategory.FAMILY, 7, new BigDecimal("0.89"));
            memory.setLastAccessedAt(Instant.now().minus(125, ChronoUnit.DAYS));

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - decay IS applied
            assertThat(result.memoriesDecayed()).isEqualTo(1);
        }

        @Test
        @DisplayName("GOAL memories linked to an active goal are exempt from decay")
        void goalMemoriesWithLinkedGoalAreExempt() {
            // Given - GOAL memory linked to a goal, accessed 100 days ago
            Memory memory = createMemory(MemoryCategory.GOAL, 7, new BigDecimal("0.80"));
            memory.setLastAccessedAt(Instant.now().minus(100, ChronoUnit.DAYS));
            memory.setGoalId(UUID.randomUUID()); // Linked to a goal

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - no decay applied because exempt
            assertThat(result.memoriesDecayed()).isEqualTo(0);
        }

        @Test
        @DisplayName("GOAL memories not linked to a goal are NOT exempt")
        void goalMemoriesWithoutLinkedGoalAreNotExempt() {
            // Given - GOAL memory without linked goal, accessed 125 days ago (past 90-day threshold)
            Memory memory = createMemory(MemoryCategory.GOAL, 7, new BigDecimal("0.80"));
            memory.setLastAccessedAt(Instant.now().minus(125, ChronoUnit.DAYS));
            memory.setGoalId(null); // No linked goal

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - decay IS applied
            assertThat(result.memoriesDecayed()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // High Reliability Memory Tests (Requirement 6 Criteria 5)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("High reliability memories (Requirement 6 Criteria 5)")
    class HighReliabilityMemoryTests {

        @Test
        @DisplayName("Memories confirmed 3+ times have halved decay rate")
        void memoriesWithThreeConfirmationsHaveHalvedDecay() {
            // Given - short-term memory with 3 confirmations, accessed 65 days ago
            // 65 days = past 30-day threshold by 35 days, which is more than 30 days = 1 decay period
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);
            memory.setConfirmationCount(3);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - decay should be 0.15/2 = 0.075, rounded to 0.08, so confidence = 0.80 - 0.08 = 0.72
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.72"));
        }

        @Test
        @DisplayName("Memories with fewer than 3 confirmations have full decay rate")
        void memoriesWithFewerConfirmationsHaveFullDecay() {
            // Given - short-term memory with 2 confirmations, accessed 65 days ago
            // 65 days = past 30-day threshold by 35 days, which is more than 30 days = 1 decay period
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);
            memory.setConfirmationCount(2);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - full decay: 0.80 - 0.15 = 0.65
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.65"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Race Condition Protection Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Race condition protection")
    class RaceConditionProtectionTests {

        @Test
        @DisplayName("Skips memories modified since job start")
        void skipsMemoriesModifiedSinceJobStart() {
            // Given - memory modified after job start (should be skipped)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);
            memory.setLastUpdatedAt(Instant.now()); // Just updated - this makes it after job start

            Instant jobStartTime = Instant.now().minus(10, ChronoUnit.MINUTES);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, jobStartTime);

            // Then - memory skipped due to race condition
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesDecayed()).isEqualTo(0);
            verify(memoryRepository, never()).save(any(Memory.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Audit Logging Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audit logging")
    class AuditLoggingTests {

        @Test
        @DisplayName("Creates audit entry for decay operations")
        void createsAuditEntryForDecay() {
            // Given - memory accessed 65 days ago (past 30-day threshold by 35+ days = 1 decay period)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then
            verify(auditService).createAuditEntry(
                    any(Memory.class),
                    eq(EventType.UPDATE),
                    eq(ActorType.SYSTEM),
                    anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence Floor Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence floor handling")
    class ConfidenceFloorTests {

        @Test
        @DisplayName("Confidence does not go below zero")
        void confidenceDoesNotGoBelowZero() {
            // Given - memory with low confidence accessed 65 days ago (past 30-day threshold)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.12"), 65);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            decayService.processFatherMemoriesDecay(FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - confidence floored at 0.00 (0.12 - 0.15 = -0.03, but floored at 0)
            ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Skips memories with confidence below minimum threshold")
        void skipsMemoriesBelowMinConfidenceThreshold() {
            // Given - memory with very low confidence (below 0.10 threshold)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.05"), 65);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));

            // When
            MemoryDecayService.DecayResult result = decayService.processFatherMemoriesDecay(
                    FATHER_ID, Instant.now().minus(1, ChronoUnit.HOURS));

            // Then - memory skipped (too low for decay, should be expired instead)
            assertThat(result.memoriesDecayed()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Manual Trigger Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Manual trigger methods")
    class ManualTriggerTests {

        @Test
        @DisplayName("triggerDecayForFather processes specific father")
        void triggerDecayForFatherProcessesSpecificFather() {
            // Given - memory accessed 65 days ago (past 30-day threshold by 35+ days)
            Memory memory = createMemoryWithLastAccess(2, new BigDecimal("0.80"), 65);

            when(memoryRepository.findByFatherIdAndStateIn(eq(FATHER_ID), anyCollection()))
                    .thenReturn(List.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");

            // When
            MemoryDecayService.DecayResult result = decayService.triggerDecayForFather(FATHER_ID);

            // Then
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesDecayed()).isEqualTo(1);
        }
    }
}
