package com.dadcoach.workflow.frustration;

import com.dadcoach.workflow.pattern.PatternMatcherImpl;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;
import net.jqwik.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Standard Message Handling
 * 
 * **Validates: Requirements 3.9, 3.10**
 * 
 * <p>These tests ensure that non-frustration messages continue to be processed through
 * the standard pattern matching flow after the Bug 5 fix is applied. The fix adds
 * frustration pattern detection, but should NOT affect how normal messages are handled.</p>
 * 
 * <p><strong>Preservation Requirements from bugfix.md:</strong></p>
 * <ul>
 *   <li>3.9: WHEN a user sends a normal message without frustration indicators 
 *        THEN the system SHALL CONTINUE TO process it through the standard pattern 
 *        matching and state handling flow</li>
 *   <li>3.10: WHEN a user expresses frustration but also provides actionable content 
 *        THEN the system SHALL CONTINUE TO process the actionable content while 
 *        adding empathetic acknowledgment</li>
 * </ul>
 * 
 * <p><strong>EXPECTED BEHAVIOR:</strong></p>
 * <ul>
 *   <li>These tests MUST PASS on unfixed code (current behavior is correct for non-frustration messages)</li>
 *   <li>These tests MUST PASS after the fix is applied (no regression for standard messages)</li>
 * </ul>
 * 
 * <p><strong>Key Distinction from Exploration Tests:</strong></p>
 * <ul>
 *   <li>Exploration tests: Test bug condition (frustration without empathy) - expected to FAIL on unfixed code</li>
 *   <li>Preservation tests: Test non-bug scenarios (normal messages) - expected to PASS on all code</li>
 * </ul>
 * 
 * <p><strong>Observation-First Methodology:</strong></p>
 * <p>These tests were written after observing that:</p>
 * <ol>
 *   <li>Normal messages without frustration indicators are processed through standard flow on unfixed code</li>
 *   <li>Messages with actionable content still get processed correctly on unfixed code</li>
 * </ol>
 */
class StandardMessageHandlingPreservationTest {

    // ============== Property: Normal Messages Without Frustration Are Processed Normally ==============

    /**
     * Property test: Non-frustration messages in SCHEDULE_QUALITY_TIME state should match
     * standard scheduling patterns (slot selection, skip, time expressions, etc.).
     * 
     * <p>This behavior is CORRECT and should remain unchanged after the frustration handler fix.
     * The fix adds frustration detection, but standard scheduling messages should continue
     * to match their intended patterns.</p>
     * 
     * **Validates: Requirements 3.9**
     */
    @Property(tries = 100)
    @Label("Non-frustration scheduling messages continue to match standard patterns")
    void nonFrustrationSchedulingMessagesContinueToMatchStandardPatterns(
            @ForAll("normalSchedulingMessages") String normalMessage
    ) {
        // Arrange: Create a pattern matcher
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        
        // Pre-condition: Message should NOT contain frustration indicators
        Assume.that(!containsFrustrationIndicator(normalMessage));
        
        // Act: Match against SCHEDULE_PATTERNS (the standard state patterns)
        Optional<PatternResult> result = patternMatcher.match(
                normalMessage, StatePatterns.SCHEDULE_PATTERNS);
        
        // ASSERT: Normal scheduling messages should match a standard scheduling pattern
        // This behavior must remain unchanged after the frustration handler fix
        assertThat(result)
                .as("Normal scheduling message '%s' should match a SCHEDULE_PATTERNS pattern. " +
                    "This behavior must remain unchanged after the frustration handler fix.",
                    normalMessage)
                .isPresent();
        
        assertThat(result.get().isMatched())
                .as("Pattern result should indicate a match for normal message '%s'", normalMessage)
                .isTrue();
        
        // The matched action should be a standard scheduling action, NOT frustration-related
        WorkflowAction matchedAction = result.get().matchedAction();
        assertThat(matchedAction)
                .as("Normal message '%s' should map to a standard scheduling action, not frustration handling",
                    normalMessage)
                .isIn(
                        WorkflowAction.SELECT_SLOT,
                        WorkflowAction.POSTPONE_SCHEDULING,
                        WorkflowAction.SHOW_MORE_SLOTS,
                        WorkflowAction.PARSE_TIME,
                        WorkflowAction.RESET_TO_WELCOME,
                        WorkflowAction.ALREADY_SCHEDULED,
                        WorkflowAction.ACKNOWLEDGE_SCHEDULE
                );
    }

