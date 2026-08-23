package com.dadcoach.memory.retrieval;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemorySubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Tests for CompositeScoreCalculator.
 *
 * <p>These tests verify the composite scoring formula defined in SPEC-004 Requirement 16 criteria 2:
 * <pre>
 * composite_score = (importance/10 × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)
 * recency_factor = max(0, 1.0 - (days_since_last_access × 0.05))
 * </pre>
 *
 * <p><strong>Validates: Requirements 16.2</strong>
 *
 * @see CompositeScoreCalculator
 */
@DisplayName("CompositeScoreCalculator Tests")
class CompositeScoreCalculatorTest {

    // ─── Test Constants ──────────────────────────────────────────────────
    
    private static final Instant FIXED_NOW = Instant.parse("2024-06-15T12:00:00Z");
    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final double DELTA = 0.0001; // Tolerance for floating-point comparisons

    private CompositeScoreCalculator calculator;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        calculator = new CompositeScoreCalculator(fixedClock);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Composite Score Formula
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Composite Score Formula Tests")
    class CompositeScoreFormulaTests {

        @Test
        @DisplayName("Should calculate composite score with all components at maximum")
        void shouldCalculateMaxScore() {
            // Arrange: importance=10, accessed today, cosine similarity=1.0
            Memory memory = createMemory(10, FIXED_NOW);
            float cosineSimilarity = 1.0f;

            // Act
            double score = calculator.calculate(memory, cosineSimilarity);

            // Assert
            // (10/10 × 0.5) + (1.0 × 0.3) + (1.0 × 0.2) = 0.5 + 0.3 + 0.2 = 1.0
            assertThat(score).isCloseTo(1.0, within(DELTA));
        }

        @Test
        @DisplayName("Should calculate composite score with minimum importance")
        void shouldCalculateScoreWithMinImportance() {
            // Arrange: importance=1, accessed today, cosine similarity=1.0
            Memory memory = createMemory(1, FIXED_NOW);
            float cosineSimilarity = 1.0f;

            // Act
            double score = calculator.calculate(memory, cosineSimilarity);

            // Assert
            // (1/10 × 0.5) + (1.0 × 0.3) + (1.0 × 0.2) = 0.05 + 0.3 + 0.2 = 0.55
            assertThat(score).isCloseTo(0.55, within(DELTA));
        }

        @Test
        @DisplayName("Should calculate composite score with zero cosine similarity")
        void shouldCalculateScoreWithZeroRelevance() {
            // Arrange: importance=10, accessed today, cosine similarity=0.0
            Memory memory = createMemory(10, FIXED_NOW);
            float cosineSimilarity = 0.0f;

            // Act
            double score = calculator.calculate(memory, cosineSimilarity);

            // Assert
            // (10/10 × 0.5) + (1.0 × 0.3) + (0.0 × 0.2) = 0.5 + 0.3 + 0.0 = 0.8
            assertThat(score).isCloseTo(0.8, within(DELTA));
        }

        @Test
        @DisplayName("Should calculate composite score with old memory (20+ days)")
        void shouldCalculateScoreWithZeroRecency() {
            // Arrange: importance=10, accessed 25 days ago (recency=0), cosine similarity=1.0
            Instant twentyFiveDaysAgo = FIXED_NOW.minus(25, ChronoUnit.DAYS);
            Memory memory = createMemory(10, twentyFiveDaysAgo);
            float cosineSimilarity = 1.0f;

            // Act
            double score = calculator.calculate(memory, cosineSimilarity);

            // Assert
            // recency_factor = max(0, 1.0 - (25 × 0.05)) = max(0, -0.25) = 0
            // (10/10 × 0.5) + (0.0 × 0.3) + (1.0 × 0.2) = 0.5 + 0.0 + 0.2 = 0.7
            assertThat(score).isCloseTo(0.7, within(DELTA));
        }

