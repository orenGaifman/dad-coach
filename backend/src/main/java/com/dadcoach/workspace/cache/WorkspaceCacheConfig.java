package com.dadcoach.workspace.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Cache configuration for the workspace bounded context.
 *
 * <p>TTLs per data type:</p>
 * <ul>
 *   <li>summary: 60s</li>
 *   <li>children, goals, conversations: 120s</li>
 *   <li>notifications: 30s</li>
 *   <li>belt: 300s</li>
 *   <li>achievements: 600s</li>
 *   <li>weekly_stats: 300s</li>
 *   <li>monthly_stats: 3600s</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class WorkspaceCacheConfig {

    /**
     * Cache TTL definitions for workspace data types.
     */
    public static final Map<String, Duration> CACHE_TTLS = Map.of(
            "summary", Duration.ofSeconds(60),
            "children", Duration.ofSeconds(120),
            "goals", Duration.ofSeconds(120),
            "conversations", Duration.ofSeconds(120),
            "notifications", Duration.ofSeconds(30),
            "belt", Duration.ofSeconds(300),
            "achievements", Duration.ofSeconds(600),
            "weekly_stats", Duration.ofSeconds(300),
            "monthly_stats", Duration.ofSeconds(3600)
    );

    /**
     * Default TTL for cache entries not explicitly listed.
     */
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(120);

    @Bean
    public CacheManager workspaceCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(DEFAULT_TTL)
                .recordStats());
        return cacheManager;
    }

    /**
     * Returns the configured TTL for a given data type.
     *
     * @param dataType the cache data type name
     * @return the TTL duration
     */
    public static Duration getTtlForDataType(String dataType) {
        return CACHE_TTLS.getOrDefault(dataType, DEFAULT_TTL);
    }
}