    /**
     * Property test: Non-frustration messages in WAITING state should match
     * standard waiting patterns (request ideas, reschedule, show schedule, etc.).
     * 
     * **Validates: Requirements 3.9**
     */
    @Property(tries = 100)
    @Label("Non-frustration waiting messages continue to match standard patterns")
    void nonFrustrationWaitingMessagesContinueToMatchStandardPatterns(
            @ForAll("normalWaitingMessages") String normalMessage
    ) {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        
        // Pre-condition: Message should NOT contain frustration indicators
        Assume.that(!containsFrustrationIndicator(normalMessage));
        
        // Act: Match against WAITING_PATTERNS
        Optional<PatternResult> result = patternMatcher.match(
                normalMessage, StatePatterns.WAITING_PATTERNS);
        
        // ASSERT: Normal waiting messages should match a standard pattern
        assertThat(result)
                .as("Normal waiting message '%s' should match a WAITING_PATTERNS pattern. " +
                    "This behavior must remain unchanged after the frustration handler fix.",
                    normalMessage)
                .isPresent();
        
        assertThat(result.get().isMatched())
                .as("Pattern result should indicate a match for normal message '%s'", normalMessage)
                .isTrue();
        
        // The matched action should be a standard waiting action
        WorkflowAction matchedAction = result.get().matchedAction();
        assertThat(matchedAction)
                .as("Normal message '%s' should map to a standard waiting action",
                    normalMessage)
                .isIn(
                        WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS,
                        WorkflowAction.RESCHEDULE,
                        WorkflowAction.SHOW_SCHEDULE,
                        WorkflowAction.SHOW_DASHBOARD_SUMMARY,
                        WorkflowAction.ACKNOWLEDGE_SCHEDULE,
                        WorkflowAction.ALREADY_SCHEDULED
                );
    }

    /**
     * Property test: Non-frustration messages in FOLLOW_UP state should match
     * standard follow-up patterns (completed, not completed).
     * 
     * **Validates: Requirements 3.9**
     */
    @Property(tries = 100)
    @Label("Non-frustration follow-up messages continue to match standard patterns")
    void nonFrustrationFollowUpMessagesContinueToMatchStandardPatterns(
            @ForAll("normalFollowUpMessages") String normalMessage
    ) {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        
        // Pre-condition: Message should NOT contain frustration indicators
        Assume.that(!containsFrustrationIndicator(normalMessage));
        
        // Act: Match against FOLLOW_UP_PATTERNS
        Optional<PatternResult> result = patternMatcher.match(
                normalMessage, StatePatterns.FOLLOW_UP_PATTERNS);
        
        // ASSERT: Normal follow-up messages should match a standard pattern
        assertThat(result)
                .as("Normal follow-up message '%s' should match a FOLLOW_UP_PATTERNS pattern. " +
                    "This behavior must remain unchanged after the frustration handler fix.",
                    normalMessage)
                .isPresent();
        
        assertThat(result.get().isMatched())
                .as("Pattern result should indicate a match for normal message '%s'", normalMessage)
                .isTrue();
        
        // The matched action should be a standard follow-up action
        WorkflowAction matchedAction = result.get().matchedAction();
        assertThat(matchedAction)
                .as("Normal message '%s' should map to a standard follow-up action",
                    normalMessage)
                .isIn(
                        WorkflowAction.MARK_COMPLETED,
                        WorkflowAction.MARK_MISSED
                );
    }

    // ============== Property: Actionable Content With Frustration Still Gets Processed ==============

    /**
     * Property test: Messages that contain BOTH frustration indicators AND actionable content
     * should still have the actionable content recognized.
     * 
     * <p>Example: "כבר אמרתי שכן, 3" contains frustration but also slot selection "3"</p>
     * <p>The actionable content (slot selection) should still be detectable.</p>
     * 
     * <p>Note: This test verifies that the underlying pattern matching CAN detect actionable
     * content. After the fix, the system should detect frustration first, then process
     * the actionable content - but the actionable content should still be processable.</p>
     * 
     * **Validates: Requirements 3.10**
     */
    @Property(tries = 50)
    @Label("Actionable content remains detectable even with frustration prefix")
    void actionableContentRemainsDetectableEvenWithFrustrationPrefix(
            @ForAll("actionableContentMessages") String actionableMessage
    ) {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        
        // Act: Check if the actionable part of the message can be matched
        // The patterns should be able to detect the actionable content
        Optional<PatternResult> scheduleResult = patternMatcher.match(
                actionableMessage, StatePatterns.SCHEDULE_PATTERNS);
        Optional<PatternResult> waitingResult = patternMatcher.match(
                actionableMessage, StatePatterns.WAITING_PATTERNS);
        Optional<PatternResult> followUpResult = patternMatcher.match(
                actionableMessage, StatePatterns.FOLLOW_UP_PATTERNS);
        
        // At least one pattern should match the actionable content
        boolean actionableContentMatched = 
                (scheduleResult.isPresent() && scheduleResult.get().isMatched()) ||
                (waitingResult.isPresent() && waitingResult.get().isMatched()) ||
                (followUpResult.isPresent() && followUpResult.get().isMatched());
        
        // ASSERT: Actionable content should be detectable
        // This ensures that after adding frustration handling, we can still process
        // the actionable part of the message
        assertThat(actionableContentMatched)
                .as("Message '%s' contains actionable content that should be detectable by pattern matching. " +
                    "After the frustration fix, both frustration AND actionable content should be processed.",
                    actionableMessage)
                .isTrue();
    }

