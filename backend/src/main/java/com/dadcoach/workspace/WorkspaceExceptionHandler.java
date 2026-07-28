package com.dadcoach.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Exception handler for the workspace bounded context.
 *
 * <p>Handles workspace-specific exceptions and formats them as consistent
 * error responses with error_code, message, timestamp, and path.</p>
 */
@Order(2)
@RestControllerAdvice(basePackages = "com.dadcoach.workspace")
public class WorkspaceExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    // --- Workspace-specific exceptions ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateSignalException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleDuplicateSignal(
            DuplicateSignalException ex, HttpServletRequest request) {

        log.warn("Duplicate signal on {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {

        log.warn("Rate limit exceeded on {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(WorkspaceErrorResponse.of(
                        ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(WorkspaceException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleWorkspaceException(
            WorkspaceException ex, HttpServletRequest request) {

        log.warn("Workspace error on {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    // --- Standard exceptions ---

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Validation error on {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(WorkspaceErrorCode.VALIDATION_ERROR, ex.getMessage(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Unknown validation error");

        String instance = getRequestUri(request);
        log.warn("Validation failed on {}: {}", instance, fieldErrors);

        WorkspaceErrorResponse body = WorkspaceErrorResponse.of(
                WorkspaceErrorCode.VALIDATION_ERROR,
                "Validation failed: " + fieldErrors,
                instance);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WorkspaceErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.error("Unhandled exception on {} [request_id={}]: {}",
                request.getRequestURI(), requestId, ex.getMessage(), ex);

        return buildResponse(
                WorkspaceErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again later.",
                request);
    }

    // --- Helpers ---

    private ResponseEntity<WorkspaceErrorResponse> buildResponse(
            WorkspaceErrorCode errorCode, String message, HttpServletRequest request) {

        WorkspaceErrorResponse body = WorkspaceErrorResponse.of(
                errorCode, message, request.getRequestURI());

        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    private String getRequestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return "";
    }

    /**
     * Standard error response body for workspace errors.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkspaceErrorResponse(
            @JsonProperty("error_code") String errorCode,
            String message,
            Instant timestamp,
            String path
    ) {
        public static WorkspaceErrorResponse of(WorkspaceErrorCode code, String message, String path) {
            return new WorkspaceErrorResponse(code.getCode(), message, Instant.now(), path);
        }
    }
}
