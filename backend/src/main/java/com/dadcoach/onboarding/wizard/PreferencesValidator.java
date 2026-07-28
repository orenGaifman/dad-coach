package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the PREFERENCES wizard step data.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>coaching_style: enum (GENTLE, BALANCED, DIRECT, MOTIVATIONAL)</li>
 *   <li>preferred_coaching_time: HH:mm format, 30-minute intervals</li>
 *   <li>notification_frequency: enum (DAILY, EVERY_OTHER_DAY, TWICE_WEEKLY)</li>
 *   <li>quiet_hours_start/end: HH:mm format</li>
 * </ul>
 */
@Component
public class PreferencesValidator implements StepValidator {

    static final Set<String> VALID_COACHING_STYLES = Set.of(
            "GENTLE", "BALANCED", "DIRECT", "MOTIVATIONAL"
    );

    static final Set<String> VALID_NOTIFICATION_FREQUENCIES = Set.of(
            "DAILY", "EVERY_OTHER_DAY", "TWICE_WEEKLY"
    );

    /**
     * HH:mm format pattern (00:00 to 23:59).
     */
    static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    @Override
    public StepValidationResult validate(WizardStep step, Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();

        validateCoachingStyle(data, errors);
        validatePreferredCoachingTime(data, errors);
        validateNotificationFrequency(data, errors);
        validateQuietHours(data, "quiet_hours_start", errors);
        validateQuietHours(data, "quiet_hours_end", errors);

        if (errors.isEmpty()) {
            return StepValidationResult.success();
        }
        return StepValidationResult.failure(errors);
    }

    @Override
    public WizardStep supportedStep() {
        return WizardStep.PREFERENCES;
    }

    private void validateCoachingStyle(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("coaching_style");
        if (value == null || value.toString().isBlank()) {
            // Optional — defaults to BALANCED when not provided
            return;
        }
        String style = value.toString().trim().toUpperCase();
        if (!VALID_COACHING_STYLES.contains(style)) {
            errors.add(new FieldError("coaching_style", "INVALID_VALUE",
                    "Coaching style must be one of: GENTLE, BALANCED, DIRECT, MOTIVATIONAL"));
        }
    }

    private void validatePreferredCoachingTime(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("preferred_coaching_time");
        if (value == null || value.toString().isBlank()) {
            // Optional — defaults to 08:00 when not provided
            return;
        }
        String time = value.toString().trim();
        if (!TIME_PATTERN.matcher(time).matches()) {
            errors.add(new FieldError("preferred_coaching_time", "INVALID_FORMAT",
                    "Preferred coaching time must be in HH:mm format"));
            return;
        }

        // Must be on 30-minute intervals (minutes must be 00 or 30)
        String minutes = time.substring(3, 5);
        if (!"00".equals(minutes) && !"30".equals(minutes)) {
            errors.add(new FieldError("preferred_coaching_time", "INVALID_INTERVAL",
                    "Preferred coaching time must be on 30-minute intervals (e.g., 08:00, 08:30)"));
        }
    }

    private void validateNotificationFrequency(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("notification_frequency");
        if (value == null || value.toString().isBlank()) {
            // Optional — defaults to DAILY when not provided
            return;
        }
        String frequency = value.toString().trim().toUpperCase();
        if (!VALID_NOTIFICATION_FREQUENCIES.contains(frequency)) {
            errors.add(new FieldError("notification_frequency", "INVALID_VALUE",
                    "Notification frequency must be one of: DAILY, EVERY_OTHER_DAY, TWICE_WEEKLY"));
        }
    }

    private void validateQuietHours(Map<String, Object> data, String fieldName, List<FieldError> errors) {
        Object value = data.get(fieldName);
        if (value == null || value.toString().isBlank()) {
            // Optional — defaults applied when not provided
            return;
        }
        String time = value.toString().trim();
        if (!TIME_PATTERN.matcher(time).matches()) {
            errors.add(new FieldError(fieldName, "INVALID_FORMAT",
                    fieldName.replace('_', ' ') + " must be in HH:mm format"));
        }
    }
}
