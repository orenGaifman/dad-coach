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
 * Health indicator for the AI provider subsystem.
 * Caches the last known status to avoid expensive calls on every health check request.
 */
@Component
public class AiProviderHealthIndicator {

    private final String openaiBaseUrl;
    private final HttpClient httpClient;
    private final AtomicReference<CachedStatus> cachedStatus = new AtomicReference<>(
            new CachedStatus("UNKNOWN", Map.of(), 0L)
    );

    private static final long CACHE_TTL_MS = 30_000; // 30 seconds

    public AiProviderHealthIndicator(
            @Value("${dad-coach.ai.openai.base-url:https://api.openai.com}") String openaiBaseUrl) {
        this.openaiBaseUrl = openaiBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Returns the cached health status of the AI provider.
     * Refreshes the cache if the TTL has expired.
     */
    public String checkHealth() {
        refreshIfStale();
        return cachedStatus.get().status();
    }

    /**
     * Returns detailed information about the AI provider health.
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
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openaiBaseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // 401 means the API is reachable (auth required is expected without key in health check)
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
            details.put("reachable", false);
            details.put("error", e.getMessage());
        }

        cachedStatus.set(new CachedStatus(status, details, System.currentTimeMillis()));
    }

    private record CachedStatus(String status, Map<String, Object> details, long checkedAt) {
    }
}
