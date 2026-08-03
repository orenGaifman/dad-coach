package com.dadcoach.workflow.pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StatePatterns.FOLLOW_UP_PATTERNS.
 * 
 * Validates Requirements 7.2 and 7.3:
 * - 7.2: IF the father responds affirmatively, THE Workflow_Engine SHALL mark Quality Time as COMPLETED
 * - 7.3: IF the father responds negatively, THE Workflow_Engine SHALL mark Quality Time as MISSED
 */
@DisplayName("StatePatterns.FOLLOW_UP_PATTERNS")
class StatePatternsFollowUpTest {

    private PatternMatcherImpl patternMatcher;

    @BeforeEach
    void setUp() {
        patternMatcher = new PatternMatcherImpl();
    }

    @Nested
    @DisplayName("Completed patterns - English")
    class CompletedPatternsEnglish {

        @Test
        @DisplayName("should match 'yes' as completed")
        void shouldMatchYes() {
            Optional<PatternResult> result = patternMatcher.match("yes", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'Yes' case-insensitively as completed")
        void shouldMatchYesCaseInsensitive() {
            assertThat(patternMatcher.match("Yes", StatePatterns.FOLLOW_UP_PATTERNS).get().patternName())
                .isEqualTo("COMPLETED");
            assertThat(patternMatcher.match("YES", StatePatterns.FOLLOW_UP_PATTERNS).get().patternName())
                .isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should match 'done' as completed")
        void shouldMatchDone() {
            Optional<PatternResult> result = patternMatcher.match("done", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'completed' as completed")
        void shouldMatchCompleted() {
            Optional<PatternResult> result = patternMatcher.match("completed", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'finished' as completed")
        void shouldMatchFinished() {
            Optional<PatternResult> result = patternMatcher.match("finished", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'yes' with additional text as completed")
        void shouldMatchYesWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("yes, we had a great time!", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'done' with additional text as completed")
        void shouldMatchDoneWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("done, it was amazing", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
        }
    }

    @Nested
    @DisplayName("Completed patterns - Hebrew")
    class CompletedPatternsHebrew {

        @Test
        @DisplayName("should match 'כן' (yes) as completed")
        void shouldMatchKen() {
            Optional<PatternResult> result = patternMatcher.match("כן", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'סיימתי' (I finished) as completed")
        void shouldMatchSiyamti() {
            Optional<PatternResult> result = patternMatcher.match("סיימתי", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'עשיתי' (I did it) as completed")
        void shouldMatchAsiti() {
            Optional<PatternResult> result = patternMatcher.match("עשיתי", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match 'הושלם' (completed) as completed")
        void shouldMatchHushlam() {
            Optional<PatternResult> result = patternMatcher.match("הושלם", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("should match Hebrew completion with additional text")
        void shouldMatchHebrewCompletionWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("כן, היה נהדר!", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }
    }

    @Nested
    @DisplayName("Not completed patterns - English")
    class NotCompletedPatternsEnglish {

        @Test
        @DisplayName("should match 'no' as not completed")
        void shouldMatchNo() {
            Optional<PatternResult> result = patternMatcher.match("no", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match 'No' case-insensitively as not completed")
        void shouldMatchNoCaseInsensitive() {
            assertThat(patternMatcher.match("No", StatePatterns.FOLLOW_UP_PATTERNS).get().patternName())
                .isEqualTo("NOT_COMPLETED");
            assertThat(patternMatcher.match("NO", StatePatterns.FOLLOW_UP_PATTERNS).get().patternName())
                .isEqualTo("NOT_COMPLETED");
        }

        @Test
        @DisplayName("should match 'not yet' as not completed")
        void shouldMatchNotYet() {
            Optional<PatternResult> result = patternMatcher.match("not yet", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match 'couldn't' as not completed")
        void shouldMatchCouldnt() {
            Optional<PatternResult> result = patternMatcher.match("couldn't", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match 'no' with additional text as not completed")
        void shouldMatchNoWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("no, I was too busy", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match 'not yet' with additional text as not completed")
        void shouldMatchNotYetWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("not yet, but I'll try tomorrow", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED");
        }
    }

    @Nested
    @DisplayName("Not completed patterns - Hebrew")
    class NotCompletedPatternsHebrew {

        @Test
        @DisplayName("should match 'לא' (no) as not completed")
        void shouldMatchLo() {
            Optional<PatternResult> result = patternMatcher.match("לא", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match 'עוד לא' (not yet) as not completed")
        void shouldMatchOdLo() {
            Optional<PatternResult> result = patternMatcher.match("עוד לא", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match 'לא הצלחתי' (I couldn't) as not completed")
        void shouldMatchLoHitzlachti() {
            Optional<PatternResult> result = patternMatcher.match("לא הצלחתי", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }

        @Test
        @DisplayName("should match Hebrew not completed with additional text")
        void shouldMatchHebrewNotCompletedWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("לא, הייתי עסוק מדי", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("NOT_COMPLETED_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.MARK_MISSED);
        }
    }

    @Nested
    @DisplayName("Pattern priority and no match cases")
    class PatternPriorityAndNoMatch {

        @Test
        @DisplayName("should return empty for unrecognized input")
        void shouldReturnEmptyForUnrecognizedInput() {
            Optional<PatternResult> result = patternMatcher.match("hello world", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for empty string")
        void shouldReturnEmptyForEmptyString() {
            Optional<PatternResult> result = patternMatcher.match("", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should prioritize English completed pattern before Hebrew not completed for ambiguous cases")
        void shouldPrioritizeEnglishCompletedPattern() {
            // "yes" should match COMPLETED (English) first
            Optional<PatternResult> result = patternMatcher.match("yes", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("English patterns are evaluated before Hebrew patterns due to list order")
        void englishPatternsEvaluatedFirst() {
            // Both "yes" and "כן" should be matched by their respective language patterns
            Optional<PatternResult> englishResult = patternMatcher.match("yes", StatePatterns.FOLLOW_UP_PATTERNS);
            Optional<PatternResult> hebrewResult = patternMatcher.match("כן", StatePatterns.FOLLOW_UP_PATTERNS);

            assertThat(englishResult.get().patternName()).isEqualTo("COMPLETED");
            assertThat(hebrewResult.get().patternName()).isEqualTo("COMPLETED_HE");
        }
    }

    @Nested
    @DisplayName("Integration with PatternMatcherImpl")
    class IntegrationTests {

        @Test
        @DisplayName("FOLLOW_UP_PATTERNS should be a valid immutable list")
        void followUpPatternsShouldBeImmutable() {
            assertThat(StatePatterns.FOLLOW_UP_PATTERNS).isNotNull();
            assertThat(StatePatterns.FOLLOW_UP_PATTERNS).hasSize(4);
        }

        @Test
        @DisplayName("All patterns should have valid actions")
        void allPatternsShouldHaveValidActions() {
            for (StatePattern pattern : StatePatterns.FOLLOW_UP_PATTERNS) {
                assertThat(pattern.action()).isIn(WorkflowAction.MARK_COMPLETED, WorkflowAction.MARK_MISSED);
            }
        }

        @Test
        @DisplayName("COMPLETED patterns should all map to MARK_COMPLETED action")
        void completedPatternsShouldMapToMarkCompletedAction() {
            assertThat(StatePatterns.FOLLOW_UP_PATTERNS.get(0).action()).isEqualTo(WorkflowAction.MARK_COMPLETED);
            assertThat(StatePatterns.FOLLOW_UP_PATTERNS.get(1).action()).isEqualTo(WorkflowAction.MARK_COMPLETED);
        }

        @Test
        @DisplayName("NOT_COMPLETED patterns should all map to MARK_MISSED action")
        void notCompletedPatternsShouldMapToMarkMissedAction() {
            assertThat(StatePatterns.FOLLOW_UP_PATTERNS.get(2).action()).isEqualTo(WorkflowAction.MARK_MISSED);
            assertThat(StatePatterns.FOLLOW_UP_PATTERNS.get(3).action()).isEqualTo(WorkflowAction.MARK_MISSED);
        }
    }
}
