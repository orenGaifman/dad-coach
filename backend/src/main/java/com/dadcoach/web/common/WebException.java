package com.dadcoach.web.common;

import org.springframework.http.HttpStatus;

/** A refusal with a stable code; the message is English for logs, never shown to a person. */
public class WebException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public WebException(HttpStatus status, String code, String message) {
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

    /** Missing, or someone else's: the same answer (never 403 for another person's object). */
    public static WebException notFound() {
        return new WebException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found");
    }

    public static WebException conflict(String code, String message) {
        return new WebException(HttpStatus.CONFLICT, code, message);
    }

    public static WebException badRequest(String code, String message) {
        return new WebException(HttpStatus.BAD_REQUEST, code, message);
    }
}
