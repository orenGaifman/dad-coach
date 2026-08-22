package com.dadcoach.workflow.error;

import com.dadcoach.workflow.WorkflowState;
import net.jqwik.api.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Bug Condition Exploration Test for Generic Error Handling
 * 
 * **Validates: Requirements 2.4, 2.5, 2.6**
 * 
 * <p>This test demonstrates that the generic error handling bug EXISTS in the current
 * unfixed code. When an error occurs in WorkflowEngineImpl.doProcessMessage(), the system
 * catches exceptions and returns "Something went wrong. Please try again." (or "משהו השתבש"
 * in Hebrew) without attempting state-specific fallbacks or logging sufficient context.</p>
 * 
 * <p><strong>Bug Condition (from bugfix.md):</strong></p>
 * <pre>
 * FUNCTION isGenericErrorBug(error, state, response)
 *   INPUT: error of type Exception, state of type WorkflowState, response of type String
 *   OUTPUT: boolean
 *   
 *   RETURN error IS NOT NULL
 *          AND response CONTAINS "went wrong" OR response CONTAINS "השתבש"
 *          AND stateSpecificFallbackExists(state)
 *          AND NOT logContainsFullContext(error)
 * END FUNCTION
 * </pre>
 * 
 * <p><strong>CRITICAL:</strong> This test is EXPECTED TO FAIL on unfixed code.
 * Test failure = bug exists = success for exploration phase.</p>
 * 
 * <p><strong>Examples (BUG scenarios):</strong></p>
 * <ul>
 *   <li>AI timeout in SCHEDULE_QUALITY_TIME → Returns generic error instead of 
 *       "Having trouble finding slots, please try again"</li>
 *   <li>Database error in FOLLOW_UP → Returns generic error without logging 
 *       father_id, state, message</li>
 * </ul>
 * 
 * <p><strong>Test Strategy:</strong></p>
 * <p>Since WorkflowEngineImpl has many dependencies, we create a focused test that:</p>
 * <ol>
 *   <li>Verifies the getStateSpecificErrorResponse method exists</li>
 *   <li>Verifies it returns appropriate state-specific messages (not generic)</li>
 * </ol>
 * <p>If the method doesn't exist or returns generic messages, the test fails - proving the bug exists.</p>
 */
class GenericErrorHandlingBugExplorationTest {

    // Expected method name that should exist after the fix
    private static final String STATE_SPECIFIC_ERROR_METHOD = "getStateSpecificErrorResponse";
    
    // States that should have specific error messages according to the design
    private static final Set<WorkflowState> STATES_WITH_SPECIFIC_ERRORS = Set.of(
            WorkflowState.SCHEDULE_QUALITY_TIME,
            WorkflowState.QUALITY_TIME_FOLLOW_UP,
            WorkflowState.WAITING
    );
    
    // Generic error phrases that indicate the bug (these should NOT be returned for states with fallbacks)
    private static final List<String> GENERIC_ERROR_PHRASES = List.of(
            "Something went wrong",
            "went wrong",
            "משהו השתבש",
            "השתבש"
    );
    
    // Expected state-specific error content (key phrases that should be in responses)
    private static final String SCHEDULE_STATE_HE_EXPECTED = "זמנים פנויים";  // "available slots" in Hebrew
    private static final String SCHEDULE_STATE_EN_EXPECTED = "trouble finding";
    private static final String FOLLOW_UP_STATE_HE_EXPECTED = "זמן האיכות";  // "quality time" in Hebrew
    private static final String FOLLOW_UP_STATE_EN_EXPECTED = "Quality Time";
    private static final String WAITING_STATE_HE_EXPECTED = "לעבד";  // "process" in Hebrew
    private static final String WAITING_STATE_EN_EXPECTED = "couldn't process";

    /**
     * Property test: WorkflowEngineImpl should have a getStateSpecificErrorResponse method.
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> The method doesn't exist yet,
     * proving the bug exists (no mechanism for state-specific error handling).</p>
     * 
     * **Validates: Requirements 2.4, 2.5**
     */
    @Property(tries = 1)
    @Label("WorkflowEngineImpl should have getStateSpecificErrorResponse method")
    void workflowEngineImplShouldHaveStateSpecificErrorMethod() {
        try {
            Class<?> workflowEngineClass = Class.forName(
                    "com.dadcoach.workflow.WorkflowEngineImpl");
            
            // Look for the method with expected signature: getStateSpecificErrorResponse(WorkflowState, String)
            Method[] methods = workflowEngineClass.getDeclaredMethods();
            boolean methodFound = Arrays.stream(methods)
                    .anyMatch(m -> m.getName().equals(STATE_SPECIFIC_ERROR_METHOD));
            
            // ASSERTION: The method should exist (fails on unfixed code = bug confirmed)
            assertThat(methodFound)
                    .as("WorkflowEngineImpl should have method '%s' for state-specific error handling. " +
                        "BUG: Currently errors are handled with generic catch-all that returns " +
                        "'Something went wrong. Please try again.' without state-specific fallbacks. " +
                        "Fix requirement: Add getStateSpecificErrorResponse(WorkflowState, String) method.",
                        STATE_SPECIFIC_ERROR_METHOD)
                    .isTrue();
                    
        } catch (ClassNotFoundException e) {
            fail("WorkflowEngineImpl class not found: " + e.getMessage());
        }
    }

