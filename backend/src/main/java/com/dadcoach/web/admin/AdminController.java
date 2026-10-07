package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.auth.DashboardProperties;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.scheduled.ScheduledResponseCallbackConfig;
import com.dadcoach.web.common.Areas;
import com.dadcoach.web.father.HomeView;
import com.dadcoach.web.training.TrainingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The internal Dad Coach admin (/api/admin/**, playbook §40): the team only (a coded 403 for anyone else, checked
 * on every call); a father who does not exist is a 404.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    static final ZoneId PRODUCT_ZONE = ZoneId.of("Asia/Jerusalem");

    public record DeleteRequest(@Size(max = 120) String confirmation) {
    }

    private final AdminQueries queries;
    private final AdminFathersService fathers;
    private final PlatformAdminClient platformAdmin;
    private final WorkflowPlatformProperties platform;
    private final ScheduledResponseCallbackConfig callback;
    private final DashboardProperties properties;
    private final TrainingService training;
    private final Clock clock;
    private final String whatsappPhoneNumberId;
    private final String whatsappAccessToken;
    private final String googleClientId;
    private final String webBaseUrl;

    public AdminController(AdminQueries queries, AdminFathersService fathers, PlatformAdminClient platformAdmin,
                           WorkflowPlatformProperties platform, ScheduledResponseCallbackConfig callback,
                           DashboardProperties properties, TrainingService training, Clock clock,
                           @Value("${dad-coach.whatsapp.phone-number-id:}") String whatsappPhoneNumberId,
                           @Value("${dad-coach.whatsapp.access-token:}") String whatsappAccessToken,
                           @Value("${google.calendar.client-id:}") String googleClientId,
                           @Value("${dad-coach.web.base-url:}") String webBaseUrl) {
        this.queries = queries;
        this.fathers = fathers;
        this.platformAdmin = platformAdmin;
        this.platform = platform;
        this.callback = callback;
        this.properties = properties;
        this.training = training;
        this.clock = clock;
        this.whatsappPhoneNumberId = whatsappPhoneNumberId;
        this.whatsappAccessToken = whatsappAccessToken;
        this.googleClientId = googleClientId;
        this.webBaseUrl = webBaseUrl;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        Instant now = clock.instant();
        LocalDate weekStart = LocalDate.ofInstant(now, PRODUCT_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        Instant from = weekStart.atStartOfDay(PRODUCT_ZONE).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(PRODUCT_ZONE).toInstant();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weekStart", weekStart.toString());
        body.put("fathersByStatus", queries.fathersByStatus());
        body.put("activeThisWeek", queries.activeSince(from));
        body.put("goalsSet", queries.goalsSet(weekStart));
        body.put("goalsMet", queries.goalsMet(weekStart));
        body.put("sessionsThisWeek", queries.sessionsBetween(from, to, null));
        body.put("sessionsCompletedThisWeek", queries.sessionsBetween(from, to, "COMPLETED"));
        body.put("failedDeliveries7d", queries.failedDeliveriesSince(now.minus(Duration.ofDays(7))));
        body.put("pendingDeletions", queries.pendingDeletions());
        return body;
    }

    @GetMapping("/fathers")
    public List<AdminQueries.FatherRow> fathers(@AuthenticationPrincipal DashboardPrincipal p,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String status) {
        Areas.requireStaff(p);
        return queries.fathers(q, status == null || status.isBlank() ? null : status.strip().toUpperCase(), 200);
    }

    @GetMapping("/fathers/{id}")
    public AdminFathersService.FatherDetail father(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable long id) {
        Areas.requireStaff(p);
        return fathers.detail(id);
    }

    /** View-as (D-105): exactly the father's own GET /api/father/home. */
    @GetMapping("/fathers/{id}/home")
    public HomeView fatherHome(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable long id) {
        Areas.requireStaff(p);
        return fathers.homeOf(id);
    }

    @GetMapping("/fathers/{id}/platform")
    public Map<String, Object> fatherPlatform(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable long id) {
        Areas.requireStaff(p);
        return platformAdmin.panelFor(fathers.find(id).getPhone());
    }

    @PostMapping("/fathers/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable long id) {
        Areas.requireStaff(p);
        fathers.deactivate(id, p);
    }

    @PostMapping("/fathers/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable long id) {
        Areas.requireStaff(p);
        fathers.reactivate(id);
    }

    @DeleteMapping("/fathers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable long id,
                       @Valid @RequestBody DeleteRequest body) {
        Areas.requireStaff(p);
        fathers.deletePermanently(id, body.confirmation());
    }

    @GetMapping("/undelivered")
    public List<AdminQueries.DeliveryRow> undelivered(@AuthenticationPrincipal DashboardPrincipal p,
                                                      @RequestParam(defaultValue = "7") int days) {
        Areas.requireStaff(p);
        int window = Math.max(1, Math.min(days, 30));
        return queries.undelivered(clock.instant().minus(Duration.ofDays(window)), null, 200);
    }

    @GetMapping("/deletions")
    public Map<String, Object> deletions(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pending", queries.pendingDeletions());
        body.put("rows", queries.pendingDeletionRows(100));
        body.put("deactivated", queries.fathers(null, "PAUSED", 200));
        return body;
    }

    @GetMapping("/integrations")
    public Map<String, Object> integrations(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        Map<String, Object> whatsapp = new LinkedHashMap<>();
        whatsapp.put("configured", !blank(whatsappPhoneNumberId) && !blank(whatsappAccessToken));
        whatsapp.put("publicNumber", blank(properties.getWhatsappPublicNumber()) ? null : properties.getWhatsappPublicNumber());
        whatsapp.put("lastInbound", queries.lastInbound());
        whatsapp.put("lastOutbound", queries.lastOutbound());
        whatsapp.put("lastFailure", queries.lastFailure());
        Map<String, Object> plat = new LinkedHashMap<>();
        plat.put("enabled", platform.isEnabled());
        plat.put("baseUrlSet", !blank(platform.getBaseUrl()));
        plat.put("reachable", platform.isEnabled() && platformAdmin.reachable());
        plat.put("adminPanelConfigured", platformAdmin.configured());
        plat.put("callbackEnabled", callback.isEnabled() && !blank(callback.getApiKey()));
        plat.put("callbackTemplate", !blank(callback.getTemplateName()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("whatsapp", whatsapp);
        body.put("platform", plat);
        body.put("calendarOAuthConfigured", !blank(googleClientId));
        body.put("opsApiConfigured", !blank(properties.getOpsApiKey()));
        body.put("trainingMediaConfigured", training.admin().mediaConfigured());
        body.put("webBaseUrl", webBaseUrl);
        return body;
    }

    @GetMapping("/training")
    public TrainingService.AdminView training(@AuthenticationPrincipal DashboardPrincipal p) {
        Areas.requireStaff(p);
        return training.admin();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
