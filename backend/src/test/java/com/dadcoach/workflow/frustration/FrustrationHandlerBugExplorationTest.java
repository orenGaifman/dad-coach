package com.dadcoach.workflow.frustration;

import com.dadcoach.workflow.pattern.PatternMatcherImpl;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;
import net.jqwik.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for Missing Frustration Handler
 * 
 * **Validates: Requirements 1.13, 1.14, 1.15**
 * 
 * <p>This test VERIFIES that the missing frustration handler bug EXISTS by demonstrating
 * that frustration messages do NOT receive empathetic responses.</p>
 * 
 * <p>The bug manifests when users express frustration (e.g., "why do I need to repeat myself",
 * "כבר אמרתי לך") and the bot ignores the emotional content, responding with generic
 * clarification or continuing with standard flow without empathy.</p>
 * 
 * <p><strong>Bug Condition Formal Spec:</strong></p>
 * <pre>
 * FUNCTION isFrustrationHandlerBug(message, response)
 *   frustrationPatterns ← ["why again", "repeat", "already said", "למה שוב", "כבר אמרתי"]
 *   empathyPhrases ← ["sorry", "understand", "apologize", "מצטער", "מבין", "סליחה"]
 *   
 *   RETURN containsAny(message, frustrationPatterns)
 *          AND NOT containsAny(response, empathyPhrases)
 * END FUNCTION
 * </pre>
 * 
 * <p><strong>EXPECTED BEHAVIOR (with bug - test should FAIL):</strong></p>
 * <ul>
 *   <li>Test FAILS - demonstrating the bug exists</li>
 *   <li>Frustration messages do not trigger any frustration-specific action</li>
 *   <li>No frustration patterns exist in StatePatterns</li>
 *   <li>No ACKNOWLEDGE_FRUSTRATION action exists in WorkflowAction</li>
 * </ul>
 * 
 * <p><strong>Examples from production (bug behavior):</strong></p>
 * <ul>
 *   <li>"למה אתה שואל שוב?" → Bot responds with standard clarification without empathy (BUG)</li>
 *   <li>"כבר אמרתי לך שכן" → Bot ignores frustration indicator (BUG)</li>
 *   <li>"why are you asking again" → Bot continues standard flow (BUG)</li>
 * </ul>
 * 
 * <p><strong>CRITICAL:</strong> This is an exploration test that MUST FAIL on unfixed code.
 * Failure confirms the bug exists. DO NOT attempt to fix the test or code when it fails.</p>
 */
class FrustrationHandlerBugExplorationTest {

    // Frustration patterns that SHOULD trigger empathetic response
    private static final List<String> FRUSTRATION_KEYWORDS = Arrays.asList(
            "why again", "repeat", "already said", "already told",
            "למה שוב", "כבר אמרתי", "שאלת כבר", "אתה שואל שוב"
    );

    // Empathy phrases that SHOULD be in response to frustrated users
    private static final List<String> EMPATHY_PHRASES = Arrays.asList(
            "sorry", "understand", "apologize",
            "מצטער", "מבין", "סליחה"
    );