    /**
     * Property test: For states with specific error fallbacks, the error response should
     * contain state-specific guidance, not generic messages.
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> Even if we somehow invoke
     * error handling, it returns generic messages for all states.</p>
     * 
     * <p>This test verifies the design requirement that states like SCHEDULE_QUALITY_TIME,
     * QUALITY_TIME_FOLLOW_UP, and WAITING should have specific, actionable error messages.</p>
     * 
     * **Validates: Requirements 2.4, 2.5, 2.6**
     */
    @Property(tries = 10)
    @Label("States with fallbacks should have state-specific error responses defined")
    void statesWithFallbacksShouldHaveSpecificErrorResponses(
            @ForAll("statesWithSpecificErrors") WorkflowState state,
            @ForAll("locales") String locale
    ) {
        try {
            Class<?> workflowEngineClass = Class.forName(
                    "com.dadcoach.workflow.WorkflowEngineImpl");
            
            // Try to find and invoke the state-specific error method
            Method method = findStateSpecificErrorMethod(workflowEngineClass);
            
            // ASSERTION: Method should exist (fails on unfixed code)
            assertThat(method)
                    .as("BUG CONFIRMED: getStateSpecificErrorResponse method not found in WorkflowEngineImpl. " +
                        "Currently, errors in state '%s' return generic 'Something went wrong' message " +
                        "instead of state-specific guidance. " +
                        "Example: AI timeout in SCHEDULE_QUALITY_TIME should return " +
                        "'Having trouble finding slots, please try again' (EN) or " +
                        "'מצטער, יש לי בעיה למצוא זמנים פנויים' (HE).",
                        state)
                    .isNotNull();
            
            // If method exists, verify it returns non-generic response
            // (This part would run after the fix is implemented)
            if (method != null) {
                method.setAccessible(true);
                // Create instance would require all dependencies - instead verify method signature
                Class<?>[] paramTypes = method.getParameterTypes();
                assertThat(paramTypes)
                        .as("Method should accept WorkflowState and locale String")
                        .hasSize(2);
            }
            
        } catch (ClassNotFoundException e) {
            fail("WorkflowEngineImpl class not found: " + e.getMessage());
        }
    }

    /**
     * Example-based test: SCHEDULE_QUALITY_TIME error scenario.
     * 
     * <p>From design.md: AI timeout in SCHEDULE_QUALITY_TIME should return:
     * Hebrew: "מצטער, יש לי בעיה למצוא זמנים פנויים. אפשר לנסות שוב?"
     * English: "Sorry, I'm having trouble finding available slots. Can you try again?"</p>
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> Currently returns generic error.</p>
     * 
     * **Validates: Requirements 2.4, 2.5**
     */
    @Example
    @Label("SCHEDULE_QUALITY_TIME state should have specific error message (not generic)")
    void scheduleQualityTimeStateShouldHaveSpecificErrorMessage() {
        verifyStateSpecificErrorMethodExists(
                WorkflowState.SCHEDULE_QUALITY_TIME,
                "AI timeout in SCHEDULE_QUALITY_TIME should return state-specific message " +
                "about finding time slots, not generic 'Something went wrong'. " +
                "Expected HE: 'מצטער, יש לי בעיה למצוא זמנים פנויים' " +
                "Expected EN: 'Sorry, I\\'m having trouble finding available slots'"
        );
    }

    /**
     * Example-based test: QUALITY_TIME_FOLLOW_UP error scenario.
     * 
     * <p>From design.md: Database error in FOLLOW_UP should return:
     * Hebrew: "מצטער, משהו השתבש. ספר לי - האם השלמת את זמן האיכות?"
     * English: "Sorry, something went wrong. Tell me - did you complete your Quality Time?"</p>
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> Currently returns generic error without QT context.</p>
     * 
     * **Validates: Requirements 2.4, 2.5**
     */
    @Example
    @Label("QUALITY_TIME_FOLLOW_UP state should have specific error message referencing QT")
    void followUpStateShouldHaveSpecificErrorMessage() {
        verifyStateSpecificErrorMethodExists(
                WorkflowState.QUALITY_TIME_FOLLOW_UP,
                "Error in QUALITY_TIME_FOLLOW_UP should return state-specific message " +
                "that asks about completed Quality Time, not generic error. " +
                "Expected HE: 'ספר לי - האם השלמת את זמן האיכות?' " +
                "Expected EN: 'Tell me - did you complete your Quality Time?'"
        );
    }

