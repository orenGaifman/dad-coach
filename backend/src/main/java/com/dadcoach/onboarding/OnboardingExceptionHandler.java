package com.dadcoach.onboarding;

import com.dadcoach.onboarding.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Global exception handler for onboarding controllers.
 */
@Order(0)
@RestControllerAdvice(basePackages = "com.dadcoach.onboarding")
public class OnboardingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getField(), determineCode(e.getCode()), e.getDefaultMessage()))
                .toList();
        log.warn("Validation failed on {} with {} error(s)", request.getRequestURI(), fieldErrors.size());
        return ResponseEntity.badRequest().body(
                ErrorResponse.withFieldErrors("VALIDATION_FAILED", "Invalid input data", fieldErrors));
    }

    @ExceptionHandler(OnboardingExceptions.ValidationFailed.class)
    public ResponseEntity<ErrorResponse> handleOnboardingValidation(
            OnboardingExceptions.ValidationFailed ex, HttpServletRequest request) {
        log.warn("Step validation failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ErrorResponse.withFieldErrors("VALIDATION_FAILED", "Invalid input data", ex.getFieldErrors()));
    }

    @ExceptionHandler(OnboardingExceptions.InvitationNotFound.class)
    public ResponseEntity<ErrorResponse> handleInvitationNotFound(
            OnboardingExceptions.InvitationNotFound ex, HttpServletRequest request) {
        log.warn("Invitation not found on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of("INVITE_NOT_FOUND", "This invitation link is invalid."));
    }

    @ExceptionHandler(OnboardingExceptions.InvitationExpired.class)
    public ResponseEntity<ErrorResponse> handleInvitationExpired(
            OnboardingExceptions.InvitationExpired ex, HttpServletRequest request) {
        Map<String, Object> details = ex.getExpiredAt() != null
                ? Map.of("expired_at", ex.getExpiredAt().toString()) : Map.of();
        log.warn("Invitation expired on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(
                ErrorResponse.withDetails("INVITE_EXPIRED", "This invitation has expired.", details));
    }

    @ExceptionHandler(OnboardingExceptions.InvitationRevoked.class)
    public ResponseEntity<ErrorResponse> handleInvitationRevoked(
            OnboardingExceptions.InvitationRevoked ex, HttpServletRequest request) {
        log.warn("Invitation revoked on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(
                ErrorResponse.of("INVITE_REVOKED", "This invitation has been revoked."));
    }

    @ExceptionHandler(OnboardingExceptions.InvitationExhausted.class)
    public ResponseEntity<ErrorResponse> handleInvitationExhausted(
            OnboardingExceptions.InvitationExhausted ex, HttpServletRequest request) {
        log.warn("Invitation exhausted on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(
                ErrorResponse.of("INVITE_EXHAUSTED", "This invitation has reached its maximum number of uses."));
    }

    @ExceptionHandler(OnboardingExceptions.SessionExpired.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(
            OnboardingExceptions.SessionExpired ex, HttpServletRequest request) {
        log.warn("Session expired on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse.of("SESSION_EXPIRED", "Your session has expired. Please start over."));
    }

    @ExceptionHandler(OnboardingExceptions.PhoneAlreadyRegistered.class)
    public ResponseEntity<ErrorResponse> handlePhoneRegistered(
            OnboardingExceptions.PhoneAlreadyRegistered ex, HttpServletRequest request) {
        log.warn("Duplicate phone detected on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.withDetails("PHONE_REGISTERED", "This phone number is already registered.",
                        Map.of("login_url", "/login")));
    }

    @ExceptionHandler(OnboardingExceptions.StepOutOfOrder.class)
    public ResponseEntity<ErrorResponse> handleStepOutOfOrder(
            OnboardingExceptions.StepOutOfOrder ex, HttpServletRequest request) {
        log.warn("Step out of order on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.withDetails("STEP_OUT_OF_ORDER", ex.getMessage(),
                        Map.of("current_step", ex.getCurrentStep(), "attempted_step", ex.getAttemptedStep())));
    }

    @ExceptionHandler(OnboardingExceptions.MaxRetriesExceeded.class)
    public ResponseEntity<ErrorResponse> handleMaxRetries(
            OnboardingExceptions.MaxRetriesExceeded ex, HttpServletRequest request) {
        log.warn("Max retries exceeded on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                ErrorResponse.withDetails("MAX_RETRIES_EXCEEDED",
                        "Maximum activation retries reached. Please contact support.",
                        Map.of("retry_count", ex.getRetryCount(), "support_url", "/support")));
    }

    @ExceptionHandler(OnboardingExceptions.RateLimitExceeded.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            OnboardingExceptions.RateLimitExceeded ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.withDetails("RATE_LIMIT_EXCEEDED", "Too many attempts. Please try again later.",
                        Map.of("retry_after", ex.getRetryAfterSeconds())));
    }

    @ExceptionHandler(OnboardingExceptions.CsrfValidation.class)
    public ResponseEntity<ErrorResponse> handleCsrfFailure(
            OnboardingExceptions.CsrfValidation ex, HttpServletRequest request) {
        log.warn("CSRF validation failed on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse.of("CSRF_INVALID", "Invalid or missing CSRF token."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred. Please try again later."));
    }

    private String determineCode(String validationCode) {
        if (validationCode == null) return "FIELD_INVALID";
        return switch (validationCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> "FIELD_REQUIRED";
            case "Size" -> "INVALID_LENGTH";
            default -> "FIELD_INVALID";
        };
    }
}
