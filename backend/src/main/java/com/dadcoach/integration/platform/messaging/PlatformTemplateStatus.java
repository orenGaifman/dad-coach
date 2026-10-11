package com.dadcoach.integration.platform.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * D-053 (Unified Workflow 6.4, spec §2.1 / §4.4): what the platform knows about Dad Coach's WhatsApp templates - the
 * read-only copy of the WABA's templates ({@code GET /api/v1/worker/whatsapp/templates}) and the kind→template mapping
 * with each candidate's usability ({@code GET /api/v1/worker/messaging/kinds}). Dad Coach no longer reads Meta for
 * this; the admin's templates screen and the integrations card show it. Cached for {@link #CACHE_FOR}; a failed read
 * keeps the last good answer (none yet = unknown) and is not retried for {@link #FAILURE_CACHE_FOR} (a platform outage
 * never costs two blocking GETs on every admin page); "sync" always reads again.
 */
@Component
public class PlatformTemplateStatus {

    private static final Logger log = LoggerFactory.getLogger(PlatformTemplateStatus.class);
    static final Duration CACHE_FOR = Duration.ofMinutes(5);
    static final Duration FAILURE_CACHE_FOR = Duration.ofSeconds(60);

    /** One template at the WABA as the platform's registry holds it. */
    public record Template(String name, String language, String status, String category) {}

    /** One mapped (kind, variant) candidate: its template, the registry's status and whether it could be sent now. */
    public record Candidate(String kind, String variant, String template, String status, Boolean usable) {}

    /** A read: when, the WABA, its templates, the mapping's candidates; {@code error} the last failure (or null). */
    public record Snapshot(Instant readAt, String wabaId, List<Template> templates, List<Candidate> candidates, String error) {

        public Optional<Template> template(String name) {
            return templates.stream().filter(t -> t.name().equals(name)).findFirst();
        }

        /** The (kind, variant)s that map to this template. */
        public List<Candidate> usesOf(String name) {
            return candidates.stream().filter(c -> c.template().equals(name)).toList();
        }

        /** Whether some mapped candidate is this template and usable now. */
        public boolean usable(String name) {
            return usesOf(name).stream().anyMatch(c -> Boolean.TRUE.equals(c.usable()));
        }

        /** Whether the kind's default message type ({@code variant ""}) could go as a template now. */
        public boolean defaultUsable(String kind) {
            return candidates.stream().anyMatch(c -> kind.equals(c.kind()) && "".equals(c.variant())
                    && Boolean.TRUE.equals(c.usable()));
        }
    }

    private final PlatformMessagingClient client;
    private final Clock clock;
    private volatile Snapshot last;
    private volatile Instant failedAt;

    public PlatformTemplateStatus(PlatformMessagingClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public boolean configured() {
        return client.isConfigured();
    }

    /** The cached read (refreshed when older than {@link #CACHE_FOR}); empty while nothing could be read yet. */
    public Optional<Snapshot> current() {
        Snapshot s = last;
        Instant now = clock.instant();
        Instant failed = failedAt;
        if (s != null && failed != null && failed.plus(FAILURE_CACHE_FOR).isAfter(now)) {
            return Optional.of(s);                     // a recent failure: not asked again yet
        }
        if (s == null || s.readAt() == null || s.readAt().plus(CACHE_FOR).isBefore(now)) {
            return refresh();
        }
        return Optional.of(s);
    }

    /** Reads the platform now. A failure keeps the last good answer (with the error noted). */
    public synchronized Optional<Snapshot> refresh() {
        PlatformMessagingClient.Answer t = client.templates();
        PlatformMessagingClient.Answer k = t.status() == 200 ? client.kinds() : t;
        if (t.status() != 200 || k.status() != 200 || t.body() == null || k.body() == null) {
            String error = (t.status() != 200 ? t : k).summary();
            log.atWarn().setMessage("messaging.templates.read_failed").addKeyValue("answer", error).log();
            failedAt = clock.instant();
            Snapshot previous = last;
            last = previous == null ? new Snapshot(null, null, List.of(), List.of(), error)
                    : new Snapshot(previous.readAt(), previous.wabaId(), previous.templates(), previous.candidates(), error);
            return Optional.of(last);
        }
        failedAt = null;
        last = new Snapshot(clock.instant(), text(t.body(), "wabaId"), templates(t.body()), candidates(k.body()), null);
        return Optional.of(last);
    }

    /** Tests: forget the cached read. */
    public void forget() {
        last = null;
        failedAt = null;
    }

    private static List<Template> templates(JsonNode body) {
        List<Template> out = new ArrayList<>();
        body.path("templates").forEach(t -> out.add(new Template(t.path("name").asText(), text(t, "language"),
                text(t, "status"), text(t, "category"))));
        return out;
    }

    private static List<Candidate> candidates(JsonNode body) {
        List<Candidate> out = new ArrayList<>();
        body.path("kinds").forEach(k -> k.path("candidates").forEach(c -> out.add(new Candidate(text(k, "kind"),
                k.path("variant").asText(""), c.path("template").asText(), text(c, "status"),
                c.hasNonNull("usable") ? c.get("usable").asBoolean() : null))));
        return out;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /** For the admin listing: each mapped use as "KIND/variant". */
    public static List<String> uses(List<Candidate> candidates) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        candidates.forEach(c -> seen.put(c.kind() + (c.variant().isEmpty() ? "" : "/" + c.variant()), true));
        return List.copyOf(seen.keySet());
    }
}
