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
 * Resolved by {@link AuthActorArgumentResolver}, which retrieves the actor from the
 * thread-local {@link ActorContext} populated by {@link ActorContextFilter}.
 * <p>
 * If no authenticated actor is present when this annotation is used, the resolver
 * throws a 401 Unauthorized response.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthActor {
}
