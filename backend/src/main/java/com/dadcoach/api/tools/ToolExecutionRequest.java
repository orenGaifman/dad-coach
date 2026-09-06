package com.dadcoach.api.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO for tool execution via the Tool API.
 * 
 * <p>This is the standard request format for all tool executions from
 * the ai-workflow-platform. Each tool execution includes:</p>
 * <ul>
 *   <li>executionId - unique identifier for tracing</li>
 *   <li>idempotencyKey - for retry safety</li>
 *   <li>userId - the father's identifier</li>
 *   <li>parameters - tool-specific parameters</li>
 * </ul>
 * 
 * @see ToolExecutionResponse
 * @see ToolApiController
 */
public record ToolExecutionRequest(
        /**
         * Unique execution identifier for tracing and logging.
         */
        @NotBlank(message = "executionId is required")
        @JsonProperty("execution_id")
        String executionId,

        /**
         * Idempotency key to ensure safe retries.
         * Multiple requests with the same key should produce the same result.
         */
        @NotBlank(message = "idempotencyKey is required")
        @JsonProperty("idempotency_key")
        String idempotencyKey,

        /**
         * The user (father) ID for whom to execute the tool.
         */
        @NotNull(message = "userId is required")
        @JsonProperty("user_id")
        Long userId,

        /**
         * Tool-specific parameters as a key-value map.
         * The required parameters depend on the specific tool being executed.
         */
        @JsonProperty("parameters")
        Map<String, Object> parameters
) {
    /**
     * Gets a parameter value as a String.
     * 
     * @param key the parameter key
     * @return the parameter value as String, or null if not present
     */
    public String getStringParam(String key) {
        if (parameters == null) return null;
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a parameter value as a Long.
     * 
     * @param key the parameter key
     * @return the parameter value as Long, or null if not present or invalid
     */
    public Long getLongParam(String key) {
        if (parameters == null) return null;
        Object value = parameters.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets a parameter value as an Integer.
     * 
     * @param key the parameter key
     * @return the parameter value as Integer, or null if not present or invalid
     */
    public Integer getIntParam(String key) {
        if (parameters == null) return null;
        Object value = parameters.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets a parameter value as a Boolean.
     * 
     * @param key the parameter key
     * @return the parameter value as Boolean, or null if not present
     */
    public Boolean getBooleanParam(String key) {
        if (parameters == null) return null;
        Object value = parameters.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
