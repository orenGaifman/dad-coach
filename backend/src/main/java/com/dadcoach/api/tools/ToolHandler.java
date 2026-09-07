package com.dadcoach.api.tools;

/**
 * Functional interface for tool handlers.
 * 
 * <p>Each tool implementation provides a handler that processes the
 * request and returns a response. Handlers are registered with the
 * {@link ToolDispatcher} and invoked when the corresponding tool key
 * is requested.</p>
 * 
 * @see ToolDispatcher
 * @see ToolApiController
 */
@FunctionalInterface
public interface ToolHandler {

    /**
     * Executes the tool with the given request.
     *
     * @param request the resolved tool execution request containing fatherId and parameters
     * @return the tool execution response with success/failure and data
     */
    ToolExecutionResponse execute(ToolApiController.ResolvedToolRequest request);
}
