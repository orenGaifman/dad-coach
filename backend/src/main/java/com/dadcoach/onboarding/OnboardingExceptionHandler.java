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
 * Returns consistent error format: {error: {code, message, field_errors, details}}
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

        ErrorResponse response = ErrorResponse.withFieldErrors(
                "VALIDATION_FAILED", "Invalid input data", fieldErrors);

        log.warn("Validation failed on {} with {} error(s)", request.getRequestURI(), fieldErrors.size());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(OnboardingValidationException.class)
    public ResponseEntity<ErrorResponse> handleOnboardingValidation(
            OnboardingValidationException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.withFieldErrors(
                "VALIDATION_FAILED", "Invalid input data", ex.getFieldErrors());

        log.warn("Step validation failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInvitationNotFound(
            InvitationNotFoundException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of("INVITE_NOT_FOUND", "This invitation link is invalid.");
        log.warn("Invitation not found on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InvitationExpiredException.class)
    public ResponseEntity<ErrorResponse> handleInvitationExpired(
            InvitationExpiredException ex, HttpServletRequest request) {

        Map<String, Object> details = ex.getExpiredAt() != null
                ? Map.of("expired_at", ex.getExpiredAt().toString())
                : Map.of();
        ErrorResponse response = ErrorResponse.withDetails(
                "INVITE_EXPIRED", "This invitation has expired.", details);
        log.warn("Invitation expired on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(response);
    }

    @ExceptionHandler(InvitationRevokedException.class)
    public ResponseEntity<ErrorResponse> handleInvitationRevoked(
            InvitationRevokedException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of("INVITE_REVOKED", "This invitation has been revoked.");
        log.warn("Invitation revoked on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(response);
    }

    @ExceptionHandler(InvitationExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleInvitationExhausted(
            InvitationExhaustedException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of("INVITE_EXHAUSTED",
                "This invitation has reached its maximum number of uses.");
        log.warn("Invitation exhausted on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(response);
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(
            SessionExpiredException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of("SESSION_EXPIRED",
                "Your session has expired. Please start over.");
        log.warn("Session expired on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(PhoneAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handlePhoneRegistered(
            PhoneAlreadyRegisteredException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.withDetails(
                "PHONE_REGISTERED",
                "This phone number is already registered.",
                Map.of("login_url", "/login"));
        log.warn("Duplicate phone detected on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(StepOutOfOrderException.class)
    public ResponseEntity<ErrorResponse> handleStepOutOfOrder(
            StepOutOfOrderException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.withDetails(
                "STEP_OUT_OF_ORDER", ex.getMessage(),
                Map.of("current_step", ex.getCurrentStep(),
                       "attempted_step", ex.getAttemptedStep()));
        log.warn("Step out of order on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(MaxRetriesExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxRetries(
            MaxRetriesExceededException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.withDetails(
                "MAX_RETRIES_EXCEEDED",
                "Maximum activation retries reached. Please contact support.",
                Map.of("retry_count", ex.getRetryCount(), "support_url", "/support"));
        log.warn("Max retries exceeded on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(OnboardingRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            OnboardingRateLimitException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.withDetails(
                "RATE_LIMIT_EXCEEDED",
                "Too many attempts. Please try again later.",
                Map.of("retry_after", ex.getRetryAfterSeconds()));
        log.warn("Rate limit exceeded on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(CsrfValidationException.class)
    public ResponseEntity<ErrorResponse> handleCsrfFailure(
            CsrfValidationException ex, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of("CSRF_INVALID", "Invalid or missing CSRF token.");
        log.warn("CSRF validation failed on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse response = ErrorResponse.of("INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
