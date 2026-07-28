package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;

import java.util.Map;

/**
 * Interface for wizard step data validators.
 * Each implementation validates the submitted data for a specific wizard step.
 */
public interface StepValidator {

    /**
     * Validates the data submitted for a specific wizard step.
     *
     * @param step the wizard step being validated
     * @param data the submitted form data as a map of field names to values
     * @return a {@link StepValidationResult} containing field-level errors if validation fails,
     *         or a success result if all data is valid
     */
    StepValidationResult validate(WizardStep step, Map<String, Object> data);

    /**
     * Returns the wizard step this validator is responsible for.
     */
    WizardStep supportedStep();
}
