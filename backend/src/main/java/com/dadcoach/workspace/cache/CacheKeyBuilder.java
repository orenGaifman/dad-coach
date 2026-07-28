package com.dadcoach.workspace.cache;

import java.util.UUID;

/**
 * Utility class for generating consistent cache keys across the workspace.
 *
 * <p>Key format: {@code workspace:{father_id}:{data_type}}</p>
 */
public final class CacheKeyBuilder {

    private static final String PREFIX = "workspace";
    private static final String SEPARATOR = ":";

    private CacheKeyBuilder() {
        // Utility class
    }

    /**
     * Builds a cache key in the format workspace:{fatherId}:{dataType}.
     *
     * @param fatherId the father's unique identifier
     * @param dataType the type of cached data (e.g., "summary", "belt", "notifications")
     * @return the formatted cache key
     */
    public static String build(UUID fatherId, String dataType) {
        return PREFIX + SEPARATOR + fatherId.toString() + SEPARATOR + dataType;
    }

    /**
     * Builds a key prefix for a father (used for invalidateAll operations).
     *
     * @param fatherId the father's unique identifier
     * @return the prefix matching all keys for this father
     */
    public static String prefixFor(UUID fatherId) {
        return PREFIX + SEPARATOR + fatherId.toString() + SEPARATOR;
    }
}
