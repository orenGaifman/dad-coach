package com.dadcoach.ai.agent;

import com.dadcoach.workflow.WorkflowState;

import java.util.Map;

/**
 * Result of an AI agent tool invocation.
 * 
 * <p>Contains the response message to send to the user, any state transition
 * that should occur, and extracted parameters from the tool call.</p>
 * 
 * @param toolName the name of the tool that was invoked
 * @param responseMessage the message to send back to the user
 * @param newState the workflow state to transition to (null if no change)
 * @param parameters the parameters extracted by the AI for this tool call
 * @param success whether the tool execution was successful
 * @param errorMessage error message if execution failed
 */
public record AgentToolResult(
    String toolName,
    String responseMessage,
    WorkflowState newState,
    Map<String, Object> parameters,
    boolean success,
    String errorMessage
) {
    
    /**
     * Create a successful tool result with a state transition.
     */
    public static AgentToolResult success(String toolName, String responseMessage, 
                                          WorkflowState newState, Map<String, Object> parameters) {
        return new AgentToolResult(toolName, responseMessage, newState, parameters, true, null);
    }
    
    /**
     * Create a successful tool result without state transition.
     */
    public static AgentToolResult success(String toolName, String responseMessage, Map<String, Object> parameters) {
        return new AgentToolResult(toolName, responseMessage, null, parameters, true, null);
    }
    
    /**
     * Create a successful tool result with just a message.
     */
    public static AgentToolResult success(String toolName, String responseMessage) {
        return new AgentToolResult(toolName, responseMessage, null, Map.of(), true, null);
    }
    
    /**
     * Create a failed tool result.
     */
    public static AgentToolResult failure(String toolName, String errorMessage) {
        return new AgentToolResult(toolName, null, null, Map.of(), false, errorMessage);
    }
    
    /**
     * Check if this result has a state transition.
     */
    public boolean hasStateTransition() {
        return newState != null;
    }
}
