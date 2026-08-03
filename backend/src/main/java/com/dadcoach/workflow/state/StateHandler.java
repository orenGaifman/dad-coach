package com.dadcoach.workflow.state;

import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;

import java.util.List;

/**
 * Handler for state-specific behavior. Each workflow state has a dedicated handler.
 * 
 * <p>The StateHandler defines how a specific workflow state processes incoming messages
 * and determines the appropriate actions to take. Each state in the workflow state machine
 * (WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP, ACTIVITY_IDEAS, DASHBOARD)
 * has a corresponding handler implementation.</p>
 * 
 * <p>The handler is responsible for:</p>
 * <ul>
 *   <li>Defining the expected message patterns for its state</li>
 *   <li>Processing matched patterns and determining state transitions/responses</li>
 *   <li>Handling unmatched messages with clarification responses</li>
 * </ul>
 * 
 * <p>This interface is part of the deterministic workflow engine that replaces AI-driven
 * conversation orchestration with explicit pattern matching and state transitions.</p>
 * 
 * <p>Implements Requirements 1.1 (Workflow State Machine) and 11.4 (Unmatched Message Handling)
 * from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowState
 * @see StatePattern
 * @see PatternResult
 * @see StateAction
 * @see WorkflowContext
 */
public interface StateHandler {

    /**
     * Returns the workflow state this handler manages.
     * 
     * <p>Each handler is associated with exactly one {@link WorkflowState}. The workflow
     * engine uses this method to select the appropriate handler for the father's current state.</p>
     * 
     * @return the workflow state this handler manages
     */
    WorkflowState getState();

    /**
     * Get the expected message patterns for this state.
     * 
     * <p>Returns a list of {@link StatePattern} objects that define the valid user inputs
     * for this state. The patterns are evaluated in order by the PatternMatcher, with the
     * first matching pattern determining the action to take.</p>
     * 
     * <p>Patterns support both English and Hebrew for bilingual message handling. Each pattern
     * specifies a regex pattern and the corresponding {@link com.dadcoach.workflow.pattern.WorkflowAction}
     * to execute when matched.</p>
     * 
     * @return the list of expected patterns for this state, in evaluation order
     */
    List<StatePattern> getExpectedPatterns();

    /**
     * Handle a matched pattern within this state.
     * 
     * <p>This method is called when the PatternMatcher successfully matches the user's
     * message against one of the expected patterns for this state. The method processes
     * the match result and returns a {@link StateAction} that specifies:</p>
     * <ul>
     *   <li>The next workflow state (if a transition should occur)</li>
     *   <li>The response message to send to the user</li>
     *   <li>Any entity updates to persist (e.g., Quality Time records, Father fields)</li>
     * </ul>
     * 
     * <p>The business logic executed here is deterministic - same inputs produce same
     * outputs regardless of AI behavior. AI is only used for generating natural language
     * messages, not for decision-making.</p>
     * 
     * @param context the workflow context containing system state, father info, and inbound message
     * @param match the pattern match result with the matched pattern name, action, and captured groups
     * @return the state action to take (transition, respond, etc.)
     * @throws IllegalArgumentException if context or match is null
     * @see WorkflowContext
     * @see PatternResult
     * @see StateAction
     */
    StateAction handle(WorkflowContext context, PatternResult match);

    /**
     * Handle an unmatched message (no pattern matched).
     * 
     * <p>This method is called when the user's message does not match any of the expected
     * patterns for this state. Per Requirement 11.4, the system remains in the current
     * state and requests clarification from the user using explicit options.</p>
     * 
     * <p>The clarification message should:</p>
     * <ul>
     *   <li>Be specific to the current state's context</li>
     *   <li>Include explicit valid options the user can choose from</li>
     *   <li>Not use AI to interpret the unmatched message</li>
     * </ul>
     * 
     * @param context the workflow context containing system state, father info, and the unmatched message
     * @return a clarification response with explicit options for valid responses
     * @throws IllegalArgumentException if context is null
     * @see WorkflowContext
     * @see StateAction
     */
    StateAction handleUnmatched(WorkflowContext context);
}
