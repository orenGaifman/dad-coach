package com.dadcoach.common;

import java.util.regex.Pattern;

/**
 * Utility class for validating phone numbers.
 * <p>
 * Validates phone numbers against the E.164 international format:
 * - Must start with '+'
 * - Followed by a non-zero digit (1-9)
 * - Then 1 to 14 additional digits
 * - Total length: 2 to 16 characters (including the '+')
 * <p>
 * Examples of valid numbers: +1234567890, +972501234567, +447911123456
 * Examples of invalid numbers: 1234567890, +0123456789, +1, +1234567890123456
 */
public final class PhoneValidator {

    /**
     * E.164 pattern: + followed by 1-9 digit, then 1-14 more digits.
     * Total digits: 2 to 15. Total chars including '+': 3 to 16.
     */
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private PhoneValidator() {
        // Utility class — no instantiation
    }

    /**
     * Checks whether the given phone number matches E.164 format.
     *
     * @param phone the phone number to validate
     * @return true if the phone number is valid E.164 format, false otherwise
     */
    public static boolean isValidE164(String phone) {
        if (phone == null) {
            return false;
        }
        return E164_PATTERN.matcher(phone).matches();
    }

    /**
     * Validates a phone number, throwing a {@link BusinessRuleViolationException} if invalid.
     *
     * @param phone the phone number to validate
     * @throws BusinessRuleViolationException if the phone number is null or not in E.164 format
     */
    public static void requireValidE164(String phone) {
        if (!isValidE164(phone)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PHONE_FORMAT",
                    "Phone number must be in E.164 format (e.g., +972501234567)"
            );
        }
    }
}
