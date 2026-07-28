package com.dadcoach.conversation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for conversation behavior.
 * Expiration windows are configurable per conversation type via Spring properties.
 *
 * <p>Example YAML:
 * <pre>
 * conversation:
 *   expiration-windows:
 *     ONBOARDING: PT48H
 *     DAILY_COACHING: PT24H
 *     FOLLOW_UP: PT24H
 *     REFLECTION: PT24H
 *     INACTIVITY_CHECK: PT48H
 *     CELEBRATION: PT24H
 *     DIFFICULT_SITUATION: null
 * </pre>
 */
@ConfigurationProperties(prefix = "conversation")
public class ConversationProperties {

    /**
     * Expiration window per conversation type. A null value means no expiration.
     */
    private Map<String, Duration> expirationWindows = Map.of(
            "ONBOARDING", Duration.ofHours(48),
            "DAILY_COACHING", Duration.ofHours(24),
            "FOLLOW_UP", Duration.ofHours(24),
            "REFLECTION", Duration.ofHours(24),
            "INACTIVITY_CHECK", Duration.ofHours(48),
            "CELEBRATION", Duration.ofHours(24)
    );

    public Map<String, Duration> getExpirationWindows() {
        return expirationWindows;
    }

    public void setExpirationWindows(Map<String, Duration> expirationWindows) {
        this.expirationWindows = expirationWindows;
    }

    /**
     * Returns the expiration window for the given conversation type,
     * or null if the type has no expiration (e.g., DIFFICULT_SITUATION).
     */
    public Duration getExpirationWindow(String type) {
        return expirationWindows.get(type);
    }
}
