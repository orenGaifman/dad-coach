package com.dadcoach.publicsite;

import com.dadcoach.common.PhoneValidator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizes what a visitor typed into the site's phone field to E.164, Israeli mobiles only (Dad Coach
 * talks to the father on WhatsApp). Accepts 050-1234567, 0501234567, +972 50 123 4567, 972501234567,
 * 00972501234567. Mirrors normalizeIsraeliMobile in site/src/signup-form.js.
 */
final class IsraeliMobile {

    private static final Pattern NATIONAL_MOBILE = Pattern.compile("^5\\d{8}$");

    private IsraeliMobile() {
    }

    static Optional<String> toE164(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.replaceAll("[\\s\\-().]", "");
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (s.startsWith("00")) {
            s = s.substring(2);
        }
        String national;
        if (s.startsWith("972")) {
            national = s.substring(3).replaceFirst("^0", "");
        } else if (s.startsWith("0")) {
            national = s.substring(1);
        } else {
            return Optional.empty();
        }
        if (!NATIONAL_MOBILE.matcher(national).matches()) {
            return Optional.empty();
        }
        String e164 = "+972" + national;
        return PhoneValidator.isValidE164(e164) ? Optional.of(e164) : Optional.empty();
    }
}
