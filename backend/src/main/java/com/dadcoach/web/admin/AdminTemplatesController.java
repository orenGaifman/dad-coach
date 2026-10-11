package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.integration.platform.messaging.PlatformTemplateStatus;
import com.dadcoach.web.common.Areas;
import com.dadcoach.web.common.WebException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * or deletes anything at Meta.
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplatesController {

    /**
     * @param metaStatus the status in the platform's copy of the WABA (APPROVED, PENDING, REJECTED, PAUSED, DISABLED),
     *     MISSING when it is not there, UNKNOWN when the platform could not be read yet
     * @param metaBodyMatches kept for the screen: since D-053 the platform's mapping (chosen by the admin) decides which
     *     template a message uses, not a comparison of bodies - true whenever the template is at the WABA
     * @param registeredStatus the platform registry's status (null when unknown)
     * @param ready whether the platform can send it now: some mapped (kind, variant) names it and it is usable
     * @param inUseAs the (kind/variant)s the platform's mapping sends with it, comma separated (null when none)
     * @param usedFor the same, as a list
     */
    public record Row(String name, String language, String category, String purpose, String body, int maxVariables,
                      List<String> examples, List<String> quickReplies, String sample, boolean general,
                      String metaStatus, String metaCategory, String metaPreviousCategory, String metaRejectedReason,
                      boolean metaBodyMatches, String registeredStatus, boolean ready, String inUseAs, List<String> usedFor) {}

    /** {@code metaConfigured}: the platform can be asked (it holds the WABA); {@code refreshedAt}: the last good read. */
    public record Listing(String wabaId, boolean metaConfigured, Instant refreshedAt, String lastError, List<Row> templates) {}

    private final PlatformTemplateStatus status;

    public AdminTemplatesController(PlatformTemplateStatus status) {
        this.status = status;
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

    private static Row row(WhatsAppTemplateCatalog.Entry entry, PlatformTemplateStatus.Snapshot s) {
        boolean general = WhatsAppTemplateCatalog.UPDATE_HE.equals(entry.name());
        boolean known = s != null && s.readAt() != null;
        Optional<PlatformTemplateStatus.Template> atWaba = known ? s.template(entry.name()) : Optional.empty();
        String metaStatus = atWaba.map(PlatformTemplateStatus.Template::status).orElse(known ? "MISSING" : "UNKNOWN");
        List<String> uses = known ? PlatformTemplateStatus.uses(s.usesOf(entry.name())) : List.of();
        return new Row(entry.name(), entry.language(), entry.category(), entry.purpose(), entry.body(),
                entry.maxVariables(), entry.examples(), entry.quickReplies(), entry.sample(), general,
                metaStatus, atWaba.map(PlatformTemplateStatus.Template::category).orElse(null), null, null,
                atWaba.isPresent(), atWaba.map(PlatformTemplateStatus.Template::status).orElse(null),
                known && s.usable(entry.name()), uses.isEmpty() ? null : String.join(", ", uses), uses);
    }
}
