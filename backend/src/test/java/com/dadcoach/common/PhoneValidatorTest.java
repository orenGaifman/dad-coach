package com.dadcoach.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneValidatorTest {

    @Test
    void normalizeToE164_addsPrefix_whenMissing() {
        // WhatsApp userId without + prefix
        assertEquals("+972503940332", PhoneValidator.normalizeToE164("972503940332"));
        assertEquals("+14155551234", PhoneValidator.normalizeToE164("14155551234"));
    }

    @Test
    void normalizeToE164_returnsOriginal_whenAlreadyE164() {
        assertEquals("+972503940332", PhoneValidator.normalizeToE164("+972503940332"));
        assertEquals("+14155551234", PhoneValidator.normalizeToE164("+14155551234"));
    }

    @Test
    void normalizeToE164_returnsNull_whenNull() {
        assertNull(PhoneValidator.normalizeToE164(null));
    }

    @Test
    void normalizeToE164_returnsOriginal_whenInvalid() {
        // Invalid formats returned as-is (validation will catch later)
        assertEquals("0123456789", PhoneValidator.normalizeToE164("0123456789")); // starts with 0
        assertEquals("abc", PhoneValidator.normalizeToE164("abc")); // non-numeric
    }

    @Test
    void isValidE164_returnsTrueForValidNumbers() {
        assertTrue(PhoneValidator.isValidE164("+972503940332"));
        assertTrue(PhoneValidator.isValidE164("+14155551234"));
        assertTrue(PhoneValidator.isValidE164("+447911123456"));
    }

    @Test
    void isValidE164_returnsFalseForInvalidNumbers() {
        assertFalse(PhoneValidator.isValidE164("972503940332")); // missing +
        assertFalse(PhoneValidator.isValidE164("+0123456789")); // starts with 0
        assertFalse(PhoneValidator.isValidE164("+1")); // too short
        assertFalse(PhoneValidator.isValidE164(null));
    }
}
