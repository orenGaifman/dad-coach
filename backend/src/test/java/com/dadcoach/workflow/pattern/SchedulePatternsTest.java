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
 * Unit tests for StatePatterns.SCHEDULE_PATTERNS.
 * Validates Requirement 5.2 - Slot selection patterns for SCHEDULE_QUALITY_TIME state.
 * 
 * <p>Tests cover:</p>
 * <ul>
 *   <li>SLOT_NUMBER: ^([1-9])$ → SELECT_SLOT</li>
 *   <li>SKIP (English): skip|not now|later → POSTPONE_SCHEDULING</li>
 *   <li>SKIP (Hebrew): דלג|לא עכשיו|אחר כך → POSTPONE_SCHEDULING</li>
 *   <li>MORE_SLOTS (English): other|more|different → SHOW_MORE_SLOTS</li>
 *   <li>MORE_SLOTS (Hebrew): אחר|עוד|אחרים → SHOW_MORE_SLOTS</li>
 *   <li>TIME_EXPRESSION: tomorrow|day patterns|time patterns|מחר → PARSE_TIME</li>
 * </ul>
 */
@DisplayName("StatePatterns.SCHEDULE_PATTERNS")
class SchedulePatternsTest {

    private PatternMatcherImpl patternMatcher;

    @BeforeEach
    void setUp() {
        patternMatcher = new PatternMatcherImpl();
    }

    @Nested
    @DisplayName("SLOT_NUMBER pattern")
    class SlotNumberPattern {

        @ParameterizedTest
        @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9"})
        @DisplayName("should match single digit slot numbers 1-9")
        void shouldMatchSingleDigitSlotNumbers(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SLOT_NUMBER");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SELECT_SLOT);
            assertThat(result.get().getCapturedValue()).contains(input);
        }

