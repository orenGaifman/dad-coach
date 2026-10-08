package com.dadcoach.channel.template;

import com.dadcoach.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Dad Coach's templates as Meta has them (Tair's and Big Boss's pattern): read from this product's own WhatsApp Business
 * Account every 10 minutes and on demand. Each refresh also keeps {@code template_messages} - the gate every template
 * send passes ({@link TemplateRegistry}) - in step with Meta: a catalog template is registered APPROVED only once Meta
 * approved it with the catalog's exact body, and leaves APPROVED as soon as Meta pauses, disables or rejects it, or no
 * longer has it. Nothing here ever creates, edits or deletes a template at Meta.
 */
@Component
public class MetaTemplateDirectory {

    private static final Logger log = LoggerFactory.getLogger(MetaTemplateDirectory.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** One template at Meta. {@code previousCategory} is set when Meta re-filed it (e.g. Utility to Marketing). */
    public record MetaTemplate(String name, String status, String category, String previousCategory,
                               String rejectedReason, String body) {
    }

    private final WebClient webClient;
    private final WhatsAppProperties properties;
    private final TemplateRegistry registry;
    private final Clock clock;
    private volatile Map<String, MetaTemplate> byName = Map.of();
    private volatile Instant refreshedAt;
    private volatile String lastError;

    public MetaTemplateDirectory(WebClient.Builder builder, WhatsAppProperties properties, TemplateRegistry registry,
                                 Clock clock) {
        this.properties = properties;
        this.registry = registry;
        this.clock = clock;
        this.webClient = builder.baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken()).build();
    }

    /** Whether this deployment can read Meta at all (an account and a token configured). */
    public boolean configured() {
        return notBlank(properties.wabaId()) && notBlank(properties.accessToken());
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT30S")
    void scheduled() {
        if (configured()) {
            refresh();
        }
    }

    /** Reads every template of the account, then aligns the registry. False when Meta could not be read. */
    public synchronized boolean refresh() {
        if (!configured()) {
            lastError = "WHATSAPP_WABA_ID or WHATSAPP_ACCESS_TOKEN is not set";
            return false;
        }
        try {
            Map<String, MetaTemplate> found = new LinkedHashMap<>();
            String uri = "/" + properties.apiVersion() + "/" + properties.wabaId()
                    + "/message_templates?fields=name,status,category,previous_category,rejected_reason,language,components&limit=250";
            while (uri != null) {
                JsonNode page = webClient.get().uri(uri).retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
                if (page == null) {
                    break;
                }
                for (JsonNode t : page.path("data")) {
                    if (!"he".equals(t.path("language").asText())) {
                        continue;
                    }
                    String body = null;
                    for (JsonNode c : t.path("components")) {
                        if ("BODY".equalsIgnoreCase(c.path("type").asText())) {
                            body = c.path("text").asText(null);
                        }
                    }
                    found.put(t.path("name").asText(), new MetaTemplate(t.path("name").asText(), t.path("status").asText(),
                            t.path("category").asText(), text(t, "previous_category"), text(t, "rejected_reason"), body));
                }
                String next = page.path("paging").path("next").asText(null);
                uri = next == null ? null : next.substring(next.indexOf('/' + properties.apiVersion() + '/'));
            }
            byName = Map.copyOf(found);
            refreshedAt = clock.instant();
            lastError = null;
            int registered = alignRegistry();
            log.atInfo().setMessage("meta.templates.refreshed").addKeyValue("templates", found.size())
                    .addKeyValue("catalogApproved", registered).log();
            return true;
        } catch (RuntimeException e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Meta templates could not be read: {}", lastError);
            return false;
        }
    }

    /** Registers what Meta approved with the catalog's body; withdraws what Meta no longer approves. */
    private int alignRegistry() {
        int approved = 0;
        for (WhatsAppTemplateCatalog.Entry entry : WhatsAppTemplateCatalog.ALL) {
            MetaTemplate meta = byName.get(entry.name());
            Optional<TemplateMessage> current = registry.find(entry.name());
            boolean usable = meta != null && "APPROVED".equals(meta.status()) && entry.body().equals(meta.body());
            if (usable) {
                approved++;
                boolean already = current.map(t -> "APPROVED".equals(t.getStatus()) && entry.body().equals(t.getBody()))
                        .orElse(false);
                if (!already) {
                    registry.registerOrUpdate(entry.name(), entry.language(), entry.category(), entry.body(), "APPROVED",
                            entry.maxVariables());
                }
            } else if (current.map(t -> "APPROVED".equals(t.getStatus())).orElse(false)) {
                String status = meta == null ? "NOT_IN_META"
                        : "APPROVED".equals(meta.status()) ? "BODY_DIFFERS" : meta.status();
                registry.registerOrUpdate(entry.name(), entry.language(), entry.category(), entry.body(), status,
                        entry.maxVariables());
                log.warn("Template withdrawn from use: name={}, meta={}", entry.name(), status);
            }
        }
        return approved;
    }

    public Optional<MetaTemplate> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Optional<Instant> refreshedAt() {
        return Optional.ofNullable(refreshedAt);
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() || "NONE".equals(v) ? null : v;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
