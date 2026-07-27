package com.dadcoach.domain.memory;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.memory.MemoryCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryService covering:
 * - Memory creation with tier-based expiration
 * - Supersede operations (Req 7.7)
 * - Confidence decay on contradiction (Req 7.9)
 * - Access tracking (Req 7.10)
 * - 500-memory capacity enforcement (Req 7.11)
 * - Expiration operations
 */
@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private FatherRepository fatherRepository;

    @Mock
    private ChildRepository childRepository;

    @InjectMocks
    private MemoryService memoryService;

    private Father father;
    private Child child;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(1L);

        child = new Child(father, "Test Child", LocalDate.of(2018, 6, 15));
        child.setId(10L);
    }

    private Memory createTestMemory(int importanceScore, BigDecimal confidenceScore) {
        Memory memory = new Memory(father, MemoryCategory.IDENTITY_FACT, "Test content",
                importanceScore, confidenceScore);
        memory.setId(100L);
        memory.setFatherId(1L);
        return memory;
    }

    // ─── Creation Tests ──────────────────────────────────────────────────

    @Nested
    class CreateMemory {

        @Test
        void shouldCreateMemoryWithShortTermExpiration() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(100L);
                return m;
            });
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(1L);

            Memory result = memoryService.createMemory(1L, null,
                    MemoryCategory.TRANSIENT_STATE, "Feeling stressed", 2, new BigDecimal("0.80"));

            assertThat(result.getImportanceScore()).isEqualTo(2);
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.80"));
            assertThat(result.getCategory()).isEqualTo(MemoryCategory.TRANSIENT_STATE);
            assertThat(result.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(result.getExpiresAt()).isNotNull();
            // Short-term: expires in 90 days
            assertThat(result.getExpiresAt())
                    .isAfter(Instant.now().plus(89, ChronoUnit.DAYS))
                    .isBefore(Instant.now().plus(91, ChronoUnit.DAYS));
        }

        @Test
        void shouldCreateMemoryWithMediumTermExpiration() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(101L);
                return m;
            });
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(1L);

            Memory result = memoryService.createMemory(1L, null,
                    MemoryCategory.PREFERENCE, "Likes soccer", 5, new BigDecimal("0.90"));

            assertThat(result.getExpiresAt()).isNotNull();
            // Medium-term: expires in 180 days
            assertThat(result.getExpiresAt())
                    .isAfter(Instant.now().plus(179, ChronoUnit.DAYS))
                    .isBefore(Instant.now().plus(181, ChronoUnit.DAYS));
        }

        @Test
        void shouldCreateMemoryWithNoExpirationForLongTerm() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(102L);
                return m;
            });
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(1L);

            Memory result = memoryService.createMemory(1L, null,
                    MemoryCategory.IDENTITY_FACT, "Child's name is David", 9, BigDecimal.ONE);

            // Long-term: never expires
            assertThat(result.getExpiresAt()).isNull();
        }

        @Test
        void shouldLinkChildWhenChildIdProvided() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(10L)).thenReturn(Optional.of(child));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                m.setId(103L);
                return m;
            });
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(1L);

            Memory result = memoryService.createMemory(1L, 10L,
                    MemoryCategory.PREFERENCE, "Likes dinosaurs", 5, new BigDecimal("0.80"));

            assertThat(result.getChild()).isEqualTo(child);
        }

        @Test
        void shouldThrowWhenFatherNotFound() {
            when(fatherRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memoryService.createMemory(999L, null,
                    MemoryCategory.IDENTITY_FACT, "Content", 5, new BigDecimal("0.80")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Father");
        }

        @Test
        void shouldThrowWhenChildNotFound() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memoryService.createMemory(1L, 999L,
                    MemoryCategory.IDENTITY_FACT, "Content", 5, new BigDecimal("0.80")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Child");
        }

        @Test
        void shouldRejectImportanceScoreBelowOne() {
            assertThatThrownBy(() -> memoryService.createMemory(1L, null,
                    MemoryCategory.IDENTITY_FACT, "Content", 0, new BigDecimal("0.80")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_IMPORTANCE_SCORE");
        }

        @Test
        void shouldRejectImportanceScoreAboveTen() {
            assertThatThrownBy(() -> memoryService.createMemory(1L, null,
                    MemoryCategory.IDENTITY_FACT, "Content", 11, new BigDecimal("0.80")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_IMPORTANCE_SCORE");
        }

        @Test
        void shouldRejectConfidenceScoreBelowZero() {
            assertThatThrownBy(() -> memoryService.createMemory(1L, null,
                    MemoryCategory.IDENTITY_FACT, "Content", 5, new BigDecimal("-0.10")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_CONFIDENCE_SCORE");
        }

        @Test
        void shouldRejectConfidenceScoreAboveOne() {
            assertThatThrownBy(() -> memoryService.createMemory(1L, null,
                    MemoryCategory.IDENTITY_FACT, "Content", 5, new BigDecimal("1.10")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_CONFIDENCE_SCORE");
        }
    }

    // ─── Supersede Tests ─────────────────────────────────────────────────

    @Nested
    class SupersedeMemory {

        @Test
        void shouldSupersedExistingMemoryWithNewContent() {
            Memory existing = createTestMemory(8, new BigDecimal("0.70"));
            existing.setFatherId(1L);

            when(memoryRepository.findById(100L)).thenReturn(Optional.of(existing));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> {
                Memory m = inv.getArgument(0);
                if (m.getId() == null) {
                    m.setId(200L); // new memory
                }
                return m;
            });
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(2L);

            Memory result = memoryService.supersedeMemory(100L, "Corrected content");

            // New memory has confidence 1.0
            assertThat(result.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(result.getContent()).isEqualTo("Corrected content");
            assertThat(result.getCategory()).isEqualTo(existing.getCategory());
            assertThat(result.getImportanceScore()).isEqualTo(existing.getImportanceScore());

            // Old memory is superseded
            assertThat(existing.getStatus()).isEqualTo(MemoryStatus.SUPERSEDED);
            assertThat(existing.getSupersededBy()).isEqualTo(200L);
        }

        @Test
        void shouldThrowWhenMemoryNotActive() {
            Memory expired = createTestMemory(5, new BigDecimal("0.50"));
            expired.expire();

            when(memoryRepository.findById(100L)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> memoryService.supersedeMemory(100L, "New content"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("MEMORY_NOT_ACTIVE");
        }

        @Test
        void shouldThrowWhenMemoryNotFound() {
            when(memoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memoryService.supersedeMemory(999L, "New content"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── Confidence Decay Tests ──────────────────────────────────────────

    @Nested
    class ConfidenceDecay {

        @Test
        void shouldReduceConfidenceByPointThree() {
            Memory memory = createTestMemory(5, new BigDecimal("0.80"));
            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.applyConfidenceDecay(100L);

            assertThat(result.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.50"));
        }

        @Test
        void shouldNotGoBelowZero() {
            Memory memory = createTestMemory(5, new BigDecimal("0.20"));
            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.applyConfidenceDecay(100L);

            assertThat(result.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldHandleExactlyPointThree() {
            Memory memory = createTestMemory(5, new BigDecimal("0.30"));
            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.applyConfidenceDecay(100L);

            assertThat(result.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldHandleZeroConfidence() {
            Memory memory = createTestMemory(5, BigDecimal.ZERO);
            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.applyConfidenceDecay(100L);

            assertThat(result.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldThrowWhenMemoryNotActive() {
            Memory memory = createTestMemory(5, new BigDecimal("0.80"));
            memory.archive();

            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));

            assertThatThrownBy(() -> memoryService.applyConfidenceDecay(100L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("MEMORY_NOT_ACTIVE");
        }
    }

    // ─── Access Tracking Tests ───────────────────────────────────────────

    @Nested
    class AccessTracking {

        @Test
        void shouldIncrementAccessCountAndUpdateTimestamp() {
            Memory memory = createTestMemory(7, new BigDecimal("0.90"));
            assertThat(memory.getAccessCount()).isZero();
            assertThat(memory.getLastAccessedAt()).isNull();

            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.recordAccess(100L);

            assertThat(result.getAccessCount()).isEqualTo(1);
            assertThat(result.getLastAccessedAt()).isNotNull();
        }

        @Test
        void shouldIncrementAccessCountMultipleTimes() {
            Memory memory = createTestMemory(7, new BigDecimal("0.90"));
            memory.setAccessCount(5);
            memory.setLastAccessedAt(Instant.now().minus(1, ChronoUnit.DAYS));

            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.recordAccess(100L);

            assertThat(result.getAccessCount()).isEqualTo(6);
        }
    }

    // ─── Capacity Enforcement Tests ──────────────────────────────────────

    @Nested
    class CapacityEnforcement {

        @Test
        void shouldNotArchiveWhenUnderLimit() {
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(499L);

            int archived = memoryService.enforceCapacityLimit(1L);

            assertThat(archived).isZero();
            verify(memoryRepository, never()).findActiveByFatherIdOrderByCombinedScoreAsc(anyLong());
        }

        @Test
        void shouldNotArchiveWhenAtExactLimit() {
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(500L);

            int archived = memoryService.enforceCapacityLimit(1L);

            assertThat(archived).isZero();
        }

        @Test
        void shouldArchiveLowestScoredMemoriesWhenOverLimit() {
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(502L);

            // Create memories with different combined scores
            Memory lowScore = createTestMemory(2, new BigDecimal("0.30")); // score = 0.6
            lowScore.setId(1L);
            Memory midScore = createTestMemory(4, new BigDecimal("0.50")); // score = 2.0
            midScore.setId(2L);
            Memory highScore = createTestMemory(9, new BigDecimal("0.90")); // score = 8.1
            highScore.setId(3L);

            when(memoryRepository.findActiveByFatherIdOrderByCombinedScoreAsc(1L))
                    .thenReturn(List.of(lowScore, midScore, highScore));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            int archived = memoryService.enforceCapacityLimit(1L);

            assertThat(archived).isEqualTo(2);
            assertThat(lowScore.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
            assertThat(midScore.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
            assertThat(highScore.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        }

        @Test
        void shouldArchiveOnlyExcessAmount() {
            when(memoryRepository.countActiveByFatherId(1L)).thenReturn(501L);

            Memory lowScore = createTestMemory(1, new BigDecimal("0.10")); // score = 0.1
            lowScore.setId(1L);
            Memory highScore = createTestMemory(10, BigDecimal.ONE); // score = 10.0
            highScore.setId(2L);

            when(memoryRepository.findActiveByFatherIdOrderByCombinedScoreAsc(1L))
                    .thenReturn(List.of(lowScore, highScore));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            int archived = memoryService.enforceCapacityLimit(1L);

            assertThat(archived).isEqualTo(1);
            assertThat(lowScore.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
            assertThat(highScore.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        }
    }

    // ─── Expiration Tests ────────────────────────────────────────────────

    @Nested
    class Expiration {

        @Test
        void shouldExpireSpecificMemory() {
            Memory memory = createTestMemory(3, new BigDecimal("0.50"));
            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            Memory result = memoryService.expireMemory(100L);

            assertThat(result.getStatus()).isEqualTo(MemoryStatus.EXPIRED);
        }

        @Test
        void shouldExpireOverdueMemories() {
            Memory m1 = createTestMemory(2, new BigDecimal("0.40"));
            m1.setId(1L);
            Memory m2 = createTestMemory(3, new BigDecimal("0.60"));
            m2.setId(2L);

            when(memoryRepository.findExpiredMemories(any(Instant.class)))
                    .thenReturn(List.of(m1, m2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

            int count = memoryService.expireOverdueMemories();

            assertThat(count).isEqualTo(2);
            assertThat(m1.getStatus()).isEqualTo(MemoryStatus.EXPIRED);
            assertThat(m2.getStatus()).isEqualTo(MemoryStatus.EXPIRED);
        }
    }

    // ─── Tier Classification Tests ───────────────────────────────────────

    @Nested
    class TierClassification {

        @Test
        void shouldClassifyImportance1AsShortTerm() {
            assertThat(MemoryTier.fromImportanceScore(1)).isEqualTo(MemoryTier.SHORT_TERM);
        }

        @Test
        void shouldClassifyImportance3AsShortTerm() {
            assertThat(MemoryTier.fromImportanceScore(3)).isEqualTo(MemoryTier.SHORT_TERM);
        }

        @Test
        void shouldClassifyImportance4AsMediumTerm() {
            assertThat(MemoryTier.fromImportanceScore(4)).isEqualTo(MemoryTier.MEDIUM_TERM);
        }

        @Test
        void shouldClassifyImportance6AsMediumTerm() {
            assertThat(MemoryTier.fromImportanceScore(6)).isEqualTo(MemoryTier.MEDIUM_TERM);
        }

        @Test
        void shouldClassifyImportance7AsLongTerm() {
            assertThat(MemoryTier.fromImportanceScore(7)).isEqualTo(MemoryTier.LONG_TERM);
        }

        @Test
        void shouldClassifyImportance10AsLongTerm() {
            assertThat(MemoryTier.fromImportanceScore(10)).isEqualTo(MemoryTier.LONG_TERM);
        }

        @Test
        void shouldThrowForImportanceBelowOne() {
            assertThatThrownBy(() -> MemoryTier.fromImportanceScore(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowForImportanceAboveTen() {
            assertThatThrownBy(() -> MemoryTier.fromImportanceScore(11))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── Retrieval Tests ─────────────────────────────────────────────────

    @Nested
    class Retrieval {

        @Test
        void getMemoryShouldReturnMemory() {
            Memory memory = createTestMemory(5, new BigDecimal("0.80"));
            when(memoryRepository.findById(100L)).thenReturn(Optional.of(memory));

            Memory result = memoryService.getMemory(100L);

            assertThat(result).isEqualTo(memory);
        }

        @Test
        void getMemoryShouldThrowWhenNotFound() {
            when(memoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memoryService.getMemory(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Memory");
        }

        @Test
        void getActiveMemoriesShouldDelegateToRepository() {
            Memory memory = createTestMemory(7, new BigDecimal("0.90"));
            when(memoryRepository.findActiveByFatherId(1L)).thenReturn(List.of(memory));

            List<Memory> results = memoryService.getActiveMemories(1L);

            assertThat(results).hasSize(1);
            verify(memoryRepository).findActiveByFatherId(1L);
        }
    }
}
