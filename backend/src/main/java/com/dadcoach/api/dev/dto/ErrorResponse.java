package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for error responses from the Dev API.
 * Designed to be simple and not expose internal details.
 *
 * @param code The error code (e.g., FORBIDDEN, NOT_FOUND, BAD_REQUEST)
 * @param message A human-readable error message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message
) {
    
    /**
     * Creates an error response for forbidden access.
     *
     * @return ErrorResponse with FORBIDDEN code
     */
    public static ErrorResponse forbidden() {
        return new ErrorResponse("FORBIDDEN", "Dev endpoints disabled in production");
    }
    
    /**
     * Creates an error response for resource not found.
     *
     * @param message The error message
     * @return ErrorResponse with NOT_FOUND code
     */
    public static ErrorResponse notFound(String message) {
        return new ErrorResponse("NOT_FOUND", message);
    }
    
    /**
     * Creates an error response for bad request.
     *
     * @param message The error message
     * @return ErrorResponse with BAD_REQUEST code
     */
    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse("BAD_REQUEST", message);
    }
    
    /**
     * Creates an error response for internal server errors.
     * Note: Should not expose internal details in production.
     *
     * @return ErrorResponse with INTERNAL_ERROR code
     */
    public static ErrorResponse internalError() {
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
