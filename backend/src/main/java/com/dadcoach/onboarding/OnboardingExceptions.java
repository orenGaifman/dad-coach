package com.dadcoach.onboarding;

import com.dadcoach.onboarding.dto.ErrorResponse;
import java.time.Instant;
import java.util.List;

/**
 * Consolidated onboarding exceptions - all domain exceptions in one file.
 */
public final class OnboardingExceptions {
    private OnboardingExceptions() {}

    /** Thrown when CSRF token validation fails. */
    public static class CsrfValidation extends RuntimeException {
        public CsrfValidation() { super("Invalid or missing CSRF token"); }
    }

    /** Thrown when an invitation token is not found. */
    public static class InvitationNotFound extends RuntimeException {
        public InvitationNotFound(String token) {
            super("Invitation not found for token: " + token.substring(0, Math.min(8, token.length())) + "...");
        }
    }

    /** Thrown when an invitation has reached its maximum number of uses. */
    public static class InvitationExhausted extends RuntimeException {
        public InvitationExhausted() { super("Invitation has reached maximum uses"); }
    }

    /** Thrown when an invitation has been revoked. */
    public static class InvitationRevoked extends RuntimeException {
        public InvitationRevoked() { super("Invitation has been revoked"); }
    }

    /** Thrown when an invitation has expired. */
    public static class InvitationExpired extends RuntimeException {
        private final Instant expiredAt;
        public InvitationExpired(Instant expiredAt) {
            super("Invitation has expired");
            this.expiredAt = expiredAt;
        }
        public Instant getExpiredAt() { return expiredAt; }
    }

    /** Thrown when a session has expired or is invalid. */
    public static class SessionExpired extends RuntimeException {
        public SessionExpired() { super("Session has expired"); }
        public SessionExpired(String message) { super(message); }
    }

    /** Thrown when a step submission is out of order. */
    public static class StepOutOfOrder extends RuntimeException {
        private final String currentStep;
        private final String attemptedStep;
        public StepOutOfOrder(String currentStep, String attemptedStep) {
            super("Cannot submit " + attemptedStep + " — current step is " + currentStep);
            this.currentStep = currentStep;
            this.attemptedStep = attemptedStep;
        }
        public String getCurrentStep() { return currentStep; }
        public String getAttemptedStep() { return attemptedStep; }
    }

    /** Thrown when the onboarding rate limit is exceeded. */
    public static class RateLimitExceeded extends RuntimeException {
        private final int retryAfterSeconds;
        public RateLimitExceeded(int retryAfterSeconds) {
            super("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public int getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    /** Thrown when step validation fails with field-level errors. */
    public static class ValidationFailed extends RuntimeException {
        private final List<ErrorResponse.FieldError> fieldErrors;
        public ValidationFailed(List<ErrorResponse.FieldError> fieldErrors) {
            super("Step validation failed with " + fieldErrors.size() + " error(s)");
            this.fieldErrors = fieldErrors;
        }
        public List<ErrorResponse.FieldError> getFieldErrors() { return fieldErrors; }
    }

    /** Thrown when activation retry limit (3) has been exceeded. */
    public static class MaxRetriesExceeded extends RuntimeException {
        private final int retryCount;
        public MaxRetriesExceeded(int retryCount) {
            super("Maximum activation retries (" + retryCount + ") exceeded");
            this.retryCount = retryCount;
        }
        public int getRetryCount() { return retryCount; }
    }

    /** Thrown when a phone number is already registered. */
    public static class PhoneAlreadyRegistered extends RuntimeException {
        public PhoneAlreadyRegistered(String maskedPhone) {
            super("Phone number ****" + maskedPhone + " is already registered");
        }
    }
}
