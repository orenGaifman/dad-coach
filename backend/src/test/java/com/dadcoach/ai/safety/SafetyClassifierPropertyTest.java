package com.dadcoach.ai.safety;

import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for SafetyClassifier.
 *
 * <p>Property 16: Safety Classification Completeness —
 * For any inbound message, the Safety Layer SHALL return exactly one classification
 * from the defined set with a confidence score in [0.0, 1.0].
 * The classification SHALL never be null or undefined.
 *
 * <p><b>Validates: Requirements 9.1</b>
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 16: Safety Classification Completeness")
class SafetyClassifierPropertyTest {

    private final SafetyClassifier classifier = new SafetyClassifier();

    private static final Set<SafetyCategory> VALID_CATEGORIES = Set.of(
        SafetyCategory.SAFE,
        SafetyCategory.EMOTIONAL_DISTRESS,
        SafetyCategory.CRISIS,
        SafetyCategory.CHILD_SAFETY,
        SafetyCategory.MEDICAL,
        SafetyCategory.LEGAL,
        SafetyCategory.MANIPULATION,
        SafetyCategory.OFF_TOPIC
    );

    // ===== Property 16: Classification is never null =====

    /**
     * **Validates: Requirements 9.1**
     *
     * For any arbitrary string input, classify() never returns null.
     */
    @Property(tries = 200)
    @Tag("Property 16: Safety Classification Completeness")
    void classificationIsNeverNull(@ForAll("arbitraryMessages") String message) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result).isNotNull();
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * For any arbitrary string input, the category is always one of the defined set.
     */
    @Property(tries = 200)
    @Tag("Property 16: Safety Classification Completeness")
    void categoryIsAlwaysFromDefinedSet(@ForAll("arbitraryMessages") String message) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result.category()).isNotNull();
        assertThat(result.category()).isIn(VALID_CATEGORIES.toArray());
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * For any arbitrary string input, confidence is always in range [0.0, 1.0].
     */
    @Property(tries = 200)
    @Tag("Property 16: Safety Classification Completeness")
    void confidenceIsAlwaysInValidRange(@ForAll("arbitraryMessages") String message) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result.confidence())
            .isGreaterThanOrEqualTo(0.0)
            .isLessThanOrEqualTo(1.0);
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * For null input, classify returns a valid SAFE classification (not an exception).
     */
    @Property(tries = 100)
    @Tag("Property 16: Safety Classification Completeness")
    void nullInputReturnsValidClassification(@ForAll("nullOrBlank") String message) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result).isNotNull();
        assertThat(result.category()).isEqualTo(SafetyCategory.SAFE);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * For messages containing crisis keywords, classification is always CRISIS
     * with confidence > 0.
     */
    @Property(tries = 100)
    @Tag("Property 16: Safety Classification Completeness")
    void crisisKeywordsAlwaysDetected(@ForAll("messagesWithCrisisKeywords") String message) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result).isNotNull();
        assertThat(result.category()).isEqualTo(SafetyCategory.CRISIS);
        assertThat(result.confidence()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * For messages containing manipulation patterns, classification is MANIPULATION.
     */
    @Property(tries = 100)
    @Tag("Property 16: Safety Classification Completeness")
    void manipulationPatternsAlwaysDetected(@ForAll("messagesWithManipulation") String message) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result).isNotNull();
        // Manipulation is detected unless a higher-priority (CRISIS/CHILD_SAFETY) keyword is also present
        assertThat(result.category()).isIn(
            SafetyCategory.MANIPULATION,
            SafetyCategory.CRISIS,
            SafetyCategory.CHILD_SAFETY
        );
        assertThat(result.confidence()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    /**
     * **Validates: Requirements 9.1**
     *
     * For any random Unicode string (including emojis, special chars), classification
     * still returns a valid result — no exceptions thrown.
     */
    @Property(tries = 200)
    @Tag("Property 16: Safety Classification Completeness")
    void randomUnicodeNeverCausesException(
        @ForAll @StringLength(min = 0, max = 2000) String message
    ) {
        SafetyClassification result = classifier.classify(message);
        assertThat(result).isNotNull();
        assertThat(result.category()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    // ===== Arbitraries =====

    @Provide
    Arbitrary<String> arbitraryMessages() {
        return Arbitraries.oneOf(
            // Normal English parenting messages
            Arbitraries.of(
                "Hello, today I played with my son at the park",
                "My daughter doesn't want to do her homework",
                "What activity can I do with Lucas?",
                "I'm happy because my son hugged me",
                "I don't know how to handle tantrums",
                "I want to be a better dad",
                "My son is 5 years old and very active"
            ),
            // Empty/whitespace
            Arbitraries.of("", "   ", "\n\t"),
            // Random strings
            Arbitraries.strings().ofMinLength(0).ofMaxLength(500),
            // Messages with numbers and special chars
            Arbitraries.strings().ofMinLength(1).ofMaxLength(200)
                .withChars('a', 'z', 'A', 'Z', ' ', '?', '!', '0', '1')
        );
    }

    @Provide
    Arbitrary<String> nullOrBlank() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }

    @Provide
    Arbitrary<String> messagesWithCrisisKeywords() {
        Arbitrary<String> prefixes = Arbitraries.of(
            "I'm thinking about ",
            "I can't anymore, I want to ",
            "Yesterday ",
            "I feel so bad that I want to "
        );
        Arbitrary<String> crisisKeywords = Arbitraries.of(
            SafetyKeywords.CRISIS_SELF_HARM.toArray(new String[0])
        );
        Arbitrary<String> suffixes = Arbitraries.of(
            "", ".", " I don't know what to do", " help"
        );
        return Combinators.combine(prefixes, crisisKeywords, suffixes)
            .as((prefix, keyword, suffix) -> prefix + keyword + suffix);
    }

    @Provide
    Arbitrary<String> messagesWithManipulation() {
        Arbitrary<String> prefixes = Arbitraries.of(
            "", "Please ", "I need you to ", "I want you to "
        );
        Arbitrary<String> patterns = Arbitraries.of(
            SafetyKeywords.MANIPULATION_PATTERNS.toArray(new String[0])
        );
        Arbitrary<String> suffixes = Arbitraries.of(
            "", ".", " now", " please"
        );
        return Combinators.combine(prefixes, patterns, suffixes)
            .as((prefix, pattern, suffix) -> prefix + pattern + suffix);
    }
}
