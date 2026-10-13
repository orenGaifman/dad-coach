package com.platform.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a product registers with the Workflow Platform when it starts - its catalog ({@code PUT /api/v1/worker/catalog}:
 * HTTP tools, context providers, kind→template mappings) and its scheduled jobs ({@code PUT /api/v1/worker/jobs}) - with
 * its own key. Both calls are idempotent: a step is retried at 0 s, 30 s, 2 min and 10 min until the platform answers
 * 2xx; a 404 means the platform does not offer the API yet and a 400/401/403/409 will not change by retrying.
 *
 * <p>Canonical source: ai-workflow-platform {@code platform-client/}. Products carry a byte-identical copy
 * ({@code scripts/platform_client.py sync}); never edit it in a product.</p>
 */
public final class PlatformPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlatformPublisher.class);
    public static final String CATALOG_PATH = "/api/v1/worker/catalog";
    public static final String JOBS_PATH = "/api/v1/worker/jobs";
    static final long[] WAITS_SECONDS = {0, 30, 120, 600};

    /** One answer of the platform: HTTP status and JSON body (null when there is none). */
    public record Outcome(String step, int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /** Not worth repeating: the platform has no such API (404) or refused the content itself. */
        public boolean permanent() {
            return status == 400 || status == 401 || status == 403 || status == 404 || status == 409;
        }
    }

    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper json;
    private final long[] waitsSeconds;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public PlatformPublisher(String baseUrl, String apiKey, ObjectMapper json) {
        this(baseUrl, apiKey, json, WAITS_SECONDS);
    }

    /** Tests: other waits between attempts. */
    public PlatformPublisher(String baseUrl, String apiKey, ObjectMapper json, long[] waitsSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.json = json;
        this.waitsSeconds = waitsSeconds.clone();
    }

    /** Configured: a platform URL to call. */
    public boolean configured() {
        return !baseUrl.isBlank();
    }

    /** The product's catalog file (the platform's {@code GET /api/v1/worker/catalog} format). */
    public static JsonNode readCatalog(ObjectMapper json, InputStream in) throws IOException {
        try (in) {
            return json.readTree(in);
        }
    }

    /** {@code PUT /api/v1/worker/catalog}; {@code dryRun} = the same report, nothing written. */
    public Outcome publishCatalog(JsonNode catalog, boolean dryRun) {
        Map<String, Object> body = json.convertValue(catalog, LinkedHashMap.class);
        body.remove("product");
        body.put("dryRun", dryRun);
        Outcome outcome = put("catalog", CATALOG_PATH, body);
        if (outcome.ok() && outcome.body() != null) {
            for (String section : List.of("tools", "providers", "kindTemplates")) {
                JsonNode s = outcome.body().path(section);
                log.atInfo().addKeyValue("section", section).addKeyValue("dryRun", dryRun)
                        .addKeyValue("created", s.path("created").size()).addKeyValue("updated", s.path("updated").size())
                        .addKeyValue("unchanged", s.path("unchanged").asInt()).addKeyValue("drift", s.path("drift").size())
                        .addKeyValue("notListed", s.path("notListed")).log("platform_catalog.published");
                for (JsonNode d : s.path("drift")) {
                    log.atWarn().addKeyValue("section", section).addKeyValue("key", d.path("key").asText())
                            .addKeyValue("fields", d.path("fields")).log("platform_catalog.drift - changed on the platform; not overwritten");
                }
            }
        }
        return outcome;
    }

    /** {@code PUT /api/v1/worker/jobs}: the worker's full list of jobs. */
    public Outcome publishJobs(String workerKey, List<Map<String, Object>> jobs) {
        Outcome outcome = put("jobs", JOBS_PATH, Map.of("workerKey", workerKey, "jobs", jobs));
        if (outcome.status() != 0) {
            log.atInfo().addKeyValue("status", outcome.status()).addKeyValue("jobs", jobs.size())
                    .addKeyValue("platformOwned", jobs.stream().filter(j -> Boolean.TRUE.equals(j.get("enabled"))).map(j -> j.get("jobKey")).toList())
                    .log("platform_jobs.synced");
        }
        return outcome;
    }

    /** One PUT; a transport failure is status 0. */
    public Outcome put(String step, String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json").header("X-API-Key", apiKey)
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode answer = null;
            try {
                answer = response.body() == null || response.body().isBlank() ? null : json.readTree(response.body());
            } catch (IOException notJson) {
                // an answer that is not JSON (a proxy's error page) keeps only its status
            }
            return new Outcome(step, response.statusCode(), answer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(step, 0, null);
        } catch (IOException | RuntimeException e) {
            log.atWarn().addKeyValue("step", step).addKeyValue("error", e.getClass().getSimpleName()).log("platform_publish.call_failed");
            return new Outcome(step, 0, null);
        }
    }

    /** Runs each step until it succeeds or is refused, retrying with the waits above; returns the last outcome. */
    public Outcome withRetries(String step, Supplier<Outcome> call) {
        Outcome last = new Outcome(step, 0, null);
        for (long wait : waitsSeconds) {
            try {
                Thread.sleep(wait * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
            last = call.get();
            if (last.ok()) {
                return last;
            }
            if (last.status() == 404) {
                log.atInfo().addKeyValue("step", step).log("platform_publish.unsupported - the platform has no such API yet");
                return last;
            }
            if (last.permanent()) {
                log.atError().addKeyValue("step", step).addKeyValue("status", last.status())
                        .addKeyValue("error", last.body() == null ? null : last.body().path("error").asText(null))
                        .addKeyValue("message", last.body() == null ? null : last.body().path("message").asText(null))
                        .log("platform_publish.refused");
                return last;
            }
        }
        log.atWarn().addKeyValue("step", step).addKeyValue("status", last.status()).log("platform_publish.gave_up");
        return last;
    }

    /**
     * A product's registration at start, on a daemon thread: its jobs, then its catalog ({@code catalogMode} {@code off},
     * {@code dry-run} = report only, {@code apply}) from {@code catalogFile}. Each step with retries; a catalog the platform
     * cannot take never delays the jobs.
     */
    public Thread register(String workerKey, String catalogMode, Supplier<InputStream> catalogFile,
                           Supplier<List<Map<String, Object>>> jobs) {
        String mode = catalogMode == null ? "off" : catalogMode.strip().toLowerCase(java.util.Locale.ROOT);
        Map<String, Supplier<Outcome>> steps = new LinkedHashMap<>();
        steps.put("jobs", () -> publishJobs(workerKey, jobs.get()));
        if (mode.equals("dry-run") || mode.equals("apply")) {
            steps.put("catalog", () -> {
                try {
                    return publishCatalog(readCatalog(json, catalogFile.get()), mode.equals("dry-run"));
                } catch (IOException | RuntimeException e) {
                    log.atError().addKeyValue("error", e.getMessage()).log("platform_catalog.unreadable");
                    return new Outcome("catalog", 400, null);
                }
            });
        } else if (!mode.equals("off")) {
            log.atWarn().addKeyValue("value", catalogMode).log("platform_catalog.publish_mode_unknown - not published");
        }
        return startInBackground("platform-registration", steps);
    }

    /** One job of {@code PUT /api/v1/worker/jobs}: {@code every} (ISO duration) or {@code cron} + {@code timezone}. */
    public static Map<String, Object> job(String jobKey, Duration every, String cron, String timezone, Duration timeout,
                                          boolean enabled, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobKey", jobKey);
        if (every != null) {
            m.put("every", every.toString());
        } else {
            m.put("cron", cron);
            m.put("timezone", timezone);
        }
        m.put("timeout", timeout.toString());
        m.put("enabled", enabled);
        m.put("description", description);
        return m;
    }

    /** The steps one after another, each with retries, on a daemon thread (start-up is never held up). */
    public Thread startInBackground(String threadName, Map<String, Supplier<Outcome>> steps) {
        Thread t = new Thread(() -> steps.forEach(this::withRetries), threadName);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
