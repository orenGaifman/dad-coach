package com.dadcoach.api.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO for context provider calls from ai-workflow-platform.
 * 
 * <p>This is the standard request format for all context provider calls.
 * Each context request includes:</p>
 * <ul>
 *   <li>userId - the father's identifier for whom to load context</li>
 *   <li>config - optional configuration parameters for the provider</li>
 * </ul>
 * 
 * @see ContextProviderResponse
 * @see ContextProviderController
 */
public record ContextProviderRequest(
        /**
         * The user (father) ID for whom to load context.
         */
        @NotNull(message = "userId is required")
        @JsonProperty("user_id")
        Long userId,

        /**
         * Optional configuration parameters for the context provider.
         * The available parameters depend on the specific provider being called.
         */
        @JsonProperty("config")
        Map<String, Object> config
) {
    /**
     * Gets a configuration value as a String.
     * 
     * @param key the configuration key
     * @return the configuration value as String, or null if not present
     */
    public String getStringConfig(String key) {
        if (config == null) return null;
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a configuration value as an Integer.
     * 
     * @param key the configuration key
     * @return the configuration value as Integer, or null if not present or invalid
     */
    public Integer getIntConfig(String key) {
        if (config == null) return null;
        Object value = config.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets a configuration value as a Boolean.
     * 
     * @param key the configuration key
     * @return the configuration value as Boolean, or null if not present
     */
    public Boolean getBooleanConfig(String key) {
        if (config == null) return null;
        Object value = config.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
