package com.dadcoach.api.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for injecting the current {@link ActorContext} into controller method parameters.
 * <p>
 * Usage:
 * <pre>{@code
 * @GetMapping("/api/v1/fathers/me/children/{childId}")
 * public ChildResponseDto getChild(@PathVariable UUID childId, @AuthActor ActorContext actor) {
 *     // actor is the authenticated actor for this request
 * }
 * }</pre>
 * <p>
 * For optional actor context (e.g., admin endpoints that work with or without auth):
 * <pre>{@code
 * @GetMapping("/api/v1/admin/fathers")
 * public ResponseEntity<?> listFathers(@AuthActor(required = false) @Nullable ActorContext actor) {
 *     // actor may be null if no authentication is present
 * }
 * }</pre>
 * <p>
 * Resolved by {@link AuthActorArgumentResolver}, which retrieves the actor from the
 * thread-local {@link ActorContext} populated by {@link ActorContextFilter}.
 * <p>
 * If no authenticated actor is present and {@code required = true} (the default),
 * the resolver throws a 401 Unauthorized response. If {@code required = false},
 * the parameter will be null.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthActor {
    /**
     * Whether an authenticated actor is required.
     * <p>
     * If {@code true} (default), a 401 error is thrown when no actor is present.
     * If {@code false}, the parameter will be null when no actor is present.
     *
     * @return true if authentication is required, false if optional
     */
    boolean required() default true;
}
