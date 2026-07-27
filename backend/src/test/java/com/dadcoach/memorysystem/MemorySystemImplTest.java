package com.dadcoach.memorysystem;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.memory.Memory;
import com.dadcoach.domain.memory.MemoryRepository;
import com.dadcoach.domain.memory.MemoryService;
import com.dadcoach.domain.memory.MemoryStatus;
import com.dadcoach.memory.MemoryCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemorySystemImpl}.
 */
class MemorySystemImplTest {

    private MemoryService memoryService;
    private MemoryRepository memoryRepository;
    private MemorySystemImpl memorySystem;

    @BeforeEach
    void setUp() {
        memoryService = mock(MemoryService.class);
        memoryRepository = mock(MemoryRepository.class);
        memorySystem = new MemorySystemImpl(memoryService, memoryRepository);
    }

    // ─── createMemory ────────────────────────────────────────────────────────

    @Nested
    class CreateMemory {

        @Test
        void shouldDelegateToMemoryService() {
            Father father = createFather();
            Memory expected = createMemory(father, 7, "0.90", "child likes soccer", 0);
            when(memoryService.createMemory(eq(1L), isNull(), eq(MemoryCategory.PREFERENCE),
                    eq("child likes soccer"), eq(7), any(BigDecimal.class)))
                    .thenReturn(expected);

            Memory result = memorySystem.createMemory(1L, MemoryCategory.PREFERENCE,
                    "child likes soccer", 7, 0.9);

            assertThat(result).isEqualTo(expected);
            verify(memoryService).createMemory(eq(1L), isNull(), eq(MemoryCategory.PREFERENCE),
                    eq("child likes soccer"), eq(7), eq(new BigDecimal("0.90")));
        }
    }

    // ─── retrieveTopMemories ─────────────────────────────────────────────────

    @Nested
    class RetrieveTopMemories {

        @Test
        void shouldReturnAtMost15Memories() {
            Father father = createFather();
            List<Memory> memories = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                memories.add(createMemory(father, (i % 10) + 1, "0.80", "content " + i, i));
            }
            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(memories);

            List<Memory> result = memorySystem.retrieveTopMemories(1L, "content", 15);

            assertThat(result).hasSize(15);
        }

        @Test
        void shouldOrderByDescendingCompositeScore() {
            Father father = createFather();

            // High importance, recent, relevant
            Memory highScored = createMemory(father, 10, "1.00", "soccer practice", 0);
            // Low importance, old, not relevant
            Memory lowScored = createMemory(father, 1, "0.30", "random note", 30);

            when(memoryRepository.findActiveByFatherId(1L))
                    .thenReturn(List.of(lowScored, highScored));

            List<Memory> result = memorySystem.retrieveTopMemories(1L, "soccer", 15);

            assertThat(result).containsExactly(highScored, lowScored);
        }

        @Test
        void shouldRecordAccessForReturnedMemories() {
            Father father = createFather();
            Memory m1 = createMemory(father, 5, "0.80", "test", 1);
            m1.setId(100L);
            Memory m2 = createMemory(father, 7, "0.90", "test", 2);
            m2.setId(200L);

            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(List.of(m1, m2));

            memorySystem.retrieveTopMemories(1L, "test", 15);

            verify(memoryService).recordAccessBatch(anyList());
        }

