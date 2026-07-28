package com.dadcoach.api.idempotency;

import com.dadcoach.api.auth.ActorContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Set;

/**
 * Servlet filter that implements idempotency for mutating requests (POST, PUT, DELETE).
 * <p>
 * When a client provides an {@code Idempotency-Key} header on a mutating request:
 * <ul>
 *   <li>If the key has been seen before (within 24h TTL) for the same actor,
 *       the cached response is returned without re-executing business logic.</li>
 *   <li>If the key is new, the request proceeds normally and the response is cached.</li>
 *   <li>If two concurrent requests arrive with the same key, only one is processed;
 *       the other waits and receives the cached result.</li>
 * </ul>
 * <p>
 * The key is OPTIONAL — if not provided, the request proceeds normally without
 * idempotency guarantees.
 * <p>
 * The key is scoped to the actor_id: the same key from different actors represents
 * different operations.
 * <p>
 * This filter runs AFTER the {@link com.dadcoach.api.auth.ActorContextFilter} (Order 1)
 * so that the actor identity is available for composite key construction.
 */
@Component
@Order(2)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final int MAX_WAIT_ATTEMPTS = 50;
    private static final long WAIT_INTERVAL_MS = 100;

    private final IdempotencyStore idempotencyStore;

    public IdempotencyFilter(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only apply to mutating methods
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check for Idempotency-Key header — if absent, proceed normally
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Need actor context for scoping
        ActorContext actor = ActorContext.current();
        if (actor == null) {
            // No actor context available — proceed without idempotency
            filterChain.doFilter(request, response);
            return;
        }

        String compositeKey = idempotencyStore.compositeKey(actor.getActorId(), idempotencyKey);

        // Check if we already have a cached response for this key
        var cached = idempotencyStore.get(compositeKey);
        if (cached.isPresent()) {
            log.debug("Returning cached response for idempotency key: {}", idempotencyKey);
            writeCachedResponse(response, cached.get());
            return;
        }

        // Try to reserve the key for processing
        if (!idempotencyStore.tryReserve(compositeKey)) {
            // Another request is currently processing this key — wait for it
            IdempotencyStore.CachedResponse result = waitForResult(compositeKey);
            if (result != null) {
                log.debug("Returning result from concurrent request for idempotency key: {}",
                        idempotencyKey);
                writeCachedResponse(response, result);
                return;
            }
            // Timeout waiting — let the request proceed (best-effort)
            log.warn("Timeout waiting for concurrent request with idempotency key: {}",
                    idempotencyKey);
            filterChain.doFilter(request, response);
            return;
        }

        // We have the reservation — execute the request and capture the response
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);

            // Cache the response
            byte[] body = responseWrapper.getContentAsByteArray();
            String contentType = responseWrapper.getContentType();
            int status = responseWrapper.getStatus();

            idempotencyStore.put(compositeKey, status, body, contentType);

            // Copy the cached content to the actual response
            responseWrapper.copyBodyToResponse();
        } catch (Exception e) {
            // Processing failed — remove the reservation so the key can be retried
            idempotencyStore.remove(compositeKey);
            throw e;
        }
    }

    /**
     * Waits for a concurrent request to finish processing the same idempotency key.
     * Returns the cached response once available, or null on timeout.
     */
    private IdempotencyStore.CachedResponse waitForResult(String compositeKey) {
        for (int attempt = 0; attempt < MAX_WAIT_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            // Check if the key is no longer in processing state
            if (!idempotencyStore.isProcessing(compositeKey)) {
                var cached = idempotencyStore.get(compositeKey);
                if (cached.isPresent()) {
                    return cached.get();
                }
                // Key was removed (processing failed) — return null to let request proceed
                return null;
            }
        }
        return null;
    }

    /**
     * Writes a cached response back to the client.
     */
    private void writeCachedResponse(HttpServletResponse response,
                                     IdempotencyStore.CachedResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        if (cached.body() != null && cached.body().length > 0) {
            response.getOutputStream().write(cached.body());
            response.getOutputStream().flush();
        }
    }
}
