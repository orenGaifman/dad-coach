package com.dadcoach.onboarding.wizard;

import com.dadcoach.onboarding.session.WizardStep;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the FATHER_PROFILE wizard step data.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>display_name: required, 2-50 characters, Unicode letters and spaces only</li>
 *   <li>phone_number: required, E.164 format</li>
 *   <li>email: optional, RFC 5322 format if present</li>
 *   <li>timezone: required, valid IANA timezone ID</li>
 * </ul>
 */
@Component
public class FatherProfileValidator implements StepValidator {

    /**
     * Display name: Unicode letters and spaces, 2-50 characters.
     */
    static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("^[\\p{L} ]{2,50}$");

    /**
     * E.164 phone number format: plus sign followed by 1-15 digits, first digit non-zero.
     */
    static final Pattern PHONE_E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    /**
     * Simplified RFC 5322 email pattern — covers the vast majority of valid email addresses.
     */
    static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );

    private static final Set<String> VALID_TIMEZONE_IDS = ZoneId.getAvailableZoneIds();

    @Override
    public StepValidationResult validate(WizardStep step, Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();

        validateDisplayName(data, errors);
        validatePhoneNumber(data, errors);
        validateEmail(data, errors);
        validateTimezone(data, errors);

        if (errors.isEmpty()) {
            return StepValidationResult.success();
        }
        return StepValidationResult.failure(errors);
    }

    @Override
    public WizardStep supportedStep() {
        return WizardStep.FATHER_PROFILE;
    }

    private void validateDisplayName(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("display_name");
        if (value == null || value.toString().isBlank()) {
            errors.add(new FieldError("display_name", "REQUIRED", "Display name is required"));
            return;
        }
        String displayName = value.toString().trim();
        if (!DISPLAY_NAME_PATTERN.matcher(displayName).matches()) {
            errors.add(new FieldError("display_name", "INVALID_FORMAT",
                    "Display name must be 2-50 characters containing only letters and spaces"));
        }
    }

    private void validatePhoneNumber(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("phone_number");
        if (value == null || value.toString().isBlank()) {
            errors.add(new FieldError("phone_number", "REQUIRED", "Phone number is required"));
            return;
        }
        String phone = value.toString().trim();
        if (!PHONE_E164_PATTERN.matcher(phone).matches()) {
            errors.add(new FieldError("phone_number", "INVALID_FORMAT",
                    "Phone number must be in E.164 format (e.g., +972501234567)"));
        }
    }

    private void validateEmail(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("email");
        if (value == null || value.toString().isBlank()) {
            // Email is optional
            return;
        }
        String email = value.toString().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            errors.add(new FieldError("email", "INVALID_FORMAT",
                    "Email must be a valid email address"));
        }
    }

    private void validateTimezone(Map<String, Object> data, List<FieldError> errors) {
        Object value = data.get("timezone");
        if (value == null || value.toString().isBlank()) {
            errors.add(new FieldError("timezone", "REQUIRED", "Timezone is required"));
            return;
        }
        String timezone = value.toString().trim();
        if (!VALID_TIMEZONE_IDS.contains(timezone)) {
            errors.add(new FieldError("timezone", "INVALID_TIMEZONE",
                    "Timezone must be a valid IANA timezone ID (e.g., Asia/Jerusalem)"));
        }
    }
}