        @Test
        void shouldReturnEmptyListWhenNoActiveMemories() {
            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(List.of());

            List<Memory> result = memorySystem.retrieveTopMemories(1L, "topic", 15);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldRespectCustomLimit() {
            Father father = createFather();
            List<Memory> memories = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                memories.add(createMemory(father, 5, "0.80", "content", i));
            }
            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(memories);

            List<Memory> result = memorySystem.retrieveTopMemories(1L, "content", 5);

            assertThat(result).hasSize(5);
        }
    }

    // ─── Composite Score Calculation ─────────────────────────────────────────

    @Nested
    class CompositeScoreCalculation {

        @Test
        void shouldComputeCorrectScoreForRecentRelevantHighImportance() {
            Father father = createFather();
            Memory memory = createMemory(father, 10, "1.00", "soccer game", 0);

            double score = MemorySystemImpl.computeCompositeScore(memory, "soccer", Instant.now());

            // importance: 10/10 * 0.5 = 0.5
            // recency: max(0, 1.0 - 0*0.05) = 1.0 * 0.3 = 0.3
            // relevance: 1.0 * 0.2 = 0.2
            // total: 1.0
            assertThat(score).isCloseTo(1.0, within(0.01));
        }

        @Test
        void shouldComputeCorrectScoreForOldIrrelevantLowImportance() {
            Father father = createFather();
            Memory memory = createMemory(father, 1, "0.30", "random text", 25);

            double score = MemorySystemImpl.computeCompositeScore(memory, "soccer", Instant.now());

            // importance: 1/10 * 0.5 = 0.05
            // recency: max(0, 1.0 - 25*0.05) = max(0, -0.25) = 0 * 0.3 = 0.0
            // relevance: 0.0 * 0.2 = 0.0
            // total: 0.05
            assertThat(score).isCloseTo(0.05, within(0.01));
        }

        @Test
        void shouldHandleNullTopic() {
            Father father = createFather();
            Memory memory = createMemory(father, 5, "0.80", "some content", 5);

            double score = MemorySystemImpl.computeCompositeScore(memory, null, Instant.now());

            // relevance should be 0.0 for null topic
            // importance: 5/10 * 0.5 = 0.25
            // recency: max(0, 1.0 - 5*0.05) = 0.75 * 0.3 = 0.225
            // total: 0.475
            assertThat(score).isCloseTo(0.475, within(0.01));
        }

        @Test
        void shouldHandleEmptyTopic() {
            Father father = createFather();
            Memory memory = createMemory(father, 5, "0.80", "some content", 5);

            double score = MemorySystemImpl.computeCompositeScore(memory, "", Instant.now());

            // relevance should be 0.0 for empty topic
            assertThat(score).isCloseTo(0.475, within(0.01));
        }
    }

    // ─── Recency Factor ──────────────────────────────────────────────────────

    @Nested
    class RecencyFactor {

        @Test
        void shouldBeOneForBrandNewMemory() {
            double factor = MemorySystemImpl.computeRecencyFactor(Instant.now(), Instant.now());
            assertThat(factor).isEqualTo(1.0);
        }

        @Test
        void shouldBeZeroAfter20Days() {
            Instant now = Instant.now();
            Instant twentyDaysAgo = now.minus(Duration.ofDays(20));
            double factor = MemorySystemImpl.computeRecencyFactor(twentyDaysAgo, now);
            assertThat(factor).isEqualTo(0.0);
        }

        @Test
        void shouldBeZeroAfterMoreThan20Days() {
            Instant now = Instant.now();
            Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
            double factor = MemorySystemImpl.computeRecencyFactor(thirtyDaysAgo, now);
            assertThat(factor).isEqualTo(0.0);
        }

        @Test
        void shouldDecayLinearlyBetweenZeroAndTwentyDays() {
            Instant now = Instant.now();
            Instant tenDaysAgo = now.minus(Duration.ofDays(10));
            double factor = MemorySystemImpl.computeRecencyFactor(tenDaysAgo, now);
            // 1.0 - (10 * 0.05) = 0.5
            assertThat(factor).isCloseTo(0.5, within(0.001));
        }
    }

    // ─── Relevance ───────────────────────────────────────────────────────────

    @Nested
    class Relevance {

        @Test
        void shouldReturnOneWhenContentContainsTopic() {
            double relevance = MemorySystemImpl.computeRelevance("child likes soccer", "soccer");
            assertThat(relevance).isEqualTo(1.0);
        }

        @Test
        void shouldReturnZeroWhenContentDoesNotContainTopic() {
            double relevance = MemorySystemImpl.computeRelevance("child likes basketball", "soccer");
            assertThat(relevance).isEqualTo(0.0);
        }

        @Test
        void shouldBeCaseInsensitive() {
            double relevance = MemorySystemImpl.computeRelevance("Child likes SOCCER", "soccer");
            assertThat(relevance).isEqualTo(1.0);
        }

        @Test
        void shouldReturnZeroForNullContent() {
            double relevance = MemorySystemImpl.computeRelevance(null, "soccer");
            assertThat(relevance).isEqualTo(0.0);
        }

        @Test
        void shouldReturnZeroForNullTopic() {
            double relevance = MemorySystemImpl.computeRelevance("some content", null);
            assertThat(relevance).isEqualTo(0.0);
        }
    }

    // ─── Consolidation ───────────────────────────────────────────────────────

    @Nested
    class Consolidation {

        @Test
        void shouldMergeShortTermMemoriesOlderThan7Days() {
            Father father = createFather();

            Memory m1 = createMemory(father, 2, "0.70", "fact one", 10);
            m1.setId(1L);
            Memory m2 = createMemory(father, 3, "0.80", "fact two", 10);
            m2.setId(2L);
            // Recent memory (should not be consolidated)
            Memory m3 = createMemory(father, 2, "0.60", "recent fact", 1);
            m3.setId(3L);
            // High importance (should not be consolidated)
            Memory m4 = createMemory(father, 7, "0.90", "important fact", 10);
            m4.setId(4L);

            when(memoryRepository.findActiveByFatherId(1L))
                    .thenReturn(List.of(m1, m2, m3, m4));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(memoryService.createMemory(eq(1L), isNull(), eq(MemoryCategory.CONVERSATION_SUMMARY),
                    anyString(), eq(3), any(BigDecimal.class)))
                    .thenReturn(createMemory(father, 3, "0.75", "Consolidated: fact one; fact two", 0));

            memorySystem.consolidateMemories(1L);

            // Old short-term memories should be archived
            assertThat(m1.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
            assertThat(m2.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
            // Recent and high importance should NOT be archived
            assertThat(m3.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(m4.getStatus()).isEqualTo(MemoryStatus.ACTIVE);

            // A new consolidated memory should be created
            verify(memoryService).createMemory(eq(1L), isNull(), eq(MemoryCategory.CONVERSATION_SUMMARY),
                    contains("Consolidated:"), eq(3), any(BigDecimal.class));
        }

        @Test
        void shouldNotConsolidateWhenNoEligibleMemories() {
            Father father = createFather();
            // Only recent memories
            Memory m1 = createMemory(father, 2, "0.70", "recent fact", 1);
            m1.setId(1L);

            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(List.of(m1));

            memorySystem.consolidateMemories(1L);

            // No archiving should happen
            assertThat(m1.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
            verify(memoryService, never()).createMemory(anyLong(), any(), any(), anyString(), anyInt(), any(BigDecimal.class));
        }

        @Test
        void shouldUseHighestImportanceFromConsolidatedMemories() {
            Father father = createFather();

            Memory m1 = createMemory(father, 1, "0.60", "fact a", 10);
            m1.setId(1L);
            Memory m2 = createMemory(father, 3, "0.80", "fact b", 10);
            m2.setId(2L);

            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(List.of(m1, m2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(memoryService.createMemory(anyLong(), any(), any(), anyString(), anyInt(), any(BigDecimal.class)))
                    .thenReturn(createMemory(father, 3, "0.70", "consolidated", 0));

            memorySystem.consolidateMemories(1L);

            // Verify highest importance (3) is used
            verify(memoryService).createMemory(eq(1L), isNull(), eq(MemoryCategory.CONVERSATION_SUMMARY),
                    anyString(), eq(3), any(BigDecimal.class));
        }

        @Test
        void shouldAverageConfidenceScores() {
            Father father = createFather();

            Memory m1 = createMemory(father, 2, "0.60", "fact a", 10);
            m1.setId(1L);
            Memory m2 = createMemory(father, 2, "0.80", "fact b", 10);
            m2.setId(2L);

            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(List.of(m1, m2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(memoryService.createMemory(anyLong(), any(), any(), anyString(), anyInt(), any(BigDecimal.class)))
                    .thenReturn(createMemory(father, 2, "0.70", "consolidated", 0));

            memorySystem.consolidateMemories(1L);

            // Verify average confidence: (0.60 + 0.80) / 2 = 0.70
            ArgumentCaptor<BigDecimal> confidenceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(memoryService).createMemory(eq(1L), isNull(), eq(MemoryCategory.CONVERSATION_SUMMARY),
                    anyString(), eq(2), confidenceCaptor.capture());

            assertThat(confidenceCaptor.getValue()).isEqualByComparingTo(new BigDecimal("0.70"));
        }
    }

    // ─── Supersede ───────────────────────────────────────────────────────────

    @Nested
    class Supersede {

        @Test
        void shouldDelegateToMemoryService() {
            Father father = createFather();
            Memory expected = createMemory(father, 5, "1.00", "corrected content", 0);
            when(memoryService.supersedeMemory(42L, "corrected content")).thenReturn(expected);

            Memory result = memorySystem.supersedeMemory(42L, "corrected content");

            assertThat(result).isEqualTo(expected);
            verify(memoryService).supersedeMemory(42L, "corrected content");
        }
    }

    // ─── Expire Low Confidence ───────────────────────────────────────────────

    @Nested
    class ExpireLowConfidence {

        @Test
        void shouldExpireMemoriesWithLowConfidenceNotAccessedIn60Days() {
            Father father = createFather();
            Memory m1 = createMemory(father, 3, "0.30", "old stale memory", 70);
            m1.setId(1L);

            when(memoryRepository.findAllLowConfidenceUnaccessed(any(BigDecimal.class), any(Instant.class)))
                    .thenReturn(List.of(m1));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            memorySystem.expireLowConfidenceMemories();

            assertThat(m1.getStatus()).isEqualTo(MemoryStatus.EXPIRED);
            verify(memoryRepository).save(m1);
        }

        @Test
        void shouldNotExpireAnythingWhenNoQualifyingMemories() {
            when(memoryRepository.findAllLowConfidenceUnaccessed(any(BigDecimal.class), any(Instant.class)))
                    .thenReturn(List.of());

            memorySystem.expireLowConfidenceMemories();

            verify(memoryRepository, never()).save(any(Memory.class));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Father createFather() {
        Father father = new Father("+972501234567");
        father.setId(1L);
        return father;
    }

    private Memory createMemory(Father father, int importance, String confidence,
                                String content, int daysAgo) {
        BigDecimal conf = new BigDecimal(confidence).setScale(2, RoundingMode.HALF_UP);
        Memory m = new Memory(father, MemoryCategory.IDENTITY_FACT, content, importance, conf);
        m.setCreatedAt(Instant.now().minus(Duration.ofDays(daysAgo)));
        return m;
    }
}
