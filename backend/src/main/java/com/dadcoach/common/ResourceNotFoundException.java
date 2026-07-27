package com.dadcoach.common;

/**
 * Thrown when a requested entity cannot be found.
 *
 * <p>This exception captures the entity type and the identifier used
 * in the lookup to provide clear diagnostic information.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String entityType;
    private final Object identifier;

    /**
     * Creates a new ResourceNotFoundException.
     *
     * @param entityType the type of entity (e.g., "Father", "Child", "Mission")
     * @param identifier the identifier used to look up the entity (e.g., ID or phone number)
     */
    public ResourceNotFoundException(String entityType, Object identifier) {
        super(formatMessage(entityType, identifier));
        this.entityType = entityType;
        this.identifier = identifier;
    }

    public String getEntityType() {
        return entityType;
    }

    public Object getIdentifier() {
        return identifier;
    }

    @Override
    public String getMessage() {
        return formatMessage(entityType, identifier);
    }

    private static String formatMessage(String entityType, Object identifier) {
        return String.format("%s not found with identifier: %s", entityType, identifier);
    }
}
