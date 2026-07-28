package com.dadcoach.onboarding.security;

/**
 * Utility for masking phone numbers in client-facing responses.
 * Shows only the last 4 digits, replacing everything else with asterisks.
 */
public final class PhoneMasker {

    private static final int VISIBLE_DIGITS = 4;
    private static final char MASK_CHAR = '*';

    private PhoneMasker() {
        // Utility class — no instantiation
    }

    /**
     * Masks a phone number to show only the last 4 digits.
     * Example: "+972501234567" → "****4567"
     * Example: "0501234567" → "****4567"
     *
     * @param phoneNumber the full phone number
     * @return the masked phone number showing only last 4 digits, or null if input is null
     */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }

        // Strip non-digit characters for processing
        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");

        if (digitsOnly.length() <= VISIBLE_DIGITS) {
            // Too short to mask meaningfully
            return phoneNumber;
        }

        String lastFour = digitsOnly.substring(digitsOnly.length() - VISIBLE_DIGITS);
        return String.valueOf(MASK_CHAR).repeat(VISIBLE_DIGITS) + lastFour;
    }

    /**
     * Checks if a phone number is already masked.
     *
     * @param phoneNumber the phone number to check
     * @return true if the number appears to be masked
     */
    public static boolean isMasked(String phoneNumber) {
        return phoneNumber != null && phoneNumber.startsWith("****");
    }
}
