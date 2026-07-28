package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FatherProfileValidatorTest {

    private FatherProfileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FatherProfileValidator();
    }

    @Test
    void supportedStep_returnsFatherProfile() {
        assertThat(validator.supportedStep()).isEqualTo(WizardStep.FATHER_PROFILE);
    }

    // ─── Valid data ──────────────────────────────────────────────────────

    @Test
    void validate_validCompleteData_returnsSuccess() {
        Map<String, Object> data = validFatherData();

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validate_validDataWithoutEmail_returnsSuccess() {
        Map<String, Object> data = validFatherData();
        data.remove("email");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_unicodeDisplayName_returnsSuccess() {
        Map<String, Object> data = validFatherData();
        data.put("display_name", "אורן גייפמן");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── display_name validation ─────────────────────────────────────────

    @Test
    void validate_missingDisplayName_returnsError() {
        Map<String, Object> data = validFatherData();
        data.remove("display_name");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("display_name") && e.errorCode().equals("REQUIRED"));
    }

    @Test
    void validate_blankDisplayName_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("display_name", "   ");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("display_name") && e.errorCode().equals("REQUIRED"));
    }

    @Test
    void validate_displayNameTooShort_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("display_name", "A");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("display_name") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @Test
    void validate_displayNameTooLong_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("display_name", "A".repeat(51));

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("display_name") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @Test
    void validate_displayNameWithNumbers_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("display_name", "John123");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("display_name") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @Test
    void validate_displayNameWithSpecialChars_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("display_name", "John@Doe");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
    }

    // ─── phone_number validation ─────────────────────────────────────────

    @Test
    void validate_missingPhoneNumber_returnsError() {
        Map<String, Object> data = validFatherData();
        data.remove("phone_number");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("phone_number") && e.errorCode().equals("REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "0501234567", "+0123456789", "abc", "+", "+1"})
    void validate_invalidPhoneFormat_returnsError(String phone) {
        Map<String, Object> data = validFatherData();
        data.put("phone_number", phone);

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("phone_number") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+972501234567", "+14155552671", "+447911123456"})
    void validate_validPhoneFormats_returnsSuccess(String phone) {
        Map<String, Object> data = validFatherData();
        data.put("phone_number", phone);

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── email validation ────────────────────────────────────────────────

    @Test
    void validate_validEmail_returnsSuccess() {
        Map<String, Object> data = validFatherData();
        data.put("email", "user@example.com");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_invalidEmail_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("email", "not-an-email");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("email") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @Test
    void validate_emptyEmail_treatedAsOptional() {
        Map<String, Object> data = validFatherData();
        data.put("email", "");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── timezone validation ─────────────────────────────────────────────

    @Test
    void validate_validTimezone_returnsSuccess() {
        Map<String, Object> data = validFatherData();
        data.put("timezone", "America/New_York");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_invalidTimezone_returnsError() {
        Map<String, Object> data = validFatherData();
        data.put("timezone", "Invalid/Timezone");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("timezone") && e.errorCode().equals("INVALID_TIMEZONE"));
    }

    @Test
    void validate_missingTimezone_returnsError() {
        Map<String, Object> data = validFatherData();
        data.remove("timezone");

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("timezone") && e.errorCode().equals("REQUIRED"));
    }

    // ─── Multiple errors ─────────────────────────────────────────────────

    @Test
    void validate_multipleInvalidFields_returnsAllErrors() {
        Map<String, Object> data = new HashMap<>();
        // All required fields missing

        StepValidationResult result = validator.validate(WizardStep.FATHER_PROFILE, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(3);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Map<String, Object> validFatherData() {
        Map<String, Object> data = new HashMap<>();
        data.put("display_name", "John Doe");
        data.put("phone_number", "+972501234567");
        data.put("email", "john@example.com");
        data.put("timezone", "Asia/Jerusalem");
        return data;
    }
}