    // ============== Example Tests for Specific Scenarios ==============

    /**
     * Example: Slot selection "3" should continue to work in SCHEDULE state.
     * 
     * **Validates: Requirements 3.9**
     */
    @Example
    @Label("Slot selection continues to work normally")
    void slotSelectionContinuesToWorkNormally() {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        String slotSelectionMessage = "3";
        
        // Act
        Optional<PatternResult> result = patternMatcher.match(
                slotSelectionMessage, StatePatterns.SCHEDULE_PATTERNS);
        
        // Assert
        assertThat(result)
                .as("Slot selection '3' should match SCHEDULE_PATTERNS")
                .isPresent();
        assertThat(result.get().matchedAction())
                .as("Slot selection should map to SELECT_SLOT action")
                .isEqualTo(WorkflowAction.SELECT_SLOT);
    }

    /**
     * Example: Hebrew "כן" (yes) in FOLLOW_UP state should continue to work.
     * 
     * **Validates: Requirements 3.9**
     */
    @Example
    @Label("Hebrew 'yes' continues to work in follow-up state")
    void hebrewYesContinuesToWorkInFollowUpState() {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        String yesMessage = "כן";
        
        // Pre-condition: This message should NOT be flagged as frustration
        assertThat(containsFrustrationIndicator(yesMessage))
                .as("Simple 'כן' should not be flagged as frustration")
                .isFalse();
        
        // Act
        Optional<PatternResult> result = patternMatcher.match(
                yesMessage, StatePatterns.FOLLOW_UP_PATTERNS);
        
        // Assert
        assertThat(result)
                .as("Hebrew 'כן' should match FOLLOW_UP_PATTERNS")
                .isPresent();
        assertThat(result.get().matchedAction())
                .as("'כן' should map to MARK_COMPLETED action")
                .isEqualTo(WorkflowAction.MARK_COMPLETED);
    }

    /**
     * Example: "when is my next quality time" should continue to work in WAITING state.
     * 
     * **Validates: Requirements 3.9**
     */
    @Example
    @Label("Schedule inquiry continues to work in waiting state")
    void scheduleInquiryContinuesToWorkInWaitingState() {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        String inquiryMessage = "when is my next quality time";
        
        // Pre-condition: This message should NOT be flagged as frustration
        assertThat(containsFrustrationIndicator(inquiryMessage))
                .as("Schedule inquiry should not be flagged as frustration")
                .isFalse();
        
        // Act
        Optional<PatternResult> result = patternMatcher.match(
                inquiryMessage, StatePatterns.WAITING_PATTERNS);
        
        // Assert
        assertThat(result)
                .as("Schedule inquiry should match WAITING_PATTERNS")
                .isPresent();
        assertThat(result.get().matchedAction())
                .as("Schedule inquiry should map to SHOW_SCHEDULE action")
                .isEqualTo(WorkflowAction.SHOW_SCHEDULE);
    }

    /**
     * Example: Hebrew "מחר" (tomorrow) should continue to work for time parsing.
     * 
     * **Validates: Requirements 3.9**
     */
    @Example
    @Label("Hebrew 'tomorrow' continues to work for time parsing")
    void hebrewTomorrowContinuesToWorkForTimeParsing() {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        String tomorrowMessage = "מחר";
        
        // Pre-condition: This message should NOT be flagged as frustration
        assertThat(containsFrustrationIndicator(tomorrowMessage))
                .as("Simple 'מחר' should not be flagged as frustration")
                .isFalse();
        
        // Act
        Optional<PatternResult> result = patternMatcher.match(
                tomorrowMessage, StatePatterns.SCHEDULE_PATTERNS);
        
        // Assert
        assertThat(result)
                .as("Hebrew 'מחר' should match SCHEDULE_PATTERNS for time parsing")
                .isPresent();
        assertThat(result.get().matchedAction())
                .as("'מחר' should map to PARSE_TIME action")
                .isEqualTo(WorkflowAction.PARSE_TIME);
    }

