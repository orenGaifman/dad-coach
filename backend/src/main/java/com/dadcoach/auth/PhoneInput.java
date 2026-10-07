package com.dadcoach.auth;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A phone number typed on the login page (or sent to the ops API) as Dad Coach stores it: E.164.
 * Israeli local forms ("050-123-4567", "0501234567", "972501234567") become +972...; anything with a
 * leading "+" is kept as typed (digits only). Invalid input is empty - never an error the page shows.
 */
public final class PhoneInput {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private PhoneInput() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        boolean plus = trimmed.startsWith("+") || trimmed.startsWith("00");
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (trimmed.startsWith("00")) {
            digits = digits.substring(2);
        }
        String e164;
        if (plus) {
            e164 = "+" + digits;
        } else if (digits.startsWith("0") && digits.length() == 10) {
            e164 = "+972" + digits.substring(1);
        } else if (digits.startsWith("972")) {
            e164 = "+" + digits;
        } else if (digits.startsWith("5") && digits.length() == 9) {
            e164 = "+972" + digits; // a mobile number typed without its leading 0
        } else {
            e164 = "+" + digits;
        }
        return E164.matcher(e164).matches() ? Optional.of(e164) : Optional.empty();
    }
}
