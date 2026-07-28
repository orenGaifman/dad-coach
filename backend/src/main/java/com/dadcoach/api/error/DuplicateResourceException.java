package com.dadcoach.api.error;

/**
 * Thrown when attempting to create a resource that already exists (idempotency conflict).
 */
public class DuplicateResourceException extends RuntimeException {

    private final String resourceType;
    private final String identifier;

    public DuplicateResourceException(String resourceType, String identifier) {
        super(String.format("%s already exists: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getIdentifier() {
        return identifier;
    }
}
