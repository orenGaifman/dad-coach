package com.dadcoach.health;

import com.dadcoach.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Health indicator for WhatsApp API connectivity.
 * 
 * <p>Reports the status of WhatsApp Business API integration by checking
 * if the WhatsApp service is properly configured and the API is reachable.</p>
 * 
 * <p>The indicator reports:
 * <ul>
 *   <li>UP - when WhatsApp API is configured and reachable</li>
 *   <li>DOWN - when the API is not configured or unreachable</li>
 * </ul>
 * </p>
 * 
 * <p>Note: This health check verifies configuration and basic connectivity,
 * not actual message delivery capability.</p>
 * 
 * <p>Implements Requirement 16.5: The system SHALL expose a health endpoint that
 * reports WhatsApp API status.</p>
 */
@Component
public class WhatsAppHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppHealthIndicator.class);

    private final WhatsAppProperties whatsAppProperties;
    private final RestTemplate restTemplate;

    /**
     * Constructs the health indicator with WhatsApp configuration.
     *
     * @param whatsAppProperties WhatsApp configuration properties
     */
    public WhatsAppHealthIndicator(WhatsAppProperties whatsAppProperties) {
        this.whatsAppProperties = whatsAppProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Health health() {
        try {
            // Check if WhatsApp is configured
            if (!isConfigured()) {
                return Health.down()
                        .withDetail("component", "WhatsAppAPI")
                        .withDetail("error", "WhatsApp API not configured")
                        .withDetail("missingConfig", getMissingConfiguration())
                        .build();
            }

            // Check API connectivity by making a simple GET request
            // Using the phone number endpoint which should return phone number details
            boolean apiReachable = checkApiConnectivity();

            if (apiReachable) {
                return Health.up()
                        .withDetail("component", "WhatsAppAPI")
                        .withDetail("apiVersion", whatsAppProperties.apiVersion())
                        .withDetail("phoneNumberId", maskPhoneNumberId(whatsAppProperties.phoneNumberId()))
                        .build();
            } else {
                return Health.down()
                        .withDetail("component", "WhatsAppAPI")
                        .withDetail("error", "WhatsApp API is not reachable")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error checking WhatsApp health: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("component", "WhatsAppAPI")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * Checks if WhatsApp API is properly configured.
     *
     * @return true if all required configuration is present
     */
    private boolean isConfigured() {
        return whatsAppProperties != null
                && isNotBlank(whatsAppProperties.accessToken())
                && isNotBlank(whatsAppProperties.phoneNumberId())
                && isNotBlank(whatsAppProperties.apiBaseUrl());
    }

    /**
     * Returns a description of missing configuration items.
     *
     * @return comma-separated list of missing config items
     */
    private String getMissingConfiguration() {
        StringBuilder missing = new StringBuilder();
        
        if (whatsAppProperties == null) {
            return "WhatsAppProperties bean not available";
        }
        
        if (!isNotBlank(whatsAppProperties.accessToken())) {
            missing.append("accessToken, ");
        }
        if (!isNotBlank(whatsAppProperties.phoneNumberId())) {
            missing.append("phoneNumberId, ");
        }
        if (!isNotBlank(whatsAppProperties.apiBaseUrl())) {
            missing.append("apiBaseUrl, ");
        }
        
        String result = missing.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        return result.isEmpty() ? "none" : result;
    }

    /**
     * Checks if the WhatsApp API is reachable by making a lightweight GET request.
     * 
     * <p>Uses the phone number details endpoint which is a read-only operation
     * and doesn't consume any messaging quota.</p>
     *
     * @return true if the API responds successfully
     */
    private boolean checkApiConnectivity() {
        try {
            String url = String.format("%s/%s/%s",
                    whatsAppProperties.apiBaseUrl(),
                    whatsAppProperties.apiVersion(),
                    whatsAppProperties.phoneNumberId());

            // Create a simple request with authorization header
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(whatsAppProperties.accessToken());
            
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getStatusCode() == HttpStatus.OK;

        } catch (RestClientException e) {
            log.warn("WhatsApp API connectivity check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Masks the phone number ID for security in health output.
     *
     * @param phoneNumberId the phone number ID
     * @return masked phone number ID showing only last 4 characters
     */
    private String maskPhoneNumberId(String phoneNumberId) {
        if (phoneNumberId == null || phoneNumberId.length() <= 4) {
            return "****";
        }
        return "****" + phoneNumberId.substring(phoneNumberId.length() - 4);
    }

    /**
     * Checks if a string is not blank (not null and not empty after trimming).
     *
     * @param str the string to check
     * @return true if the string is not blank
     */
    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
