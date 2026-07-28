package com.dadcoach.onboarding.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PhoneMasker utility.
 */
class PhoneMaskerTest {

    @Test
    void mask_internationalFormat_showsLastFour() {
        assertEquals("****4567", PhoneMasker.mask("+972501234567"));
    }

    @Test
    void mask_localFormat_showsLastFour() {
        assertEquals("****4567", PhoneMasker.mask("0501234567"));
    }

    @Test
    void mask_usFormat_showsLastFour() {
        assertEquals("****1234", PhoneMasker.mask("+12125551234"));
    }

    @Test
    void mask_shortNumber_returnsOriginal() {
        assertEquals("1234", PhoneMasker.mask("1234"));
    }

    @Test
    void mask_null_returnsNull() {
        assertNull(PhoneMasker.mask(null));
    }

    @Test
    void mask_blank_returnsBlank() {
        assertEquals("", PhoneMasker.mask(""));
    }

    @Test
    void mask_withDashes_showsLastFour() {
        assertEquals("****5678", PhoneMasker.mask("+1-212-555-5678"));
    }

    @Test
    void mask_withSpaces_showsLastFour() {
        assertEquals("****4567", PhoneMasker.mask("+972 50 123 4567"));
    }

    @Test
    void isMasked_maskedNumber_returnsTrue() {
        assertTrue(PhoneMasker.isMasked("****4567"));
    }

    @Test
    void isMasked_unmaskedNumber_returnsFalse() {
        assertFalse(PhoneMasker.isMasked("+972501234567"));
    }

    @Test
    void isMasked_null_returnsFalse() {
        assertFalse(PhoneMasker.isMasked(null));
    }
}
