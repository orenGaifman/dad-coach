package com.dadcoach.onboarding.security;

/**
 * Utility class for input sanitization and XSS prevention.
 * Provides static methods for HTML entity escaping on all user-provided content
 * before rendering in client-facing responses.
 */
public final class InputSanitizer {

    private InputSanitizer() {
        // Utility class — no instantiation
    }

    /**
     * Escapes HTML entities in the input string to prevent XSS.
     * Replaces: &lt;, &gt;, &amp;, &quot;, &#x27; (single quote)
     *
     * @param input the raw user input
     * @return the escaped string safe for HTML rendering, or null if input is null
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Strips all HTML tags from the input, keeping only text content.
     * Use this when HTML is never expected in the input.
     *
     * @param input the raw input
     * @return input with all HTML tags removed, or null if input is null
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("<[^>]*>", "");
    }

    /**
     * Sanitizes a user-provided string by escaping HTML and trimming whitespace.
     * This is the recommended method for all user input that will be stored or displayed.
     *
     * @param input the raw user input
     * @return sanitized and trimmed string, or null if input is null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return escapeHtml(input.trim());
    }

    /**
     * Validates that a string does not contain potential script injection patterns.
     * Returns true if the input is safe (no script-like patterns detected).
     *
     * @param input the string to check
     * @return true if safe, false if potentially malicious content detected
     */
    public static boolean isSafe(String input) {
        if (input == null || input.isBlank()) {
            return true;
        }
        String lower = input.toLowerCase();
        return !lower.contains("<script") &&
               !lower.contains("javascript:") &&
               !lower.contains("onerror") &&
               !lower.contains("onload") &&
               !lower.contains("eval(") &&
               !lower.contains("document.cookie");
    }
}
