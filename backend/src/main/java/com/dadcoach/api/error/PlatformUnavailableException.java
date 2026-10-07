package com.dadcoach.api.error;

import org.springframework.http.HttpStatus;

/** The AI Workflow Platform could not produce a turn (disabled, down, circuit open, timed out). */
public class PlatformUnavailableException extends ApiException {

    public PlatformUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE", message);
    }
}
