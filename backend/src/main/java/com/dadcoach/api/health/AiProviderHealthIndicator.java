package com.dadcoach.api.health;

import java.util.Map;

/**
 * Health indicator interface for the AI provider subsystem.
 * <p>
 * The concrete implementation that performs actual connectivity checks
 * belongs to the AI Intelligence Layer (SPEC-003). This interface defines
 * the contract that the Health API endpoint consumes.
 */
public interface AiProviderHealthIndicator {

    /**
     * Returns the health status of the AI provider.
     *
     * @return "UP", "DOWN", "DEGRADED", or "UNKNOWN"
     */
    String checkHealth();

    /**
     * Returns detailed information about the AI provider health.
     *
     * @return map of health details (reachable, response_code, error, etc.)
     */
    Map<String, Object> getDetails();
}
