package com.dadcoach.workspace;

/**
 * Thrown when a workspace resource (father, child, goal, etc.) cannot be found.
 */
public class ResourceNotFoundException extends WorkspaceException {

    private final String entityType;
    private final Object identifier;

    public ResourceNotFoundException(String entityType, Object identifier) {
        super(
                resolveErrorCode(entityType),
                resolveErrorCode(entityType).formatMessage(identifier)
        );
        this.entityType = entityType;
        this.identifier = identifier;
    }

    public String getEntityType() {
        return entityType;
    }

    public Object getIdentifier() {
        return identifier;
    }

    private static WorkspaceErrorCode resolveErrorCode(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "father" -> WorkspaceErrorCode.FATHER_NOT_FOUND;
            case "child" -> WorkspaceErrorCode.CHILD_NOT_FOUND;
            case "goal" -> WorkspaceErrorCode.GOAL_NOT_FOUND;
            default -> WorkspaceErrorCode.RESOURCE_NOT_FOUND;
        };
    }
}
