package com.dadcoach.onboarding.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WizardStep} enum logic.
 */
class WizardStepTest {

    @Test
    void allStepsHaveSequentialOrder() {
        WizardStep[] steps = WizardStep.values();
        for (int i = 0; i < steps.length; i++) {
            assertEquals(i + 1, steps[i].getOrder(),
                    "Step " + steps[i] + " should have order " + (i + 1));
        }
    }

    @Test
    void totalStepsIsEight() {
        assertEquals(8, WizardStep.totalSteps());
    }

    @Test
    void requiredStepsAreCorrect() {
        assertTrue(WizardStep.WELCOME.isRequired());
        assertTrue(WizardStep.LANGUAGE.isRequired());
        assertTrue(WizardStep.FATHER_PROFILE.isRequired());
        assertFalse(WizardStep.CHILDREN.isRequired());
        assertFalse(WizardStep.GOALS.isRequired());
        assertFalse(WizardStep.PREFERENCES.isRequired());
        assertTrue(WizardStep.REVIEW.isRequired());
        assertTrue(WizardStep.ACTIVATION.isRequired());
    }

    @Test
    void optionalStepsCanBeSkipped() {
        assertTrue(WizardStep.CHILDREN.canSkip());
        assertTrue(WizardStep.GOALS.canSkip());
        assertTrue(WizardStep.PREFERENCES.canSkip());
    }

    @Test
    void requiredStepsCannotBeSkipped() {
        assertFalse(WizardStep.WELCOME.canSkip());
        assertFalse(WizardStep.LANGUAGE.canSkip());
        assertFalse(WizardStep.FATHER_PROFILE.canSkip());
        assertFalse(WizardStep.REVIEW.canSkip());
        assertFalse(WizardStep.ACTIVATION.canSkip());
    }

    @Test
    void nextReturnsCorrectNextStep() {
        assertEquals(WizardStep.LANGUAGE, WizardStep.WELCOME.next());
        assertEquals(WizardStep.FATHER_PROFILE, WizardStep.LANGUAGE.next());
        assertEquals(WizardStep.CHILDREN, WizardStep.FATHER_PROFILE.next());
        assertEquals(WizardStep.GOALS, WizardStep.CHILDREN.next());
        assertEquals(WizardStep.PREFERENCES, WizardStep.GOALS.next());
        assertEquals(WizardStep.REVIEW, WizardStep.PREFERENCES.next());
        assertEquals(WizardStep.ACTIVATION, WizardStep.REVIEW.next());
        assertNull(WizardStep.ACTIVATION.next(), "ACTIVATION is the last step");
    }

    @Test
    void canNavigateBackToEarlierSteps() {
        assertTrue(WizardStep.FATHER_PROFILE.canNavigateBackTo(WizardStep.WELCOME));
        assertTrue(WizardStep.FATHER_PROFILE.canNavigateBackTo(WizardStep.LANGUAGE));
        assertTrue(WizardStep.REVIEW.canNavigateBackTo(WizardStep.CHILDREN));
        assertTrue(WizardStep.ACTIVATION.canNavigateBackTo(WizardStep.WELCOME));
    }

    @Test
    void cannotNavigateBackToSameOrLaterSteps() {
        assertFalse(WizardStep.WELCOME.canNavigateBackTo(WizardStep.WELCOME));
        assertFalse(WizardStep.WELCOME.canNavigateBackTo(WizardStep.LANGUAGE));
        assertFalse(WizardStep.CHILDREN.canNavigateBackTo(WizardStep.GOALS));
        assertFalse(WizardStep.CHILDREN.canNavigateBackTo(WizardStep.CHILDREN));
    }

    @Test
    void cannotNavigateBackToNull() {
        assertFalse(WizardStep.FATHER_PROFILE.canNavigateBackTo(null));
    }

    @Test
    void canSubmitFromDataSteps() {
        assertTrue(WizardStep.LANGUAGE.canSubmitFrom());
        assertTrue(WizardStep.FATHER_PROFILE.canSubmitFrom());
        assertTrue(WizardStep.CHILDREN.canSubmitFrom());
        assertTrue(WizardStep.GOALS.canSubmitFrom());
        assertTrue(WizardStep.PREFERENCES.canSubmitFrom());
    }

    @Test
    void cannotSubmitFromNonDataSteps() {
        assertFalse(WizardStep.WELCOME.canSubmitFrom());
        assertFalse(WizardStep.REVIEW.canSubmitFrom());
        assertFalse(WizardStep.ACTIVATION.canSubmitFrom());
    }

    @Test
    void fromOrderReturnsCorrectStep() {
        assertEquals(WizardStep.WELCOME, WizardStep.fromOrder(1));
        assertEquals(WizardStep.ACTIVATION, WizardStep.fromOrder(8));
    }

    @Test
    void fromOrderReturnsNullForInvalidOrder() {
        assertNull(WizardStep.fromOrder(0));
        assertNull(WizardStep.fromOrder(9));
        assertNull(WizardStep.fromOrder(-1));
    }

    @ParameterizedTest
    @EnumSource(WizardStep.class)
    void everyStepHasPositiveOrder(WizardStep step) {
        assertTrue(step.getOrder() > 0);
    }
}
