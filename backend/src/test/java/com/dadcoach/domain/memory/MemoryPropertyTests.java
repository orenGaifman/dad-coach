package com.dadcoach.domain.memory;

import com.dadcoach.domain.father.Father;
import com.dadcoach.memory.MemoryCategory;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Memory domain logic.
 *
 * Tests three correctness properties from the design document:
 * - Property 17: Memory tier expiration rules
 * - Property 18: Memory confidence decay on contradiction
 * - Property 20: Memory capacity limit enforcement
 */
class MemoryPropertyTests {

    // ─── Property 17: Memory Tier Expiration Rules ───────────────────────────

    /**
     * **Validates: Requirements 7.2**
     *
     * For any memory with importance_score 1-3 (SHORT_TERM),
     * the expiration should be created_at + 90 days.
     */
    @Property
    void shortTermMemoryShouldExpireIn90Days(
            @ForAll @IntRange(min = 1, max = 3) int importanceScore,
            @ForAll("pastInstant") Instant createdAt) {

        Instant expiration = Memory.calculateExpiration(createdAt, importanceScore);
        Instant expected = createdAt.plus(90, ChronoUnit.DAYS);

        assertThat(expiration).isEqualTo(expected);
    }

    /**
     * **Validates: Requirements 7.2**
     *
     * For any memory with importance_score 4-6 (MEDIUM_TERM),
     * the expiration should be created_at + 180 days.
     */
    @Property
    void mediumTermMemoryShouldExpireIn180Days(
            @ForAll @IntRange(min = 4, max = 6) int importanceScore,
            @ForAll("pastInstant") Instant createdAt) {

        Instant expiration = Memory.calculateExpiration(createdAt, importanceScore);
        Instant expected = createdAt.plus(180, ChronoUnit.DAYS);

        assertThat(expiration).isEqualTo(expected);
    }

    /**
     * **Validates: Requirements 7.2**
     *
     * For any memory with importance_score 7-10 (LONG_TERM),
     * the expiration should be null (never expires).
     */
    @Property
    void longTermMemoryShouldNeverExpire(
            @ForAll @IntRange(min = 7, max = 10) int importanceScore,
            @ForAll("pastInstant") Instant createdAt) {

        Instant expiration = Memory.calculateExpiration(createdAt, importanceScore);

        assertThat(expiration).isNull();
    }

    /**
     * **Validates: Requirements 7.2**
     *
     * For any valid importance score (1-10), the tier classification should be deterministic:
     * 1-3 → SHORT_TERM, 4-6 → MEDIUM_TERM, 7-10 → LONG_TERM.
     */
    @Property
    void tierClassificationIsDeterministic(
            @ForAll @IntRange(min = 1, max = 10) int importanceScore) {

        MemoryTier tier = MemoryTier.fromImportanceScore(importanceScore);

        if (importanceScore <= 3) {
            assertThat(tier).isEqualTo(MemoryTier.SHORT_TERM);
            assertThat(tier.getExpirationDays()).isEqualTo(90);
            assertThat(tier.expires()).isTrue();
        } else if (importanceScore <= 6) {
            assertThat(tier).isEqualTo(MemoryTier.MEDIUM_TERM);
            assertThat(tier.getExpirationDays()).isEqualTo(180);
            assertThat(tier.expires()).isTrue();
        } else {
            assertThat(tier).isEqualTo(MemoryTier.LONG_TERM);
            assertThat(tier.getExpirationDays()).isEqualTo(0);
            assertThat(tier.expires()).isFalse();
        }
    }

    // ─── Property 18: Memory Confidence Decay on Contradiction ───────────────

    /**
     * **Validates: Requirements 7.9**
     *
     * For any existing memory with confidence_score C where a contradiction is detected,
     * the updated confidence should be max(0.0, C - 0.3).
     */
    @Property
    void confidenceDecayShouldReduceByPointThreeMinZero(
            @ForAll("validConfidenceScore") BigDecimal originalConfidence) {

        Father father = new Father("+972501234567");
        father.setId(1L);

        Memory memory = new Memory(father, MemoryCategory.IDENTITY_FACT, "test content",
                5, originalConfidence);

        memory.applyConfidenceDecay();

        BigDecimal expected = originalConfidence.subtract(new BigDecimal("0.30"))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        assertThat(memory.getConfidenceScore()).isEqualByComparingTo(expected);
    }

