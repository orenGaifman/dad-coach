package com.dadcoach.api.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authenticated health endpoint for the Service API surface.
 * <p>
 * Provides detailed subsystem status including database connectivity,
 * AI provider availability, and WhatsApp API status.
 * Requires SERVICE role (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/service")
public class HealthController {

    private final DataSource dataSource;
    private final AiProviderHealthIndicator aiProviderHealthIndicator;
    private final HealthIndicator whatsAppHealthIndicator;

    public HealthController(DataSource dataSource,
                            AiProviderHealthIndicator aiProviderHealthIndicator,
                            com.dadcoach.health.WhatsAppHealthIndicator whatsAppHealthIndicator) {
        this.dataSource = dataSource;
        this.aiProviderHealthIndicator = aiProviderHealthIndicator;
        this.whatsAppHealthIndicator = whatsAppHealthIndicator;
    }

    /**
     * Returns detailed health status for all subsystems.
     * Accessible only with SERVICE role authentication.
     */
    @GetMapping("/health")
    public Map<String, Object> detailedHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overallStatus());
        response.put("timestamp", Instant.now().toString());
        response.put("subsystems", buildSubsystemStatuses());
        return response;
    }

    private String overallStatus() {
        boolean dbUp = checkDatabaseHealth().equals("UP");
        boolean aiUp = aiProviderHealthIndicator.checkHealth().equals("UP");
        Health whatsAppHealth = whatsAppHealthIndicator.health();
        boolean whatsAppUp = whatsAppHealth.getStatus().getCode().equals("UP");

        if (dbUp && aiUp && whatsAppUp) {
            return "UP";
        } else if (!dbUp) {
            return "DOWN";
        } else {
            return "DEGRADED";
        }
    }

    private Map<String, Object> buildSubsystemStatuses() {
        Map<String, Object> subsystems = new LinkedHashMap<>();
        subsystems.put("database", buildDatabaseStatus());
        subsystems.put("ai_provider", buildAiProviderStatus());
        subsystems.put("whatsapp_api", buildWhatsAppStatus());
        return subsystems;
    }

    private Map<String, Object> buildDatabaseStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        String health = checkDatabaseHealth();
        status.put("status", health);
        status.put("type", "postgresql");
        return status;
    }

    private Map<String, Object> buildAiProviderStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", aiProviderHealthIndicator.checkHealth());
        status.put("details", aiProviderHealthIndicator.getDetails());
        return status;
    }

    private Map<String, Object> buildWhatsAppStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        Health health = whatsAppHealthIndicator.health();
        status.put("status", health.getStatus().getCode());
        status.put("details", health.getDetails());
        return status;
    }

    private String checkDatabaseHealth() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return "UP";
            }
            return "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