    /**
     * Property test: Frustration messages should be specifically handled with empathy.
     * 
     * <p><strong>BUG EXPLORATION:</strong> This test demonstrates that frustration patterns
     * do NOT match any pattern that triggers empathy-based handling, confirming the bug exists.</p>
     * 
     * <p>The test checks that when a frustration message is sent, it either:</p>
     * <ul>
     *   <li>Matches a frustration-specific pattern (expected fix behavior)</li>
     *   <li>OR matches an existing pattern but with frustration-aware action</li>
     * </ul>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS - no frustration-specific handling exists</p>
     * 
     * **Validates: Requirements 1.13, 1.14, 1.15**
     */
    @Property(tries = 100)
    @Label("Frustration messages should trigger frustration-specific handling")
    void frustrationMessagesShouldTriggerFrustrationSpecificHandling(
            @ForAll("englishFrustrationMessages") String frustrationMessage
    ) {
        // Arrange: Create a pattern matcher
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        
        // Act: Check if frustration-specific patterns exist in StatePatterns
        // If the fix is implemented, there should be FRUSTRATION_PATTERNS in StatePatterns
        boolean frustrationPatternsExist = hasFrustrationPatterns();
        
        // Also check if any existing pattern matches the frustration message with
        // frustration-aware handling
        boolean frustrationHandlingExists = false;
        String matchedPatternInfo = "NO_MATCH";
        
        // Check all state patterns
        List<List<StatePattern>> allPatterns = Arrays.asList(
                StatePatterns.WELCOME_PATTERNS,
                StatePatterns.SCHEDULE_PATTERNS,
                StatePatterns.WAITING_PATTERNS,
                StatePatterns.FOLLOW_UP_PATTERNS,
                StatePatterns.ACTIVITY_IDEAS_PATTERNS
        );
        
        for (List<StatePattern> patterns : allPatterns) {
            Optional<PatternResult> result = patternMatcher.match(frustrationMessage, patterns);
            if (result.isPresent() && result.get().isMatched()) {
                matchedPatternInfo = result.get().patternName() + " -> " + result.get().matchedAction();
                // Check if this action is frustration-related (would need ACKNOWLEDGE_FRUSTRATION)
                if (isFrustrationAwareAction(result.get().matchedAction())) {
                    frustrationHandlingExists = true;
                    break;
                }
            }
        }
        
        // ASSERT: Frustration handling SHOULD exist (test fails if bug exists)
        // 
        // With the bug present:
        // - No FRUSTRATION_PATTERNS list exists in StatePatterns
        // - No ACKNOWLEDGE_FRUSTRATION action exists in WorkflowAction
        // - Test fails because frustrationPatternsExist is false AND frustrationHandlingExists is false
        assertThat(frustrationPatternsExist || frustrationHandlingExists)
                .as("Frustration message '%s' should trigger frustration-specific handling. " +
                    "Currently: FRUSTRATION_PATTERNS exist in StatePatterns? %s, " +
                    "Message matched: %s. " +
                    "This confirms Bug 5 - Missing Frustration Handler exists.",
                    frustrationMessage, frustrationPatternsExist, matchedPatternInfo)
                .isTrue();
    }

    /**
     * Property test: Hebrew frustration messages should also trigger specific handling.
     * 
     * <p><strong>BUG EXPLORATION:</strong> This test demonstrates that Hebrew frustration
     * patterns like "כבר אמרתי" and "למה שוב" do not trigger empathetic responses.</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS - no Hebrew frustration handling exists</p>
     * 
     * **Validates: Requirements 1.13, 1.15**
     */
    @Property(tries = 100)
    @Label("Hebrew frustration messages should trigger frustration-specific handling")
    void hebrewFrustrationMessagesShouldTriggerSpecificHandling(
            @ForAll("hebrewFrustrationMessages") String frustrationMessage
    ) {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        
        // Act: Check if FRUSTRATION_PATTERNS exist in StatePatterns
        boolean frustrationPatternsExist = hasFrustrationPatterns();
        
        // Check what action Hebrew frustration messages currently trigger
        String matchedPatternInfo = "NO_MATCH";
        boolean frustrationHandlingExists = false;
        
        List<List<StatePattern>> allPatterns = Arrays.asList(
                StatePatterns.WELCOME_PATTERNS,
                StatePatterns.SCHEDULE_PATTERNS,
                StatePatterns.WAITING_PATTERNS,
                StatePatterns.FOLLOW_UP_PATTERNS,
                StatePatterns.ACTIVITY_IDEAS_PATTERNS
        );
        
        for (List<StatePattern> patterns : allPatterns) {
            Optional<PatternResult> result = patternMatcher.match(frustrationMessage, patterns);
            if (result.isPresent() && result.get().isMatched()) {
                matchedPatternInfo = result.get().patternName() + " -> " + result.get().matchedAction();
                if (isFrustrationAwareAction(result.get().matchedAction())) {
                    frustrationHandlingExists = true;
                    break;
                }
            }
        }
        
        // ASSERT: Hebrew frustration handling SHOULD exist (test fails if bug exists)
        assertThat(frustrationPatternsExist || frustrationHandlingExists)
                .as("Hebrew frustration message '%s' should trigger frustration-specific handling. " +
                    "Currently: FRUSTRATION_PATTERNS exist? %s, Message matched: %s. " +
                    "This confirms Bug 5 - no Hebrew frustration patterns exist.",
                    frustrationMessage, frustrationPatternsExist, matchedPatternInfo)
                .isTrue();
    }

