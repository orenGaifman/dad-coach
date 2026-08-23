package com.dadcoach.memory.retrieval;

import com.dadcoach.memory.*;
import com.dadcoach.memory.dto.MemoryDto;
import com.dadcoach.memory.dto.RetrievalResultDto;
import com.dadcoach.memory.mapper.MemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for MemoryRetriever.
 *
 * <p>These tests verify the memory retrieval functionality defined in SPEC-004 Requirement 16,
 * focusing on:
 * <ul>
 *   <li>Results ordered by descending composite score</li>
 *   <li>Correct calculation and application of composite scores</li>
 *   <li>Access tracking updates on retrieval</li>
 *   <li>Confidence filtering (>= 0.3)</li>
 *   <li>maxCount limiting</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 16.2, 16.3</b> - Composite scoring and descending order
 *
 * @see MemoryRetriever
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryRetriever Tests")
class MemoryRetrieverTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-06-15T12:00:00Z");
    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final UUID TEST_CHILD_ID = UUID.randomUUID();

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryMapper memoryMapper;

    private CompositeScoreCalculator scoreCalculator;
    private MemoryRetriever memoryRetriever;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        scoreCalculator = new CompositeScoreCalculator(fixedClock);
        memoryRetriever = new MemoryRetriever(memoryRepository, scoreCalculator, memoryMapper);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Results Ordered by Descending Composite Score
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Descending Composite Score Ordering Tests")
    class DescendingOrderTests {

        @Test
        @DisplayName("Should return results ordered by descending composite score")
        void shouldReturnResultsInDescendingCompositeScoreOrder() {
            // Arrange: Create memories with different characteristics
            // Memory 1: High importance (10), accessed today → highest score
            Memory highScoreMemory = createMemory(UUID.randomUUID(), 10, FIXED_NOW, new BigDecimal("0.9"));
            
            // Memory 2: Medium importance (5), accessed 10 days ago → medium score
            Memory mediumScoreMemory = createMemory(UUID.randomUUID(), 5, 
                    FIXED_NOW.minus(10, ChronoUnit.DAYS), new BigDecimal("0.8"));
            
            // Memory 3: Low importance (2), accessed 15 days ago → lowest score
            Memory lowScoreMemory = createMemory(UUID.randomUUID(), 2, 
                    FIXED_NOW.minus(15, ChronoUnit.DAYS), new BigDecimal("0.7"));

            // Return memories in wrong order (lowest to highest)
            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Arrays.asList(lowScoreMemory, mediumScoreMemory, highScoreMemory));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setConfidenceScore(m.getConfidenceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, "test topic", null, 10);

            // Assert: Results should be ordered by descending composite score
            assertThat(results).hasSize(3);
            
            // Verify descending order by checking each consecutive pair
            for (int i = 0; i < results.size() - 1; i++) {
                double currentScore = results.get(i).getCompositeScore();
                double nextScore = results.get(i + 1).getCompositeScore();
                assertThat(currentScore)
                        .as("Score at index %d (%.4f) should be >= score at index %d (%.4f)", 
                                i, currentScore, i + 1, nextScore)
                        .isGreaterThanOrEqualTo(nextScore);
            }

            // Verify the highest importance memory is first
            assertThat(results.get(0).getMemory().getId()).isEqualTo(highScoreMemory.getId());
            // Verify the lowest importance memory is last
            assertThat(results.get(2).getMemory().getId()).isEqualTo(lowScoreMemory.getId());
        }

        @Test
        @DisplayName("Should order by composite score considering all three factors")
        void shouldOrderByCompositeScoreConsideringAllFactors() {
            // Arrange: Create memories where recency differences outweigh importance
            // Memory 1: Importance 5, accessed today (recency=1.0)
            // Score = (5/10 × 0.5) + (1.0 × 0.3) + (0.5 × 0.2) = 0.25 + 0.3 + 0.1 = 0.65
            Memory recentButLowerImportance = createMemory(UUID.randomUUID(), 5, FIXED_NOW, new BigDecimal("0.8"));
            
            // Memory 2: Importance 8, accessed 18 days ago (recency=0.1)
            // Score = (8/10 × 0.5) + (0.1 × 0.3) + (0.5 × 0.2) = 0.4 + 0.03 + 0.1 = 0.53
            Memory oldButHigherImportance = createMemory(UUID.randomUUID(), 8, 
                    FIXED_NOW.minus(18, ChronoUnit.DAYS), new BigDecimal("0.8"));

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Arrays.asList(oldButHigherImportance, recentButLowerImportance));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert: Recent memory should be first despite lower importance
            assertThat(results).hasSize(2);
            assertThat(results.get(0).getMemory().getId()).isEqualTo(recentButLowerImportance.getId());
            assertThat(results.get(0).getCompositeScore()).isGreaterThan(results.get(1).getCompositeScore());
        }

        @Test
        @DisplayName("Should maintain descending order with many memories")
        void shouldMaintainDescendingOrderWithManyMemories() {
            // Arrange: Create 20 memories with varying characteristics across different categories
            // to ensure diversity filter doesn't limit the total
            List<Memory> memories = new ArrayList<>();
            Random random = new Random(42); // Fixed seed for reproducibility
            MemoryCategory[] categories = MemoryCategory.values();
            
            for (int i = 0; i < 20; i++) {
                int importance = random.nextInt(10) + 1; // 1-10
                int daysAgo = random.nextInt(25); // 0-24 days
                double confidence = 0.3 + random.nextDouble() * 0.7; // 0.3-1.0
                // Distribute across categories to avoid hitting the 5-per-category limit
                MemoryCategory category = categories[i % categories.length];
                
                Memory memory = createMemoryWithCategory(
                        UUID.randomUUID(),
                        importance,
                        FIXED_NOW.minus(daysAgo, ChronoUnit.DAYS),
                        new BigDecimal(String.format("%.2f", confidence)),
                        category
                );
                memories.add(memory);
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 20);

            // Assert: All results should be in strictly descending order
            assertThat(results).hasSize(20);
            
            List<Double> scores = results.stream()
                    .map(RetrievalResultDto::getCompositeScore)
                    .toList();
            
            assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: maxCount Limiting
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MaxCount Limiting Tests")
    class MaxCountLimitingTests {

        @Test
        @DisplayName("Should limit results to maxCount")
        void shouldLimitResultsToMaxCount() {
            // Arrange: Create 10 memories
            List<Memory> memories = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                memories.add(createMemory(UUID.randomUUID(), 10 - i, FIXED_NOW, new BigDecimal("0.8")));
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                return dto;
            });

            // Act: Request only 5
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 5);

            // Assert
            assertThat(results).hasSize(5);
            
            // Verify they are the top 5 by score (still in descending order)
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).getCompositeScore())
                        .isGreaterThanOrEqualTo(results.get(i + 1).getCompositeScore());
            }
        }

        @Test
        @DisplayName("Should return all results when maxCount exceeds available memories")
        void shouldReturnAllWhenMaxCountExceedsAvailable() {
            // Arrange: Create 3 memories
            List<Memory> memories = Arrays.asList(
                    createMemory(UUID.randomUUID(), 10, FIXED_NOW, new BigDecimal("0.8")),
                    createMemory(UUID.randomUUID(), 5, FIXED_NOW, new BigDecimal("0.8")),
                    createMemory(UUID.randomUUID(), 3, FIXED_NOW, new BigDecimal("0.8"))
            );

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                return dto;
            });

            // Act: Request 100
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 100);

            // Assert
            assertThat(results).hasSize(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Access Tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Access Tracking Tests")
    class AccessTrackingTests {

        @Test
        @DisplayName("Should update access tracking for retrieved memories")
        void shouldUpdateAccessTrackingForRetrievedMemories() {
            // Arrange
            Memory memory = createMemory(UUID.randomUUID(), 8, FIXED_NOW, new BigDecimal("0.8"));
            int initialAccessCount = memory.getAccessCount();

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Collections.singletonList(memory));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                return dto;
            });

            // Act
            memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert: Access count should be incremented
            assertThat(memory.getAccessCount()).isEqualTo(initialAccessCount + 1);
            
            // Verify saveAll was called
            ArgumentCaptor<List<Memory>> captor = ArgumentCaptor.forClass(List.class);
            verify(memoryRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).contains(memory);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Retrieval Metadata
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retrieval Metadata Tests")
    class RetrievalMetadataTests {

        @Test
        @DisplayName("Should include all score components in metadata")
        void shouldIncludeAllScoreComponentsInMetadata() {
            // Arrange
            Memory memory = createMemory(UUID.randomUUID(), 7, FIXED_NOW, new BigDecimal("0.65"));

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Collections.singletonList(memory));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setConfidenceScore(m.getConfidenceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert
            assertThat(results).hasSize(1);
            RetrievalMetadata metadata = results.get(0).getMetadata();
            
            assertThat(metadata.getImportanceScore()).isEqualTo(7);
            assertThat(metadata.getConfidenceScore()).isCloseTo(0.65, within(0.01));
            assertThat(metadata.getRecencyFactor()).isCloseTo(1.0, within(0.01)); // Accessed today
            assertThat(metadata.getRelevanceScore()).isCloseTo(0.5, within(0.01)); // Default relevance
            assertThat(metadata.getCompositeScore()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should flag uncertain memories with low confidence")
        void shouldFlagUncertainMemoriesWithLowConfidence() {
            // Arrange: Memory with confidence between 0.3 and 0.5
            Memory uncertainMemory = createMemory(UUID.randomUUID(), 5, FIXED_NOW, new BigDecimal("0.35"));

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Collections.singletonList(uncertainMemory));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setConfidenceScore(m.getConfidenceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).isUncertain()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Input Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when fatherId is null")
        void shouldThrowWhenFatherIdIsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> memoryRetriever.retrieveRanked(null, "topic", null, 10))
                    .withMessage("fatherId cannot be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when maxCount is less than 1")
        void shouldThrowWhenMaxCountIsLessThan1() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> memoryRetriever.retrieveRanked(TEST_FATHER_ID, "topic", null, 0))
                    .withMessage("maxCount must be at least 1");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Empty Results
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Empty Results Tests")
    class EmptyResultsTests {

        @Test
        @DisplayName("Should return empty list when no memories found")
        void shouldReturnEmptyListWhenNoMemoriesFound() {
            // Arrange
            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, "topic", null, 10);

            // Assert
            assertThat(results).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Diversity Filter (Max 5 Per Category)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Diversity Filter Tests")
    class DiversityFilterTests {

        @Test
        @DisplayName("Should limit each category to maximum 5 occurrences")
        void shouldLimitEachCategoryToMax5Occurrences() {
            // Arrange: Create 8 IDENTITY memories (all same category)
            List<Memory> memories = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Memory memory = createMemoryWithCategory(UUID.randomUUID(), 10 - i, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.IDENTITY);
                memories.add(memory);
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 20);

            // Assert: Only 5 IDENTITY memories should be returned
            assertThat(results).hasSize(5);
            
            // Count occurrences of IDENTITY category
            long identityCount = results.stream()
                    .filter(r -> r.getMemory().getCategory() == MemoryCategory.IDENTITY)
                    .count();
            assertThat(identityCount).isEqualTo(5);
        }

        @Test
        @DisplayName("Should keep high-scoring memories even when their category is at limit")
        void shouldKeepHighScoringMemoriesWhenCategoryAtLimit() {
            // Arrange: Create memories with different categories
            // 6 IDENTITY memories with scores 10, 9, 8, 7, 6, 5 (all same category)
            // 2 RELATIONSHIP memories with scores 4, 3 (different category)
            List<Memory> memories = new ArrayList<>();
            
            // IDENTITY memories (high scores)
            for (int i = 0; i < 6; i++) {
                Memory memory = createMemoryWithCategory(UUID.randomUUID(), 10 - i, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.IDENTITY);
                memories.add(memory);
            }
            
            // RELATIONSHIP memories (lower scores)
            for (int i = 0; i < 2; i++) {
                Memory memory = createMemoryWithCategory(UUID.randomUUID(), 4 - i, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.RELATIONSHIP);
                memories.add(memory);
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 20);

            // Assert: Should have 5 IDENTITY + 2 RELATIONSHIP = 7 total
            assertThat(results).hasSize(7);
            
            // Top 5 should be the high-scoring IDENTITY memories
            long identityCount = results.stream()
                    .filter(r -> r.getMemory().getCategory() == MemoryCategory.IDENTITY)
                    .count();
            assertThat(identityCount).isEqualTo(5);
            
            // All RELATIONSHIP memories should be included
            long relationshipCount = results.stream()
                    .filter(r -> r.getMemory().getCategory() == MemoryCategory.RELATIONSHIP)
                    .count();
            assertThat(relationshipCount).isEqualTo(2);
        }

        @Test
        @DisplayName("Should drop lower-scoring memories in over-represented category")
        void shouldDropLowerScoringMemoriesInOverRepresentedCategory() {
            // Arrange: Create 7 IDENTITY memories with descending importance
            // The 6th and 7th should be dropped
            List<Memory> memories = new ArrayList<>();
            List<UUID> ids = new ArrayList<>();
            
            for (int i = 0; i < 7; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                Memory memory = createMemoryWithCategory(id, 10 - i, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.IDENTITY);
                memories.add(memory);
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 20);

            // Assert: Only 5 should be returned
            assertThat(results).hasSize(5);
            
            // Verify the top 5 IDs are included (importance 10, 9, 8, 7, 6)
            List<UUID> resultIds = results.stream()
                    .map(r -> r.getMemory().getId())
                    .toList();
            
            assertThat(resultIds).containsExactlyElementsOf(ids.subList(0, 5));
            
            // Verify the last 2 IDs (importance 5, 4) are NOT included
            assertThat(resultIds).doesNotContain(ids.get(5));
            assertThat(resultIds).doesNotContain(ids.get(6));
        }

        @Test
        @DisplayName("Should preserve descending score order after diversity filter")
        void shouldPreserveDescendingScoreOrderAfterDiversityFilter() {
            // Arrange: Create memories of different categories with interleaved scores
            // This tests that the order is preserved even when some memories are filtered
            List<Memory> memories = new ArrayList<>();
            
            // 6 IDENTITY memories with scores 10, 8, 6, 4, 2, 1
            int[] identityScores = {10, 8, 6, 4, 2, 1};
            for (int score : identityScores) {
                Memory memory = createMemoryWithCategory(UUID.randomUUID(), score, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.IDENTITY);
                memories.add(memory);
            }
            
            // 3 RELATIONSHIP memories with scores 9, 5, 3
            int[] relationshipScores = {9, 5, 3};
            for (int score : relationshipScores) {
                Memory memory = createMemoryWithCategory(UUID.randomUUID(), score, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.RELATIONSHIP);
                memories.add(memory);
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 20);

            // Assert: Results should still be in descending score order
            for (int i = 0; i < results.size() - 1; i++) {
                double currentScore = results.get(i).getCompositeScore();
                double nextScore = results.get(i + 1).getCompositeScore();
                assertThat(currentScore)
                        .as("Score at index %d (%.4f) should be >= score at index %d (%.4f)", 
                                i, currentScore, i + 1, nextScore)
                        .isGreaterThanOrEqualTo(nextScore);
            }
        }

        @Test
        @DisplayName("Should allow multiple categories to each have up to 5 memories")
        void shouldAllowMultipleCategoriesToHaveUpTo5Memories() {
            // Arrange: Create 5 memories each for 3 categories
            List<Memory> memories = new ArrayList<>();
            
            MemoryCategory[] categories = {
                MemoryCategory.IDENTITY, 
                MemoryCategory.RELATIONSHIP, 
                MemoryCategory.PREFERENCE
            };
            
            for (MemoryCategory category : categories) {
                for (int i = 0; i < 5; i++) {
                    Memory memory = createMemoryWithCategory(UUID.randomUUID(), 8 - i, FIXED_NOW, 
                            new BigDecimal("0.8"), category);
                    memories.add(memory);
                }
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 50);

            // Assert: All 15 memories should be returned (5 per category, 3 categories)
            assertThat(results).hasSize(15);
            
            // Verify each category has exactly 5
            for (MemoryCategory category : categories) {
                long count = results.stream()
                        .filter(r -> r.getMemory().getCategory() == category)
                        .count();
                assertThat(count).as("Category %s should have 5 memories", category).isEqualTo(5);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Top-Scoring Memory Inclusion
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Top-Scoring Memory Inclusion Tests")
    class TopScoringMemoryInclusionTests {

        @Test
        @DisplayName("Should always include top-scoring memory when maxCount >= 1")
        void shouldAlwaysIncludeTopScoringMemory() {
            // Arrange: Create multiple memories with different scores
            // The highest-scoring memory should ALWAYS be in the result when maxCount >= 1
            UUID highestScoringId = UUID.randomUUID();
            Memory highestScoringMemory = createMemory(highestScoringId, 10, FIXED_NOW, new BigDecimal("1.0"));
            
            Memory mediumMemory1 = createMemory(UUID.randomUUID(), 6, 
                    FIXED_NOW.minus(5, ChronoUnit.DAYS), new BigDecimal("0.8"));
            Memory mediumMemory2 = createMemory(UUID.randomUUID(), 5, 
                    FIXED_NOW.minus(10, ChronoUnit.DAYS), new BigDecimal("0.7"));
            Memory lowMemory = createMemory(UUID.randomUUID(), 3, 
                    FIXED_NOW.minus(15, ChronoUnit.DAYS), new BigDecimal("0.5"));

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Arrays.asList(lowMemory, mediumMemory2, highestScoringMemory, mediumMemory1));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setConfidenceScore(m.getConfidenceScore());
                return dto;
            });

            // Act: Request just 1 memory
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, "test topic", null, 1);

            // Assert: The highest-scoring memory should be the one returned
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMemory().getId())
                    .as("Top-scoring memory (importance=10, confidence=1.0, accessed today) should be first")
                    .isEqualTo(highestScoringId);
        }

        @Test
        @DisplayName("Should include top-scoring memory even if its category is over-represented")
        void shouldIncludeTopScoringMemoryEvenWhenCategoryOverRepresented() {
            // Arrange: Create 7 IDENTITY memories (exceeds max 5 per category)
            // The highest-scoring one should ALWAYS be included, never filtered by diversity
            List<Memory> memories = new ArrayList<>();
            UUID highestScoringId = UUID.randomUUID();
            
            // First (highest-scoring) memory - importance 10
            Memory highestScoringMemory = createMemoryWithCategory(highestScoringId, 10, FIXED_NOW, 
                    new BigDecimal("1.0"), MemoryCategory.IDENTITY);
            memories.add(highestScoringMemory);
            
            // 6 more IDENTITY memories with lower scores
            for (int i = 1; i <= 6; i++) {
                Memory memory = createMemoryWithCategory(UUID.randomUUID(), 10 - i, FIXED_NOW, 
                        new BigDecimal("0.8"), MemoryCategory.IDENTITY);
                memories.add(memory);
            }

            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(memories);
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setCategory(m.getCategory());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert: The highest-scoring memory is included (diversity filter only removes 6th and 7th)
            assertThat(results).hasSize(5);
            assertThat(results.get(0).getMemory().getId())
                    .as("Top-scoring memory should always be first, never filtered out by diversity")
                    .isEqualTo(highestScoringId);
        }

        @Test
        @DisplayName("Should ensure top-scoring memory is first regardless of input order")
        void shouldEnsureTopScoringMemoryIsFirstRegardlessOfInputOrder() {
            // Arrange: Repository returns memories in random/wrong order
            // Verify that sorting ensures the top-scorer is always first
            Memory topScorer = createMemory(UUID.randomUUID(), 10, FIXED_NOW, new BigDecimal("1.0"));
            Memory middleScorer = createMemory(UUID.randomUUID(), 5, FIXED_NOW, new BigDecimal("0.8"));
            Memory lowestScorer = createMemory(UUID.randomUUID(), 2, 
                    FIXED_NOW.minus(18, ChronoUnit.DAYS), new BigDecimal("0.5"));

            // Return in reverse order (lowest first)
            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Arrays.asList(lowestScorer, middleScorer, topScorer));
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert: Despite input order, top scorer should be first
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getMemory().getId()).isEqualTo(topScorer.getId());
            assertThat(results.get(0).getCompositeScore())
                    .isGreaterThan(results.get(1).getCompositeScore());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Confidence Threshold Filtering
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence Threshold Filtering Tests")
    class ConfidenceThresholdFilteringTests {

        @Test
        @DisplayName("Should exclude memories with confidence below 0.3 from retrieval")
        void shouldExcludeMemoriesWithConfidenceBelowThreshold() {
            // Arrange: Create memories with varying confidence scores
            // Memory 1: Confidence 0.29 (below threshold) - should be EXCLUDED
            Memory lowConfidenceMemory = createMemory(UUID.randomUUID(), 10, FIXED_NOW, new BigDecimal("0.29"));
            
            // Memory 2: Confidence 0.30 (at threshold) - should be INCLUDED
            Memory atThresholdMemory = createMemory(UUID.randomUUID(), 8, FIXED_NOW, new BigDecimal("0.30"));
            
            // Memory 3: Confidence 0.31 (above threshold) - should be INCLUDED
            Memory aboveThresholdMemory = createMemory(UUID.randomUUID(), 6, FIXED_NOW, new BigDecimal("0.31"));

            // The repository query already filters by confidence >= 0.3, so we only return valid memories
            // This simulates the database returning only memories that meet the threshold
            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenAnswer(invocation -> {
                        BigDecimal minConfidence = invocation.getArgument(2);
                        // Simulate DB filtering - return only memories >= minConfidence
                        List<Memory> allMemories = Arrays.asList(lowConfidenceMemory, atThresholdMemory, aboveThresholdMemory);
                        return allMemories.stream()
                                .filter(m -> m.getConfidenceScore().compareTo(minConfidence) >= 0)
                                .toList();
                    });
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setImportanceScore(m.getImportanceScore());
                dto.setConfidenceScore(m.getConfidenceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, "test topic", null, 10);

            // Assert: Only 2 memories should be returned (at and above threshold)
            assertThat(results).hasSize(2);
            
            // Verify the low confidence memory is NOT in results
            List<UUID> resultIds = results.stream()
                    .map(r -> r.getMemory().getId())
                    .toList();
            assertThat(resultIds).doesNotContain(lowConfidenceMemory.getId());
            
            // Verify the threshold and above threshold memories ARE in results
            assertThat(resultIds).contains(atThresholdMemory.getId());
            assertThat(resultIds).contains(aboveThresholdMemory.getId());
        }

        @Test
        @DisplayName("Should pass MIN_CONFIDENCE_SCORE (0.30) to repository query")
        void shouldPassMinConfidenceScoreToRepositoryQuery() {
            // Arrange
            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            memoryRetriever.retrieveRanked(TEST_FATHER_ID, "test topic", null, 10);

            // Assert: Verify the repository was called with confidence threshold 0.30
            ArgumentCaptor<BigDecimal> confidenceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(memoryRepository).findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), confidenceCaptor.capture());
            
            BigDecimal capturedConfidence = confidenceCaptor.getValue();
            assertThat(capturedConfidence).isEqualByComparingTo(new BigDecimal("0.30"));
        }

        @Test
        @DisplayName("Should exclude confidence 0.29 but include confidence 0.30 - boundary test")
        void shouldExcludeConfidence029ButInclude030() {
            // Arrange: Create memories at the exact boundary
            Memory justBelowThreshold = createMemory(UUID.randomUUID(), 8, FIXED_NOW, new BigDecimal("0.29"));
            Memory exactlyAtThreshold = createMemory(UUID.randomUUID(), 8, FIXED_NOW, new BigDecimal("0.30"));

            // Simulate database query behavior
            when(memoryRepository.findRetrievableMemories(eq(TEST_FATHER_ID), anyCollection(), any(BigDecimal.class)))
                    .thenAnswer(invocation -> {
                        BigDecimal minConfidence = invocation.getArgument(2);
                        List<Memory> allMemories = Arrays.asList(justBelowThreshold, exactlyAtThreshold);
                        return allMemories.stream()
                                .filter(m -> m.getConfidenceScore().compareTo(minConfidence) >= 0)
                                .toList();
                    });
            
            when(memoryMapper.toDto(any(Memory.class))).thenAnswer(invocation -> {
                Memory m = invocation.getArgument(0);
                MemoryDto dto = new MemoryDto();
                dto.setId(m.getId());
                dto.setConfidenceScore(m.getConfidenceScore());
                return dto;
            });

            // Act
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(TEST_FATHER_ID, null, null, 10);

            // Assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMemory().getId()).isEqualTo(exactlyAtThreshold.getId());
            assertThat(results.get(0).getMemory().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.30"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a test memory with specified parameters.
     */
    private Memory createMemory(UUID id, int importanceScore, Instant lastAccessedAt, BigDecimal confidenceScore) {
        Memory memory = new Memory(
                TEST_FATHER_ID,
                MemoryCategory.IDENTITY,
                MemorySubjectType.FATHER,
                "Test memory content",
                importanceScore,
                confidenceScore,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(id);
        memory.setLastAccessedAt(lastAccessedAt);
        memory.setCreatedAt(FIXED_NOW.minus(30, ChronoUnit.DAYS));
        return memory;
    }

    /**
     * Creates a test memory with specified parameters including category.
     */
    private Memory createMemoryWithCategory(UUID id, int importanceScore, Instant lastAccessedAt, 
            BigDecimal confidenceScore, MemoryCategory category) {
        Memory memory = new Memory(
                TEST_FATHER_ID,
                category,
                MemorySubjectType.FATHER,
                "Test memory content for " + category,
                importanceScore,
                confidenceScore,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(id);
        memory.setLastAccessedAt(lastAccessedAt);
        memory.setCreatedAt(FIXED_NOW.minus(30, ChronoUnit.DAYS));
        return memory;
    }
}