        @ParameterizedTest(name = "importance={0}, daysSinceAccess={1}, cosine={2} → score={3}")
        @CsvSource({
                // importance, daysSinceAccess, cosineSimilarity, expectedScore
                "10, 0, 1.0, 1.0",      // Max score
                "1, 0, 1.0, 0.55",      // Min importance
                "5, 0, 1.0, 0.75",      // Mid importance
                "10, 10, 1.0, 0.85",    // 10 days: recency = 0.5
                "10, 20, 1.0, 0.7",     // 20 days: recency = 0
                "10, 0, 0.5, 0.9",      // Half cosine similarity
                "5, 10, 0.5, 0.50",     // Mid all values: (0.25) + (0.15) + (0.1) = 0.5
        })
        @DisplayName("Should calculate composite score correctly for various inputs")
        void shouldCalculateCorrectScoreForVariousInputs(
                int importance, int daysSinceAccess, float cosineSimilarity, double expectedScore) {
            // Arrange
            Instant accessTime = FIXED_NOW.minus(daysSinceAccess, ChronoUnit.DAYS);
            Memory memory = createMemory(importance, accessTime);

            // Act
            double score = calculator.calculate(memory, cosineSimilarity);

            // Assert
            assertThat(score).isCloseTo(expectedScore, within(DELTA));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Importance Component
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Importance Component Tests")
    class ImportanceComponentTests {

        @ParameterizedTest(name = "importance={0} → component={1}")
        @CsvSource({
                "1, 0.05",   // 1/10 × 0.5 = 0.05
                "2, 0.10",   // 2/10 × 0.5 = 0.10
                "3, 0.15",   // 3/10 × 0.5 = 0.15
                "4, 0.20",   // 4/10 × 0.5 = 0.20
                "5, 0.25",   // 5/10 × 0.5 = 0.25
                "6, 0.30",   // 6/10 × 0.5 = 0.30
                "7, 0.35",   // 7/10 × 0.5 = 0.35
                "8, 0.40",   // 8/10 × 0.5 = 0.40
                "9, 0.45",   // 9/10 × 0.5 = 0.45
                "10, 0.50"   // 10/10 × 0.5 = 0.50
        })
        @DisplayName("Should calculate correct importance component for all valid scores")
        void shouldCalculateCorrectImportanceComponent(int importance, double expectedComponent) {
            // Act
            double component = calculator.calculateImportanceComponent(importance);

            // Assert
            assertThat(component).isCloseTo(expectedComponent, within(DELTA));
        }

        @Test
        @DisplayName("Importance weight should be 0.5")
        void importanceWeightShouldBe05() {
            assertThat(CompositeScoreCalculator.IMPORTANCE_WEIGHT).isEqualTo(0.5);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Recency Factor
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Recency Factor Tests")
    class RecencyFactorTests {

        @Test
        @DisplayName("Recency factor should be 1.0 when accessed today")
        void recencyFactorShouldBe1WhenAccessedToday() {
            // Arrange
            Memory memory = createMemory(5, FIXED_NOW);

            // Act
            double recencyFactor = calculator.calculateRecencyFactor(memory);

            // Assert
            assertThat(recencyFactor).isCloseTo(1.0, within(DELTA));
        }

        @ParameterizedTest(name = "days since access={0} → recency factor={1}")
        @CsvSource({
                "0, 1.0",     // max(0, 1.0 - (0 × 0.05)) = 1.0
                "1, 0.95",    // max(0, 1.0 - (1 × 0.05)) = 0.95
                "5, 0.75",    // max(0, 1.0 - (5 × 0.05)) = 0.75
                "10, 0.5",    // max(0, 1.0 - (10 × 0.05)) = 0.5
                "15, 0.25",   // max(0, 1.0 - (15 × 0.05)) = 0.25
                "19, 0.05",   // max(0, 1.0 - (19 × 0.05)) = 0.05
                "20, 0.0",    // max(0, 1.0 - (20 × 0.05)) = 0.0
                "25, 0.0",    // max(0, 1.0 - (25 × 0.05)) = 0.0 (clamped)
                "100, 0.0"    // max(0, 1.0 - (100 × 0.05)) = 0.0 (clamped)
        })
        @DisplayName("Recency factor should decay correctly over days")
        void recencyFactorShouldDecayCorrectly(int daysSinceAccess, double expectedFactor) {
            // Arrange
            Instant accessTime = FIXED_NOW.minus(daysSinceAccess, ChronoUnit.DAYS);
            Memory memory = createMemory(5, accessTime);

            // Act
            double recencyFactor = calculator.calculateRecencyFactor(memory);

            // Assert
            assertThat(recencyFactor).isCloseTo(expectedFactor, within(DELTA));
        }

        @Test
        @DisplayName("Recency factor should never be negative")
        void recencyFactorShouldNeverBeNegative() {
            // Arrange: very old memory
            Instant veryOld = FIXED_NOW.minus(1000, ChronoUnit.DAYS);
            Memory memory = createMemory(5, veryOld);

            // Act
            double recencyFactor = calculator.calculateRecencyFactor(memory);

            // Assert
            assertThat(recencyFactor).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("Should use createdAt when lastAccessedAt is null")
        void shouldUseCreatedAtWhenLastAccessedAtIsNull() {
            // Arrange
            Instant createdAt = FIXED_NOW.minus(5, ChronoUnit.DAYS);
            Memory memory = createMemoryWithoutAccess(5, createdAt);
            
            // Memory was created 5 days ago and never accessed
            assertThat(memory.getLastAccessedAt()).isNull();

            // Act
            double recencyFactor = calculator.calculateRecencyFactor(memory);

            // Assert
            // Uses createdAt: max(0, 1.0 - (5 × 0.05)) = 0.75
            assertThat(recencyFactor).isCloseTo(0.75, within(DELTA));
        }

        @Test
        @DisplayName("Recency decay rate should be 0.05 per day")
        void recencyDecayRateShouldBe005PerDay() {
            assertThat(CompositeScoreCalculator.RECENCY_DECAY_RATE_PER_DAY).isEqualTo(0.05);
        }

        @Test
        @DisplayName("Recency weight should be 0.3")
        void recencyWeightShouldBe03() {
            assertThat(CompositeScoreCalculator.RECENCY_WEIGHT).isEqualTo(0.3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Relevance Component
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Relevance Component Tests")
    class RelevanceComponentTests {

        @ParameterizedTest(name = "cosine similarity={0} → component={1}")
        @CsvSource({
                "0.0, 0.0",    // 0.0 × 0.2 = 0.0
                "0.25, 0.05",  // 0.25 × 0.2 = 0.05
                "0.5, 0.1",    // 0.5 × 0.2 = 0.1
                "0.75, 0.15",  // 0.75 × 0.2 = 0.15
                "1.0, 0.2"     // 1.0 × 0.2 = 0.2
        })
        @DisplayName("Should calculate correct relevance component")
        void shouldCalculateCorrectRelevanceComponent(float cosineSimilarity, double expectedComponent) {
            // Act
            double component = calculator.calculateRelevanceComponent(cosineSimilarity);

            // Assert
            assertThat(component).isCloseTo(expectedComponent, within(DELTA));
        }

        @Test
        @DisplayName("Relevance weight should be 0.2")
        void relevanceWeightShouldBe02() {
            assertThat(CompositeScoreCalculator.RELEVANCE_WEIGHT).isEqualTo(0.2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when memory is null")
        void shouldThrowWhenMemoryIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.calculate(null, 0.5f))
                    .withMessage("Memory cannot be null");
        }

        @Test
        @DisplayName("Sum of weights should equal 1.0")
        void sumOfWeightsShouldEqual1() {
            double sumOfWeights = CompositeScoreCalculator.IMPORTANCE_WEIGHT
                    + CompositeScoreCalculator.RECENCY_WEIGHT
                    + CompositeScoreCalculator.RELEVANCE_WEIGHT;

            assertThat(sumOfWeights).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Score should be in valid range 0.0-1.0 for any valid input")
        void scoreShouldBeInValidRange() {
            // Test with various combinations
            for (int importance = 1; importance <= 10; importance++) {
                for (int daysSinceAccess = 0; daysSinceAccess <= 30; daysSinceAccess += 5) {
                    for (float cosine = 0.0f; cosine <= 1.0f; cosine += 0.25f) {
                        Instant accessTime = FIXED_NOW.minus(daysSinceAccess, ChronoUnit.DAYS);
                        Memory memory = createMemory(importance, accessTime);
                        
                        double score = calculator.calculate(memory, cosine);
                        
                        assertThat(score)
                                .as("Score for importance=%d, days=%d, cosine=%.2f",
                                        importance, daysSinceAccess, cosine)
                                .isBetween(0.0, 1.0);
                    }
                }
            }
        }

        @Test
        @DisplayName("Maximum importance score constant should be 10")
        void maxImportanceScoreShouldBe10() {
            assertThat(CompositeScoreCalculator.MAX_IMPORTANCE_SCORE).isEqualTo(10.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Clock Injection (Testability)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Clock Injection Tests")
    class ClockInjectionTests {

        @Test
        @DisplayName("Default constructor should use system clock")
        void defaultConstructorShouldUseSystemClock() {
            // Arrange
            CompositeScoreCalculator defaultCalculator = new CompositeScoreCalculator();
            Memory memory = createMemory(10, Instant.now());

            // Act - should not throw
            double score = defaultCalculator.calculate(memory, 1.0f);

            // Assert - score should be valid
            assertThat(score).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Injected clock should be used for recency calculation")
        void injectedClockShouldBeUsedForRecencyCalculation() {
            // Arrange: create a clock 10 days in the future
            Instant futureNow = FIXED_NOW.plus(10, ChronoUnit.DAYS);
            Clock futureClock = Clock.fixed(futureNow, ZoneId.of("UTC"));
            CompositeScoreCalculator futureCalculator = new CompositeScoreCalculator(futureClock);
            
            // Memory accessed "now" (FIXED_NOW), but clock is 10 days in the future
            Memory memory = createMemory(10, FIXED_NOW);

            // Act
            double recencyFactor = futureCalculator.calculateRecencyFactor(memory);

            // Assert: 10 days difference → recency = 0.5
            assertThat(recencyFactor).isCloseTo(0.5, within(DELTA));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a test memory with specified importance and last access time.
     */
    private Memory createMemory(int importanceScore, Instant lastAccessedAt) {
        Memory memory = new Memory(
                TEST_FATHER_ID,
                MemoryCategory.IDENTITY,
                MemorySubjectType.FATHER,
                "Test memory content",
                importanceScore,
                new BigDecimal("0.8"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setLastAccessedAt(lastAccessedAt);
        memory.setCreatedAt(FIXED_NOW.minus(30, ChronoUnit.DAYS)); // Created 30 days ago
        return memory;
    }

    /**
     * Creates a test memory that has never been accessed (lastAccessedAt is null).
     */
    private Memory createMemoryWithoutAccess(int importanceScore, Instant createdAt) {
        Memory memory = new Memory(
                TEST_FATHER_ID,
                MemoryCategory.IDENTITY,
                MemorySubjectType.FATHER,
                "Test memory content",
                importanceScore,
                new BigDecimal("0.8"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setCreatedAt(createdAt);
        // lastAccessedAt remains null
        return memory;
    }
}
