package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the GOALS wizard step data.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>1-5 total goal selections (predefined + custom combined)</li>
 *   <li>Predefined goals must be from the allowed list</li>
 *   <li>Custom goals: max 100 characters each</li>
 * </ul>
 */
@Component
public class GoalsValidator implements StepValidator {

    static final int MIN_GOALS = 1;
    static final int MAX_GOALS = 5;
    static final int MAX_CUSTOM_GOAL_LENGTH = 100;

    static final Set<String> PREDEFINED_GOALS = Set.of(
            "spend-more-quality-time",
            "improve-communication",
            "build-stronger-emotional-connection",
            "handle-conflicts-better",
            "create-family-routines",
            "support-child-development",
            "be-more-patient"
    );

    @Override
    public StepValidationResult validate(WizardStep step, Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();

        Object selectedGoalsObj = data.get("selected_goals");
        Object customGoalsObj = data.get("custom_goals");

        List<String> selectedGoals = extractStringList(selectedGoalsObj);
        List<String> customGoals = extractStringList(customGoalsObj);

        int totalGoals = selectedGoals.size() + customGoals.size();

        // Validate total count
        if (totalGoals < MIN_GOALS) {
            errors.add(new FieldError("selected_goals", "MIN_NOT_MET",
                    "At least " + MIN_GOALS + " goal must be selected"));
        } else if (totalGoals > MAX_GOALS) {
            errors.add(new FieldError("selected_goals", "MAX_EXCEEDED",
                    "Maximum " + MAX_GOALS + " goals allowed (predefined + custom combined)"));
        }

        // Validate predefined goals are from the allowed list
        for (int i = 0; i < selectedGoals.size(); i++) {
            String goal = selectedGoals.get(i);
            if (!PREDEFINED_GOALS.contains(goal)) {
                errors.add(new FieldError("selected_goals[" + i + "]", "INVALID_GOAL",
                        "'" + goal + "' is not a valid predefined goal"));
            }
        }

        // Validate custom goals max length
        for (int i = 0; i < customGoals.size(); i++) {
            String customGoal = customGoals.get(i);
            if (customGoal.isBlank()) {
                errors.add(new FieldError("custom_goals[" + i + "]", "EMPTY_VALUE",
                        "Custom goal cannot be empty"));
            } else if (customGoal.length() > MAX_CUSTOM_GOAL_LENGTH) {
                errors.add(new FieldError("custom_goals[" + i + "]", "MAX_LENGTH_EXCEEDED",
                        "Custom goal must be " + MAX_CUSTOM_GOAL_LENGTH + " characters or less"));
            }
        }

        if (errors.isEmpty()) {
            return StepValidationResult.success();
        }
        return StepValidationResult.failure(errors);
    }

    @Override
    public WizardStep supportedStep() {
        return WizardStep.GOALS;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object obj) {
        if (obj == null) {
            return List.of();
        }
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString().trim());
                }
            }
            return result;
        }
        return List.of();
    }
}
