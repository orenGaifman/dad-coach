package com.dadcoach.onboarding.wizard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating a wizard step's submitted data.
 * Contains a validity flag and a list of field-level errors.
 */
public class StepValidationResult {

    private final boolean valid;
    private final List<FieldError> errors;

    private StepValidationResult(boolean valid, List<FieldError> errors) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors);
    }

    /**
     * Creates a successful (valid) validation result with no errors.
     */
    public static StepValidationResult success() {
        return new StepValidationResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed validation result with the given errors.
     *
     * @param errors the field-level validation errors (must not be empty)
     * @throws IllegalArgumentException if errors is null or empty
     */
    public static StepValidationResult failure(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Failure result must contain at least one error");
        }
        return new StepValidationResult(false, new ArrayList<>(errors));
    }

    /**
     * Returns true if the step data passed validation.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the list of field-level validation errors.
     * Empty list if the result is valid.
     */
    public List<FieldError> getErrors() {
        return errors;
    }
}
