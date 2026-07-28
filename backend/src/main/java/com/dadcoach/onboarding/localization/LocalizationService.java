package com.dadcoach.onboarding.localization;

import com.dadcoach.onboarding.session.WizardStep;

import java.util.Map;

/**
 * Service for resolving localized messages, step content, and locale metadata.
 * Supports Hebrew (RTL) and English (LTR) with fallback to English when a key
 * is not found in the requested language.
 */
public interface LocalizationService {

    /**
     * Resolves a localized message by key and language, interpolating named placeholders.
     * Falls back to English if the key is missing in the requested language (with a warning log).
     *
     * @param key      the message key (e.g., "wizard.welcome.title")
     * @param language BCP 47 language code ("he" or "en")
     * @param args     optional named placeholder values (key-value pairs or positional)
     * @return the resolved, interpolated message
     */
    String getMessage(String key, String language, Object... args);

    /**
     * Returns all localized messages for a given wizard step in the specified language.
     * Includes title, description, instructions, validation messages, etc.
     *
     * @param step     the wizard step
     * @param language BCP 47 language code
     * @return map of message keys to resolved messages for that step
     */
    Map<String, String> getStepMessages(WizardStep step, String language);

    /**
     * Returns the text direction for the given language.
     *
     * @param language BCP 47 language code
     * @return RTL for Hebrew, LTR for English
     */
    TextDirection getTextDirection(String language);

    /**
     * Returns the date format pattern for the given language.
     *
     * @param language BCP 47 language code
     * @return date format pattern (e.g., "dd/MM/yyyy" for Hebrew, "MM/dd/yyyy" for English)
     */
    String getDateFormat(String language);

    /**
     * Returns the time format pattern for the given language.
     *
     * @param language BCP 47 language code
     * @return time format pattern (e.g., "HH:mm" for 24h, "h:mm a" for 12h)
     */
    String getTimeFormat(String language);
}
