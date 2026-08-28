package com.dadcoach.api.error;

import com.dadcoach.ai.AiRateLimitExceededException;
import com.dadcoach.api.auth.RolePermission;
import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.common.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler that formats all API errors as RFC 9457 Problem Details.
 *
 * <p>Error categories handled:
 * <ul>
 *   <li>400 - VALIDATION_FAILED, FIELD_REQUIRED, FIELD_INVALID (Jakarta Bean Validation)</li>
 *   <li>401 - UNAUTHORIZED, TOKEN_EXPIRED (Spring Security authentication)</li>
 *   <li>404 - RESOURCE_NOT_FOUND (also covers ownership mismatch)</li>
 *   <li>409 - STATE_TRANSITION_INVALID, DUPLICATE_RESOURCE</li>
 *   <li>422 - LIMIT_EXCEEDED, OPERATION_NOT_ALLOWED</li>
 *   <li>429 - RATE_LIMIT_EXCEEDED</li>
 *   <li>500 - INTERNAL_ERROR (sanitized, no stack traces)</li>
 * </ul>
 *
 * <p>500 errors NEVER expose stack traces or internal details to consumers.</p>
 */
@Order(1)
@RestControllerAdvice(basePackages = "com.dadcoach.api")
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    // --- 400: Validation Failures ---

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String requestId = generateRequestId();
        String instance = getRequestUri(request);

        List<ProblemDetail.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ProblemDetail.FieldError(
                        error.getField(),
                        error.getDefaultMessage(),
                        determineFieldErrorCode(error.getCode())))
                .toList();

        String detail = String.format("Validation failed with %d error(s).", fieldErrors.size());

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.VALIDATION_FAILED, detail, instance, requestId, fieldErrors);

        log.warn("Validation failed on {} [request_id={}]: {} field error(s)",
                instance, requestId, fieldErrors.size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- 401: Authentication Failures ---

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        ErrorCode code = isTokenExpired(ex) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;

        ProblemDetail problemDetail = ProblemDetail.of(
                code, code.getTitle(), request.getRequestURI(), requestId);

        log.warn("Authentication failure on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.UNAUTHORIZED, "Invalid credentials.", request.getRequestURI(), requestId);

        log.warn("Bad credentials on {} [request_id={}]", request.getRequestURI(), requestId);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        // For Father actors, ownership mismatch is handled as 404 by ResourceNotFoundException.
        // AccessDeniedException only applies to Admin/Service role-based denials.
        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.UNAUTHORIZED, "Access denied.", request.getRequestURI(), requestId);

        log.warn("Access denied on {} [request_id={}]", request.getRequestURI(), requestId);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- 400: IllegalArgument (custom business validation) ---

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.FIELD_INVALID, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Illegal argument on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- 404: Resource Not Found ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Resource not found on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(RolePermission.ResourceNotOwnedException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotOwned(
            RolePermission.ResourceNotOwnedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        // Ownership mismatch always returns 404 (not 403) to prevent enumeration
        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Resource ownership mismatch on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- 409: Conflicts ---

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidStateTransition(
            InvalidStateTransitionException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.STATE_TRANSITION_INVALID, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Invalid state transition on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.DUPLICATE_RESOURCE, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Duplicate resource on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- 422: Business Rule Violations ---

    @ExceptionHandler(LimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleLimitExceeded(
            LimitExceededException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.LIMIT_EXCEEDED, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Limit exceeded on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRuleViolation(
            BusinessRuleViolationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.LIMIT_EXCEEDED, ex.getDetail(), request.getRequestURI(), requestId);

        log.warn("Business rule violation on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(OperationNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleOperationNotAllowed(
            OperationNotAllowedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.OPERATION_NOT_ALLOWED, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Operation not allowed on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- 429: Rate Limit ---

    @ExceptionHandler(AiRateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleAiRateLimitExceeded(
            AiRateLimitExceededException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        // User-friendly Hebrew message for AI credits exhaustion
        String detail = "נגמרו הקרדיטים היומיים לשיחה עם הבוט. הקרדיטים יתחדשו מחר בחצות. " +
                        "בינתיים, אתה יכול לצפות במשימות הקיימות או לדווח על זמן איכות.";

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.AI_CREDITS_EXHAUSTED, detail, request.getRequestURI(), requestId);

        log.warn("AI credits exhausted for father [request_id={}]: {}/{}",
                requestId, ex.getCurrentCount(), ex.getMaxAllowed());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(PROBLEM_JSON)
                .header("Retry-After", String.valueOf(getSecondsUntilMidnight()))
                .body(problemDetail);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.RATE_LIMIT_EXCEEDED, ex.getMessage(), request.getRequestURI(), requestId);

        log.warn("Rate limit exceeded on {} [request_id={}]", request.getRequestURI(), requestId);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(problemDetail);
    }

    // --- 500: Internal Errors (sanitized) ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(
            Exception ex, HttpServletRequest request) {

        String requestId = generateRequestId();

        // NEVER expose stack traces or internal details to consumers
        ProblemDetail problemDetail = ProblemDetail.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                requestId);

        // Log full details server-side for debugging
        log.error("Unhandled exception on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(PROBLEM_JSON)
                .body(problemDetail);
    }

    // --- Helpers ---

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    private String getRequestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return "";
    }

    private boolean isTokenExpired(AuthenticationException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("expired");
    }

    /**
     * Maps Jakarta Bean Validation constraint code to an API error code string.
     */
    private String determineFieldErrorCode(String validationCode) {
        if (validationCode == null) {
            return ErrorCode.FIELD_INVALID.name();
        }
        return switch (validationCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> ErrorCode.FIELD_REQUIRED.name();
            default -> ErrorCode.FIELD_INVALID.name();
        };
    }

    /**
     * Calculate seconds until midnight (Israel timezone) for AI credit reset.
     */
    private long getSecondsUntilMidnight() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Jerusalem"));
        java.time.ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        return java.time.Duration.between(now, midnight).getSeconds();
    }
}
