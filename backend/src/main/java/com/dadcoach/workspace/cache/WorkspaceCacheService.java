package com.dadcoach.workspace.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-process caching service for workspace data using Caffeine.
 *
 * <p>Provides per-father, per-data-type caching with configurable TTLs,
 * stampede protection via per-key ReentrantLocks, and bulk invalidation.</p>
 */
@Service
public class WorkspaceCacheService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceCacheService.class);
    private static final long STAMPEDE_LOCK_TIMEOUT_MS = 2000;

    private final Cache<String, CacheEntry> cache;
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    public WorkspaceCacheService() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10)) // max TTL fallback
                .recordStats()
                .build();
    }

    /**
     * Retrieves a cached value for the given father and data type.
     *
     * @param fatherId the father's identifier
     * @param dataType the data type key
     * @param type     the expected class of the cached value
     * @return the cached value, or empty if not found or expired
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(UUID fatherId, String dataType, Class<T> type) {
        String key = CacheKeyBuilder.build(fatherId, dataType);
        CacheEntry entry = cache.getIfPresent(key);

        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                cache.invalidate(key);
            }
            return Optional.empty();
        }

        if (!type.isInstance(entry.value())) {
            log.warn("Cache type mismatch for key {}: expected {}, got {}",
                    key, type.getSimpleName(), entry.value().getClass().getSimpleName());
            cache.invalidate(key);
            return Optional.empty();
        }

        return Optional.of((T) entry.value());
    }

    /**
     * Stores a value in the cache with the specified TTL.
     *
     * @param fatherId the father's identifier
     * @param dataType the data type key
     * @param value    the value to cache
     * @param ttl      the time-to-live duration
     */
    public void put(UUID fatherId, String dataType, Object value, Duration ttl) {
        String key = CacheKeyBuilder.build(fatherId, dataType);
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    /**
     * Invalidates a single cache entry for the given father and data type.
     *
     * @param fatherId the father's identifier
     * @param dataType the data type key
     */
    public void invalidate(UUID fatherId, String dataType) {
        String key = CacheKeyBuilder.build(fatherId, dataType);
        cache.invalidate(key);
        keyLocks.remove(key);
    }

    /**
     * Invalidates all cache entries for the given father.
     *
     * @param fatherId the father's identifier
     */
    public void invalidateAll(UUID fatherId) {
        String prefix = CacheKeyBuilder.prefixFor(fatherId);
        cache.asMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(k -> {
                    cache.invalidate(k);
                    keyLocks.remove(k);
                });
    }

    /**
     * Returns whether the cache service is available.
     *
     * @return true (Caffeine is always available in-process)
     */
    public boolean isAvailable() {
        return true;
    }

    /**
     * Gets a value from cache, or computes it using the supplier with stampede protection.
     *
     * <p>Uses a per-key ReentrantLock so only the first request populates the cache.
     * Other concurrent requests wait up to 2 seconds, then fall back to the supplier directly.</p>
     *
     * @param fatherId the father's identifier
     * @param dataType the data type key
     * @param supplier the function to compute the value if not cached
     * @param ttl      the time-to-live for the cached result
     * @return the cached or freshly computed value
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(UUID fatherId, String dataType, Supplier<T> supplier, Duration ttl) {
        String key = CacheKeyBuilder.build(fatherId, dataType);

        // Fast path: check cache first without locking
        CacheEntry entry = cache.getIfPresent(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value();
        }

        // Stampede protection: acquire per-key lock
        ReentrantLock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());

        boolean acquired = false;
        try {
            acquired = lock.tryLock(STAMPEDE_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while waiting for cache lock on key: {}", key);
        }

        try {
            if (acquired) {
                // Double-check after acquiring lock
                entry = cache.getIfPresent(key);
                if (entry != null && !entry.isExpired()) {
                    return (T) entry.value();
                }

                // Compute and store
                T value = supplier.get();
                if (value != null) {
                    cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis()));
                }
                return value;
            } else {
                // Timeout: fall back to direct computation (no caching to avoid races)
                log.debug("Cache lock timeout for key: {}, falling back to source", key);
                return supplier.get();
            }
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    /**
     * Internal cache entry with TTL tracking.
     */
    private record CacheEntry(Object value, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
