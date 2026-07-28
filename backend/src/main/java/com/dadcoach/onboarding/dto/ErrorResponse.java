package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Consistent error response format for the onboarding API.
 * Format: {error: {code, message, field_errors: [{field, code, message}], details}}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response wrapper")
public record ErrorResponse(
    @Schema(description = "Error details")
    Error error
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Error details object")
    public record Error(
        @Schema(description = "Machine-readable error code", example = "INVITE_NOT_FOUND")
        String code,

        @Schema(description = "Human-readable error message")
        String message,

        @JsonProperty("field_errors")
        @Schema(description = "Field-level validation errors")
        List<FieldError> fieldErrors,

        @Schema(description = "Additional error details")
        Map<String, Object> details
    ) {
        public static Error of(String code, String message) {
            return new Error(code, message, null, null);
        }

        public static Error withDetails(String code, String message, Map<String, Object> details) {
            return new Error(code, message, null, details);
        }

        public static Error withFieldErrors(String code, String message, List<FieldError> fieldErrors) {
            return new Error(code, message, fieldErrors, null);
        }
    }

    @Schema(description = "Individual field-level error")
    public record FieldError(
        @Schema(description = "Field name that has the error", example = "phone_number")
        String field,

        @Schema(description = "Machine-readable error code for this field", example = "INVALID_E164")
        String code,

        @Schema(description = "Human-readable error message")
        String message
    ) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(Error.of(code, message));
    }

    public static ErrorResponse withDetails(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(Error.withDetails(code, message, details));
    }

    public static ErrorResponse withFieldErrors(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(Error.withFieldErrors(code, message, fieldErrors));
    }
}
