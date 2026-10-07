package com.dadcoach.api.error;

import org.springframework.http.HttpStatus;

/**
 * 409 with a specific business code (SLOT_TAKEN, OUTSIDE_AVAILABILITY, ...). These are expected
 * outcomes the agent or the dashboard turns into a sentence for the person — not errors.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
