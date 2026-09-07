package com.dadcoach.api.context;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request DTO for context provider calls from ai-workflow-platform.
 * 
 * <p>This is the standard request format for all context provider calls.
 * Each context request includes:</p>
 * <ul>
 *   <li>userId - the father's identifier for whom to load context (optional when phone is in config)</li>
 *   <li>config - optional configuration parameters for the provider</li>
 * </ul>
 * 
 * <p><b>Note:</b> Either userId OR phone in config must be provided. When ai-workflow-platform
 * receives a phone number like "+972503020551" that cannot be parsed as Long, it sends
 * userId=null with the phone in config for lookup.</p>
 * 
 * @see ContextProviderResponse
 * @see ContextProviderController
 */
public record ContextProviderRequest(
        /**
         * The user (father) ID for whom to load context.
         * Optional when phone number is provided in config for lookup.
         */
        @JsonProperty("user_id")
        Long userId,

        /**
         * Optional configuration parameters for the context provider.
         * The available parameters depend on the specific provider being called.
         * 
         * <p>For user lookup fallback, may include:</p>
         * <ul>
         *   <li>phone - Phone number for fallback lookup when userId is null</li>
         * </ul>
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
    
    /**
     * Checks if this request has a valid identifier (either userId or phone in config).
     * 
     * @return true if userId is set or phone is in config
     */
    public boolean hasValidIdentifier() {
        if (userId != null) {
            return true;
        }
        String phone = getStringConfig("phone");
        return phone != null && !phone.isBlank();
    }
}
