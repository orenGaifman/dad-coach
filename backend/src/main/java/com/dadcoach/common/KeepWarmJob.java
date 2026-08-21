package com.dadcoach.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Keep-warm job to prevent Render from sleeping.
 * 
 * <p>Render free tier sleeps after 15 minutes of inactivity. This job pings
 * the health endpoint every 5 minutes to keep the instance warm.</p>
 * 
 * <p>Note: This only helps if the instance is already running. For cold starts,
 * consider upgrading to Render paid tier or using an external ping service.</p>
 */
@Component
public class KeepWarmJob {

    private static final Logger log = LoggerFactory.getLogger(KeepWarmJob.class);

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${RENDER_EXTERNAL_URL:}")
    private String renderExternalUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Pings the health endpoint every 5 minutes to keep the instance warm.
     * Uses the Render external URL if available, otherwise localhost.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // Every 5 minutes
    public void keepWarm() {
        String healthUrl;
        
        if (renderExternalUrl != null && !renderExternalUrl.isBlank()) {
            // Use Render external URL (makes actual HTTP request through load balancer)
            healthUrl = renderExternalUrl + "/actuator/health/liveness";
        } else {
            // Local development - use localhost
            healthUrl = "http://localhost:" + serverPort + "/actuator/health/liveness";
        }

        try {
            String response = restTemplate.getForObject(healthUrl, String.class);
            log.debug("Keep-warm ping successful: {}", healthUrl);
        } catch (Exception e) {
            // Don't log error - this is expected to fail during startup or if health is down
            log.trace("Keep-warm ping failed (expected during startup): {}", e.getMessage());
        }
    }
}
