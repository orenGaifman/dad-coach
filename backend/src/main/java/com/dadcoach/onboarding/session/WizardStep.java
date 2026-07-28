package com.dadcoach.onboarding.session;

/**
 * Enumerates the steps in the onboarding wizard, each with an ordinal order,
 * a required/optional flag, and navigation logic.
 *
 * <p>Steps:
 * <ol>
 *   <li>WELCOME — required, greeting screen</li>
 *   <li>LANGUAGE — required, language selection</li>
 *   <li>FATHER_PROFILE — required, father identity (name, phone, email, timezone)</li>
 *   <li>CHILDREN — optional, children information (can be skipped)</li>
 *   <li>GOALS — optional, coaching goals (can be skipped)</li>
 *   <li>PREFERENCES — optional, coaching preferences (can be skipped)</li>
 *   <li>REVIEW — required, review all data (no data input)</li>
 *   <li>ACTIVATION — required, activation step (no data input)</li>
 * </ol>
 *
 * @see OnboardingSession
 */
public enum WizardStep {

    WELCOME(1, true),
    LANGUAGE(2, true),
    FATHER_PROFILE(3, true),
    CHILDREN(4, false),
    GOALS(5, false),
    PREFERENCES(6, false),
    REVIEW(7, true),
    ACTIVATION(8, true);

    private final int order;
    private final boolean required;

    WizardStep(int order, boolean required) {
        this.order = order;
        this.required = required;
    }

    /**
     * Returns the 1-based order of this step in the wizard flow.
     */
    public int getOrder() {
        return order;
    }

    /**
     * Returns true if this step is required (cannot be skipped).
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Returns true if this step can be skipped (optional steps only).
     * REVIEW and ACTIVATION are required but have no data input — they cannot be skipped.
     */
    public boolean canSkip() {
        return !required;
    }

    /**
     * Returns the next step in the wizard flow, or null if this is the last step (ACTIVATION).
     */
    public WizardStep next() {
        WizardStep[] steps = values();
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal >= steps.length) {
            return null;
        }
        return steps[nextOrdinal];
    }

    /**
     * Determines whether a user at this step can navigate back to the specified target step.
     * Navigation back is allowed only to steps that precede the current step.
     *
     * @param target the step to navigate back to
     * @return true if backward navigation to target is permitted
     */
    public boolean canNavigateBackTo(WizardStep target) {
        if (target == null) {
            return false;
        }
        return target.order < this.order;
    }

    /**
     * Returns true if this step accepts data submission.
     * WELCOME has no data input. REVIEW and ACTIVATION are confirmation steps with no data.
     * All other steps accept data submissions.
     */
    public boolean canSubmitFrom() {
        return this != WELCOME && this != REVIEW && this != ACTIVATION;
    }

    /**
     * Returns the total number of steps in the wizard.
     */
    public static int totalSteps() {
        return values().length;
    }

    /**
     * Returns the WizardStep for the given order number, or null if not found.
     *
     * @param order the 1-based order number
     * @return the corresponding WizardStep, or null
     */
    public static WizardStep fromOrder(int order) {
        for (WizardStep step : values()) {
            if (step.order == order) {
                return step;
            }
        }
        return null;
    }
}
