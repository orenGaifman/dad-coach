package com.dadcoach.workflow.pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PatternMatcherImpl.
 * Validates Requirement 11.3 - Pattern-based message processing without AI interpretation.
 */
@DisplayName("PatternMatcherImpl")
class PatternMatcherImplTest {

    private PatternMatcherImpl patternMatcher;

    @BeforeEach
    void setUp() {
        patternMatcher = new PatternMatcherImpl();
    }

    @Nested
    @DisplayName("Pattern matching basics")
    class PatternMatchingBasics {

        @Test
        @DisplayName("should return matching pattern result when input matches first pattern")
        void shouldReturnMatchingPatternResultWhenFirstPatternMatches() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE", Pattern.compile("(?i)^(yes|ready|ok)$"), WorkflowAction.TRANSITION_TO_SCHEDULE),
                StatePattern.of("MORE_INFO", Pattern.compile("(?i).*(how|what).*"), WorkflowAction.EXPLAIN_AND_REPROMPT)
            );

            Optional<PatternResult> result = patternMatcher.match("yes", patterns);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("AFFIRMATIVE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.TRANSITION_TO_SCHEDULE);
        }

        @Test
        @DisplayName("should return matching pattern result when input matches second pattern")
        void shouldReturnMatchingPatternResultWhenSecondPatternMatches() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE", Pattern.compile("(?i)^(yes|ready|ok)$"), WorkflowAction.TRANSITION_TO_SCHEDULE),
                StatePattern.of("MORE_INFO", Pattern.compile("(?i).*(how|what).*"), WorkflowAction.EXPLAIN_AND_REPROMPT)
            );

            Optional<PatternResult> result = patternMatcher.match("how does it work", patterns);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_INFO");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.EXPLAIN_AND_REPROMPT);
        }

        @Test
        @DisplayName("should return empty when no pattern matches")
        void shouldReturnEmptyWhenNoPatternMatches() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE", Pattern.compile("(?i)^(yes|ready|ok)$"), WorkflowAction.TRANSITION_TO_SCHEDULE),
                StatePattern.of("MORE_INFO", Pattern.compile("(?i).*(how|what).*"), WorkflowAction.EXPLAIN_AND_REPROMPT)
            );

            Optional<PatternResult> result = patternMatcher.match("hello world", patterns);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for empty pattern list")
        void shouldReturnEmptyForEmptyPatternList() {
            Optional<PatternResult> result = patternMatcher.match("yes", Collections.emptyList());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("First match wins behavior")
    class FirstMatchWinsBehavior {

        @Test
        @DisplayName("should return first matching pattern when multiple patterns could match")
        void shouldReturnFirstMatchingPattern() {
            // Both patterns could match "yes", but AFFIRMATIVE is first
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE", Pattern.compile("(?i)^yes$"), WorkflowAction.TRANSITION_TO_SCHEDULE),
                StatePattern.of("GENERIC_YES", Pattern.compile("(?i).*yes.*"), WorkflowAction.MARK_COMPLETED)
            );

            Optional<PatternResult> result = patternMatcher.match("yes", patterns);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("AFFIRMATIVE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.TRANSITION_TO_SCHEDULE);
        }

        @Test
        @DisplayName("should respect pattern order - more specific patterns can precede general ones")
        void shouldRespectPatternOrder() {
            // Specific pattern for slot numbers 1-5 before general number pattern
            List<StatePattern> patterns = List.of(
                StatePattern.of("VALID_SLOT", Pattern.compile("^([1-5])$"), WorkflowAction.SELECT_SLOT),
                StatePattern.of("INVALID_SLOT", Pattern.compile("^([6-9])$"), WorkflowAction.SHOW_MORE_SLOTS)
            );

            // Test valid slot
            Optional<PatternResult> validResult = patternMatcher.match("3", patterns);
            assertThat(validResult).isPresent();
            assertThat(validResult.get().patternName()).isEqualTo("VALID_SLOT");

            // Test invalid slot
            Optional<PatternResult> invalidResult = patternMatcher.match("7", patterns);
            assertThat(invalidResult).isPresent();
            assertThat(invalidResult.get().patternName()).isEqualTo("INVALID_SLOT");
        }
    }

    @Nested
    @DisplayName("Captured groups extraction")
    class CapturedGroupsExtraction {

        @Test
        @DisplayName("should extract captured groups from regex")
        void shouldExtractCapturedGroups() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("SLOT_NUMBER", Pattern.compile("^([1-9])$"), WorkflowAction.SELECT_SLOT)
            );

            Optional<PatternResult> result = patternMatcher.match("3", patterns);

            assertThat(result).isPresent();
            assertThat(result.get().hasCapturedGroups()).isTrue();
            assertThat(result.get().getCapturedValue()).contains("3");
            assertThat(result.get().getCapturedGroup("group1")).contains("3");
        }

        @Test
        @DisplayName("should extract multiple captured groups")
        void shouldExtractMultipleCapturedGroups() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("TIME_RANGE", Pattern.compile("^(\\d+):(\\d+)$"), WorkflowAction.PARSE_TIME)
            );

            Optional<PatternResult> result = patternMatcher.match("10:30", patterns);

            assertThat(result).isPresent();
            assertThat(result.get().getCapturedGroup("group1")).contains("10");
            assertThat(result.get().getCapturedGroup("group2")).contains("30");
            assertThat(result.get().getCapturedValue()).contains("10"); // First group is "value"
        }

        @Test
        @DisplayName("should return empty captured groups for pattern without groups")
        void shouldReturnEmptyCapturedGroupsForPatternWithoutGroups() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("SKIP", Pattern.compile("(?i)^skip$"), WorkflowAction.POSTPONE_SCHEDULING)
            );

            Optional<PatternResult> result = patternMatcher.match("skip", patterns);

            assertThat(result).isPresent();
            assertThat(result.get().hasCapturedGroups()).isFalse();
        }
    }

    @Nested
    @DisplayName("Bilingual support (English and Hebrew)")
    class BilingualSupport {

        @Test
        @DisplayName("should match English affirmative responses")
        void shouldMatchEnglishAffirmativeResponses() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE", 
                    Pattern.compile("(?i)^(yes|ready|let's go|lets go|ok|okay|sure|start|begin).*"), 
                    WorkflowAction.TRANSITION_TO_SCHEDULE)
            );

            assertThat(patternMatcher.match("yes", patterns)).isPresent();
            assertThat(patternMatcher.match("Yes", patterns)).isPresent();
            assertThat(patternMatcher.match("YES", patterns)).isPresent();
            assertThat(patternMatcher.match("ready", patterns)).isPresent();
            assertThat(patternMatcher.match("let's go", patterns)).isPresent();
            assertThat(patternMatcher.match("ok", patterns)).isPresent();
        }

        @Test
        @DisplayName("should match Hebrew affirmative responses")
        void shouldMatchHebrewAffirmativeResponses() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE_HE", 
                    Pattern.compile("^(כן|מוכן|יאללה|בסדר|בוא נתחיל|התחל).*"), 
                    WorkflowAction.TRANSITION_TO_SCHEDULE)
            );

            assertThat(patternMatcher.match("כן", patterns)).isPresent();
            assertThat(patternMatcher.match("מוכן", patterns)).isPresent();
            assertThat(patternMatcher.match("יאללה", patterns)).isPresent();
            assertThat(patternMatcher.match("בסדר", patterns)).isPresent();
        }

        @Test
        @DisplayName("should handle combined English and Hebrew patterns")
        void shouldHandleCombinedEnglishAndHebrewPatterns() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("AFFIRMATIVE_EN", 
                    Pattern.compile("(?i)^(yes|ready|ok)$"), 
                    WorkflowAction.TRANSITION_TO_SCHEDULE),
                StatePattern.of("AFFIRMATIVE_HE", 
                    Pattern.compile("^(כן|מוכן|בסדר)$"), 
                    WorkflowAction.TRANSITION_TO_SCHEDULE),
                StatePattern.of("MORE_INFO_EN", 
                    Pattern.compile("(?i).*(how|what).*"), 
                    WorkflowAction.EXPLAIN_AND_REPROMPT),
                StatePattern.of("MORE_INFO_HE", 
                    Pattern.compile(".*(איך|מה זה).*"), 
                    WorkflowAction.EXPLAIN_AND_REPROMPT)
            );

            // English
            assertThat(patternMatcher.match("yes", patterns).get().patternName()).isEqualTo("AFFIRMATIVE_EN");
            assertThat(patternMatcher.match("how does it work", patterns).get().patternName()).isEqualTo("MORE_INFO_EN");
            
            // Hebrew
            assertThat(patternMatcher.match("כן", patterns).get().patternName()).isEqualTo("AFFIRMATIVE_HE");
            assertThat(patternMatcher.match("איך זה עובד", patterns).get().patternName()).isEqualTo("MORE_INFO_HE");
        }
    }

    @Nested
    @DisplayName("Null handling and validation")
    class NullHandlingAndValidation {

        @Test
        @DisplayName("should throw NullPointerException when input is null")
        void shouldThrowExceptionWhenInputIsNull() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("TEST", Pattern.compile(".*"), WorkflowAction.TRANSITION_TO_SCHEDULE)
            );

            assertThatThrownBy(() -> patternMatcher.match(null, patterns))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input must not be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when patterns list is null")
        void shouldThrowExceptionWhenPatternsIsNull() {
            assertThatThrownBy(() -> patternMatcher.match("test", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("patterns must not be null");
        }
    }

    @Nested
    @DisplayName("Case sensitivity")
    class CaseSensitivity {

        @Test
        @DisplayName("case insensitive patterns should match regardless of case")
        void caseInsensitivePatternsMatch() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("SKIP", Pattern.compile("(?i)^(skip|not now|later)$"), WorkflowAction.POSTPONE_SCHEDULING)
            );

            assertThat(patternMatcher.match("skip", patterns)).isPresent();
            assertThat(patternMatcher.match("SKIP", patterns)).isPresent();
            assertThat(patternMatcher.match("Skip", patterns)).isPresent();
            assertThat(patternMatcher.match("not now", patterns)).isPresent();
            assertThat(patternMatcher.match("NOT NOW", patterns)).isPresent();
        }

        @Test
        @DisplayName("case sensitive patterns should only match exact case")
        void caseSensitivePatternsMatchExactCase() {
            List<StatePattern> patterns = List.of(
                StatePattern.of("EXACT", Pattern.compile("^Test$"), WorkflowAction.TRANSITION_TO_SCHEDULE)
            );

            assertThat(patternMatcher.match("Test", patterns)).isPresent();
            assertThat(patternMatcher.match("test", patterns)).isEmpty();
            assertThat(patternMatcher.match("TEST", patterns)).isEmpty();
        }
    }
}