    /**
     * Example-based test demonstrating the exact production scenario with Hebrew "already said".
     * 
     * <p>From bug report: "כבר אמרתי לך שכן" → Bot ignores frustration indicator</p>
     * <p>With the bug: The pattern is not detected and no empathy is shown.</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS (confirming bug exists)</p>
     * 
     * **Validates: Requirements 1.13, 1.15**
     */
    @Example
    @Label("Production bug scenario: Hebrew 'כבר אמרתי' should be detected as frustration")
    void productionBugScenarioHebrewAlreadySaid() {
        // Arrange - exact scenario from bug report
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        String frustrationMessage = "כבר אמרתי לך שכן";
        
        // Act: Check if FRUSTRATION_PATTERNS exist
        boolean frustrationPatternsExist = hasFrustrationPatterns();
        
        // Check what this message currently matches
        String matchedPatternInfo = "NO_MATCH";
        boolean isFrustrationHandled = false;
        
        List<List<StatePattern>> allPatterns = Arrays.asList(
                StatePatterns.WELCOME_PATTERNS,
                StatePatterns.SCHEDULE_PATTERNS,
                StatePatterns.WAITING_PATTERNS,
                StatePatterns.FOLLOW_UP_PATTERNS,
                StatePatterns.ACTIVITY_IDEAS_PATTERNS
        );
        
        for (List<StatePattern> patterns : allPatterns) {
            Optional<PatternResult> result = patternMatcher.match(frustrationMessage, patterns);
            if (result.isPresent() && result.get().isMatched()) {
                matchedPatternInfo = result.get().patternName() + " -> " + result.get().matchedAction();
                if (isFrustrationAwareAction(result.get().matchedAction())) {
                    isFrustrationHandled = true;
                    break;
                }
            }
        }
        
        // ASSERT: Production bug - frustration should be detected but ISN'T
        assertThat(frustrationPatternsExist || isFrustrationHandled)
                .as("Production scenario - 'כבר אמרתי לך שכן' (I already told you yes) " +
                    "should be detected as frustration and trigger empathetic handling. " +
                    "Currently: FRUSTRATION_PATTERNS exist? %s, Message matched: %s. " +
                    "This confirms the Missing Frustration Handler bug exists.",
                    frustrationPatternsExist, matchedPatternInfo)
                .isTrue();
    }

    /**
     * Example-based test demonstrating English "why are you asking again" scenario.
     * 
     * <p>From bug report: User asks "why are you asking again" → Bot continues standard flow</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS (confirming bug exists)</p>
     * 
     * **Validates: Requirements 1.13, 1.14**
     */
    @Example
    @Label("Production bug scenario: English 'why are you asking again' should be detected as frustration")
    void productionBugScenarioEnglishWhyAskingAgain() {
        // Arrange
        PatternMatcherImpl patternMatcher = new PatternMatcherImpl();
        String frustrationMessage = "why are you asking again";
        
        // Act: Check if FRUSTRATION_PATTERNS exist and what current patterns match
        boolean frustrationPatternsExist = hasFrustrationPatterns();
        
        String matchedPatternInfo = "NO_MATCH";
        boolean isFrustrationHandled = false;
        
        // Check SCHEDULE_QUALITY_TIME patterns first (common state where this occurs)
        Optional<PatternResult> scheduleResult = patternMatcher.match(
                frustrationMessage, StatePatterns.SCHEDULE_PATTERNS);
        if (scheduleResult.isPresent() && scheduleResult.get().isMatched()) {
            matchedPatternInfo = scheduleResult.get().patternName() + " -> " + scheduleResult.get().matchedAction();
            if (isFrustrationAwareAction(scheduleResult.get().matchedAction())) {
                isFrustrationHandled = true;
            }
        }
        
        // Check other states if not found
        if (!isFrustrationHandled) {
            for (List<StatePattern> patterns : Arrays.asList(
                    StatePatterns.WELCOME_PATTERNS,
                    StatePatterns.WAITING_PATTERNS,
                    StatePatterns.FOLLOW_UP_PATTERNS
            )) {
                Optional<PatternResult> result = patternMatcher.match(frustrationMessage, patterns);
                if (result.isPresent() && result.get().isMatched()) {
                    matchedPatternInfo = result.get().patternName() + " -> " + result.get().matchedAction();
                    if (isFrustrationAwareAction(result.get().matchedAction())) {
                        isFrustrationHandled = true;
                        break;
                    }
                }
            }
        }
        
        // ASSERT: English frustration should be detected but ISN'T
        assertThat(frustrationPatternsExist || isFrustrationHandled)
                .as("English frustration message 'why are you asking again' should be detected " +
                    "and trigger frustration-specific handling. " +
                    "Currently: FRUSTRATION_PATTERNS exist? %s, Message matched: %s. " +
                    "This confirms the Missing Frustration Handler bug exists.",
                    frustrationPatternsExist, matchedPatternInfo)
                .isTrue();
    }

