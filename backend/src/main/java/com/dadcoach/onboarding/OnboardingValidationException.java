package com.dadcoach.onboarding;

import com.dadcoach.onboarding.dto.ErrorResponse;

import java.util.List;

/**
 * Thrown when step validation fails with field-level errors.
 */
public class OnboardingValidationException extends RuntimeException {

    private final List<ErrorResponse.FieldError> fieldErrors;

    public OnboardingValidationException(List<ErrorResponse.FieldError> fieldErrors) {
        super("Step validation failed with " + fieldErrors.size() + " error(s)");
        this.fieldErrors = fieldErrors;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
