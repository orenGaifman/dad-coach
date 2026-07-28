package com.dadcoach.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for the WhatsApp Business API subsystem.
 * Caches the last known status to avoid expensive calls on every health check request.
 */
@Component
public class WhatsAppHealthIndicator {

    private final String whatsappBaseUrl;
    private final String whatsappApiVersion;
    private final String phoneNumberId;
    private final HttpClient httpClient;
    private final AtomicReference<CachedStatus> cachedStatus = new AtomicReference<>(
            new CachedStatus("UNKNOWN", Map.of(), 0L)
    );

    private static final long CACHE_TTL_MS = 30_000; // 30 seconds

    public WhatsAppHealthIndicator(
            @Value("${dad-coach.whatsapp.api-base-url:https://graph.facebook.com}") String whatsappBaseUrl,
            @Value("${dad-coach.whatsapp.api-version:v25.0}") String whatsappApiVersion,
            @Value("${dad-coach.whatsapp.phone-number-id:}") String phoneNumberId) {
        this.whatsappBaseUrl = whatsappBaseUrl;
        this.whatsappApiVersion = whatsappApiVersion;
        this.phoneNumberId = phoneNumberId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Returns the cached health status of the WhatsApp API.
     * Refreshes the cache if the TTL has expired.
     */
    public String checkHealth() {
        refreshIfStale();
        return cachedStatus.get().status();
    }

    /**
     * Returns detailed information about the WhatsApp API health.
     */
    public Map<String, Object> getDetails() {
        refreshIfStale();
        return cachedStatus.get().details();
    }

    private void refreshIfStale() {
        CachedStatus current = cachedStatus.get();
        if (System.currentTimeMillis() - current.checkedAt() < CACHE_TTL_MS) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        String status;

        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            status = "UNCONFIGURED";
            details.put("configured", false);
            details.put("reason", "phone_number_id not set");
            cachedStatus.set(new CachedStatus(status, details, System.currentTimeMillis()));
            return;
        }

        try {
            String url = whatsappBaseUrl + "/" + whatsappApiVersion + "/" + phoneNumberId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            details.put("configured", true);
            // 401/190 means API is reachable but token is not provided (expected in health check)
            if (response.statusCode() == 200 || response.statusCode() == 401) {
                status = "UP";
                details.put("reachable", true);
            } else {
                status = "DEGRADED";
                details.put("reachable", true);
                details.put("response_code", response.statusCode());
            }
        } catch (Exception e) {
            status = "DOWN";
            details.put("configured", true);
            details.put("reachable", false);
            details.put("error", e.getMessage());
        }

        cachedStatus.set(new CachedStatus(status, details, System.currentTimeMillis()));
    }

    private record CachedStatus(String status, Map<String, Object> details, long checkedAt) {
    }
}
