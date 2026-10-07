package com.dadcoach.config;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.api.error.ErrorResponse;
import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.common.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to {@link ErrorResponse} (Tair's shape). Unexpected exceptions answer a generic 500 with the
 * correlation id — internals never reach the client; the log has them under the same id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return respond(e.status(), e.code(), e.getMessage(), List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getEntityType() + " not found", List.of());
    }

    @ExceptionHandler({BusinessRuleViolationException.class, InvalidStateTransitionException.class})
    ResponseEntity<ErrorResponse> businessRule(RuntimeException e) {
        String code = e instanceof BusinessRuleViolationException b ? b.getRuleName() : "INVALID_STATE";
        return respond(HttpStatus.CONFLICT, code, e instanceof BusinessRuleViolationException b2 ? b2.getDetail() : e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldProblem> problems = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldProblem(f.getField(), f.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is invalid", problems);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request body is not readable", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noRoute(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.atError().setMessage("http.unexpected_error").setCause(e)
                .addKeyValue("correlationId", CorrelationIdFilter.currentCorrelationId()).log();
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong", List.of());
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message,
                                                         List<ErrorResponse.FieldProblem> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), code, message,
                CorrelationIdFilter.currentCorrelationId(), details));
    }
}
