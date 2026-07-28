package com.dadcoach.onboarding.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TextDirection enum.
 */
class TextDirectionTest {

    @Test
    void forLanguage_hebrew_returnsRTL() {
        assertEquals(TextDirection.RTL, TextDirection.forLanguage("he"));
    }

    @Test
    void forLanguage_english_returnsLTR() {
        assertEquals(TextDirection.LTR, TextDirection.forLanguage("en"));
    }

    @Test
    void forLanguage_arabic_returnsRTL() {
        assertEquals(TextDirection.RTL, TextDirection.forLanguage("ar"));
    }

    @Test
    void forLanguage_null_returnsLTR() {
        assertEquals(TextDirection.LTR, TextDirection.forLanguage(null));
    }

    @Test
    void forLanguage_unknown_returnsLTR() {
        assertEquals(TextDirection.LTR, TextDirection.forLanguage("fr"));
    }

    @Test
    void forLanguage_caseInsensitive() {
        assertEquals(TextDirection.RTL, TextDirection.forLanguage("HE"));
        assertEquals(TextDirection.LTR, TextDirection.forLanguage("EN"));
    }

    @Test
    void isRtl_hebrew_returnsTrue() {
        assertTrue(TextDirection.isRtl("he"));
    }

    @Test
    void isRtl_english_returnsFalse() {
        assertFalse(TextDirection.isRtl("en"));
    }

    @Test
    void isRtl_null_returnsFalse() {
        assertFalse(TextDirection.isRtl(null));
    }
}
