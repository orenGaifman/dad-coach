package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.channel.template.MetaTemplateDirectory;
import com.dadcoach.channel.template.TemplateMessage;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.integration.platform.scheduled.ScheduledResponseCallbackConfig;
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
 * The templates screen (Tair's and Big Boss's pattern, the team only): each template the code sends, exactly as it was
 * submitted to Meta, beside its live state at Meta (status, category, a re-filing, a rejection reason) and whether Dad
 * Coach can send it now. Meta's state is read every 10 minutes and on "sync" ({@link MetaTemplateDirectory}), which also
 * registers a template for sending only once Meta approved it with the catalog's exact body. Nothing here submits,
 * edits or deletes anything at Meta.
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplatesController {

    /**
     * @param metaStatus Meta's status (APPROVED, PENDING, REJECTED, PAUSED, DISABLED), or MISSING when Meta does not
     *     have it, or UNKNOWN when Meta has not been read yet
     * @param metaBodyMatches whether Meta's body is the catalog's, word for word
     * @param ready whether Dad Coach sends it now outside the 24-hour window: registered as approved with this body
     * @param inUseAs for the general template, the name the server sends ({@code WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME}
     *     or the default); null for a message's own template
     */
    public record Row(String name, String language, String category, String purpose, String body, int maxVariables,
                      List<String> examples, List<String> quickReplies, String sample, boolean general,
                      String metaStatus, String metaCategory, String metaPreviousCategory, String metaRejectedReason,
                      boolean metaBodyMatches, String registeredStatus, boolean ready, String inUseAs) {}

    public record Listing(String wabaId, boolean metaConfigured, Instant refreshedAt, String lastError, List<Row> templates) {}

    private final TemplateRegistry registry;
    private final MetaTemplateDirectory meta;
    private final ScheduledResponseCallbackConfig callback;
    private final com.dadcoach.config.WhatsAppProperties whatsapp;

    public AdminTemplatesController(TemplateRegistry registry, MetaTemplateDirectory meta,
                                    ScheduledResponseCallbackConfig callback,
                                    com.dadcoach.config.WhatsAppProperties whatsapp) {
        this.registry = registry;
        this.meta = meta;
        this.callback = callback;
        this.whatsapp = whatsapp;
    }

    @GetMapping
    public Listing list(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        return listing();
    }

    /** Reads Meta now (it is also read every 10 minutes) and aligns what may be sent. */
    @PostMapping("/sync")
    public Listing sync(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        if (!meta.configured()) {
            throw new WebException(HttpStatus.CONFLICT, "META_NOT_CONFIGURED", "WHATSAPP_WABA_ID or WHATSAPP_ACCESS_TOKEN is not set");
        }
        meta.refresh();
        return listing();
    }

    private Listing listing() {
        return new Listing(whatsapp.wabaId(), meta.configured(), meta.refreshedAt().orElse(null),
                meta.lastError().orElse(null), WhatsAppTemplateCatalog.ALL.stream().map(this::row).toList());
    }

    private Row row(WhatsAppTemplateCatalog.Entry entry) {
        boolean general = WhatsAppTemplateCatalog.UPDATE_HE.equals(entry.name());
        Optional<MetaTemplateDirectory.MetaTemplate> atMeta = meta.find(entry.name());
        String metaStatus = atMeta.map(MetaTemplateDirectory.MetaTemplate::status)
                .orElse(meta.refreshedAt().isPresent() ? "MISSING" : "UNKNOWN");
        Optional<TemplateMessage> registered = registry.find(entry.name());
        boolean ready = registered.map(t -> "APPROVED".equals(t.getStatus()) && entry.body().equals(t.getBody()))
                .orElse(false) && (!general || entry.name().equals(callback.effectiveTemplateName()));
        return new Row(entry.name(), entry.language(), entry.category(), entry.purpose(), entry.body(),
                entry.maxVariables(), entry.examples(), entry.quickReplies(), entry.sample(), general,
                metaStatus, atMeta.map(MetaTemplateDirectory.MetaTemplate::category).orElse(null),
                atMeta.map(MetaTemplateDirectory.MetaTemplate::previousCategory).orElse(null),
                atMeta.map(MetaTemplateDirectory.MetaTemplate::rejectedReason).orElse(null),
                atMeta.map(t -> entry.body().equals(t.body())).orElse(false),
                registered.map(TemplateMessage::getStatus).orElse(null), ready,
                general ? callback.effectiveTemplateName() : null);
    }
}
