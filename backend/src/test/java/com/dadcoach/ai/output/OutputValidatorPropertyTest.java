package com.dadcoach.ai.output;

import com.dadcoach.ai.safety.SafetyClassification;
import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for OutputValidator.
 * Validates Property 15: Output Schema Validation — all required fields present,
 * enums valid, numerics in range, strings in bounds, confidence in [0.0, 1.0].
 *
 * <p><b>Validates: Requirements 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8</b>
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 15: Output Schema Validation")
class OutputValidatorPropertyTest {

    private final OutputValidator validator = new OutputValidator();

    private static final Set<String> VALID_MEMORY_CATEGORIES = Set.of(
        "IDENTITY", "RELATIONSHIP", "PREFERENCE", "GOAL",
        "CHALLENGE", "MILESTONE", "CONTEXT", "CONVERSATION_SUMMARY"
    );

    private static final Set<String> VALID_MISSION_CATEGORIES = Set.of(
        "CONNECTION", "COMMUNICATION", "DISCIPLINE", "EDUCATION",
        "HEALTH", "EMOTIONAL", "INDEPENDENCE", "FUN", "ROUTINE", "CUSTOM"
    );

    private static final Set<String> VALID_EMOTIONAL_TONES = Set.of(
        "positive", "neutral", "negative"
    );

    // ---- CoachingResponse Properties ----

    @Property(tries = 100)
    void validCoachingResponseAlwaysPasses(
        @ForAll("validMessages") String message,
        @ForAll("validModels") String model,
        @ForAll("validConfidence") double confidence
    ) {
        var response = new CoachingResponse(
            message, model, "openai", 100, 50,
            Duration.ofMillis(500), false, true, confidence
        );

        ValidationResult result = validator.validate(response);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Property(tries = 100)
    void coachingResponseWithLongMessageFails(
        @ForAll("overLengthMessages") String message,
        @ForAll("validModels") String model,
        @ForAll("validConfidence") double confidence
    ) {
        var response = new CoachingResponse(
            message, model, "openai", 100, 50,
            Duration.ofMillis(500), false, true, confidence
        );

        ValidationResult result = validator.validate(response);
        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.field().equals("message"));
    }

    // ---- MissionOutput Properties ----

    @Property(tries = 100)
    void validMissionOutputAlwaysPasses(
        @ForAll("validTitles") String title,
        @ForAll("validDescriptions") String description,
        @ForAll("validMissionCategory") String category,
        @ForAll @IntRange(min = 1, max = 5) int difficulty,
        @ForAll @IntRange(min = 1, max = 120) int minutes
    ) {
        var output = new MissionOutput(title, description, category, difficulty, minutes, true, "gpt-4o");

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Property(tries = 100)
    void missionOutputWithInvalidCategoryFails(
        @ForAll("validTitles") String title,
        @ForAll("validDescriptions") String description,
        @ForAll("invalidCategory") String category,
        @ForAll @IntRange(min = 1, max = 5) int difficulty,
        @ForAll @IntRange(min = 1, max = 120) int minutes
    ) {
        var output = new MissionOutput(title, description, category, difficulty, minutes, true, "gpt-4o");

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.field().equals("category"));
    }

    // ---- MemoryExtractionOutput Properties ----

    @Property(tries = 100)
    void validMemoryExtractionOutputAlwaysPasses(
        @ForAll("validMemoryList") List<MemoryExtractionOutput.ExtractedMemory> memories
    ) {
        var output = new MemoryExtractionOutput(memories, "conv-1", "gpt-4o", true);

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Property(tries = 100)
    void memoryWithInvalidCategoryFails(
        @ForAll("invalidCategory") String badCategory,
        @ForAll("validMemoryContent") String content,
        @ForAll @IntRange(min = 1, max = 10) int importance,
        @ForAll("validConfidence") double confidence
    ) {
        var memory = new MemoryExtractionOutput.ExtractedMemory(
            badCategory, content, importance, confidence, "father"
        );
        var output = new MemoryExtractionOutput(List.of(memory), "conv-1", "gpt-4o", true);

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.field().contains("category"));
    }

    // ---- SafetyClassification Properties ----

