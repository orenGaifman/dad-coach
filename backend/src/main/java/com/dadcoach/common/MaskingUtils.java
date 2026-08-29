package com.dadcoach.common;

/**
 * Utility class for masking sensitive data in logs and displays.
 * Provides consistent masking across the application to prevent PII leakage.
 */
public final class MaskingUtils {

    private MaskingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Masks a phone number for logging, showing only the last 4 digits.
     * Example: "+1234567890" becomes "****7890"
     *
     * @param phone the phone number to mask
     * @return masked phone number, or "****" if input is null/too short
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Masks a phone number for user display, showing only the last 4 digits.
     * Example: "+1234567890" becomes "***-7890"
     *
     * @param phone the phone number to mask
     * @return masked phone for display, or "****" if input is null/too short
     */
    public static String maskPhoneForDisplay(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "***-" + phone.substring(phone.length() - 4);
    }

    /**
     * Masks a phone number ID (used for WhatsApp business API).
     * Shows first 4 and last 4 characters for identification while hiding the middle.
     *
     * @param phoneNumberId the phone number ID to mask
     * @return masked phone number ID
     */
    public static String maskPhoneNumberId(String phoneNumberId) {
        if (phoneNumberId == null || phoneNumberId.length() <= 8) {
            return "****";
        }
        return phoneNumberId.substring(0, 4) + "****" + 
               phoneNumberId.substring(phoneNumberId.length() - 4);
    }
}
