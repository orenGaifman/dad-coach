package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ContradictionDetectionService}.
 *
 * <p>Validates: SPEC-004 Requirement 7 (Memory Conflicts and Contradiction Resolution)
 * <ul>
 *   <li>Detects contradictions between memories of the same subject</li>
 *   <li>Uses negation patterns, opposite values, and semantic similarity</li>
 *   <li>Returns contradiction pairs with confidence scores</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContradictionDetectionService Tests")
class ContradictionDetectionServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    private ContradictionDetectionService service;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ContradictionDetectionService(memoryRepository);
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    private Memory createMemory(String content, MemoryCategory category) {
        return createMemory(content, category, MemorySubjectType.CHILD, CHILD_ID);
    }

    private Memory createMemory(String content, MemoryCategory category, MemorySubjectType subjectType, UUID childId) {
        Memory memory = new Memory(
                FATHER_ID,
                category,
                subjectType,
                content,
                5,
                new BigDecimal("0.8"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
        memory.setChildId(childId);
        return memory;
    }

    private Memory createMemoryWithEmbedding(String content, MemoryCategory category, float[] embedding) {
        Memory memory = createMemory(content, category);
        memory.setEmbedding(embedding);
        return memory;
    }

    // ─── Negation Detection Tests ────────────────────────────────────────

    @Nested
    @DisplayName("Negation Contradiction Detection")
    class NegationContradictionTests {

        @Test
        @DisplayName("Detects contradiction: 'likes X' vs 'doesn't like X'")
        void detectsLikesVsDoesntLike() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE);
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE);

            when(memoryRepository.findForContradictionDetection(
                    eq(FATHER_ID), eq(CHILD_ID), eq(MemoryCategory.PREFERENCE), 
                    eq(MemorySubjectType.CHILD), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.NEGATION);
            assertThat(contradictions.get(0).confidenceScore()).isGreaterThanOrEqualTo(0.4);
        }

        @Test
        @DisplayName("Detects contradiction: 'loves X' vs 'hates X'")
        void detectsLovesVsHates() {
            Memory existing = createMemory("Lucas loves reading books", MemoryCategory.PREFERENCE);
            Memory newMemory = createMemory("Lucas hates reading books", MemoryCategory.PREFERENCE);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.NEGATION);
        }

        @Test
        @DisplayName("Detects contradiction: 'enjoys X' vs 'never enjoys X'")
        void detectsEnjoysVsNeverEnjoys() {
            Memory existing = createMemory("He enjoys playing soccer", MemoryCategory.PREFERENCE);
            Memory newMemory = createMemory("He never enjoys playing soccer", MemoryCategory.PREFERENCE);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.NEGATION);
        }

        @Test
        @DisplayName("No contradiction when topics are different")
        void noContradictionForDifferentTopics() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE);
            Memory newMemory = createMemory("Lucas doesn't like carrots", MemoryCategory.PREFERENCE);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).isEmpty();
        }
    }

    // ─── Value Difference Detection Tests ────────────────────────────────

    @Nested
    @DisplayName("Value Difference Contradiction Detection")
    class ValueDifferenceTests {

        @Test
        @DisplayName("Detects time contradiction: different bedtimes")
        void detectsDifferentBedtimes() {
            Memory existing = createMemory("Lucas's bedtime is 7pm", MemoryCategory.CONTEXT);
            Memory newMemory = createMemory("Lucas's bedtime is 9pm", MemoryCategory.CONTEXT);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.DIFFERENT_VALUE);
            assertThat(contradictions.get(0).reason()).contains("time");
        }

        @Test
        @DisplayName("Detects age contradiction: different ages")
        void detectsDifferentAges() {
            Memory existing = createMemory("Lucas is 5 years old", MemoryCategory.IDENTITY);
            Memory newMemory = createMemory("Lucas is 7 years old", MemoryCategory.IDENTITY);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.DIFFERENT_VALUE);
            assertThat(contradictions.get(0).reason()).contains("age");
        }

        @Test
        @DisplayName("Detects quantity contradiction: different frequencies")
        void detectsDifferentQuantities() {
            Memory existing = createMemory("We read together 3 times per week", MemoryCategory.HABIT);
            Memory newMemory = createMemory("We read together 5 times per week", MemoryCategory.HABIT);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.DIFFERENT_VALUE);
        }

        @Test
        @DisplayName("No contradiction when times are the same")
        void noContradictionWhenTimesMatch() {
            Memory existing = createMemory("Bedtime is at 8pm", MemoryCategory.CONTEXT);
            Memory newMemory = createMemory("His bedtime is 8pm", MemoryCategory.CONTEXT);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).isEmpty();
        }
    }

    // ─── Explicit Correction Detection Tests ─────────────────────────────

    @Nested
    @DisplayName("Explicit Correction Detection")
    class ExplicitCorrectionTests {

        @Test
        @DisplayName("Detects 'actually' correction")
        void detectsActuallyCorrection() {
            Memory existing = createMemory("Lucas goes to Lincoln School", MemoryCategory.IDENTITY);
            Memory newMemory = createMemory("Actually, Lucas goes to Jefferson School", MemoryCategory.IDENTITY);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.EXPLICIT_CORRECTION);
            assertThat(contradictions.get(0).confidenceScore()).isGreaterThanOrEqualTo(0.9);
        }

        @Test
        @DisplayName("Detects 'I was wrong' correction")
        void detectsIWasWrongCorrection() {
            Memory existing = createMemory("His favorite color is blue", MemoryCategory.PREFERENCE);
            Memory newMemory = createMemory("I was wrong, his favorite color is green", MemoryCategory.PREFERENCE);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.EXPLICIT_CORRECTION);
        }

        @Test
        @DisplayName("Detects 'no, it's' correction")
        void detectsNoItsCorrection() {
            Memory existing = createMemory("He is 6 years old", MemoryCategory.IDENTITY);
            Memory newMemory = createMemory("No, he's 7 years old now", MemoryCategory.IDENTITY);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            // Could be EXPLICIT_CORRECTION or DIFFERENT_VALUE depending on which is detected first
            assertThat(contradictions.get(0).confidenceScore()).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("Detects 'correction' keyword")
        void detectsCorrectionKeyword() {
            Memory existing = createMemory("Bedtime is 8pm", MemoryCategory.CONTEXT);
            Memory newMemory = createMemory("Correction: bedtime is actually 7:30pm", MemoryCategory.CONTEXT);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.EXPLICIT_CORRECTION);
        }
    }

    // ─── Semantic Contradiction Tests ────────────────────────────────────

    @Nested
    @DisplayName("Semantic Contradiction Detection")
    class SemanticContradictionTests {

        @Test
        @DisplayName("Detects semantic contradiction with high similarity embeddings")
        void detectsSemanticContradictionWithEmbeddings() {
            // Create vectors that are very similar (simulating similar topics)
            float[] baseEmbedding = new float[1536];
            Arrays.fill(baseEmbedding, 0.1f);
            
            float[] similarEmbedding = new float[1536];
            Arrays.fill(similarEmbedding, 0.1f);
            similarEmbedding[0] = 0.11f; // Slightly different to get high but not identical similarity

            Memory existing = createMemoryWithEmbedding(
                    "Lucas is a very social child who loves playing with friends",
                    MemoryCategory.IDENTITY,
                    baseEmbedding
            );
            Memory newMemory = createMemoryWithEmbedding(
                    "Lucas doesn't like playing with other children",
                    MemoryCategory.IDENTITY,
                    similarEmbedding
            );

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            // Should detect either NEGATION or SEMANTIC_CONFLICT
            assertThat(contradictions).isNotEmpty();
        }

        @Test
        @DisplayName("No semantic contradiction with low similarity embeddings")
        void noSemanticContradictionWithLowSimilarity() {
            // Create vectors that are dissimilar
            float[] embedding1 = new float[1536];
            Arrays.fill(embedding1, 0.1f);
            
            float[] embedding2 = new float[1536];
            Arrays.fill(embedding2, -0.1f); // Opposite direction

            Memory existing = createMemoryWithEmbedding(
                    "Lucas enjoys soccer practice",
                    MemoryCategory.PREFERENCE,
                    embedding1
            );
            Memory newMemory = createMemoryWithEmbedding(
                    "Lucas struggles with math homework",
                    MemoryCategory.PREFERENCE,
                    embedding2
            );

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            // Should not detect semantic contradiction due to low similarity
            boolean hasSemanticConflict = contradictions.stream()
                    .anyMatch(c -> c.contradictionType() == ContradictionType.SEMANTIC_CONFLICT);
            assertThat(hasSemanticConflict).isFalse();
        }
    }

    // ─── Same Subject Validation Tests ───────────────────────────────────

    @Nested
    @DisplayName("Same Subject Validation")
    class SameSubjectTests {

        @Test
        @DisplayName("Only compares memories with same category")
        void comparesOnlySameCategory() {
            Memory newMemory = createMemory("Lucas likes pizza", MemoryCategory.PREFERENCE);

            service.detectContradictions(newMemory);

            verify(memoryRepository).findForContradictionDetection(
                    eq(FATHER_ID),
                    eq(CHILD_ID),
                    eq(MemoryCategory.PREFERENCE),
                    eq(MemorySubjectType.CHILD),
                    any()
            );
        }

        @Test
        @DisplayName("Only compares memories with same subject type")
        void comparesOnlySameSubjectType() {
            Memory newMemory = createMemory("I prefer morning missions", 
                    MemoryCategory.PREFERENCE, MemorySubjectType.FATHER, null);

            service.detectContradictions(newMemory);

            verify(memoryRepository).findForContradictionDetection(
                    eq(FATHER_ID),
                    isNull(),
                    eq(MemoryCategory.PREFERENCE),
                    eq(MemorySubjectType.FATHER),
                    any()
            );
        }

        @Test
        @DisplayName("Skips same memory ID in comparison")
        void skipsSameMemoryId() {
            Memory memory = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE);
            UUID memoryId = memory.getId();

            // Return the same memory from repository (simulating it being in the DB)
            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(memory));

            List<Contradiction> contradictions = service.detectContradictions(memory);

            assertThat(contradictions).isEmpty();
        }
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles null new memory")
        void handlesNullNewMemory() {
            assertThatThrownBy(() -> service.detectContradictions(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("newMemory cannot be null");
        }

        @Test
        @DisplayName("Returns empty list when no existing memories")
        void returnsEmptyWhenNoExistingMemories() {
            Memory newMemory = createMemory("Lucas likes pizza", MemoryCategory.PREFERENCE);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            assertThat(contradictions).isEmpty();
        }

        @Test
        @DisplayName("Handles memories without embeddings")
        void handlesMemoriesWithoutEmbeddings() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE);
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE);

            // Ensure no embeddings
            existing.setEmbedding(null);
            newMemory.setEmbedding(null);

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            // Should still detect negation-based contradiction without embeddings
            assertThat(contradictions).hasSize(1);
            assertThat(contradictions.get(0).contradictionType()).isEqualTo(ContradictionType.NEGATION);
        }

        @Test
        @DisplayName("Sorts contradictions by confidence descending")
        void sortsContradictionsByConfidenceDescending() {
            Memory existing1 = createMemory("Lucas is 5 years old", MemoryCategory.IDENTITY);
            Memory existing2 = createMemory("Lucas likes dinosaurs", MemoryCategory.IDENTITY);
            
            // New memory contradicts existing1 more strongly (explicit correction)
            Memory newMemory = createMemory(
                    "Actually, Lucas is 7 years old and loves trucks",
                    MemoryCategory.IDENTITY
            );

            when(memoryRepository.findForContradictionDetection(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(existing1, existing2));

            List<Contradiction> contradictions = service.detectContradictions(newMemory);

            // Should be sorted by confidence descending
            if (contradictions.size() >= 2) {
                assertThat(contradictions.get(0).confidenceScore())
                        .isGreaterThanOrEqualTo(contradictions.get(1).confidenceScore());
            }
        }
    }

    // ─── Pattern Detection Helper Tests ──────────────────────────────────

    @Nested
    @DisplayName("Pattern Detection Helpers")
    class PatternDetectionTests {

        @ParameterizedTest
        @CsvSource({
                "doesn't like, true",
                "does not want, true",
                "never goes, true",
                "no longer plays, true",
                "hates vegetables, true",
                "stopped playing, true",
                "loves playing, false",
                "enjoys reading, false",
                "always happy, false"
        })
        @DisplayName("Correctly identifies negation patterns")
        void identifiesNegationPatterns(String text, boolean hasNegation) {
            assertThat(service.containsNegation(text)).isEqualTo(hasNegation);
        }

        @ParameterizedTest
        @CsvSource({
                "likes broccoli, true",
                "loves reading, true",
                "enjoys soccer, true",
                "always happy, true",
                "usually plays, true",
                "hates vegetables, false",
                "never goes, false"
        })
        @DisplayName("Correctly identifies affirmation patterns")
        void identifiesAffirmationPatterns(String text, boolean hasAffirmation) {
            assertThat(service.containsAffirmation(text)).isEqualTo(hasAffirmation);
        }

        @ParameterizedTest
        @CsvSource({
                "actually he is, true",
                "I was wrong, true",
                "correction: it's, true",
                "'no, it's actually', true",
                "he likes pizza, false",
                "the same thing, false"
        })
        @DisplayName("Correctly identifies correction language")
        void identifiesCorrectionLanguage(String text, boolean hasCorrection) {
            assertThat(service.containsCorrectionLanguage(text)).isEqualTo(hasCorrection);
        }
    }

    // ─── Value Extraction Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Value Extraction")
    class ValueExtractionTests {

        @Test
        @DisplayName("Extracts time values correctly")
        void extractsTimeValues() {
            List<String> times = service.extractTimes("Bedtime is at 7pm and wake up at 7:30 AM");
            assertThat(times).containsAnyOf("7pm", "7:30 AM");
        }

        @Test
        @DisplayName("Extracts age values correctly")
        void extractsAgeValues() {
            List<Integer> ages = service.extractAges("Lucas is 5 years old and his sister is 3 year-old");
            assertThat(ages).containsExactlyInAnyOrder(5, 3);
        }

        @Test
        @DisplayName("Extracts quantity values correctly")
        void extractsQuantityValues() {
            Map<String, Integer> quantities = service.extractQuantities("We read 3 times a week and play 5 days a week");
            assertThat(quantities).containsEntry("time", 3);
            assertThat(quantities).containsEntry("day", 5);
        }
    }

    // ─── Cosine Similarity Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Cosine Similarity Calculation")
    class CosineSimilarityTests {

        @Test
        @DisplayName("Returns 1.0 for identical vectors")
        void identicalVectorsHaveSimilarityOne() {
            float[] embedding = {1.0f, 2.0f, 3.0f};
            double similarity = service.calculateCosineSimilarity(embedding, embedding);
            assertThat(similarity).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("Returns 0.0 for orthogonal vectors")
        void orthogonalVectorsHaveSimilarityZero() {
            float[] embedding1 = {1.0f, 0.0f, 0.0f};
            float[] embedding2 = {0.0f, 1.0f, 0.0f};
            double similarity = service.calculateCosineSimilarity(embedding1, embedding2);
            assertThat(similarity).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("Returns 0.0 for null embeddings")
        void nullEmbeddingsReturnZero() {
            float[] embedding = {1.0f, 2.0f, 3.0f};
            assertThat(service.calculateCosineSimilarity(null, embedding)).isEqualTo(0.0);
            assertThat(service.calculateCosineSimilarity(embedding, null)).isEqualTo(0.0);
            assertThat(service.calculateCosineSimilarity(null, null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Returns 0.0 for different length vectors")
        void differentLengthVectorsReturnZero() {
            float[] embedding1 = {1.0f, 2.0f, 3.0f};
            float[] embedding2 = {1.0f, 2.0f};
            double similarity = service.calculateCosineSimilarity(embedding1, embedding2);
            assertThat(similarity).isEqualTo(0.0);
        }
    }

    // ─── Topic Overlap Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Topic Overlap Calculation")
    class TopicOverlapTests {

        @Test
        @DisplayName("High overlap for similar texts")
        void highOverlapForSimilarTexts() {
            boolean hasOverlap = service.hasSufficientTopicOverlap(
                    "lucas likes broccoli vegetables",
                    "lucas doesn't like broccoli vegetables"
            );
            assertThat(hasOverlap).isTrue();
        }

        @Test
        @DisplayName("Low overlap for different texts")
        void lowOverlapForDifferentTexts() {
            boolean hasOverlap = service.hasSufficientTopicOverlap(
                    "likes broccoli",
                    "plays soccer outside"
            );
            assertThat(hasOverlap).isFalse();
        }

        @Test
        @DisplayName("Filters out stop words")
        void filtersOutStopWords() {
            double overlap = service.calculateWordOverlap(
                    "the boy is playing with a ball",
                    "the girl is playing with the toy"
            );
            // Only "playing" should match (stop words filtered)
            assertThat(overlap).isGreaterThan(0.0);
        }
    }

    // ─── Contradiction Record Tests ──────────────────────────────────────

    @Nested
    @DisplayName("Contradiction Record Validation")
    class ContradictionRecordTests {

        @Test
        @DisplayName("shouldAutoSupersede returns true for high confidence")
        void shouldAutoSupersedeForHighConfidence() {
            Memory existing = createMemory("Lucas is 5 years old", MemoryCategory.IDENTITY);
            Memory newMemory = createMemory("Actually, Lucas is 7 years old", MemoryCategory.IDENTITY);
            
            Contradiction contradiction = new Contradiction(
                    existing, newMemory, 0.85, 
                    ContradictionType.EXPLICIT_CORRECTION, "Explicit correction"
            );

            assertThat(contradiction.shouldAutoSupersede()).isTrue();
        }

        @Test
        @DisplayName("requiresManualResolution returns true for IDENTITY")
        void requiresManualResolutionForIdentity() {
            Memory existing = createMemory("Lucas goes to Lincoln School", MemoryCategory.IDENTITY);
            Memory newMemory = createMemory("Lucas goes to Jefferson School", MemoryCategory.IDENTITY);
            
            Contradiction contradiction = new Contradiction(
                    existing, newMemory, 0.8, 
                    ContradictionType.DIFFERENT_VALUE, "Different school names"
            );

            assertThat(contradiction.requiresManualResolution()).isTrue();
        }

        @Test
        @DisplayName("Validates required fields")
        void validatesRequiredFields() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE);

            assertThatThrownBy(() -> new Contradiction(null, memory, 0.5, 
                    ContradictionType.NEGATION, "reason"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new Contradiction(memory, null, 0.5, 
                    ContradictionType.NEGATION, "reason"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new Contradiction(memory, memory, 1.5, 
                    ContradictionType.NEGATION, "reason"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new Contradiction(memory, memory, 0.5, 
                    null, "reason"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new Contradiction(memory, memory, 0.5, 
                    ContradictionType.NEGATION, ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
