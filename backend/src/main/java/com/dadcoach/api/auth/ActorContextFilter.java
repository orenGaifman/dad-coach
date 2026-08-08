package com.dadcoach.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates {@link ActorContext} from the authenticated
 * {@link JwtAuthFilter.JwtPrincipal} and guarantees cleanup after request completion.
 * <p>
 * This filter runs AFTER Spring Security's authentication chain so that
 * {@code SecurityContextHolder} already contains the authenticated principal.
 * It translates the JWT role into an {@link ActorType} and stores the actor identity
 * in the thread-local {@link ActorContext} for downstream use.
 * <p>
 * The finally block ensures the ThreadLocal is always cleared, preventing leaks
 * in servlet container thread pools.
 */
@Component
@Order(1)
public class ActorContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActorContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            populateActorContext();
            filterChain.doFilter(request, response);
        } finally {
            ActorContext.clear();
        }
    }

    private void populateActorContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtAuthFilter.JwtPrincipal jwtPrincipal)) {
            return;
        }

        ActorType actorType = mapRole(jwtPrincipal.role());
        if (actorType == null) {
            log.warn("Unknown role '{}' for subject '{}', skipping ActorContext population",
                    jwtPrincipal.role(), jwtPrincipal.subject());
            return;
        }

        UUID actorId = resolveActorId(jwtPrincipal, actorType);
        if (actorId == null) {
            log.warn("Unable to resolve actorId for {} actor with subject '{}'",
                    actorType, jwtPrincipal.subject());
            return;
        }

        // For FATHER actors, pass the direct fatherId
        Long fatherId = (actorType == ActorType.FATHER) ? jwtPrincipal.fatherId() : null;
        ActorContext.set(new ActorContext(actorType, actorId, fatherId));
    }

    private ActorType mapRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role.toUpperCase()) {
            case "FATHER" -> ActorType.FATHER;
            case "ADMIN" -> ActorType.ADMIN;
            case "SERVICE" -> ActorType.SERVICE;
            default -> null;
        };
    }

    private UUID resolveActorId(JwtAuthFilter.JwtPrincipal principal, ActorType actorType) {
        // For Father actors, create a UUID where getLeastSignificantBits() returns the father_id
        // This maintains compatibility with existing code that uses actorId.getLeastSignificantBits()
        if (actorType == ActorType.FATHER && principal.fatherId() != null) {
            // Create UUID with fatherId in the least significant bits
            // Most significant bits are 0 (or could be a marker)
            return new UUID(0L, principal.fatherId());
        }

        // For Admin and Service actors, derive ID from the subject claim
        if (principal.subject() != null) {
            try {
                return UUID.fromString(principal.subject());
            } catch (IllegalArgumentException e) {
                // Subject is not a UUID — generate deterministic ID from subject
                return UUID.nameUUIDFromBytes(principal.subject().getBytes());
            }
        }

        return null;
    }
}
