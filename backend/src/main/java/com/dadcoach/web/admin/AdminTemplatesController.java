package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.integration.platform.messaging.PlatformTemplateStatus;
import com.dadcoach.web.common.Areas;
import com.dadcoach.web.common.WebException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The templates screen (Tair's and Big Boss's pattern, the team only): each template in the catalog, exactly as it was
 * submitted to Meta, beside its live state. Since D-053 (Unified Workflow 6.4) the state comes from the platform, which
 * sends every message and picks the template ({@link PlatformTemplateStatus}: its copy of the WABA's templates and its
 * kind→template mapping); Dad Coach no longer reads Meta. "sync" reads the platform again. Nothing here submits, edits
 * or deletes anything at Meta. D-067: each row carries its Meta definition ({@link #definition}) - what is submitted.
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplatesController {

    /**
     * @param metaStatus the status in the platform's copy of the WABA (APPROVED, PENDING, REJECTED, PAUSED, DISABLED),
     *     MISSING when it is not there, UNKNOWN when the platform could not be read yet
     * @param metaBodyMatches always null since D-053: the platform's copy of the WABA carries no bodies and its mapping
     *     (chosen by the admin), not a comparison of bodies, decides which template a message uses
     * @param registeredStatus the platform registry's status (null when unknown)
     * @param ready whether the platform can send it now: some mapped (kind, variant) names it and it is usable
     * @param inUseAs the (kind/variant)s the platform's mapping sends with it, comma separated (null when none)
     * @param usedFor the same, as a list
     * @param urlButton D-067: the URL button's text, or null
     * @param urlButtonUrl its address as Meta stores it ({{1}} = the sign-in token), or null
     * @param definition D-067: the template as submitted to Meta ({@link #definition})
     */
    public record Row(String name, String language, String category, String purpose, String body, int maxVariables,
                      List<String> examples, List<String> quickReplies, String sample, boolean general,
                      String metaStatus, String metaCategory, String metaPreviousCategory, String metaRejectedReason,
                      Boolean metaBodyMatches, String registeredStatus, boolean ready, String inUseAs, List<String> usedFor,
                      String urlButton, String urlButtonUrl, Map<String, Object> definition) {}

    /** {@code metaConfigured}: the platform can be asked (it holds the WABA); {@code refreshedAt}: the last good read. */
    public record Listing(String wabaId, boolean metaConfigured, Instant refreshedAt, String lastError, List<Row> templates) {}

    private final PlatformTemplateStatus status;
    private final String webBaseUrl;

    public AdminTemplatesController(PlatformTemplateStatus status,
                                    @Value("${dad-coach.web.base-url:http://localhost:3000}") String webBaseUrl) {
        this.status = status;
        this.webBaseUrl = webBaseUrl;
    }

    @GetMapping
    public Listing list(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        return listing(status.configured() ? status.current() : Optional.empty());
    }

    /** Reads the platform now (it is also re-read when the cached copy is older than 5 minutes). */
    @PostMapping("/sync")
    public Listing sync(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        if (!status.configured()) {
            throw new WebException(HttpStatus.CONFLICT, "PLATFORM_NOT_CONFIGURED",
                    "WORKFLOW_PLATFORM_ENABLED / WORKFLOW_PLATFORM_BASE_URL is not set");
        }
        return listing(status.refresh());
    }

    private Listing listing(Optional<PlatformTemplateStatus.Snapshot> read) {
        PlatformTemplateStatus.Snapshot s = read.orElse(null);
        return new Listing(s == null ? null : s.wabaId(), status.configured(), s == null ? null : s.readAt(),
                s == null ? null : s.error(), WhatsAppTemplateCatalog.ALL.stream().map(e -> row(e, s)).toList());
    }

    /** A URL button's example suffix: the shape of a real sign-in token, never a working one. */
    static final String URL_SUFFIX_SAMPLE = "Xk3vQ9pLm2RtY7wB4nZc8dHs6fJa1gEu5iKo0qWxTyU";

    /**
     * D-067 (Tair's D-105 shape): Meta's definition of one catalog template - the body with its examples, the quick
     * replies, and a URL button whose address is {@code webBaseUrl} + its path (the production dashboard's address).
     */
    public static Map<String, Object> definition(WhatsAppTemplateCatalog.Entry e, String webBaseUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "BODY");
        body.put("text", e.body());
        if (!e.examples().isEmpty()) {
            body.put("example", Map.of("body_text", List.of(e.examples())));
        }
        List<Map<String, Object>> components = new ArrayList<>(List.of(body));
        List<Map<String, Object>> buttons = new ArrayList<>();
        e.quickReplies().forEach(b -> buttons.add(ordered("type", "QUICK_REPLY", "text", b)));
        if (e.urlButton() != null) {
            String url = e.urlButton().url(webBaseUrl);
            Map<String, Object> button = new LinkedHashMap<>();
            button.put("type", "URL");
            button.put("text", e.urlButton().text());
            button.put("url", url);
            button.put("example", List.of(url.replace("{{1}}", URL_SUFFIX_SAMPLE)));
            buttons.add(button);
        }
        if (!buttons.isEmpty()) {
            components.add(ordered("type", "BUTTONS", "buttons", buttons));
        }
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("name", e.name());
        def.put("language", e.language());
        def.put("category", e.category());
        def.put("components", components);
        return def;
    }

    /** Two keys in this order (the definition reads as Meta's documentation writes it). */
    private static Map<String, Object> ordered(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private Row row(WhatsAppTemplateCatalog.Entry entry, PlatformTemplateStatus.Snapshot s) {
        boolean general = WhatsAppTemplateCatalog.UPDATE_HE.equals(entry.name());
        boolean known = s != null && s.readAt() != null;
        Optional<PlatformTemplateStatus.Template> atWaba = known ? s.template(entry.name()) : Optional.empty();
        String metaStatus = atWaba.map(PlatformTemplateStatus.Template::status).orElse(known ? "MISSING" : "UNKNOWN");
        List<String> uses = known ? PlatformTemplateStatus.uses(s.usesOf(entry.name())) : List.of();
        return new Row(entry.name(), entry.language(), entry.category(), entry.purpose(), entry.body(),
                entry.maxVariables(), entry.examples(), entry.quickReplies(), entry.sample(), general,
                metaStatus, atWaba.map(PlatformTemplateStatus.Template::category).orElse(null), null, null,
                null, atWaba.map(PlatformTemplateStatus.Template::status).orElse(null),
                known && s.usable(entry.name()), uses.isEmpty() ? null : String.join(", ", uses), uses,
                entry.urlButton() == null ? null : entry.urlButton().text(),
                entry.urlButton() == null ? null : entry.urlButton().url(webBaseUrl), definition(entry, webBaseUrl));
    }
}
