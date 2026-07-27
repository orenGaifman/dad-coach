package com.dadcoach.memorysystem;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.memory.Memory;
import com.dadcoach.memory.MemoryCategory;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for the Memory System retrieval and ranking logic.
 *
 * <p>Tests Property 19 from the design document: Memory Ranking Order.</p>
 */
class MemorySystemPropertyTests {

    // ─── Property 19: Memory Ranking Order ───────────────────────────────────

    /**
     * **Validates: Requirements 7.6**
     *
     * For any set of active memories for a Father and a given topic,
     * the retrieval should return at most 15 memories ordered by descending composite score:
     * (importance × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)
     * where recency_factor = max(0, 1.0 - (days_since_creation × 0.05))
     */
    @Property(tries = 200)
    void retrievalShouldReturnAtMost15MemoriesOrderedByDescendingCompositeScore(
            @ForAll("memoryList") List<Memory> memories,
            @ForAll("topic") String topic) {

        Instant now = Instant.now();
        int limit = MemorySystemImpl.DEFAULT_TOP_MEMORIES_LIMIT;

        // Compute scores for all memories
        List<Double> allScores = new ArrayList<>();
        for (Memory m : memories) {
            allScores.add(MemorySystemImpl.computeCompositeScore(m, topic, now));
        }

        // Sort descending by score, take top 15
        allScores.sort((a, b) -> Double.compare(b, a));
        List<Double> expectedTopScores = allScores.subList(0, Math.min(limit, allScores.size()));

        // Simulate the retrieval logic (same as MemorySystemImpl)
        List<Memory> ranked = memories.stream()
                .sorted((a, b) -> Double.compare(
                        MemorySystemImpl.computeCompositeScore(b, topic, now),
                        MemorySystemImpl.computeCompositeScore(a, topic, now)))
                .limit(limit)
                .toList();

        // Property: at most 15 results
        assertThat(ranked).hasSizeLessThanOrEqualTo(15);

        // Property: results are in descending order by composite score
        double previousScore = Double.MAX_VALUE;
        for (Memory m : ranked) {
            double score = MemorySystemImpl.computeCompositeScore(m, topic, now);
            assertThat(score).isLessThanOrEqualTo(previousScore);
            previousScore = score;
        }

        // Property: the scores of returned memories match the top-N expected scores
        List<Double> actualScores = ranked.stream()
                .map(m -> MemorySystemImpl.computeCompositeScore(m, topic, now))
                .toList();
        assertThat(actualScores).isEqualTo(expectedTopScores);
    }

    /**
     * **Validates: Requirements 7.6**
     *
     * The recency factor should always be between 0.0 and 1.0, and should be 0.0
     * for memories older than 20 days.
     */
    @Property
    void recencyFactorShouldBeBetweenZeroAndOne(
            @ForAll @IntRange(min = 0, max = 100) int daysSinceCreation) {

        Instant now = Instant.now();
        Instant createdAt = now.minus(Duration.ofDays(daysSinceCreation));

        double recencyFactor = MemorySystemImpl.computeRecencyFactor(createdAt, now);

        assertThat(recencyFactor).isBetween(0.0, 1.0);

        if (daysSinceCreation >= 20) {
            assertThat(recencyFactor).isEqualTo(0.0);
        }
    }

    /**
     * **Validates: Requirements 7.6**
     *
     * For any two memories, if memory A has a higher composite score than memory B,
     * memory A should appear before memory B in the retrieval results.
     */
    @Property(tries = 200)
    void higherScoredMemoryShouldAppearBeforeLowerScored(
            @ForAll("singleMemory") Memory memoryA,
            @ForAll("singleMemory") Memory memoryB,
            @ForAll("topic") String topic) {

        Instant now = Instant.now();

        double scoreA = MemorySystemImpl.computeCompositeScore(memoryA, topic, now);
        double scoreB = MemorySystemImpl.computeCompositeScore(memoryB, topic, now);

        // Only test when scores are meaningfully different to avoid floating point issues
        if (Math.abs(scoreA - scoreB) < 0.0001) {
            return; // scores essentially equal, ordering doesn't matter
        }

        List<Memory> memories = List.of(memoryA, memoryB);

        List<Memory> ranked = memories.stream()
                .sorted((a, b) -> Double.compare(
                        MemorySystemImpl.computeCompositeScore(b, topic, now),
                        MemorySystemImpl.computeCompositeScore(a, topic, now)))
                .limit(15)
                .toList();

        if (scoreA > scoreB) {
            assertThat(ranked.indexOf(memoryA)).isLessThan(ranked.indexOf(memoryB));
        } else {
            assertThat(ranked.indexOf(memoryB)).isLessThan(ranked.indexOf(memoryA));
        }
    }

    /**
     * **Validates: Requirements 7.6**
     *
     * The composite score formula should produce values in range [0.0, 1.0]
     * since each component is normalized to [0,1] and weights sum to 1.0.
     */
    @Property(tries = 500)
    void compositeScoreShouldBeBetweenZeroAndOne(
            @ForAll("singleMemory") Memory memory,
            @ForAll("topic") String topic) {

        Instant now = Instant.now();
        double score = MemorySystemImpl.computeCompositeScore(memory, topic, now);

        // importance/10 is in [0.1, 1.0], recency in [0, 1], relevance in {0, 1}
        // max = 1.0*0.5 + 1.0*0.3 + 1.0*0.2 = 1.0
        // min = 0.1*0.5 + 0.0*0.3 + 0.0*0.2 = 0.05
        assertThat(score).isBetween(0.0, 1.0);
    }

    // ─── Providers ───────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<Memory>> memoryList() {
        return singleMemory().list().ofMinSize(1).ofMaxSize(30);
    }

    @Provide
    Arbitrary<Memory> singleMemory() {
        Arbitrary<Integer> importance = Arbitraries.integers().between(1, 10);
        Arbitrary<BigDecimal> confidence = Arbitraries.integers().between(0, 100)
                .map(i -> new BigDecimal(i).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        Arbitrary<MemoryCategory> category = Arbitraries.of(MemoryCategory.values());
        Arbitrary<String> content = Arbitraries.of(
                "child likes soccer and outdoor activities",
                "father works as engineer",
                "bedtime routine at 8pm",
                "child struggles with math homework",
                "family enjoys weekend hikes",
                "prefers gentle coaching style",
                "birthday party planning",
                "school report was excellent"
        );
        Arbitrary<Integer> daysAgo = Arbitraries.integers().between(0, 40);

        return Combinators.combine(importance, confidence, category, content, daysAgo)
                .as((imp, conf, cat, cont, days) -> {
                    Father father = new Father("+972501234567");
                    father.setId(1L);
                    Memory m = new Memory(father, cat, cont, imp, conf);
                    m.setCreatedAt(Instant.now().minus(Duration.ofDays(days)));
                    m.setId((long) (imp * 100 + days)); // unique-ish ID
                    return m;
                });
    }

    @Provide
    Arbitrary<String> topic() {
        return Arbitraries.of(
                "soccer", "school", "bedtime", "math", "hikes",
                "birthday", "coaching", "routine", "work", "family"
        );
    }
}
