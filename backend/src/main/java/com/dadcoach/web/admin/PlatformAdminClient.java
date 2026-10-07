package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardProperties;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The admin's read-only "platform" panel (playbook §41): read-through to the AI Workflow Platform's own admin API -
 * the father's workflow instances (by his WhatsApp id), the current one's state, its scheduled transitions and its
 * last messages. Nothing is stored or changed; the platform admin key never leaves the server. Without
 * WORKFLOW_PLATFORM_ADMIN_API_KEY the panel says "not configured".
 */
@Component
public class PlatformAdminClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    static final int MESSAGES = 12;

    private final WorkflowPlatformProperties platform;
    private final DashboardProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public PlatformAdminClient(WorkflowPlatformProperties platform, DashboardProperties properties, ObjectMapper json) {
        this.platform = platform;
        this.properties = properties;
        this.json = json;
    }

    public boolean configured() {
        return key() != null && base() != null;
    }

    /** {configured, reachable, error?, instances, current?, scheduledTransitions?, messages?} */
    public Map<String, Object> panelFor(String phone) {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("configured", configured());
        if (!configured()) {
            return panel;
        }
        try {
            String externalUserId = PersonRefs.whatsappId(phone);
            JsonNode list = get("/api/v1/admin/instances?size=20&sort=createdAt,desc&externalUserId="
                    + URLEncoder.encode(externalUserId, StandardCharsets.UTF_8));
            panel.put("reachable", true);
            JsonNode instances = list.path("instances");
            panel.put("instances", instances);
            JsonNode first = instances.isArray() && !instances.isEmpty() ? instances.get(0) : null;
            if (first != null && first.hasNonNull("id")) {
                String id = first.get("id").asText();
                panel.put("current", get("/api/v1/admin/instances/" + id));
                panel.put("scheduledTransitions", get("/api/v1/admin/workflow-instances/" + id + "/scheduled-transitions"));
                JsonNode probe = get("/api/v1/admin/instances/" + id + "/messages?page=0&size=" + MESSAGES);
                int pages = probe.path("totalPages").asInt(1);
                JsonNode last = pages > 1 ? get("/api/v1/admin/instances/" + id + "/messages?page=" + (pages - 1) + "&size=" + MESSAGES) : probe;
                panel.put("messages", last.path("messages"));
            }
        } catch (Exception e) {
            log.warn("admin.platform_panel.failed type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            panel.put("reachable", false);
            panel.put("error", e.getClass().getSimpleName());
        }
        return panel;
    }

    /** Integrations card: does the platform answer (health), regardless of the admin key. */
    public boolean reachable() {
        String base = base();
        if (base == null) {
            return false;
        }
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health")).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode() < 500;
        } catch (Exception e) {
            return false;
        } 
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base() + path)).timeout(TIMEOUT)
                .header("X-API-Key", key()).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404) {
            return json.createObjectNode();
        }
        if (r.statusCode() >= 300) {
            throw new IllegalStateException("platform answered " + r.statusCode());
        }
        return json.readTree(r.body());
    }

    private String key() {
        String k = properties.getPlatformAdminApiKey();
        return k == null || k.isBlank() ? null : k.strip();
    }

    private String base() {
        String b = platform.getBaseUrl();
        if (b == null || b.isBlank()) {
            return null;
        }
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }
}
