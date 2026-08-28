package com.dadcoach.common;

/** Thrown when a requested entity cannot be found. */
public class ResourceNotFoundException extends RuntimeException {
    private final String entityType;
    private final Object identifier;

    public ResourceNotFoundException(String entityType, Object identifier) {
        super(entityType + " not found with identifier: " + identifier);
        this.entityType = entityType;
        this.identifier = identifier;
    }

    public String getEntityType() { return entityType; }
    public Object getIdentifier() { return identifier; }
}
