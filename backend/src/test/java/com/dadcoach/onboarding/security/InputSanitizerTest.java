package com.dadcoach.onboarding.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InputSanitizer.
 */
class InputSanitizerTest {

    @Test
    void escapeHtml_escapesLessThan() {
        assertEquals("&lt;script&gt;", InputSanitizer.escapeHtml("<script>"));
    }

    @Test
    void escapeHtml_escapesGreaterThan() {
        assertEquals("hello &gt; world", InputSanitizer.escapeHtml("hello > world"));
    }

    @Test
    void escapeHtml_escapesAmpersand() {
        assertEquals("Tom &amp; Jerry", InputSanitizer.escapeHtml("Tom & Jerry"));
    }

    @Test
    void escapeHtml_escapesDoubleQuote() {
        assertEquals("say &quot;hello&quot;", InputSanitizer.escapeHtml("say \"hello\""));
    }

    @Test
    void escapeHtml_escapesSingleQuote() {
        assertEquals("it&#x27;s", InputSanitizer.escapeHtml("it's"));
    }

    @Test
    void escapeHtml_allSpecialChars() {
        String input = "<script>alert('xss' & \"evil\")</script>";
        String expected = "&lt;script&gt;alert(&#x27;xss&#x27; &amp; &quot;evil&quot;)&lt;/script&gt;";
        assertEquals(expected, InputSanitizer.escapeHtml(input));
    }

    @Test
    void escapeHtml_plainText_unchanged() {
        String input = "Hello World 123";
        assertEquals(input, InputSanitizer.escapeHtml(input));
    }

    @Test
    void escapeHtml_null_returnsNull() {
        assertNull(InputSanitizer.escapeHtml(null));
    }

    @Test
    void escapeHtml_empty_returnsEmpty() {
        assertEquals("", InputSanitizer.escapeHtml(""));
    }

    @Test
    void escapeHtml_hebrewText_unchanged() {
        String input = "\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05DC\u05DD";
        assertEquals(input, InputSanitizer.escapeHtml(input));
    }

    @Test
    void stripHtml_removesAllTags() {
        assertEquals("Hello World", InputSanitizer.stripHtml("<b>Hello</b> <i>World</i>"));
    }

    @Test
    void stripHtml_nestedTags() {
        assertEquals("alert", InputSanitizer.stripHtml("<script><b>alert</b></script>"));
    }

    @Test
    void stripHtml_null_returnsNull() {
        assertNull(InputSanitizer.stripHtml(null));
    }

    @Test
    void sanitize_trimsAndEscapes() {
        String result = InputSanitizer.sanitize("  <b>Hello</b>  ");
        assertEquals("&lt;b&gt;Hello&lt;/b&gt;", result);
    }

    @Test
    void sanitize_null_returnsNull() {
        assertNull(InputSanitizer.sanitize(null));
    }

    @Test
    void isSafe_plainText_returnsTrue() {
        assertTrue(InputSanitizer.isSafe("Hello World"));
    }

    @Test
    void isSafe_scriptTag_returnsFalse() {
        assertFalse(InputSanitizer.isSafe("<script>alert('xss')</script>"));
    }

    @Test
    void isSafe_javascriptProtocol_returnsFalse() {
        assertFalse(InputSanitizer.isSafe("javascript:alert(1)"));
    }

    @Test
    void isSafe_onerror_returnsFalse() {
        assertFalse(InputSanitizer.isSafe("<img onerror=alert(1)>"));
    }

    @Test
    void isSafe_null_returnsTrue() {
        assertTrue(InputSanitizer.isSafe(null));
    }

    @Test
    void isSafe_blank_returnsTrue() {
        assertTrue(InputSanitizer.isSafe("   "));
    }
}
