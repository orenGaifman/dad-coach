package com.dadcoach.workflow.pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StatePatterns.ACTIVITY_IDEAS_PATTERNS.
 * 
 * Validates Requirement 9.4:
 * - Allow selecting an idea by number (1-3) for more details
 * - Allow requesting more ideas
 * - Allow exiting to return to previous workflow state
 */
@DisplayName("StatePatterns.ACTIVITY_IDEAS_PATTERNS")
class StatePatternsActivityIdeasTest {

    private PatternMatcherImpl patternMatcher;

    @BeforeEach
    void setUp() {
        patternMatcher = new PatternMatcherImpl();
    }

    @Nested
    @DisplayName("Idea selection patterns")
    class IdeaSelectionPatterns {

        @ParameterizedTest
        @ValueSource(strings = {"1", "2", "3"})
        @DisplayName("should match idea numbers 1-3")
        void shouldMatchIdeaNumbers(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("IDEA_NUMBER");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_IDEA_DETAILS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "4", "5", "9"})
        @DisplayName("should not match numbers outside 1-3")
        void shouldNotMatchNumbersOutsideRange(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result.filter(r -> r.patternName().equals("IDEA_NUMBER"))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"12", "1a", "a1", "one"})
        @DisplayName("should not match non-single-digit inputs")
        void shouldNotMatchNonSingleDigitInputs(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result.filter(r -> r.patternName().equals("IDEA_NUMBER"))).isEmpty();
        }

        @Test
        @DisplayName("should extract captured group for idea number")
        void shouldExtractCapturedGroupForIdeaNumber() {
            Optional<PatternResult> result = patternMatcher.match("2", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().getCapturedValue()).contains("2");
        }
    }

    @Nested
    @DisplayName("Exit patterns - English")
    class ExitPatternsEnglish {

        @Test
        @DisplayName("should match 'thanks'")
        void shouldMatchThanks() {
            Optional<PatternResult> result = patternMatcher.match("thanks", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match 'thank you'")
        void shouldMatchThankYou() {
            Optional<PatternResult> result = patternMatcher.match("thank you", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match 'done'")
        void shouldMatchDone() {
            Optional<PatternResult> result = patternMatcher.match("done", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match 'enough'")
        void shouldMatchEnough() {
            Optional<PatternResult> result = patternMatcher.match("enough", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match 'that's enough'")
        void shouldMatchThatsEnough() {
            Optional<PatternResult> result = patternMatcher.match("that's enough", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
        }

        @Test
        @DisplayName("should match exit phrases case-insensitively")
        void shouldMatchExitCaseInsensitive() {
            assertThat(patternMatcher.match("Thanks", StatePatterns.ACTIVITY_IDEAS_PATTERNS).get().patternName())
                .isEqualTo("EXIT");
            assertThat(patternMatcher.match("DONE", StatePatterns.ACTIVITY_IDEAS_PATTERNS).get().patternName())
                .isEqualTo("EXIT");
        }

        @Test
        @DisplayName("should match 'done' with additional text")
        void shouldMatchDoneWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("done for now", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
        }
    }

    @Nested
    @DisplayName("Exit patterns - Hebrew")
    class ExitPatternsHebrew {

        @Test
        @DisplayName("should match 'תודה' (thanks)")
        void shouldMatchToda() {
            Optional<PatternResult> result = patternMatcher.match("תודה", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match 'סיימתי' (I'm done)")
        void shouldMatchSiyamti() {
            Optional<PatternResult> result = patternMatcher.match("סיימתי", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match 'מספיק' (enough)")
        void shouldMatchMaspik() {
            Optional<PatternResult> result = patternMatcher.match("מספיק", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.RETURN_TO_PREVIOUS);
        }

        @Test
        @DisplayName("should match Hebrew exit with additional text")
        void shouldMatchHebrewExitWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("תודה רבה!", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT_HE");
        }
    }

    @Nested
    @DisplayName("More ideas patterns - English")
    class MoreIdeasPatternsEnglish {

        @Test
        @DisplayName("should match 'more'")
        void shouldMatchMore() {
            Optional<PatternResult> result = patternMatcher.match("more", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.GENERATE_MORE_IDEAS);
        }

        @Test
        @DisplayName("should match 'another'")
        void shouldMatchAnother() {
            Optional<PatternResult> result = patternMatcher.match("another", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.GENERATE_MORE_IDEAS);
        }

        @Test
        @DisplayName("should match 'different'")
        void shouldMatchDifferent() {
            Optional<PatternResult> result = patternMatcher.match("different", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.GENERATE_MORE_IDEAS);
        }

        @Test
        @DisplayName("should match 'show me more ideas'")
        void shouldMatchShowMeMoreIdeas() {
            Optional<PatternResult> result = patternMatcher.match("show me more ideas", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS");
        }

        @Test
        @DisplayName("should match 'other ideas'")
        void shouldMatchOtherIdeas() {
            Optional<PatternResult> result = patternMatcher.match("other ideas", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS");
        }
    }

    @Nested
    @DisplayName("More ideas patterns - Hebrew")
    class MoreIdeasPatternsHebrew {

        @Test
        @DisplayName("should match 'עוד רעיונות' (more ideas)")
        void shouldMatchOdRaayonot() {
            Optional<PatternResult> result = patternMatcher.match("עוד רעיונות", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.GENERATE_MORE_IDEAS);
        }

        @Test
        @DisplayName("should match 'רעיונות אחרים' (other ideas)")
        void shouldMatchRaayonotAcherim() {
            Optional<PatternResult> result = patternMatcher.match("רעיונות אחרים", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_IDEAS_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.GENERATE_MORE_IDEAS);
        }
    }

    @Nested
    @DisplayName("Pattern priority and edge cases")
    class PatternPriorityAndEdgeCases {

        @Test
        @DisplayName("should return empty for unrecognized input")
        void shouldReturnEmptyForUnrecognizedInput() {
            Optional<PatternResult> result = patternMatcher.match("hello world", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for empty string")
        void shouldReturnEmptyForEmptyString() {
            Optional<PatternResult> result = patternMatcher.match("", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("IDEA_NUMBER should have highest priority for digits 1-3")
        void ideaNumberShouldHaveHighestPriorityForDigits() {
            // "1" should match IDEA_NUMBER, not any other pattern
            Optional<PatternResult> result = patternMatcher.match("1", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("IDEA_NUMBER");
        }

        @Test
        @DisplayName("EXIT should match before MORE_IDEAS for 'done'")
        void exitShouldMatchBeforeMoreIdeasForDone() {
            // "done" contains patterns from both EXIT and potentially could conflict
            // EXIT should win because it's evaluated first
            Optional<PatternResult> result = patternMatcher.match("done", StatePatterns.ACTIVITY_IDEAS_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("EXIT");
        }

        @Test
        @DisplayName("ACTIVITY_IDEAS_PATTERNS should contain 5 patterns")
        void activityIdeasPatternsShouldContainFivePatterns() {
            // 5 patterns: IDEA_NUMBER, EXIT (EN), EXIT_HE, MORE_IDEAS (EN), MORE_IDEAS_HE
            assertThat(StatePatterns.ACTIVITY_IDEAS_PATTERNS).hasSize(5);
        }
    }
}
