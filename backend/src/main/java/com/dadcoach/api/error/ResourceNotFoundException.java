package com.dadcoach.api.error;

/**
 * Thrown when a resource is not found or the actor does not own the resource.
 *
 * <p>Ownership mismatches return 404 (not 403) to prevent resource enumeration.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s not found: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }
}
