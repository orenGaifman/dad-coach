package com.dadcoach.api.dev;

/**
 * Thrown when a father resource is not found in dev API operations.
 *
 * <p>This exception is specific to the Dev Dashboard API and results in
 * an HTTP 404 Not Found response.</p>
 */
public class FatherNotFoundException extends RuntimeException {

    private final Long fatherId;

    public FatherNotFoundException(Long fatherId) {
        super("Father not found with id: " + fatherId);
        this.fatherId = fatherId;
    }

    public Long getFatherId() {
        return fatherId;
    }
}