    /**
     * Example test: Verify that ACKNOWLEDGE_FRUSTRATION action does not exist in WorkflowAction.
     * 
     * <p>This confirms part of the bug - the action enum is missing the frustration handling action.</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS (action doesn't exist = bug exists)</p>
     * 
     * **Validates: Requirements 1.13**
     */
    @Example
    @Label("WorkflowAction should have frustration-related enum value")
    void workflowActionShouldHaveFrustrationHandlingValue() {
        // Act: Check if any frustration-related action exists in WorkflowAction
        boolean hasFrustrationAction = false;
        
        for (WorkflowAction action : WorkflowAction.values()) {
            String actionName = action.name().toLowerCase();
            if (actionName.contains("frustrat") || 
                actionName.contains("empathy") || 
                actionName.contains("acknowledge_frustration")) {
                hasFrustrationAction = true;
                break;
            }
        }
        
        // ASSERT: Frustration action should exist but doesn't (bug condition)
        assertThat(hasFrustrationAction)
                .as("WorkflowAction enum should contain a frustration-handling action " +
                    "(e.g., ACKNOWLEDGE_FRUSTRATION) for handling frustrated users. " +
                    "Currently available actions: %s. " +
                    "This confirms Bug 5 - no frustration handling mechanism exists.",
                    Arrays.toString(WorkflowAction.values()))
                .isTrue();
    }

    /**
     * Example test: Verify that StatePatterns does not have FRUSTRATION_PATTERNS field.
     * 
     * <p>This confirms the structural absence of frustration pattern definitions.</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS (no frustration patterns = bug exists)</p>
     * 
     * **Validates: Requirements 1.13**
     */
    @Example
    @Label("StatePatterns should have FRUSTRATION_PATTERNS list")
    void statePatternsShoudHaveFrustrationPatternsList() {
        // Act: Check if FRUSTRATION_PATTERNS field exists in StatePatterns
        boolean hasFrustrationPatterns = hasFrustrationPatterns();
        
        // ASSERT: FRUSTRATION_PATTERNS should exist but doesn't
        assertThat(hasFrustrationPatterns)
                .as("StatePatterns class should define a FRUSTRATION_PATTERNS list " +
                    "containing patterns like 'why again', 'כבר אמרתי', etc. " +
                    "This confirms Bug 5 - no frustration patterns are defined.")
                .isTrue();
    }

    // ============== Helper Methods ==============

    /**
     * Checks if FRUSTRATION_PATTERNS field exists in StatePatterns class via reflection.
     * 
     * @return true if FRUSTRATION_PATTERNS field exists, false otherwise
     */
    private boolean hasFrustrationPatterns() {
        try {
            StatePatterns.class.getField("FRUSTRATION_PATTERNS");
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Checks if the given action is a frustration-aware action.
     * 
     * @param action the WorkflowAction to check
     * @return true if this action handles frustration with empathy
     */
    private boolean isFrustrationAwareAction(WorkflowAction action) {
        if (action == null) return false;
        String actionName = action.name().toLowerCase();
        return actionName.contains("frustrat") || 
               actionName.contains("empathy") ||
               actionName.contains("acknowledge_frustration");
    }

    // ============== Generators ==============

    /**
     * Generator for English frustration messages.
     */
    @Provide
    Arbitrary<String> englishFrustrationMessages() {
        return Arbitraries.of(
                // Direct frustration expressions
                "why are you asking again",
                "I already told you",
                "why do I need to repeat myself",
                "you asked this before",
                "I already said yes",
                "didn't I already answer this",
                "why again",
                "you keep asking the same thing",
                "I repeat myself constantly",
                "already answered that"
        );
    }

    /**
     * Generator for Hebrew frustration messages.
     */
    @Provide
    Arbitrary<String> hebrewFrustrationMessages() {
        return Arbitraries.of(
                // Hebrew frustration expressions
                "כבר אמרתי לך שכן",       // I already told you yes
                "למה אתה שואל שוב",       // why are you asking again
                "שאלת את זה כבר",         // you already asked this
                "כבר אמרתי",              // I already said
                "למה שוב",                // why again
                "אתה שואל שוב ושוב",      // you ask again and again
                "חוזר על אותו דבר",        // repeating the same thing
                "כבר ענינו על זה"         // we already answered this
        );
    }

    /**
     * Generator for mixed frustration messages (both languages with context).
     */
    @Provide
    Arbitrary<String> mixedFrustrationMessages() {
        return Arbitraries.oneOf(
                englishFrustrationMessages(),
                hebrewFrustrationMessages()
        );
    }
}
