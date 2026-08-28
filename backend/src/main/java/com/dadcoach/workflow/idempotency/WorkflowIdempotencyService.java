package com.dadcoach.workflow.idempotency;

import com.dadcoach.channel.dto.OutboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * <p>Implements duplicate detection for WorkflowEngine (fixes duplicate message bug).
 * Uses two-level detection:
 * <ul>
 *   <li>Primary: WhatsApp message ID (idempotency key) - 1 hour TTL</li>
 *   <li>Secondary: Content fingerprint (sender + content hash) - 60 second TTL</li>
 * </ul>
 * </p>
 */
@Service
public class WorkflowIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowIdempotencyService.class);
    
    /** TTL for cached responses by idempotency key - 1 hour should be plenty for WhatsApp retries */
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    
    /** TTL for content fingerprint cache - 60 seconds for content-based duplicate detection */
    private static final Duration FINGERPRINT_TTL = Duration.ofSeconds(60);
    
    /** In-memory cache: idempotencyKey -> CachedResponse */
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    
    /** Content fingerprint cache: contentFingerprint -> CachedResponse */
    private final Map<String, CachedResponse> contentFingerprintCache = new ConcurrentHashMap<>();
    
    /** 
     * Set of content fingerprints currently being processed (in-flight).
     * Prevents race conditions where two identical messages arrive before either finishes processing.
     */
    private final Set<String> inFlightFingerprints = ConcurrentHashMap.newKeySet();
    
    /**
     * Record of a cached response with its creation timestamp.
     */
    private record CachedResponse(OutboundMessageDto response, Instant createdAt) {
        /**
         * Checks if this cached response has expired using the standard TTL (1 hour).
         */
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(CACHE_TTL));
        }
        
        /**
         * Checks if this cached response has expired using the short TTL (60 seconds).
         * Used for content fingerprint cache.
         */
        boolean isExpiredShort() {
            return Instant.now().isAfter(createdAt.plus(FINGERPRINT_TTL));
        }
    }
    
    /**
     * Checks if a message with the given idempotency key has already been processed.
     * This is the backward-compatible version that only checks by idempotency key.
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
     * Checks if a message is a duplicate using both idempotency key and content fingerprint.
     * This enhanced version detects duplicates even when messages have different webhook delivery IDs
     * but identical content from the same sender within a 60-second window.
     * 
     * <p>Also prevents race conditions by tracking messages currently being processed (in-flight).
     * If an identical message is already being processed, this returns empty but does NOT mark
     * it as in-flight (caller should use {@link #markInFlight} to do that).</p>
     * 
     * @param idempotencyKey the unique key from the inbound message (WhatsApp message ID)
     * @param sender the sender identifier (e.g., phone number or father channel identity)
     * @param content the message text content
     * @return Optional containing the cached response if duplicate, empty if new message
     */
    public Optional<OutboundMessageDto> checkDuplicate(String idempotencyKey, String sender, String content) {
        // Check primary key first (existing logic)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CachedResponse cached = cache.get(idempotencyKey);
            if (cached != null && !cached.isExpired()) {
                log.info("Duplicate detected by idempotency key: {}", idempotencyKey);
                return Optional.of(cached.response());
            }
        }
        
        // Check content fingerprint (new logic for detecting identical content)
        if (sender != null && content != null) {
            String fingerprint = generateContentFingerprint(sender, content);
            CachedResponse fingerprintCached = contentFingerprintCache.get(fingerprint);
            if (fingerprintCached != null && !fingerprintCached.isExpiredShort()) {
                log.info("Duplicate detected by content fingerprint for sender: {}", sender);
                return Optional.of(fingerprintCached.response());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Marks a message as in-flight (currently being processed).
     * This prevents race conditions where two identical messages arrive before either finishes processing.
     * 
     * @param sender the sender identifier (e.g., phone number or father channel identity)
     * @param content the message text content
     * @return true if the message was successfully marked as in-flight (new message),
     *         false if an identical message is already being processed (duplicate)
     */
    public boolean markInFlight(String sender, String content) {
        if (sender == null || content == null) {
            return true; // Allow processing if we can't generate fingerprint
        }
        
        String fingerprint = generateContentFingerprint(sender, content);
        boolean added = inFlightFingerprints.add(fingerprint);
        
        if (!added) {
            log.info("Race condition prevented: identical message already in-flight for sender: {}", sender);
        } else {
            log.debug("Message marked in-flight for sender: {}", sender);
        }
        
        return added;
    }
    
    /**
     * Removes a message from the in-flight set after processing completes.
     * Should be called in a finally block to ensure cleanup.
     * 
     * @param sender the sender identifier (e.g., phone number or father channel identity)
     * @param content the message text content
     */
    public void clearInFlight(String sender, String content) {
        if (sender == null || content == null) {
            return;
        }
        
        String fingerprint = generateContentFingerprint(sender, content);
        inFlightFingerprints.remove(fingerprint);
        log.debug("Message cleared from in-flight for sender: {}", sender);
    }
    
    /**
     * Generates a content fingerprint from sender + content hash.
     * The fingerprint is used to detect duplicate messages with identical content
     * but different idempotency keys (e.g., WhatsApp webhook retries).
     *
     * @param sender the sender identifier
     * @param content the message content
     * @return SHA-256 hex hash of normalized sender|content
     */
    private String generateContentFingerprint(String sender, String content) {
        String normalized = (sender + "|" + content.trim().toLowerCase()).strip();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java, but handle the exception anyway
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash code (less collision-resistant)
            return Integer.toHexString(normalized.hashCode());
        }
    }
    
    /**
     * Records a processed message to prevent future duplicates.
     * This is the backward-compatible version that only records by idempotency key.
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
     * Records a processed message to prevent future duplicates using both idempotency key
     * and content fingerprint. This ensures duplicates are detected even when messages
     * have different webhook delivery IDs but identical content.
     * 
     * @param idempotencyKey the unique key from the inbound message (WhatsApp message ID)
     * @param sender the sender identifier (e.g., phone number or father channel identity)
     * @param content the message text content
     * @param response the response that was generated and sent
     */
    public void recordProcessed(String idempotencyKey, String sender, String content, OutboundMessageDto response) {
        Instant now = Instant.now();
        
        // Record by idempotency key (existing logic)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cache.put(idempotencyKey, new CachedResponse(response, now));
            log.debug("Recorded idempotency key '{}' with response (expires in {} hour(s))", 
                    idempotencyKey, CACHE_TTL.toHours());
        }
        
        // Record by content fingerprint (new logic)
        if (sender != null && content != null) {
            String fingerprint = generateContentFingerprint(sender, content);
            contentFingerprintCache.put(fingerprint, new CachedResponse(response, now));
            log.debug("Recorded content fingerprint for sender '{}' (expires in {} seconds)", 
                    sender, FINGERPRINT_TTL.toSeconds());
        }
        
        // Periodically cleanup expired entries
        if (cache.size() > 1000 || contentFingerprintCache.size() > 1000) {
            cleanupExpired();
        }
    }
    
    /**
     * Removes expired entries from both caches.
     */
    private void cleanupExpired() {
        // Cleanup idempotency key cache
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
        
        // Cleanup content fingerprint cache
        int fingerprintRemovedCount = 0;
        var fingerprintIterator = contentFingerprintCache.entrySet().iterator();
        while (fingerprintIterator.hasNext()) {
            var entry = fingerprintIterator.next();
            if (entry.getValue().isExpiredShort()) {
                fingerprintIterator.remove();
                fingerprintRemovedCount++;
            }
        }
        if (fingerprintRemovedCount > 0) {
            log.info("Cleaned up {} expired fingerprint cache entries", fingerprintRemovedCount);
        }
    }
    
    /**
     * Clears all cached entries from both caches. Useful for testing.
     */
    public void clearCache() {
        cache.clear();
        contentFingerprintCache.clear();
        inFlightFingerprints.clear();
        log.debug("Idempotency caches cleared");
    }
    
    /**
     * Returns the current idempotency key cache size. Useful for monitoring.
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Returns the current content fingerprint cache size. Useful for monitoring.
     */
    public int getFingerprintCacheSize() {
        return contentFingerprintCache.size();
    }
}
