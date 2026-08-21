package com.dadcoach.api.dev;

import com.dadcoach.api.dev.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for Dev Dashboard API endpoints.
 *
 * <p>Handles dev-specific exceptions with sanitized error responses that
 * do not expose stack traces or internal details. This handler is scoped
 * to the dev package and takes precedence over the global API exception handler.</p>
 *
 * <p>Validates: Requirements 5.4</p>
 */
@Order(0) // Higher priority than ApiExceptionHandler (Order 1)
@RestControllerAdvice(basePackages = "com.dadcoach.api.dev")
public class DevExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DevExceptionHandler.class);

    /**
     * Handles DevEndpointsDisabledException - returns HTTP 403 Forbidden.
     *
     * <p>This is thrown when dev endpoints are accessed in a production environment.
     * The response is sanitized to not expose any internal details.</p>
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return HTTP 403 with sanitized error message
     */
    @ExceptionHandler(DevEndpointsDisabledException.class)
    public ResponseEntity<ErrorResponse> handleDevDisabled(
            DevEndpointsDisabledException ex,
            HttpServletRequest request) {

        log.warn("Dev endpoint access rejected in production environment: {}", request.getRequestURI());

        // No stack trace, no internal details - just a sanitized message
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.forbidden());
    }

    /**
     * Handles FatherNotFoundException - returns HTTP 404 Not Found.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return HTTP 404 with error message
     */
    @ExceptionHandler(FatherNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            FatherNotFoundException ex,
            HttpServletRequest request) {

        log.debug("Father not found: {} on {}", ex.getFatherId(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.notFound(ex.getMessage()));
    }

    /**
     * Handles ConstraintViolationException - returns HTTP 400 Bad Request.
     *
     * <p>This handles validation failures from Jakarta Bean Validation annotations
     * like @Max, @Min, etc. on method parameters.</p>
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return HTTP 400 with sanitized error message
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.debug("Constraint violation on {}: {}", request.getRequestURI(), ex.getMessage());

        // Sanitized message - don't expose internal validation details
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest("Invalid parameter value"));
    }
}