    @Property(tries = 100)
    void validSafetyClassificationAlwaysPasses(
        @ForAll("validSafetyCategory") SafetyCategory category,
        @ForAll("validConfidence") double confidence
    ) {
        var classification = new SafetyClassification(category, confidence, "test reason");

        ValidationResult result = validator.validate(classification);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    // ---- ActionRecommendation Properties ----

    @Property(tries = 100)
    void validActionRecommendationAlwaysPasses(
        @ForAll("validActionType") ActionRecommendation.ActionType action,
        @ForAll @IntRange(min = 1, max = 10) int priority,
        @ForAll("validConfidence") double confidence
    ) {
        var recommendation = new ActionRecommendation(
            UUID.randomUUID(), action, priority, "test reasoning", confidence, Instant.now()
        );

        ValidationResult result = validator.validate(recommendation);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    // ---- WeeklySummaryOutput Properties ----

    @Property(tries = 100)
    void validWeeklySummaryOutputAlwaysPasses(
        @ForAll("validDescriptions") String summary,
        @ForAll @IntRange(min = 0, max = 20) int missionsCompleted,
        @ForAll @IntRange(min = 0, max = 365) int streakDays
    ) {
        var output = new WeeklySummaryOutput(
            UUID.randomUUID(), LocalDate.now().minusDays(7), LocalDate.now(),
            summary, List.of("highlight1"), missionsCompleted, streakDays, "gpt-4o", true
        );

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    // ---- ReflectionInsightOutput Properties ----

    @Property(tries = 100)
    void validReflectionInsightOutputAlwaysPasses(
        @ForAll("validEmotionalTone") String tone
    ) {
        var output = new ReflectionInsightOutput(
            List.of("insight1", "insight2"),
            List.of("growth1"),
            "focus on communication",
            tone,
            "gpt-4o",
            true
        );

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Property(tries = 100)
    void reflectionWithInvalidToneFails(
        @ForAll("invalidCategory") String invalidTone
    ) {
        var output = new ReflectionInsightOutput(
            List.of("insight1"),
            List.of("growth1"),
            "focus area",
            invalidTone,
            "gpt-4o",
            true
        );

        ValidationResult result = validator.validate(output);
        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.field().equals("emotionalTone"));
    }

    // ---- Cross-cutting: Confidence always validated ----

    @Property(tries = 100)
    void confidenceClampedByRecordAlwaysPassesValidation(
        @ForAll double rawConfidence
    ) {
        // All records clamp confidence to [0,1] in their compact constructors.
        // This property confirms the validator always passes for any input confidence
        // (since the record will have clamped it to valid range).
        var classification = new SafetyClassification(SafetyCategory.SAFE, rawConfidence, "test");
        ValidationResult result = validator.validate(classification);
        // The clamped confidence is always in [0,1], so validator should pass
        assertThat(result.valid()).isTrue();
        assertThat(classification.confidence()).isBetween(0.0, 1.0);
    }

    // ---- Arbitraries ----

    @Provide
    Arbitrary<String> validMessages() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(2000);
    }

    @Provide
    Arbitrary<String> overLengthMessages() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(2001)
            .ofMaxLength(2500);
    }

    @Provide
    Arbitrary<String> validModels() {
        return Arbitraries.of("gpt-4o", "gpt-4o-mini", "claude-3.5-sonnet");
    }

    @Provide
    Arbitrary<Double> validConfidence() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<String> validTitles() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(200);
    }

    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(500);
    }

    @Provide
    Arbitrary<String> validMissionCategory() {
        return Arbitraries.of(VALID_MISSION_CATEGORIES.toArray(new String[0]));
    }

    @Provide
    Arbitrary<String> invalidCategory() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter(s -> !VALID_MISSION_CATEGORIES.contains(s)
                && !VALID_MEMORY_CATEGORIES.contains(s)
                && !VALID_EMOTIONAL_TONES.contains(s));
    }

    @Provide
    Arbitrary<String> validMemoryContent() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(500);
    }

    @Provide
    Arbitrary<List<MemoryExtractionOutput.ExtractedMemory>> validMemoryList() {
        return validMemory().list().ofMinSize(0).ofMaxSize(5);
    }

    private Arbitrary<MemoryExtractionOutput.ExtractedMemory> validMemory() {
        return Combinators.combine(
            Arbitraries.of(VALID_MEMORY_CATEGORIES.toArray(new String[0])),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(500),
            Arbitraries.integers().between(1, 10),
            Arbitraries.doubles().between(0.0, 1.0),
            Arbitraries.of("father", "child", "family")
        ).as(MemoryExtractionOutput.ExtractedMemory::new);
    }

    @Provide
    Arbitrary<SafetyCategory> validSafetyCategory() {
        return Arbitraries.of(SafetyCategory.values());
    }

    @Provide
    Arbitrary<ActionRecommendation.ActionType> validActionType() {
        return Arbitraries.of(ActionRecommendation.ActionType.values());
    }

    @Provide
    Arbitrary<String> validEmotionalTone() {
        return Arbitraries.of(VALID_EMOTIONAL_TONES.toArray(new String[0]));
    }
}