    /**
     * **Validates: Requirements 7.9**
     *
     * Confidence should never go below zero after decay, regardless of initial value.
     */
    @Property
    void confidenceAfterDecayShouldNeverBeNegative(
            @ForAll("validConfidenceScore") BigDecimal originalConfidence) {

        Father father = new Father("+972501234567");
        father.setId(1L);

        Memory memory = new Memory(father, MemoryCategory.IDENTITY_FACT, "test content",
                5, originalConfidence);

        memory.applyConfidenceDecay();

        assertThat(memory.getConfidenceScore()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    /**
     * **Validates: Requirements 7.9**
     *
     * Multiple consecutive decays should each reduce by 0.3 (floored at 0.0).
     */
    @Property
    void multipleDecaysShouldEachReduceByPointThree(
            @ForAll("validConfidenceScore") BigDecimal originalConfidence,
            @ForAll @IntRange(min = 1, max = 5) int decayCount) {

        Father father = new Father("+972501234567");
        father.setId(1L);

        Memory memory = new Memory(father, MemoryCategory.IDENTITY_FACT, "test content",
                5, originalConfidence);

        for (int i = 0; i < decayCount; i++) {
            memory.applyConfidenceDecay();
        }

        BigDecimal expectedReduction = new BigDecimal("0.30").multiply(BigDecimal.valueOf(decayCount));
        BigDecimal expected = originalConfidence.subtract(expectedReduction)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        assertThat(memory.getConfidenceScore()).isEqualByComparingTo(expected);
    }

    // ─── Property 20: Memory Capacity Limit ──────────────────────────────────

    /**
     * **Validates: Requirements 7.11**
     *
     * When a father has more than 500 active memories, the system should archive
     * enough memories to bring the count back to 500, always choosing those with
     * the lowest combined score (importance × confidence) first.
     */
    @Property
    void capacityEnforcementShouldArchiveLowestScoresFirst(
            @ForAll @IntRange(min = 501, max = 510) int activeCount) {

        // Create a MemoryService with mocked dependencies
        MemoryRepository mockRepo = mock(MemoryRepository.class);
        MemoryService service = new MemoryService(mockRepo, null, null);

        when(mockRepo.countActiveByFatherId(1L)).thenReturn((long) activeCount);

        // Create memories with distinct combined scores
        List<Memory> memories = new ArrayList<>();
        for (int i = 0; i < activeCount; i++) {
            Father father = new Father("+972501234567");
            father.setId(1L);
            int importance = (i % 10) + 1;
            BigDecimal confidence = new BigDecimal("0.10")
                    .add(new BigDecimal("0.09").multiply(BigDecimal.valueOf(i % 10)))
                    .setScale(2, RoundingMode.HALF_UP);
            if (confidence.compareTo(BigDecimal.ONE) > 0) {
                confidence = BigDecimal.ONE;
            }
            Memory m = new Memory(father, MemoryCategory.IDENTITY_FACT, "content " + i,
                    importance, confidence);
            m.setId((long) i);
            memories.add(m);
        }

        // Sort ascending by combined score (what the repository would return)
        memories.sort(Comparator.comparing(Memory::getCombinedScore));

        when(mockRepo.findActiveByFatherIdOrderByCombinedScoreAsc(1L)).thenReturn(memories);
        when(mockRepo.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

        int archived = service.enforceCapacityLimit(1L);

        long expectedExcess = activeCount - Memory.MAX_ACTIVE_MEMORIES_PER_FATHER;
        assertThat(archived).isEqualTo((int) expectedExcess);

        // Verify the archived ones are the lowest-scored
        for (int i = 0; i < archived; i++) {
            assertThat(memories.get(i).getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
        }
        // The rest remain active
        for (int i = archived; i < memories.size(); i++) {
            assertThat(memories.get(i).getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        }
    }

    /**
     * **Validates: Requirements 7.11**
     *
     * When a father has 500 or fewer active memories, no archiving should occur.
     */
    @Property
    void noArchivingWhenUnderOrAtLimit(
            @ForAll @IntRange(min = 0, max = 500) int activeCount) {

        MemoryRepository mockRepo = mock(MemoryRepository.class);
        MemoryService service = new MemoryService(mockRepo, null, null);

        when(mockRepo.countActiveByFatherId(1L)).thenReturn((long) activeCount);

        int archived = service.enforceCapacityLimit(1L);

        assertThat(archived).isZero();
        verify(mockRepo, never()).findActiveByFatherIdOrderByCombinedScoreAsc(anyLong());
    }

    // ─── Providers ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Instant> pastInstant() {
        // Generate instants within the last 2 years
        long now = Instant.now().getEpochSecond();
        long twoYearsAgo = now - (2L * 365 * 24 * 3600);
        return Arbitraries.longs()
                .between(twoYearsAgo, now)
                .map(Instant::ofEpochSecond);
    }

    @Provide
    Arbitrary<BigDecimal> validConfidenceScore() {
        // Generate confidence scores between 0.00 and 1.00 with 2 decimal places
        return Arbitraries.integers()
                .between(0, 100)
                .map(i -> new BigDecimal(i).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
    }
}
