package com.dadcoach.systemstate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Thread-local cache for {@link SystemState} during a single request processing cycle.
 * 
 * <p>This cache implements the request-scoped caching requirement (Requirement 2.4):
 * "The Workflow_Engine SHALL cache the System_State for the duration of a single 
 * request processing cycle. Each new request SHALL reload state from authoritative sources."</p>
 * 
 * <p>The cache follows the same pattern as {@link com.dadcoach.api.auth.ActorContext},
 * using ThreadLocal storage that is cleared after each request by 
 * {@link SystemStateCacheFilter}.</p>
 * 
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // At the start of request processing, load and cache state:
 * SystemState state = systemStateLoader.loadState(fatherId);
 * SystemStateCache.set(fatherId, state);
 * 
 * // Later in the same request, retrieve cached state:
 * SystemState cached = SystemStateCache.get(fatherId);
 * if (cached != null) {
 *     // Use cached state
 * } else {
 *     // Cache miss - load fresh
 * }
 * 
 * // After request completes (handled by SystemStateCacheFilter):
 * SystemStateCache.clear();
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>Each thread has its own cache entry via ThreadLocal, so concurrent requests
 * on different threads do not interfere with each other.</p>
 * 
 * <h2>Memory Management</h2>
 * <p>The cache is cleared after each request by {@link SystemStateCacheFilter} 
 * to prevent memory leaks in servlet container thread pools.</p>
 * 
 * @see SystemState
 * @see SystemStateCacheFilter
 * @see <a href="Requirement 2.4">Request-Scoped State Caching</a>
 */
public final class SystemStateCache {

    private static final Logger log = LoggerFactory.getLogger(SystemStateCache.class);
    
    /**
     * Thread-local storage for cached state entry.
     * Each thread maintains its own cache entry for the duration of a request.
     */
    private static final ThreadLocal<CacheEntry> CACHE = new ThreadLocal<>();
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private SystemStateCache() {
        // Utility class
    }
    
    /**
     * Stores the system state in the cache for the current request.
     * 
     * <p>If a state is already cached for the same father ID, it will be replaced.
     * If a state is cached for a different father ID, a warning is logged and
     * the new state replaces the old one (this should not happen in normal operation).</p>
     * 
     * @param fatherId the father ID associated with this state
     * @param state the system state to cache
     * @throws IllegalArgumentException if fatherId or state is null
     */
    public static void set(UUID fatherId, SystemState state) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        
        CacheEntry existing = CACHE.get();
        if (existing != null && !existing.fatherId.equals(fatherId)) {
            log.warn("Replacing cached state for father {} with state for different father {}. " +
                    "This may indicate a bug in request handling.", existing.fatherId, fatherId);
        }
        
        CACHE.set(new CacheEntry(fatherId, state));
        log.debug("Cached SystemState for father {} in workflow state {}", 
                fatherId, state.workflowState());
    }
    
    /**
     * Retrieves the cached system state for the specified father.
     * 
     * <p>Returns null if:</p>
     * <ul>
     *   <li>No state is cached for this request</li>
     *   <li>The cached state is for a different father ID</li>
     * </ul>
     * 
     * @param fatherId the father ID to retrieve state for
     * @return the cached SystemState, or null if not cached or cached for different father
     */
    public static SystemState get(UUID fatherId) {
        if (fatherId == null) {
            return null;
        }
        
        CacheEntry entry = CACHE.get();
        if (entry == null) {
            log.debug("No cached SystemState found for father {}", fatherId);
            return null;
        }
        
        if (!entry.fatherId.equals(fatherId)) {
            log.debug("Cached SystemState is for different father {} (requested: {})", 
                    entry.fatherId, fatherId);
            return null;
        }
        
        log.debug("Returning cached SystemState for father {}", fatherId);
        return entry.state;
    }
    
    /**
     * Retrieves the cached system state if any exists, regardless of father ID.
     * 
     * <p>This is useful when the caller knows there should be exactly one cached
     * state for the current request and wants to avoid passing the father ID.</p>
     * 
     * @return the cached SystemState, or null if no state is cached
     */
    public static SystemState current() {
        CacheEntry entry = CACHE.get();
        return entry != null ? entry.state : null;
    }
    
    /**
     * Checks if a system state is currently cached.
     * 
     * @return true if a state is cached for this request
     */
    public static boolean isCached() {
        return CACHE.get() != null;
    }
    
    /**
     * Checks if a system state is cached for the specified father.
     * 
     * @param fatherId the father ID to check
     * @return true if a state is cached for this father in this request
     */
    public static boolean isCached(UUID fatherId) {
        if (fatherId == null) {
            return false;
        }
        CacheEntry entry = CACHE.get();
        return entry != null && entry.fatherId.equals(fatherId);
    }
    
    /**
     * Clears the cached state for the current thread.
     * 
     * <p><b>IMPORTANT:</b> This method MUST be called after every request completes
     * to prevent memory leaks in thread pools. The {@link SystemStateCacheFilter}
     * handles this automatically for HTTP requests.</p>
     */
    public static void clear() {
        CacheEntry entry = CACHE.get();
        if (entry != null) {
            log.debug("Clearing cached SystemState for father {}", entry.fatherId);
        }
        CACHE.remove();
    }
    
    /**
     * Internal cache entry holding the father ID and associated state.
     * Immutable record to ensure thread safety.
     */
    private record CacheEntry(UUID fatherId, SystemState state) {
        CacheEntry {
            if (fatherId == null) {
                throw new IllegalArgumentException("fatherId must not be null");
            }
            if (state == null) {
                throw new IllegalArgumentException("state must not be null");
            }
        }
    }
}
