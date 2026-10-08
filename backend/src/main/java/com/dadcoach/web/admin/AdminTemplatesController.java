package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.channel.template.TemplateMessage;
import com.dadcoach.channel.template.TemplateMessageRepository;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.integration.platform.scheduled.ScheduledResponseCallbackConfig;
import com.dadcoach.web.common.Areas;
import com.dadcoach.web.common.WebException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The templates screen (BB D-167 pattern, the team only): each template the code sends, exactly as the
 * owner submits it to Meta, beside its state here — whether the callback is configured to use it and
 * whether it is registered as approved in {@code template_messages} (the send-side gate,
 * {@link TemplateRegistry}). Dad Coach has no direct Meta connection (it sends through the platform
 * gateway), so submission happens in Meta's UI by the owner; after Meta approves, the "mark approved"
 * action here is what used to be a manual INSERT.
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplatesController {

    public record Row(String name, String language, String category, String body, int maxVariables,
                      String example, String sample, boolean configured, String configuredName,
                      String registeredStatus, boolean registeredBodyMatches) {}

    public record Approve(Boolean confirmed) {}

    private final TemplateMessageRepository templates;
    private final TemplateRegistry registry;
    private final ScheduledResponseCallbackConfig callback;

    public AdminTemplatesController(TemplateMessageRepository templates, TemplateRegistry registry,
                                    ScheduledResponseCallbackConfig callback) {
        this.templates = templates;
        this.registry = registry;
        this.callback = callback;
    }

    @GetMapping
    public List<Row> list(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        return WhatsAppTemplateCatalog.ALL.stream().map(this::row).toList();
    }

    /**
     * Records a template as approved in {@code template_messages}, with the catalog's body — only after
     * the owner saw Meta approve it; nothing is sent to Meta from here.
     */
    @PostMapping("/{name}/approve")
    public Row approve(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable String name,
                       @RequestBody Approve request) {
        Areas.requireStaff(p);
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new WebException(HttpStatus.BAD_REQUEST, "NOT_CONFIRMED",
                    "mark approved only from the review dialog, after Meta approved the template");
        }
        WhatsAppTemplateCatalog.Entry entry = WhatsAppTemplateCatalog.named(name)
                .orElseThrow(() -> new WebException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "no such template"));
        registry.registerOrUpdate(entry.name(), entry.language(), entry.category(), entry.body(), "APPROVED",
                entry.maxVariables());
        return row(entry);
    }

    private Row row(WhatsAppTemplateCatalog.Entry entry) {
        Optional<TemplateMessage> registered = templates.findByTemplateName(entry.name());
        String configuredName = callback.getTemplateName();
        return new Row(entry.name(), entry.language(), entry.category(), entry.body(), entry.maxVariables(),
                entry.example(), entry.sample(),
                entry.name().equals(configuredName == null ? "" : configuredName.strip()),
                configuredName == null || configuredName.isBlank() ? null : configuredName.strip(),
                registered.map(TemplateMessage::getStatus).orElse(null),
                registered.map(t -> entry.body().equals(t.getBody())).orElse(false));
    }
}
