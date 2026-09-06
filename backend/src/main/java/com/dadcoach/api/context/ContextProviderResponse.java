package com.dadcoach.api.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response DTO for context provider calls from ai-workflow-platform.
 * 
 * <p>This is the standard response format for all context provider calls.
 * It includes a success flag, data payload, and optional error message.</p>
 * 
 * @see ContextProviderRequest
 * @see ContextProviderController
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextProviderResponse(
        /**
         * Whether the context retrieval was successful.
         */
        @JsonProperty("success")
        boolean success,

        /**
         * Provider-specific context data as a key-value map.
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
     * @param data the context data
     * @return a success response
     */
    public static ContextProviderResponse success(Map<String, Object> data) {
        return new ContextProviderResponse(true, data, null, null);
    }

    /**
     * Creates a failed response with the given error message.
     *
     * @param errorMessage the error message
     * @return a failure response
     */
    public static ContextProviderResponse failure(String errorMessage) {
        return new ContextProviderResponse(false, null, errorMessage, null);
    }

    /**
     * Creates a failed response with the given error message and code.
     *
     * @param errorMessage the error message
     * @param errorCode    the error code for programmatic handling
     * @return a failure response
     */
    public static ContextProviderResponse failure(String errorMessage, String errorCode) {
        return new ContextProviderResponse(false, null, errorMessage, errorCode);
    }

    /**
     * Creates a "user not found" error response.
     *
     * @param userId the user ID that was not found
     * @return a failure response with USER_NOT_FOUND error code
     */
    public static ContextProviderResponse userNotFound(Long userId) {
        return failure("User not found: " + userId, "USER_NOT_FOUND");
    }

    /**
     * Creates a "provider not found" error response.
     *
     * @param providerKey the provider key that was not found
     * @return a failure response with PROVIDER_NOT_FOUND error code
     */
    public static ContextProviderResponse providerNotFound(String providerKey) {
        return failure("Unknown context provider: " + providerKey, "PROVIDER_NOT_FOUND");
    }
}
