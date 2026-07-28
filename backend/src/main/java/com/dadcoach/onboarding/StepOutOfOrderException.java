package com.dadcoach.onboarding;

/**
 * Thrown when a step submission is out of order.
 */
public class StepOutOfOrderException extends RuntimeException {

    private final String currentStep;
    private final String attemptedStep;

    public StepOutOfOrderException(String currentStep, String attemptedStep) {
        super("Cannot submit " + attemptedStep + " — current step is " + currentStep);
        this.currentStep = currentStep;
        this.attemptedStep = attemptedStep;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getAttemptedStep() {
        return attemptedStep;
    }
}
