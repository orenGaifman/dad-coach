package com.dadcoach.api.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request DTO for tool execution via the Tool API.
 * 
 * <p>This is the standard request format for all tool executions from
 * the ai-workflow-platform. Each tool execution includes:</p>
 * <ul>
 *   <li>executionId - unique identifier for tracing</li>
 *   <li>idempotencyKey - for retry safety</li>
 *   <li>userId - the father's identifier (phone number or numeric ID)</li>
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
         * The user (father) identifier for whom to execute the tool.
         * Can be a phone number (e.g., "+972503020551") or a numeric ID.
         * The controller resolves phone numbers to father IDs.
         */
        @NotBlank(message = "userId is required")
        @JsonProperty("user_id")
        String userId,

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

    /**
     * Resolves the userId to a Long father ID.
     * If userId is a phone number (contains + or is non-numeric), returns null.
     * If userId is numeric, parses and returns it as a Long.
     * 
     * @return the numeric father ID, or null if userId is a phone number
     */
    public Long resolveNumericUserId() {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        // If contains + or any non-digit character (except leading -), treat as phone number
        if (userId.contains("+") || !userId.matches("-?\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Checks if the userId appears to be a phone number.
     * 
     * @return true if userId looks like a phone number
     */
    public boolean isPhoneNumber() {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return userId.contains("+") || !userId.matches("-?\\d+");
    }
}
