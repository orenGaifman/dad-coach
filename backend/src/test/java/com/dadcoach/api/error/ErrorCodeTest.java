package com.dadcoach.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link ErrorCode} enum.
 */
class ErrorCodeTest {

    @Test
    void allErrorCodes_haveCorrectHttpStatus() {
        assertThat(ErrorCode.VALIDATION_FAILED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.FIELD_REQUIRED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.FIELD_INVALID.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.TOKEN_EXPIRED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.STATE_TRANSITION_INVALID.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.LIMIT_EXCEEDED.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.OPERATION_NOT_ALLOWED.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.RATE_LIMIT_EXCEEDED.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void retryable_onlyRateLimitAndInternalError() {
        for (ErrorCode code : ErrorCode.values()) {
            if (code == ErrorCode.RATE_LIMIT_EXCEEDED || code == ErrorCode.INTERNAL_ERROR) {
                assertThat(code.isRetryable())
                        .as("Expected %s to be retryable", code)
                        .isTrue();
            } else {
                assertThat(code.isRetryable())
                        .as("Expected %s to NOT be retryable", code)
                        .isFalse();
            }
        }
    }

    @Test
    void typeUri_followsConvention() {
        assertThat(ErrorCode.LIMIT_EXCEEDED.getTypeUri())
                .isEqualTo("https://dadcoach.app/errors/LIMIT_EXCEEDED");
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getTypeUri())
                .isEqualTo("https://dadcoach.app/errors/RESOURCE_NOT_FOUND");
    }

    @Test
    void allErrorCodes_haveNonNullTitle() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getTitle())
                    .as("ErrorCode %s should have a non-null title", code)
                    .isNotNull()
                    .isNotBlank();
        }
    }
}