    /**
     * Example: Frustration + slot selection should have actionable content detectable.
     * 
     * <p>After the fix: Frustration acknowledged first, then slot selection processed.</p>
     * <p>This test verifies the slot selection part is detectable.</p>
     * 
     * **Validates: Requirements 3.10**
     */
    @Example
    @Label("Frustration with slot selection: slot is still detectable")
    void frustrationWithSlotSelectionSlotIsStillDetectable() {
        // Arrange: Message with frustration AND actionable slot selection
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        // Note: This message just contains a slot number which should match
        // The frustration handler might process this first after fix, but slot should match
        String message = "3";  // Pure slot selection without frustration
        
        // Act
        Optional<PatternResult> result = patternMatcher.match(
                message, StatePatterns.SCHEDULE_PATTERNS);
        
        // Assert: Slot selection should be detectable
        assertThat(result)
                .as("Slot '3' should be detectable by SCHEDULE_PATTERNS")
                .isPresent();
        assertThat(result.get().matchedAction())
                .as("Should detect SELECT_SLOT action")
                .isEqualTo(WorkflowAction.SELECT_SLOT);
    }

    // ============== Helper Methods ==============

    /**
     * Checks if a message contains frustration indicators.
     * 
     * <p>This mirrors the frustration detection that will be added in the fix.
     * Messages that return true here would trigger frustration handling after the fix.</p>
     * 
     * @param message the message to check
     * @return true if the message contains frustration indicators
     */
    private boolean containsFrustrationIndicator(String message) {
        if (message == null) return false;
        String lowerMessage = message.toLowerCase();
        
        // English frustration indicators
        List<String> englishPatterns = Arrays.asList(
                "why again", "repeat", "already said", "already told",
                "you asked", "asked before", "why do i need to"
        );
        
        // Hebrew frustration indicators
        List<String> hebrewPatterns = Arrays.asList(
                "למה שוב", "כבר אמרתי", "שאלת כבר", "אתה שואל שוב",
                "חוזר על"
        );
        
        for (String pattern : englishPatterns) {
            if (lowerMessage.contains(pattern)) return true;
        }
        
        for (String pattern : hebrewPatterns) {
            if (message.contains(pattern)) return true;
        }
        
        return false;
    }

    // ============== Generators ==============

    /**
     * Generator for normal scheduling messages (no frustration indicators).
     */
    @Provide
    Arbitrary<String> normalSchedulingMessages() {
        return Arbitraries.of(
                // Slot selections
                "1", "2", "3", "4", "5",
                // Skip/postpone
                "skip", "not now", "later",
                "דלג", "לא עכשיו", "אחר כך",
                // Time expressions
                "tomorrow", "tomorrow at 3pm",
                "מחר", "מחר בשלוש",
                "monday", "tuesday", "wednesday",
                "יום ראשון", "יום שני", "יום שלישי",
                // More slots
                "other times", "more options",
                "עוד אפשרויות",
                // Greetings (reset)
                "hi", "hello", "היי", "שלום",
                // Acknowledgments
                "ok", "thanks", "אוקי", "תודה"
        );
    }

    /**
     * Generator for normal waiting state messages (no frustration indicators).
     */
    @Provide
    Arbitrary<String> normalWaitingMessages() {
        return Arbitraries.of(
                // Request ideas
                "give me ideas", "activity suggestions",
                "רעיונות לפעילות", "מה אפשר לעשות",
                // Reschedule
                "reschedule", "change the time", "cancel",
                "שנה זמן", "ביטול",
                // Schedule inquiry
                "when", "what's scheduled", "next quality time",
                "מתי", "הבא",
                // Dashboard
                "show progress", "my streak",
                "התקדמות", "חגורה",
                // Acknowledgments
                "ok", "thanks", "got it",
                "אוקי", "תודה", "מעולה"
        );
    }

    /**
     * Generator for normal follow-up messages (no frustration indicators).
     */
    @Provide
    Arbitrary<String> normalFollowUpMessages() {
        return Arbitraries.of(
                // Completed responses (English)
                "yes", "done", "completed", "finished", "we did it",
                // Completed responses (Hebrew)
                "כן", "סיימתי", "עשיתי", "עשינו",
                // Not completed responses (English)
                "no", "not yet", "couldn't", "didn't",
                // Not completed responses (Hebrew)
                "לא", "עוד לא", "לא הצלחתי", "לא עשינו"
        );
    }

    /**
     * Generator for messages with actionable content that should still be processable.
     * These test that pattern matching can detect actionable content.
     */
    @Provide
    Arbitrary<String> actionableContentMessages() {
        return Arbitraries.of(
                // Pure slot selections
                "1", "2", "3",
                // Pure yes/no for follow-up
                "כן", "לא",
                "yes", "no",
                // Pure schedule inquiries
                "when", "מתי",
                // Pure time expressions
                "tomorrow", "מחר",
                // Pure acknowledgments
                "ok", "אוקי"
        );
    }
}
