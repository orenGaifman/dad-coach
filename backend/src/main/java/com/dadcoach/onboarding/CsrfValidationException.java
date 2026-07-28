package com.dadcoach.onboarding;

/**
 * Thrown when CSRF token validation fails.
 */
public class CsrfValidationException extends RuntimeException {
    public CsrfValidationException() {
        super("Invalid or missing CSRF token");
    }
}
