package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesValidatorTest {

    private PreferencesValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PreferencesValidator();
    }

    @Test
    void supportedStep_returnsPreferences() {
        assertThat(validator.supportedStep()).isEqualTo(WizardStep.PREFERENCES);
    }

    // ─── Valid data ──────────────────────────────────────────────────────

    @Test
    void validate_allFieldsValid_returnsSuccess() {
        Map<String, Object> data = validPreferencesData();

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_emptyData_returnsSuccess() {
        // All preferences are optional (defaults applied when not provided)
        Map<String, Object> data = new HashMap<>();

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_allFieldsNull_returnsSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("coaching_style", null);
        data.put("preferred_coaching_time", null);
        data.put("notification_frequency", null);
        data.put("quiet_hours_start", null);
        data.put("quiet_hours_end", null);

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    // ─── Coaching style validation ───────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"GENTLE", "BALANCED", "DIRECT", "MOTIVATIONAL"})
    void validate_validCoachingStyles_returnsSuccess(String style) {
        Map<String, Object> data = new HashMap<>();
        data.put("coaching_style", style);

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_coachingStyleCaseInsensitive_returnsSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("coaching_style", "gentle");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_invalidCoachingStyle_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("coaching_style", "AGGRESSIVE");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("coaching_style") && e.errorCode().equals("INVALID_VALUE"));
    }

    // ─── Preferred coaching time validation ──────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"08:00", "08:30", "12:00", "12:30", "23:00", "23:30", "00:00", "00:30"})
    void validate_validCoachingTimes_returnsSuccess(String time) {
        Map<String, Object> data = new HashMap<>();
        data.put("preferred_coaching_time", time);

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:15", "08:45", "12:10", "23:59"})
    void validate_nonThirtyMinuteInterval_returnsError(String time) {
        Map<String, Object> data = new HashMap<>();
        data.put("preferred_coaching_time", time);

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("preferred_coaching_time") && e.errorCode().equals("INVALID_INTERVAL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"25:00", "8:00", "abc", "24:00", "12:60"})
    void validate_invalidTimeFormat_returnsError(String time) {
        Map<String, Object> data = new HashMap<>();
        data.put("preferred_coaching_time", time);

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("preferred_coaching_time") && e.errorCode().equals("INVALID_FORMAT"));
    }

    // ─── Notification frequency validation ───────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"DAILY", "EVERY_OTHER_DAY", "TWICE_WEEKLY"})
    void validate_validNotificationFrequencies_returnsSuccess(String freq) {
        Map<String, Object> data = new HashMap<>();
        data.put("notification_frequency", freq);

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_invalidNotificationFrequency_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("notification_frequency", "WEEKLY");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("notification_frequency") && e.errorCode().equals("INVALID_VALUE"));
    }

    // ─── Quiet hours validation ──────────────────────────────────────────

    @Test
    void validate_validQuietHours_returnsSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("quiet_hours_start", "21:00");
        data.put("quiet_hours_end", "07:00");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_invalidQuietHoursStart_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("quiet_hours_start", "invalid");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("quiet_hours_start") && e.errorCode().equals("INVALID_FORMAT"));
    }

    @Test
    void validate_invalidQuietHoursEnd_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("quiet_hours_end", "25:00");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.fieldName().equals("quiet_hours_end") && e.errorCode().equals("INVALID_FORMAT"));
    }

    // ─── Multiple errors ─────────────────────────────────────────────────

    @Test
    void validate_multipleInvalidFields_returnsAllErrors() {
        Map<String, Object> data = new HashMap<>();
        data.put("coaching_style", "INVALID");
        data.put("preferred_coaching_time", "not-a-time");
        data.put("notification_frequency", "INVALID");

        StepValidationResult result = validator.validate(WizardStep.PREFERENCES, data);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(3);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Map<String, Object> validPreferencesData() {
        Map<String, Object> data = new HashMap<>();
        data.put("coaching_style", "BALANCED");
        data.put("preferred_coaching_time", "08:00");
        data.put("notification_frequency", "DAILY");
        data.put("quiet_hours_start", "21:00");
        data.put("quiet_hours_end", "07:00");
        return data;
    }
}