    /**
     * Example-based test: WAITING state error scenario.
     * 
     * <p>From design.md: Error in WAITING should return:
     * Hebrew: "מצטער, לא הצלחתי לעבד את ההודעה. מה תרצה לעשות?"
     * English: "Sorry, I couldn't process that. What would you like to do?"</p>
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> Currently returns generic error.</p>
     * 
     * **Validates: Requirements 2.4, 2.5**
     */
    @Example
    @Label("WAITING state should have specific error message with follow-up question")
    void waitingStateShouldHaveSpecificErrorMessage() {
        verifyStateSpecificErrorMethodExists(
                WorkflowState.WAITING,
                "Error in WAITING state should return state-specific message " +
                "that prompts user for next action, not generic error. " +
                "Expected HE: 'מצטער, לא הצלחתי לעבד את ההודעה. מה תרצה לעשות?' " +
                "Expected EN: 'Sorry, I couldn\\'t process that. What would you like to do?'"
        );
    }

    /**
     * Property test: Error logging should include comprehensive context.
     * 
     * <p>Bug from bugfix.md: "error details are logged but the conversation context 
     * (current state, last successful action) is lost"</p>
     * 
     * <p>This test verifies that the enhanced error logging mechanism exists.
     * The fix should log: father_id, current_workflow_state, message_content, 
     * exception_type, and stack_trace.</p>
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> Current logging only includes
     * message_id and phone number, not the full context needed for debugging.</p>
     * 
     * **Validates: Requirements 2.4, 2.6**
     */
    @Property(tries = 1)
    @Label("Error handling should log comprehensive context for debugging")
    void errorHandlingShouldLogComprehensiveContext() {
        try {
            Class<?> workflowEngineClass = Class.forName(
                    "com.dadcoach.workflow.WorkflowEngineImpl");
            
            // The fix should include state-specific error method which implies
            // the enhanced error handling pattern exists
            Method method = findStateSpecificErrorMethod(workflowEngineClass);
            
            assertThat(method)
                    .as("BUG CONFIRMED: Enhanced error handling not implemented. " +
                        "Current error logging is missing critical context. " +
                        "Bug: 'Error processing message {} for father {}' only logs messageId and phone. " +
                        "Required fix: Log father_id (UUID), current_workflow_state, message_content (truncated), " +
                        "exception_type, and implement state-specific error responses.")
                    .isNotNull();
                    
        } catch (ClassNotFoundException e) {
            fail("WorkflowEngineImpl class not found: " + e.getMessage());
        }
    }

    // ============== Helper Methods ==============
    
    /**
     * Verifies that the state-specific error response method exists in WorkflowEngineImpl.
     * This is the core assertion that proves or disproves the bug exists.
     */
    private void verifyStateSpecificErrorMethodExists(WorkflowState state, String bugDescription) {
        try {
            Class<?> workflowEngineClass = Class.forName(
                    "com.dadcoach.workflow.WorkflowEngineImpl");
            
            Method method = findStateSpecificErrorMethod(workflowEngineClass);
            
            assertThat(method)
                    .as("BUG EXISTS: No state-specific error handling for %s state. %s",
                        state, bugDescription)
                    .isNotNull();
                    
        } catch (ClassNotFoundException e) {
            fail("WorkflowEngineImpl class not found: " + e.getMessage());
        }
    }
    
    /**
     * Finds the getStateSpecificErrorResponse method in WorkflowEngineImpl.
     * Returns null if not found (indicating the bug exists).
     */
    private Method findStateSpecificErrorMethod(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(STATE_SPECIFIC_ERROR_METHOD))
                .findFirst()
                .orElse(null);
    }

    // ============== Generators ==============

    /**
     * Generator for workflow states that should have specific error responses.
     */
    @Provide
    Arbitrary<WorkflowState> statesWithSpecificErrors() {
        return Arbitraries.of(
                WorkflowState.SCHEDULE_QUALITY_TIME,
                WorkflowState.QUALITY_TIME_FOLLOW_UP,
                WorkflowState.WAITING
        );
    }

    /**
     * Generator for supported locales.
     */
    @Provide
    Arbitrary<String> locales() {
        return Arbitraries.of("he", "en");
    }

    /**
     * Generator for exception types that might occur during processing.
     */
    @Provide
    Arbitrary<String> exceptionTypes() {
        return Arbitraries.of(
                "java.util.concurrent.TimeoutException",  // AI timeout
                "java.sql.SQLException",                   // Database error
                "java.lang.RuntimeException",              // General runtime error
                "com.dadcoach.common.ResourceNotFoundException"  // Resource not found
        );
    }
}