        @Test
        @DisplayName("should not match 0")
        void shouldNotMatchZero() {
            Optional<PatternResult> result = patternMatcher.match("0", StatePatterns.SCHEDULE_PATTERNS);

            // 0 doesn't match SLOT_NUMBER pattern ^([1-9])$
            assertThat(result.filter(r -> r.patternName().equals("SLOT_NUMBER"))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"10", "11", "99", "123"})
        @DisplayName("should not match multi-digit numbers")
        void shouldNotMatchMultiDigitNumbers(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result.filter(r -> r.patternName().equals("SLOT_NUMBER"))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1a", "a1", " 1", "1 ", "1.", ".1"})
        @DisplayName("should not match numbers with extra characters")
        void shouldNotMatchNumbersWithExtraCharacters(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result.filter(r -> r.patternName().equals("SLOT_NUMBER"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("SKIP pattern (English)")
    class SkipPatternEnglish {

        @ParameterizedTest
        @ValueSource(strings = {"skip", "Skip", "SKIP"})
        @DisplayName("should match 'skip' in various cases")
        void shouldMatchSkip(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }

        @ParameterizedTest
        @ValueSource(strings = {"not now", "Not Now", "NOT NOW"})
        @DisplayName("should match 'not now' in various cases")
        void shouldMatchNotNow(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }

        @ParameterizedTest
        @ValueSource(strings = {"later", "Later", "LATER"})
        @DisplayName("should match 'later' in various cases")
        void shouldMatchLater(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }

        @ParameterizedTest
        @ValueSource(strings = {"skip for now", "later today"})
        @DisplayName("should match skip keywords with additional text")
        void shouldMatchSkipKeywordsWithAdditionalText(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }
    }

    @Nested
    @DisplayName("SKIP pattern (Hebrew)")
    class SkipPatternHebrew {

        @Test
        @DisplayName("should match 'דלג' (skip in Hebrew)")
        void shouldMatchDaleg() {
            Optional<PatternResult> result = patternMatcher.match("דלג", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }

        @Test
        @DisplayName("should match 'לא עכשיו' (not now in Hebrew)")
        void shouldMatchLoAchshav() {
            Optional<PatternResult> result = patternMatcher.match("לא עכשיו", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }

        @Test
        @DisplayName("should match 'אחר כך' (later in Hebrew)")
        void shouldMatchAcharKach() {
            Optional<PatternResult> result = patternMatcher.match("אחר כך", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }

        @Test
        @DisplayName("should match Hebrew skip with additional text")
        void shouldMatchHebrewSkipWithAdditionalText() {
            Optional<PatternResult> result = patternMatcher.match("דלג בבקשה", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.POSTPONE_SCHEDULING);
        }
    }

    @Nested
    @DisplayName("MORE_SLOTS pattern (English)")
    class MoreSlotsPatternEnglish {

        @ParameterizedTest
        @ValueSource(strings = {"other", "Other", "OTHER", "show me other options"})
        @DisplayName("should match 'other' keyword")
        void shouldMatchOther(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"more", "More", "MORE", "show more", "give me more"})
        @DisplayName("should match 'more' keyword")
        void shouldMatchMore(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"different", "Different", "DIFFERENT", "something different"})
        @DisplayName("should match 'different' keyword")
        void shouldMatchDifferent(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }
    }

    @Nested
    @DisplayName("MORE_SLOTS pattern (Hebrew)")
    class MoreSlotsPatternHebrew {

        @Test
        @DisplayName("should match 'אחר' (other in Hebrew)")
        void shouldMatchAcher() {
            Optional<PatternResult> result = patternMatcher.match("אחר", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }

        @Test
        @DisplayName("should match 'עוד' (more in Hebrew)")
        void shouldMatchOd() {
            Optional<PatternResult> result = patternMatcher.match("עוד", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }

        @Test
        @DisplayName("should match 'אחרים' (others in Hebrew)")
        void shouldMatchAcherim() {
            Optional<PatternResult> result = patternMatcher.match("אחרים", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }

        @Test
        @DisplayName("should match Hebrew more slots with context")
        void shouldMatchHebrewMoreSlotsWithContext() {
            Optional<PatternResult> result = patternMatcher.match("תראה לי עוד אפשרויות", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("MORE_SLOTS_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.SHOW_MORE_SLOTS);
        }
    }

    @Nested
    @DisplayName("TIME_EXPRESSION pattern (English)")
    class TimeExpressionPatternEnglish {

        @ParameterizedTest
        @ValueSource(strings = {"tomorrow", "Tomorrow", "TOMORROW", "tomorrow afternoon"})
        @DisplayName("should match 'tomorrow'")
        void shouldMatchTomorrow(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "monday", "Monday", "MONDAY",
            "tuesday", "Tuesday",
            "wednesday", "Wednesday",
            "thursday", "Thursday",
            "friday", "Friday",
            "saturday", "Saturday",
            "sunday", "Sunday"
        })
        @DisplayName("should match day names")
        void shouldMatchDayNames(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"3pm", "3PM", "3 pm", "3 PM", "10am", "10 am"})
        @DisplayName("should match time with am/pm")
        void shouldMatchTimeWithAmPm(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"15:00", "3:00", "10:30", "9:45"})
        @DisplayName("should match time in HH:MM format")
        void shouldMatchTimeInHHMMFormat(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"in the morning", "in the afternoon", "in the evening"})
        @DisplayName("should match time of day expressions")
        void shouldMatchTimeOfDayExpressions(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }
    }

    @Nested
    @DisplayName("TIME_EXPRESSION pattern (Hebrew)")
    class TimeExpressionPatternHebrew {

        @Test
        @DisplayName("should match 'מחר' (tomorrow in Hebrew)")
        void shouldMatchMachar() {
            Optional<PatternResult> result = patternMatcher.match("מחר", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת"})
        @DisplayName("should match Hebrew day names")
        void shouldMatchHebrewDayNames(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"בבוקר", "אחר הצהריים", "בערב"})
        @DisplayName("should match Hebrew time of day expressions")
        void shouldMatchHebrewTimeOfDayExpressions(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }

        @Test
        @DisplayName("should match combined Hebrew time expression")
        void shouldMatchCombinedHebrewTimeExpression() {
            Optional<PatternResult> result = patternMatcher.match("מחר בבוקר", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("TIME_EXPRESSION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.PARSE_TIME);
        }
    }

    @Nested
    @DisplayName("Unmatched input")
    class UnmatchedInput {

        @ParameterizedTest
        @ValueSource(strings = {"hello", "world", "שלום", "random text", "xyz123"})
        @DisplayName("should not match random text")
        void shouldNotMatchRandomText(String input) {
            Optional<PatternResult> result = patternMatcher.match(input, StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Pattern priority")
    class PatternPriority {

        @Test
        @DisplayName("SLOT_NUMBER should have highest priority for single digits")
        void slotNumberShouldHaveHighestPriorityForSingleDigits() {
            // Test that "3" matches SLOT_NUMBER, not any other pattern
            Optional<PatternResult> result = patternMatcher.match("3", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SLOT_NUMBER");
        }

        @Test
        @DisplayName("SKIP should match before MORE_SLOTS when both could apply")
        void skipShouldMatchBeforeMoreSlotsWhenBothCouldApply() {
            // "later" is a SKIP keyword, should match before any MORE_SLOTS patterns
            Optional<PatternResult> result = patternMatcher.match("later", StatePatterns.SCHEDULE_PATTERNS);

            assertThat(result).isPresent();
            assertThat(result.get().patternName()).isEqualTo("SKIP");
        }
    }
}
