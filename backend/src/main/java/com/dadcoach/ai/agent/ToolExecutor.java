package com.dadcoach.ai.agent;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for executing AI agent tools.
 * 
 * <p>Implementations of this interface handle the actual execution of tools
 * selected by the AI agent, coordinating with various services like
 * QualityTimeService, MissionService, etc.</p>
 */
public interface ToolExecutor {
    
    /**
     * Execute a tool with the given parameters.
     * 
     * @param toolName the name of the tool to execute
     * @param parameters the parameters extracted by the AI
     * @param context the agent context for additional information
     * @return the result of the tool execution
     */
    AgentToolResult execute(String toolName, Map<String, Object> parameters, AgentContext context);
    
    /**
     * Check if this executor can handle the given tool.
     * 
     * @param toolName the name of the tool
     * @return true if this executor can handle the tool
     */
    boolean canExecute(String toolName);
}
