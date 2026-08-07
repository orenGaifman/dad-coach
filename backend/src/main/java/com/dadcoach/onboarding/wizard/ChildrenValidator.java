package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the CHILDREN wizard step data.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>1-8 children required (at least one child is mandatory for the coaching workflow)</li>
 *   <li>Per child: name required (2-30 chars), birth_date required (0-18 years), gender optional enum</li>
 * </ul>
 */
@Component
public class ChildrenValidator implements StepValidator {

    static final int MIN_CHILDREN = 1;
    static final int MAX_CHILDREN = 8;
    static final int MIN_NAME_LENGTH = 2;
    static final int MAX_NAME_LENGTH = 30;
    static final int MAX_AGE_YEARS = 18;

    static final Set<String> VALID_GENDERS = Set.of(
            "MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY"
    );

    @Override
    public StepValidationResult validate(WizardStep step, Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();

        Object childrenObj = data.get("children");
        if (childrenObj == null) {
            // At least one child is required for the coaching workflow
            errors.add(new FieldError("children", "REQUIRED",
                    "At least one child is required to use Dad Coach"));
            return StepValidationResult.failure(errors);
        }

        if (!(childrenObj instanceof List<?> childrenList)) {
            errors.add(new FieldError("children", "INVALID_FORMAT",
                    "Children must be provided as a list"));
            return StepValidationResult.failure(errors);
        }

        if (childrenList.isEmpty()) {
            // At least one child is required
            errors.add(new FieldError("children", "REQUIRED",
                    "At least one child is required to use Dad Coach"));
            return StepValidationResult.failure(errors);
        }

        if (childrenList.size() > MAX_CHILDREN) {
            errors.add(new FieldError("children", "MAX_EXCEEDED",
                    "Maximum " + MAX_CHILDREN + " children allowed"));
            return StepValidationResult.failure(errors);
        }

        for (int i = 0; i < childrenList.size(); i++) {
            Object childObj = childrenList.get(i);
            if (!(childObj instanceof Map<?, ?> childMap)) {
                errors.add(new FieldError("children[" + i + "]", "INVALID_FORMAT",
                        "Each child must be an object with name and birth_date"));
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) childMap;
            validateChildName(child, i, errors);
            validateChildBirthDate(child, i, errors);
            validateChildGender(child, i, errors);
        }

        if (errors.isEmpty()) {
            return StepValidationResult.success();
        }
        return StepValidationResult.failure(errors);
    }

    @Override
    public WizardStep supportedStep() {
        return WizardStep.CHILDREN;
    }

    private void validateChildName(Map<String, Object> child, int index, List<FieldError> errors) {
        Object nameObj = child.get("name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            errors.add(new FieldError("children[" + index + "].name", "REQUIRED",
                    "Child name is required"));
            return;
        }
        String name = nameObj.toString().trim();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            errors.add(new FieldError("children[" + index + "].name", "INVALID_LENGTH",
                    "Child name must be " + MIN_NAME_LENGTH + "-" + MAX_NAME_LENGTH + " characters"));
        }
    }

    private void validateChildBirthDate(Map<String, Object> child, int index, List<FieldError> errors) {
        Object birthDateObj = child.get("birth_date");
        if (birthDateObj == null || birthDateObj.toString().isBlank()) {
            errors.add(new FieldError("children[" + index + "].birth_date", "REQUIRED",
                    "Birth date is required"));
            return;
        }

        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(birthDateObj.toString().trim());
        } catch (DateTimeParseException e) {
            errors.add(new FieldError("children[" + index + "].birth_date", "INVALID_FORMAT",
                    "Birth date must be a valid date in ISO format (yyyy-MM-dd)"));
            return;
        }

        LocalDate today = LocalDate.now();
        if (birthDate.isAfter(today)) {
            errors.add(new FieldError("children[" + index + "].birth_date", "FUTURE_DATE",
                    "Birth date cannot be in the future"));
            return;
        }

        LocalDate maxAgeDate = today.minusYears(MAX_AGE_YEARS);
        if (birthDate.isBefore(maxAgeDate)) {
            errors.add(new FieldError("children[" + index + "].birth_date", "AGE_EXCEEDED",
                    "Child must be " + MAX_AGE_YEARS + " years old or younger"));
        }
    }

    private void validateChildGender(Map<String, Object> child, int index, List<FieldError> errors) {
        Object genderObj = child.get("gender");
        if (genderObj == null || genderObj.toString().isBlank()) {
            // Gender is optional
            return;
        }
        String gender = genderObj.toString().trim().toUpperCase();
        if (!VALID_GENDERS.contains(gender)) {
            errors.add(new FieldError("children[" + index + "].gender", "INVALID_VALUE",
                    "Gender must be one of: MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY"));
        }
    }
}
