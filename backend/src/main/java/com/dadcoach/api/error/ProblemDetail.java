package com.dadcoach.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * RFC 9457 Problem Details response structure for the Application API.
 *
 * <p>Example response:
 * <pre>{@code
 * {
 *   "type": "https://dadcoach.app/errors/LIMIT_EXCEEDED",
 *   "title": "Business Rule Violation",
 *   "status": 422,
 *   "detail": "Maximum of 8 children per father. Current count: 8.",
 *   "instance": "/api/v1/fathers/me/children",
 *   "error_code": "LIMIT_EXCEEDED",
 *   "request_id": "550e8400-e29b-41d4-a716-446655440000",
 *   "retryable": false
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("request_id")
    private String requestId;

    private boolean retryable;

    /**
     * Optional field-level validation errors.
     */
    private List<FieldError> errors;

    private ProblemDetail() {
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail, String instance, String requestId) {
        ProblemDetail pd = new ProblemDetail();
        pd.type = errorCode.getTypeUri();
        pd.title = errorCode.getTitle();
        pd.status = errorCode.getHttpStatus().value();
        pd.detail = detail;
        pd.instance = instance;
        pd.errorCode = errorCode.name();
        pd.requestId = requestId;
        pd.retryable = errorCode.isRetryable();
        return pd;
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail, String instance,
                                   String requestId, List<FieldError> errors) {
        ProblemDetail pd = of(errorCode, detail, instance, requestId);
        pd.errors = errors;
        return pd;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    /**
     * Represents a single field-level validation error.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(String field, String message, String code) {
    }
}
