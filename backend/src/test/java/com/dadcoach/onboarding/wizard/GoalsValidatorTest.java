package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalsValidatorTest {

    private GoalsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GoalsValidator();
    }

    @Test
    void supportedStep_returnsGoals() {
        assertThat(validator.supportedStep()).isEqualTo(WizardStep.GOALS);
    }

    // ─── Valid data ──────────────────────────────────────────────────────

    @Test
    void validate_singlePredefinedGoal_returnsSuccess() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of("spend-more-quality-time")
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_fivePredefinedGoals_returnsSuccess() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of(
                        "spend-more-quality-time",
                        "improve-communication",
                        "build-stronger-emotional-connection",
                        "handle-conflicts-better",
                        "create-family-routines"
                )
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_singleCustomGoal_returnsSuccess() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of(),
                "custom_goals", List.of("Be a better dad")
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_mixedPredefinedAndCustomGoals_returnsSuccess() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of("spend-more-quality-time", "improve-communication"),
                "custom_goals", List.of("Learn to cook together")
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── Count validation ────────────────────────────────────────────────

    @Test
    void validate_noGoalsSelected_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("selected_goals", List.of());
        data.put("custom_goals", List.of());

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("selected_goals") && e.errorCode().equals("MIN_NOT_MET"));
    }

    @Test
    void validate_sixTotalGoals_returnsError() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of(
                        "spend-more-quality-time",
                        "improve-communication",
                        "build-stronger-emotional-connection",
                        "handle-conflicts-better",
                        "create-family-routines"
                ),
                "custom_goals", List.of("One more goal")
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("selected_goals") && e.errorCode().equals("MAX_EXCEEDED"));
    }

    @Test
    void validate_noGoalFieldsAtAll_returnsError() {
        Map<String, Object> data = new HashMap<>();

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.errorCode().equals("MIN_NOT_MET"));
    }

    // ─── Predefined goal validation ──────────────────────────────────────

    @Test
    void validate_invalidPredefinedGoal_returnsError() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of("not-a-real-goal")
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("selected_goals[0]") && e.errorCode().equals("INVALID_GOAL"));
    }

    // ─── Custom goal validation ──────────────────────────────────────────

    @Test
    void validate_customGoalTooLong_returnsError() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of(),
                "custom_goals", List.of("X".repeat(101))
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("custom_goals[0]") && e.errorCode().equals("MAX_LENGTH_EXCEEDED"));
    }

    @Test
    void validate_customGoalExactly100Chars_returnsSuccess() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of(),
                "custom_goals", List.of("X".repeat(100))
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_emptyCustomGoal_returnsError() {
        Map<String, Object> data = Map.of(
                "selected_goals", List.of("spend-more-quality-time"),
                "custom_goals", List.of("   ")
        );

        StepValidationResult result = validator.validate(WizardStep.GOALS, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("custom_goals[0]") && e.errorCode().equals("EMPTY_VALUE"));
    }

    // ─── All predefined goals are valid ──────────────────────────────────

    @Test
    void validate_allPredefinedGoalsAreAccepted() {
        List<String> allGoals = List.of(
                "spend-more-quality-time",
                "improve-communication",
                "build-stronger-emotional-connection",
                "handle-conflicts-better",
                "create-family-routines",
                "support-child-development",
                "be-more-patient"
        );

        // Test each individually
        for (String goal : allGoals) {
            Map<String, Object> data = Map.of("selected_goals", List.of(goal));
            StepValidationResult result = validator.validate(WizardStep.GOALS, data);
            assertThat(result.isValid()).as("Goal '%s' should be valid", goal).isTrue();
        }
    }
}
