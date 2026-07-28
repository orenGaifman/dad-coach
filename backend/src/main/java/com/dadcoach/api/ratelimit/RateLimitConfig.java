package com.dadcoach.api.ratelimit;

import com.dadcoach.api.auth.ActorType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration for per-actor rate limiting.
 * <p>
 * Rate limits are specified per actor type as requests per minute.
 * Configurable via application properties under the prefix {@code api.rate-limit}.
 * <p>
 * Default limits:
 * <ul>
 *   <li>FATHER: 60 requests/minute</li>
 *   <li>ADMIN: 300 requests/minute</li>
 *   <li>SERVICE: 1000 requests/minute</li>
 * </ul>
 *
 * <p>Example application.yml:
 * <pre>
 * api:
 *   rate-limit:
 *     enabled: true
 *     window-seconds: 60
 *     limits:
 *       FATHER: 60
 *       ADMIN: 300
 *       SERVICE: 1000
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "api.rate-limit")
public class RateLimitConfig {

    /**
     * Whether rate limiting is enabled. Defaults to true.
     */
    private boolean enabled = true;

    /**
     * The sliding window duration in seconds. Defaults to 60 (1 minute).
     */
    private int windowSeconds = 60;

    /**
     * Maximum requests per window, keyed by ActorType.
     */
    private Map<ActorType, Integer> limits = new EnumMap<>(Map.of(
            ActorType.FATHER, 60,
            ActorType.ADMIN, 300,
            ActorType.SERVICE, 1000
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Map<ActorType, Integer> getLimits() {
        return limits;
    }

    public void setLimits(Map<ActorType, Integer> limits) {
        this.limits = limits;
    }

    /**
     * Returns the rate limit for a specific actor type.
     * Falls back to 60 if no limit is configured for the type.
     */
    public int getLimitForActorType(ActorType actorType) {
        return limits.getOrDefault(actorType, 60);
    }
}
