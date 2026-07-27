package com.dadcoach.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PhoneValidator}.
 * Validates E.164 phone number format: ^\+[1-9]\d{1,14}$
 */
class PhoneValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "+1234567890",       // US-style (10 digits after +)
            "+972501234567",     // Israel (12 digits after +)
            "+447911123456",     // UK (12 digits after +)
            "+12",              // Minimum length: + followed by 2 digits
            "+123456789012345"  // Maximum length: + followed by 15 digits
    })
    @DisplayName("isValidE164 returns true for valid E.164 phone numbers")
    void validPhoneNumbers(String phone) {
        assertThat(PhoneValidator.isValidE164(phone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890",         // Missing '+' prefix
            "+0123456789",        // Starts with 0 after '+'
            "+1",                 // Too short (only 1 digit after +)
            "+1234567890123456",  // Too long (16 digits after +)
            "+12345678901234567", // Way too long
            "++1234567890",       // Double '+'
            "+12345abc78",        // Contains letters
            "+",                  // Only '+'
            "",                   // Empty string
            "+12 345 678",        // Contains spaces
            "+12-345-678",        // Contains dashes
            "+ 1234567890"        // Space after '+'
    })
    @DisplayName("isValidE164 returns false for invalid phone numbers")
    void invalidPhoneNumbers(String phone) {
        assertThat(PhoneValidator.isValidE164(phone)).isFalse();
    }

    @Test
    @DisplayName("isValidE164 returns false for null input")
    void nullInput() {
        assertThat(PhoneValidator.isValidE164(null)).isFalse();
    }

    @Test
    @DisplayName("requireValidE164 does not throw for valid phone number")
    void requireValid_acceptsValidPhone() {
        PhoneValidator.requireValidE164("+972501234567");
        // No exception means success
    }

    @Test
    @DisplayName("requireValidE164 throws BusinessRuleViolationException for invalid phone")
    void requireValid_rejectsInvalidPhone() {
        assertThatThrownBy(() -> PhoneValidator.requireValidE164("1234567890"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    @DisplayName("requireValidE164 throws BusinessRuleViolationException for null")
    void requireValid_rejectsNull() {
        assertThatThrownBy(() -> PhoneValidator.requireValidE164(null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("E.164");
    }
}
