package com.dadcoach.api.error;

import org.springframework.http.HttpStatus;

/** 404 — also the answer for another father's data (someone else's session is 404, never 403). */
public class NotFoundException extends ApiException {

    public NotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }
}
