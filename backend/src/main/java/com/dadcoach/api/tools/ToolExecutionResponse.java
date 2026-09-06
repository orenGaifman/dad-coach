package com.dadcoach.api.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response DTO for tool execution via the Tool API.
 * 
 * <p>This is the standard response format for all tool executions.
 * It includes a success flag, data payload, and optional error message.</p>
 * 
 * @see ToolExecutionRequest
 * @see ToolApiController
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolExecutionResponse(
        /**
         * Whether the tool execution was successful.
         */
        @JsonProperty("success")
        boolean success,

        /**
         * Tool-specific response data as a key-value map.
         * Present only when success is true.
         */
        @JsonProperty("data")
        Map<String, Object> data,

        /**
         * Error message describing what went wrong.
         * Present only when success is false.
         */
        @JsonProperty("error_message")
        String errorMessage,

        /**
         * Error code for programmatic error handling.
         * Present only when success is false.
         */
        @JsonProperty("error_code")
        String errorCode
) {
    /**
     * Creates a successful response with the given data.
     *
     * @param data the response data
     * @return a success response
     */
    public static ToolExecutionResponse success(Map<String, Object> data) {
        return new ToolExecutionResponse(true, data, null, null);
    }

    /**
     * Creates a failed response with the given error message.
     *
     * @param errorMessage the error message
     * @return a failure response
     */
    public static ToolExecutionResponse failure(String errorMessage) {
        return new ToolExecutionResponse(false, null, errorMessage, null);
    }

    /**
     * Creates a failed response with the given error message and code.
     *
     * @param errorMessage the error message
     * @param errorCode    the error code for programmatic handling
     * @return a failure response
     */
    public static ToolExecutionResponse failure(String errorMessage, String errorCode) {
        return new ToolExecutionResponse(false, null, errorMessage, errorCode);
    }

    /**
     * Creates a "not found" error response.
     *
     * @param resourceType the type of resource that was not found
     * @param resourceId   the ID of the resource
     * @return a failure response with NOT_FOUND error code
     */
    public static ToolExecutionResponse notFound(String resourceType, Object resourceId) {
        return failure(resourceType + " not found: " + resourceId, "NOT_FOUND");
    }

    /**
     * Creates an "invalid parameters" error response.
     *
     * @param message description of what's invalid
     * @return a failure response with INVALID_PARAMETERS error code
     */
    public static ToolExecutionResponse invalidParameters(String message) {
        return failure(message, "INVALID_PARAMETERS");
    }

    /**
     * Creates a "tool not found" error response.
     *
     * @param toolKey the tool key that was not found
     * @return a failure response with TOOL_NOT_FOUND error code
     */
    public static ToolExecutionResponse toolNotFound(String toolKey) {
        return failure("Unknown tool: " + toolKey, "TOOL_NOT_FOUND");
    }
}
