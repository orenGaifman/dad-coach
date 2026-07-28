package com.dadcoach.api.health;

import java.util.Map;

/**
 * Health indicator interface for the WhatsApp Business API subsystem.
 * <p>
 * The concrete implementation that performs actual connectivity checks
 * belongs to the Communication Channels layer (SPEC-006). This interface
 * defines the contract that the Health API endpoint consumes.
 */
public interface WhatsAppHealthIndicator {

    /**
     * Returns the health status of the WhatsApp API.
     *
     * @return "UP", "DOWN", "DEGRADED", "UNCONFIGURED", or "UNKNOWN"
     */
    String checkHealth();

    /**
     * Returns detailed information about the WhatsApp API health.
     *
     * @return map of health details (configured, reachable, error, etc.)
     */
    Map<String, Object> getDetails();
}
