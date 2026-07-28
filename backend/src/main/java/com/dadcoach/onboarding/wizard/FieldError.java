package com.dadcoach.onboarding.wizard;

/**
 * Represents a single field-level validation error.
 *
 * @param fieldName the name of the field that failed validation
 * @param errorCode a machine-readable error code (e.g., "INVALID_FORMAT", "REQUIRED")
 * @param message   a localized human-readable error message
 */
public record FieldError(String fieldName, String errorCode, String message) {
}
