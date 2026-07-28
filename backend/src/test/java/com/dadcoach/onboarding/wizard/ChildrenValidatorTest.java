package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChildrenValidatorTest {

    private ChildrenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ChildrenValidator();
    }

    @Test
    void supportedStep_returnsChildren() {
        assertThat(validator.supportedStep()).isEqualTo(WizardStep.CHILDREN);
    }

    // ─── Valid data ──────────────────────────────────────────────────────

    @Test
    void validate_noChildrenProvided_returnsSuccess() {
        Map<String, Object> data = new HashMap<>();

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_nullChildren_returnsSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("children", null);

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_emptyChildrenList_returnsSuccess() {
        Map<String, Object> data = Map.of("children", List.of());

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_singleValidChild_returnsSuccess() {
        Map<String, Object> data = Map.of("children", List.of(validChild("Noah", 5)));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_multipleValidChildren_returnsSuccess() {
        List<Map<String, Object>> children = List.of(
                validChild("Noah", 5),
                validChild("Emma", 3),
                validChild("Liam", 1)
        );
        Map<String, Object> data = Map.of("children", children);

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_childWithOptionalGender_returnsSuccess() {
        Map<String, Object> child = new HashMap<>(validChild("Noah", 5));
        child.put("gender", "MALE");
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_eightChildren_returnsSuccess() {
        List<Map<String, Object>> children = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            children.add(validChild("Child" + i, i + 1));
        }
        Map<String, Object> data = Map.of("children", children);

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── Max children ────────────────────────────────────────────────────

    @Test
    void validate_nineChildren_returnsError() {
        List<Map<String, Object>> children = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            children.add(validChild("Child" + i, i + 1));
        }
        Map<String, Object> data = Map.of("children", children);

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children") && e.errorCode().equals("MAX_EXCEEDED"));
    }

    // ─── Child name validation ───────────────────────────────────────────

    @Test
    void validate_childMissingName_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("birth_date", LocalDate.now().minusYears(5).toString());
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].name") && e.errorCode().equals("REQUIRED"));
    }

    @Test
    void validate_childNameTooShort_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "A");
        child.put("birth_date", LocalDate.now().minusYears(5).toString());
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].name") && e.errorCode().equals("INVALID_LENGTH"));
    }

    @Test
    void validate_childNameTooLong_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "A".repeat(31));
        child.put("birth_date", LocalDate.now().minusYears(5).toString());
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].name") && e.errorCode().equals("INVALID_LENGTH"));
    }

    // ─── Birth date validation ───────────────────────────────────────────

    @Test
    void validate_childMissingBirthDate_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "Noah");
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].birth_date") && e.errorCode().equals("REQUIRED"));
    }

    @Test
    void validate_childInvalidBirthDateFormat_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "Noah");
        child.put("birth_date", "not-a-date");
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].birth_date") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @Test
    void validate_childBirthDateInFuture_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "Noah");
        child.put("birth_date", LocalDate.now().plusDays(1).toString());
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].birth_date") && e.errorCode().equals("FUTURE_DATE"));
    }

    @Test
    void validate_childOlderThan18_returnsError() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "Noah");
        child.put("birth_date", LocalDate.now().minusYears(19).toString());
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].birth_date") && e.errorCode().equals("AGE_EXCEEDED"));
    }

    @Test
    void validate_newbornChild_returnsSuccess() {
        Map<String, Object> child = new HashMap<>();
        child.put("name", "Baby");
        child.put("birth_date", LocalDate.now().toString());
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── Gender validation ───────────────────────────────────────────────

    @Test
    void validate_invalidGender_returnsError() {
        Map<String, Object> child = new HashMap<>(validChild("Noah", 5));
        child.put("gender", "INVALID");
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children[0].gender") && e.errorCode().equals("INVALID_VALUE"));
    }

    @Test
    void validate_genderCaseInsensitive_returnsSuccess() {
        Map<String, Object> child = new HashMap<>(validChild("Noah", 5));
        child.put("gender", "female");
        Map<String, Object> data = Map.of("children", List.of(child));

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── Invalid format ──────────────────────────────────────────────────

    @Test
    void validate_childrenNotAList_returnsError() {
        Map<String, Object> data = Map.of("children", "not a list");

        StepValidationResult result = validator.validate(WizardStep.CHILDREN, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("children") && e.errorCode().equals("INVALID_FORMAT"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Map<String, Object> validChild(String name, int ageYears) {
        Map<String, Object> child = new HashMap<>();
        child.put("name", name);
        child.put("birth_date", LocalDate.now().minusYears(ageYears).toString());
        return child;
    }
}
