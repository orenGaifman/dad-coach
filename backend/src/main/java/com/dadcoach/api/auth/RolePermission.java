package com.dadcoach.api.auth;

import java.util.UUID;

/**
 * Role-to-permission mapping and resource ownership enforcement.
 * <p>
 * Key security invariant: Father actors NEVER receive a 403 (Forbidden) response
 * for another father's resources. Instead, they always receive 404 (Not Found)
 * to prevent resource enumeration attacks.
 * <p>
 * Ownership check: for Father actors, access is granted only when
 * {@code resource.fatherId == actor.fatherId}.
 */
public final class RolePermission {

    private RolePermission() {
        // Utility class — no instantiation
    }

    /**
     * Admin permissions for role-based access control on the Admin API.
     */
    public enum AdminPermission {
        /** View any father's data, conversations, memories, audit logs */
        READ,
        /** Modify father status, override settings, force state transitions */
        WRITE,
        /** Initiate account deletion, purge data */
        DELETE
    }

    /**
     * Verifies that the current actor owns the target resource.
     * <p>
     * For FATHER actors: returns true only if the resource's fatherId matches the actor's ID.
     * For ADMIN/SERVICE actors: always returns true (admins can access any resource).
     * <p>
     * When this method returns false for a Father actor, callers MUST throw a
     * ResourceNotFoundException (404), NOT a ForbiddenException (403), to prevent
     * resource enumeration.
     *
     * @param actor             the current actor context
     * @param resourceFatherId  the fatherId that owns the target resource
     * @return true if the actor is authorized to access the resource
     */
    public static boolean isOwner(ActorContext actor, UUID resourceFatherId) {
        if (actor == null || resourceFatherId == null) {
            return false;
        }

        // Admin and Service actors can access any resource
        if (actor.isAdmin() || actor.isService()) {
            return true;
        }

        // Father actors can only access their own resources
        return actor.getActorId().equals(resourceFatherId);
    }

    /**
     * Asserts resource ownership for the current actor.
     * <p>
     * If the actor does not own the resource, throws {@link ResourceNotOwnedException}
     * which should be translated to a 404 (Not Found) response — never 403 (Forbidden).
     *
     * @param actor             the current actor context
     * @param resourceFatherId  the fatherId that owns the target resource
     * @param resourceType      the type of resource (for error messaging)
     * @param resourceId        the ID of the resource (for error messaging)
     * @throws ResourceNotOwnedException if the actor does not own the resource
     */
    public static void assertOwnership(ActorContext actor, UUID resourceFatherId,
                                       String resourceType, UUID resourceId) {
        if (!isOwner(actor, resourceFatherId)) {
            // Always 404 for Father actors — prevents enumeration
            throw new ResourceNotOwnedException(resourceType, resourceId);
        }
    }

    /**
     * Checks if the given actor type has a specific admin permission.
     * <p>
     * Only ADMIN actors can have admin permissions. FATHER and SERVICE actors
     * never have admin permissions (SERVICE uses separate service-level access).
     *
     * @param actor      the current actor context
     * @param permission the required admin permission
     * @return true if the actor has the specified permission
     */
    public static boolean hasAdminPermission(ActorContext actor, AdminPermission permission) {
        if (actor == null || !actor.isAdmin()) {
            return false;
        }
        // All authenticated ADMIN actors have READ permission.
        // WRITE and DELETE require additional role claims (future enhancement).
        // For now, authenticated ADMINs have all permissions.
        return true;
    }

    /**
     * Exception thrown when a resource ownership check fails.
     * <p>
     * This MUST be caught and translated to a 404 (Not Found) response,
     * not a 403 (Forbidden), to prevent resource enumeration by Father actors.
     */
    public static class ResourceNotOwnedException extends RuntimeException {

        private final String resourceType;
        private final UUID resourceId;

        public ResourceNotOwnedException(String resourceType, UUID resourceId) {
            super(String.format("%s not found: %s", resourceType, resourceId));
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }

        public String getResourceType() {
            return resourceType;
        }

        public UUID getResourceId() {
            return resourceId;
        }
    }
}
