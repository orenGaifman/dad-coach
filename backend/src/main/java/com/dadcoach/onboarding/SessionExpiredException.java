package com.dadcoach.onboarding;

/**
 * Thrown when a session has expired or is invalid.
 */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() {
        super("Session has expired");
    }

    public SessionExpiredException(String message) {
        super(message);
    }
}
