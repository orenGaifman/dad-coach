package com.dadcoach.api.auth;

import java.util.UUID;

/**
 * Thread-local context carrying the authenticated actor's identity for the current request.
 * <p>
 * Populated by {@link ActorContextFilter} after JWT authentication succeeds,
 * and cleared after request completion to prevent thread-local leaks in pooled threads.
 * <p>
 * Usage in controllers via the {@link AuthActor} annotation for parameter injection,
 * or directly via {@link #current()} for service-layer access.
 */
public final class ActorContext {

    private static final ThreadLocal<ActorContext> CONTEXT = new ThreadLocal<>();

    private final ActorType actorType;
    private final UUID actorId;

    public ActorContext(ActorType actorType, UUID actorId) {
        if (actorType == null) {
            throw new IllegalArgumentException("actorType must not be null");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        this.actorType = actorType;
        this.actorId = actorId;
    }

    /**
     * Returns the current actor context for this thread/request.
     *
     * @return the current ActorContext, or null if not set
     */
    public static ActorContext current() {
        return CONTEXT.get();
    }

    /**
     * Sets the actor context for the current thread/request.
     * Called by {@link ActorContextFilter} after successful authentication.
     */
    public static void set(ActorContext context) {
        CONTEXT.set(context);
    }

    /**
     * Clears the actor context for the current thread.
     * MUST be called after request completion to prevent thread-local leaks.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    public ActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    /**
     * Convenience method to check if the current actor is a Father.
     */
    public boolean isFather() {
        return actorType == ActorType.FATHER;
    }

    /**
     * Convenience method to check if the current actor is an Admin.
     */
    public boolean isAdmin() {
        return actorType == ActorType.ADMIN;
    }

    /**
     * Convenience method to check if the current actor is a Service.
     */
    public boolean isService() {
        return actorType == ActorType.SERVICE;
    }

    @Override
    public String toString() {
        return "ActorContext{actorType=" + actorType + ", actorId=" + actorId + "}";
    }
}
