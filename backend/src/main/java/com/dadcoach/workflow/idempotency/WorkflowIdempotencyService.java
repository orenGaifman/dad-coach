package com.dadcoach.workflow.idempotency;

import com.dadcoach.channel.dto.OutboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides idempotency checking for the WorkflowEngine to prevent duplicate message processing.
 * 
 * <p>When WhatsApp sends the same message multiple times (retries), this service ensures
 * that only the first message is processed and subsequent duplicates return the cached response.</p>
 * 
 * <p>Uses an in-memory cache with TTL for fast lookups. For production, this could be
 * backed by Redis for multi-instance deployments.</p>
 * 
 * <p>Implements duplicate detection for WorkflowEngine (fixes duplicate message bug).</p>
 */
@Service
public class WorkflowIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowIdempotencyService.class);
    
    /** TTL for cached responses - 1 hour should be plenty for WhatsApp retries */
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    
    /** In-memory cache: idempotencyKey -> CachedResponse */
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    
    /**
     * Record of a cached response with its creation timestamp.
     */
    private record CachedResponse(OutboundMessageDto response, Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(CACHE_TTL));
        }
    }
    
    /**
     * Checks if a message with the given idempotency key has already been processed.
     * 
     * @param idempotencyKey the unique key from the inbound message (WhatsApp message ID)
     * @return Optional containing the cached response if duplicate, empty if new message
     */
    public Optional<OutboundMessageDto> checkDuplicate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        
        CachedResponse cached = cache.get(idempotencyKey);
        if (cached == null) {
            return Optional.empty();
        }
        
        if (cached.isExpired()) {
            cache.remove(idempotencyKey);
            log.debug("Cached response for key '{}' has expired, removing from cache", idempotencyKey);
            return Optional.empty();
        }
        
        log.info("Duplicate message detected for idempotency key '{}'. Returning cached response.", 
                idempotencyKey);
        return Optional.of(cached.response());
    }
    
    /**
     * Records a processed message to prevent future duplicates.
     * 
     * @param idempotencyKey the unique key from the inbound message (WhatsApp message ID)
     * @param response the response that was generated and sent
     */
    public void recordProcessed(String idempotencyKey, OutboundMessageDto response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Attempted to record processed message with null/blank idempotency key");
            return;
        }
        
        cache.put(idempotencyKey, new CachedResponse(response, Instant.now()));
        log.debug("Recorded idempotency key '{}' with response (expires in {} hour(s))", 
                idempotencyKey, CACHE_TTL.toHours());
        
        // Periodically cleanup expired entries (simple approach - could use scheduled task)
        if (cache.size() > 1000) {
            cleanupExpired();
        }
    }
    
    /**
     * Removes expired entries from the cache.
     */
    private void cleanupExpired() {
        int removedCount = 0;
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removedCount++;
            }
        }
        if (removedCount > 0) {
            log.info("Cleaned up {} expired idempotency cache entries", removedCount);
        }
    }
    
    /**
     * Clears all cached entries. Useful for testing.
     */
    public void clearCache() {
        cache.clear();
        log.debug("Idempotency cache cleared");
    }
    
    /**
     * Returns the current cache size. Useful for monitoring.
     */
    public int getCacheSize() {
        return cache.size();
    }
}
