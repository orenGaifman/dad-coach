package com.dadcoach.onboarding.localization;

import java.util.Map;
import java.util.Set;

/**
 * Enum representing text direction for localized content.
 * Maps languages to their appropriate text direction (RTL for Hebrew, LTR for English).
 */
public enum TextDirection {

    RTL,
    LTR;

    private static final Set<String> RTL_LANGUAGES = Set.of("he", "ar", "fa", "ur");

    private static final Map<String, TextDirection> LANGUAGE_DIRECTION_MAP = Map.of(
        "he", RTL,
        "en", LTR,
        "ar", RTL,
        "fa", RTL,
        "ur", RTL
    );

    /**
     * Returns the text direction for the given language code.
     * Defaults to LTR if the language is not recognized.
     *
     * @param language BCP 47 language code (e.g., "he", "en")
     * @return the TextDirection for that language
     */
    public static TextDirection forLanguage(String language) {
        if (language == null) {
            return LTR;
        }
        return LANGUAGE_DIRECTION_MAP.getOrDefault(language.toLowerCase(), LTR);
    }

    /**
     * Returns true if the given language is right-to-left.
     */
    public static boolean isRtl(String language) {
        return language != null && RTL_LANGUAGES.contains(language.toLowerCase());
    }
}
