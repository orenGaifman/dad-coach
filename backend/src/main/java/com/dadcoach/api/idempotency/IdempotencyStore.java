package com.dadcoach.api.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for idempotency keys with 24-hour TTL.
 * <p>
 * Composite key = actor_id + idempotency_key header value, ensuring that
 * the same key from different actors represents different operations.
 * <p>
 * Uses ConcurrentHashMap for thread safety. Expired keys are cleaned up
 * periodically via a scheduled task.
 * <p>
 * This implementation can be replaced with Redis for distributed deployments.
 */
@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private static final long TTL_HOURS = 24;

    private final ConcurrentHashMap<String, CachedResponse> store = new ConcurrentHashMap<>();

    /**
     * Represents a cached response for an idempotency key.
     */
    public record CachedResponse(int status, byte[] body, String contentType, Instant expiresAt) {

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Builds the composite key from actor ID and the idempotency key header value.
     */
    public String compositeKey(UUID actorId, String idempotencyKey) {
        return actorId.toString() + ":" + idempotencyKey;
    }

    /**
     * Looks up a cached response for the given composite key.
     * Returns empty if the key doesn't exist or has expired.
     */
    public Optional<CachedResponse> get(String compositeKey) {
        CachedResponse cached = store.get(compositeKey);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.isExpired()) {
            store.remove(compositeKey);
            return Optional.empty();
        }
        return Optional.of(cached);
    }

    /**
     * Stores a response for the given composite key with a 24-hour TTL.
     */
    public void put(String compositeKey, int status, byte[] body, String contentType) {
        Instant expiresAt = Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS);
        store.put(compositeKey, new CachedResponse(status, body, contentType, expiresAt));
    }

    /**
     * Attempts to reserve a key for processing. Returns true if this thread
     * successfully reserved the key (it was not already present), false if
     * another request already holds this key.
     * <p>
     * A reserved key has a sentinel response (status -1) indicating processing is in progress.
     */
    public boolean tryReserve(String compositeKey) {
        Instant expiresAt = Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS);
        CachedResponse sentinel = new CachedResponse(-1, new byte[0], null, expiresAt);
        CachedResponse existing = store.putIfAbsent(compositeKey, sentinel);
        if (existing == null) {
            return true; // Successfully reserved
        }
        // Key already exists — check if expired
        if (existing.isExpired()) {
            // Replace expired entry with our sentinel
            if (store.replace(compositeKey, existing, sentinel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the key is currently being processed (has a sentinel response).
     */
    public boolean isProcessing(String compositeKey) {
        CachedResponse cached = store.get(compositeKey);
        return cached != null && cached.status() == -1 && !cached.isExpired();
    }

    /**
     * Removes a key from the store. Used when processing fails and
     * the key should not remain reserved.
     */
    public void remove(String compositeKey) {
        store.remove(compositeKey);
    }

    /**
     * Periodically cleans up expired keys to prevent memory leaks.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpiredKeys() {
        int removedCount = 0;
        for (Map.Entry<String, CachedResponse> entry : store.entrySet()) {
            if (entry.getValue().isExpired()) {
                store.remove(entry.getKey(), entry.getValue());
                removedCount++;
            }
        }
        if (removedCount > 0) {
            log.info("Cleaned up {} expired idempotency keys", removedCount);
        }
    }

    /**
     * Returns the current number of stored keys (for monitoring/testing).
     */
    public int size() {
        return store.size();
    }
}
