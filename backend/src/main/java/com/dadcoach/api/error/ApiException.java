package com.dadcoach.api.error;

import org.springframework.http.HttpStatus;

/**
 * A failure with a stable machine-readable code. REST maps it to its HTTP status; the tool layer
 * (the tool layer) maps the same code into the {success:false, error_code} envelope so the agent can
 * explain the business rejection instead of seeing a transport error.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
