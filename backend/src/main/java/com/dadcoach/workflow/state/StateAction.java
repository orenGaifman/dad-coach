package com.dadcoach.workflow.state;

import com.dadcoach.workflow.WorkflowState;

import java.util.Optional;

/**
 * Represents the action to take after processing a message in a workflow state.
 * 
 * <p>A StateAction encapsulates the result of a StateHandler processing a message,
 * including:</p>
 * <ul>
 *   <li><b>Action type</b>: The type of action to perform (transition, respond, etc.)</li>
 *   <li><b>Next state</b>: The workflow state to transition to (if applicable)</li>
 *   <li><b>Response message</b>: The message to send back to the user</li>
 *   <li><b>Updated entities</b>: Any entities that need to be persisted</li>
 * </ul>
 * 
 * <p>This is a placeholder stub that will be fully implemented in task 9.2.
 * The full implementation will include additional fields for entity updates
 * and action metadata.</p>
 * 
 * <p>Implements Requirement 1.3 (State Transitions) from the deterministic-workflow-engine spec.</p>
 * 
 * @see StateHandler
 * @see WorkflowContext
 */
public class StateAction {
    
    /**
     * Types of actions that can be taken.
     */
    public enum ActionType {
        /** Transition to a new workflow state */
        TRANSITION,
        /** Respond without changing state */
        RESPOND,
        /** Send clarification and remain in current state */
        CLARIFY,
        /** No action required */
        NONE
    }
    
    private final ActionType actionType;
    private final WorkflowState nextState;
    private final String responseMessage;
    
    /**
     * Creates a new state action.
     * 
     * @param actionType the type of action to take
     * @param nextState the next workflow state (null if no transition)
     * @param responseMessage the message to send to the user
     */
    public StateAction(ActionType actionType, WorkflowState nextState, String responseMessage) {
        this.actionType = actionType;
        this.nextState = nextState;
        this.responseMessage = responseMessage;
    }
    
    /**
     * Creates a transition action to a new state.
     * 
     * @param nextState the state to transition to
     * @param responseMessage the message to send
     * @return a new StateAction for transitioning
     */
    public static StateAction transition(WorkflowState nextState, String responseMessage) {
        return new StateAction(ActionType.TRANSITION, nextState, responseMessage);
    }
    
    /**
     * Creates a respond action that stays in the current state.
     * 
     * @param responseMessage the message to send
     * @return a new StateAction for responding
     */
    public static StateAction respond(String responseMessage) {
        return new StateAction(ActionType.RESPOND, null, responseMessage);
    }
    
    /**
     * Creates a clarification action for unmatched messages.
     * 
     * @param clarificationMessage the clarification message with explicit options
     * @return a new StateAction for clarification
     */
    public static StateAction clarify(String clarificationMessage) {
        return new StateAction(ActionType.CLARIFY, null, clarificationMessage);
    }
    
    /**
     * Creates a no-action response.
     * 
     * @return a new StateAction indicating no action needed
     */
    public static StateAction none() {
        return new StateAction(ActionType.NONE, null, null);
    }
    
    /**
     * Returns the action type.
     * 
     * @return the type of action to take
     */
    public ActionType getActionType() {
        return actionType;
    }
    
    /**
     * Returns the next workflow state if this is a transition action.
     * 
     * @return Optional containing the next state, or empty if not a transition
     */
    public Optional<WorkflowState> getNextState() {
        return Optional.ofNullable(nextState);
    }
    
    /**
     * Returns the response message.
     * 
     * @return Optional containing the response message, or empty if none
     */
    public Optional<String> getResponseMessage() {
        return Optional.ofNullable(responseMessage);
    }
    
    /**
     * Checks if this action includes a state transition.
     * 
     * @return true if this is a transition action
     */
    public boolean isTransition() {
        return actionType == ActionType.TRANSITION && nextState != null;
    }
    
    /**
     * Checks if this action has a response message.
     * 
     * @return true if there is a response message
     */
    public boolean hasResponse() {
        return responseMessage != null && !responseMessage.isEmpty();
    }
}
