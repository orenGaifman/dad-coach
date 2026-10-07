package com.dadcoach.web.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every dashboard/ops API failure in the {@link ApiError} shape - never a stack trace, never raw backend text. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"com.dadcoach.web", "com.dadcoach.auth", "com.dadcoach.ops"})
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(WebException.class)
    ResponseEntity<ApiError> refused(WebException e) {
        return body(e.status(), e.code(), e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        List<ApiError.FieldProblem> problems = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldProblem(f.getField(), f.getCode()))
                .toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid request", problems);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid request", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "method not allowed", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        log.error("web.request.failed method={} path={}", request.getMethod(), request.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error", List.of());
    }

    public static ResponseEntity<ApiError> body(HttpStatus status, String code, String message, List<ApiError.FieldProblem> details) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message, CorrelationIdFilter.current(), details));
    }
}
