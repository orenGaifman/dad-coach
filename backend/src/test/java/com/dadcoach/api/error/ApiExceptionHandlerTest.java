package com.dadcoach.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link ApiExceptionHandler}.
 *
 * <p>Tests verify RFC 9457 Problem Details formatting, correct HTTP status codes,
 * and that 500 errors never expose internal details.</p>
 */
class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/fathers/me/children");
    }

    @Test
    void handleResourceNotFound_returns404WithProblemDetail() {
        var ex = new ResourceNotFoundException("Child", "abc-123");

        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.getType()).isEqualTo("https://dadcoach.app/errors/RESOURCE_NOT_FOUND");
        assertThat(body.getTitle()).isEqualTo("Resource Not Found");
        assertThat(body.getDetail()).contains("Child not found");
        assertThat(body.getInstance()).isEqualTo("/api/v1/fathers/me/children");
        assertThat(body.getRequestId()).isNotBlank();
        assertThat(body.isRetryable()).isFalse();
    }

    @Test
    void handleLimitExceeded_returns422WithProblemDetail() {
        var ex = new LimitExceededException("children per father", 8, 8);

        ResponseEntity<ProblemDetail> response = handler.handleLimitExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
        assertThat(body.getErrorCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(body.getType()).isEqualTo("https://dadcoach.app/errors/LIMIT_EXCEEDED");
        assertThat(body.getTitle()).isEqualTo("Business Rule Violation");
        assertThat(body.getDetail()).contains("Maximum of 8");
        assertThat(body.isRetryable()).isFalse();
    }

    @Test
    void handleOperationNotAllowed_returns422() {
        var ex = new OperationNotAllowedException("update", "Cannot modify a completed goal");

        ResponseEntity<ProblemDetail> response = handler.handleOperationNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("OPERATION_NOT_ALLOWED");
        assertThat(body.getDetail()).contains("Cannot modify a completed goal");
    }

    @Test
    void handleInvalidStateTransition_returns409() {
        var ex = new InvalidStateTransitionException("Father", 1L, "ACTIVE", "DELETED");

        ResponseEntity<ProblemDetail> response = handler.handleInvalidStateTransition(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("STATE_TRANSITION_INVALID");
        assertThat(body.getDetail()).contains("ACTIVE").contains("DELETED");
    }

    @Test
    void handleDuplicateResource_returns409() {
        var ex = new DuplicateResourceException("Child", "idempotency-key-abc");

        ResponseEntity<ProblemDetail> response = handler.handleDuplicateResource(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
    }

    @Test
    void handleRateLimitExceeded_returns429WithRetryAfterHeader() {
        var ex = new RateLimitExceededException(30);

        ResponseEntity<ProblemDetail> response = handler.handleRateLimitExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(body.isRetryable()).isTrue();
    }

    @Test
    void handleGeneral_returns500WithSanitizedMessage_noStackTraces() {
        var ex = new RuntimeException("Sensitive internal error: DB password is XYZ");

        ResponseEntity<ProblemDetail> response = handler.handleGeneral(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        // Must NOT expose internal details
        assertThat(body.getDetail()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(body.getDetail()).doesNotContain("Sensitive");
        assertThat(body.getDetail()).doesNotContain("DB password");
        assertThat(body.isRetryable()).isTrue();
    }

    @Test
    void handleBusinessRuleViolation_returns422() {
        var ex = new BusinessRuleViolationException("MAX_GOALS_EXCEEDED",
                "Maximum of 5 active goals per father.");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessRuleViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(body.getDetail()).contains("Maximum of 5 active goals");
    }

    @Test
    void problemDetail_containsRequestId() {
        var ex = new ResourceNotFoundException("Goal", "xyz");

        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFound(ex, request);

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRequestId()).isNotNull();
        // Request ID should be a valid UUID format
        assertThat(body.getRequestId()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
