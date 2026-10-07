package com.dadcoach.api.error;

import org.springframework.http.HttpStatus;

/** 400 VALIDATION_ERROR for service-layer validation that request DTOs cannot express. */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
