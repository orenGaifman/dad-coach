package com.dadcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.channel.DeliveryStatusController;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-045 (Unified Workflow Phase 6.3-DC, DC1) / D-053 (6.4): a dashboard link through the platform's Send API - the only
 * sender - against the real rows and a fake platform (FakeServers: {@code /messages/send}, the lookup by key). No real
 * WhatsApp; the test base proves fake Meta never receives a send. Every test also checks how many POSTs could have
 * reached him. The answer table of spec 6.4 §4.1 row by row: 2xx owned, NOT_CREATED (503 disabled, 401/403, never
 * written) pending and re-POSTed, 400/422/409 refused, ambiguous reconciled, lookup 404 re-POSTed, build failure FAILED.
 */
class LoginLinkPlatformSendTest extends AbstractIntegrationTest {

    private static final String WAMID = "wamid.HBgMRDQ1LVBMQVRGT1JNLVNFTkQA";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LoginLinkService links;
    @Autowired LoginLinkPlatform platform;
    @Autowired LoginLinkRateLimiter rateLimiter;
    @Autowired MessagingSettings messaging;
    @Autowired WorkflowPlatformProperties platformProperties;
    @Autowired com.dadcoach.integration.platform.timeline.TimelineReports timelineReports;

    private ListAppender<ILoggingEvent> logs;
    private Logger platformLog;
    /** Every log line of the whole application during the test (review: the sign-in link never reaches a log). */
    private ListAppender<ILoggingEvent> allLogs;
    private Logger rootLog;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        platformLog = (Logger) LoggerFactory.getLogger(LoginLinkPlatform.class);
        logs = new ListAppender<>();
        logs.start();
        platformLog.addAppender(logs);
        rootLog = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        allLogs = new ListAppender<>();
        allLogs.start();
        rootLog.addAppender(allLogs);
    }

    @AfterEach
    void tearDown() {
        platform.setRequestFaultForTests(null);
        platformLog.detachAppender(logs);
        rootLog.detachAppender(allLogs);
        List<ILoggingEvent> lines;
        synchronized (allLogs) {
            lines = List.copyOf(allLogs.list);
        }
        for (ILoggingEvent e : lines) {
            String line = e.getFormattedMessage() + " " + (e.getKeyValuePairs() == null ? "" : e.getKeyValuePairs())
                    + " " + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage());
            assertThat(line).as("no sign-in link in any log line").doesNotContain("token=").doesNotContain("auth/consume");
        }
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Father father(String phone) {
        Father f = data.activeFather(phone);
        data.endpoint(f, true);
        return f;
    }

    private Map<String, Object> link(Father f) {
        return jdbc.queryForMap("SELECT id, delivery_status, delivery_error, receipt_status, provider_message_id, "
                + "gateway_hold_id, platform_message_id, platform_state, platform_status, platform_request, platform_posts, "
                + "platform_next_at FROM login_link WHERE father_id = ?", f.getId());
    }

    private UUID linkId(Father f) {
        return (UUID) link(f).get("id");
    }

    private static Reply answer(int http, String messageId, String status, String wamid, String reason, Long heldId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("messageId", messageId);
        b.put("idempotencyKey", "ignored-by-dad-coach");
        b.put("replayed", false);
        b.put("status", status);
        b.put("providerMessageId", wamid);
        b.put("reason", reason);
        b.put("errorCode", null);
        b.put("channelWindow", "ACCEPTED".equals(status) ? "SESSION" : null);
        b.put("template", null);
        b.put("heldId", heldId);
        try {
            return new Reply(http, new ObjectMapper().writeValueAsString(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Reply accepted(String messageId) {
        return answer(200, messageId, "ACCEPTED", WAMID, null, null);
    }

    private static final Reply EDGE_502 = new Reply(502, "<html><body>Bad Gateway</body></html>", "text/html", null);

    private JsonNode body(FakeServers.Call call) throws Exception {
        return json.readTree(call.body());
    }

    private MvcResult report(Map<String, Object> body, String apiKey, String idempotencyKey) throws Exception {
        var request = post(DeliveryStatusController.PATH).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (apiKey != null) {
            request = request.header("X-API-Key", apiKey);
        }
        if (idempotencyKey != null) {
            request = request.header("X-Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult report(UUID linkId, String messageId, String status, String wamid, String reason) throws Exception {
        return report(reportBody(linkId, messageId, status, wamid, reason), CALLBACK_KEY,
                "delivery:" + messageId + ":" + status);
    }

    private static Map<String, Object> reportBody(UUID linkId, String messageId, String status, String wamid, String reason) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("messageId", messageId);
        b.put("idempotencyKey", linkId == null ? null : LoginLinkPlatform.key(linkId));
        b.put("workerKey", "dad_3");
        b.put("kind", "LOGIN_LINK");
        b.put("status", status);
        b.put("providerMessageId", wamid);
        b.put("reason", reason);
        b.put("errorCode", null);
        b.put("channelWindow", "SESSION");
        b.put("template", null);
        b.put("heldId", null);
        b.put("retryOf", null);
        b.put("resendable", false);
        b.put("at", Map.of("queuedAt", "2026-11-03T10:00:00Z"));
        b.put("reportSeq", 1);
        b.put("someFieldAddedLater", "ignored");
        return b;
    }

    private JsonNode json(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private boolean logged(String message) {
        return logs.list.stream().anyMatch(e -> message.equals(e.getMessage()));
    }

    private ILoggingEvent event(String message) {
        return logs.list.stream().filter(e -> message.equals(e.getMessage())).findFirst().orElseThrow();
    }

    private static String kv(ILoggingEvent e, String key) {
        return e.getKeyValuePairs() == null ? null : e.getKeyValuePairs().stream().filter(p -> p.key.equals(key))
                .map(p -> String.valueOf(p.value)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------------------------------------- PLATFORM

    @Test
    @DisplayName("PLATFORM ACCEPTED: the platform sends it - the bytes were committed before the POST, the wamid is kept, Dad Coach sends and reports nothing")
    void platformAccepted() throws Exception {
        platformProperties.setDeliveryReports(true);
        Father f = father("+19995550807");
        String messageId = UUID.randomUUID().toString();
        AtomicReference<String> storedAtPost = new AtomicReference<>();
        fake.onPlatformSend(c -> {
            // another connection: only a committed row is visible
            storedAtPost.set(jdbc.queryForObject("SELECT platform_request FROM login_link WHERE father_id = ?", String.class,
                    f.getId()));
            return accepted(messageId);
        });

        assertThat(links.sendTo(f.getPhone(), "turn-77")).isEqualTo(LoginLinkService.SendOutcome.SENT);

        assertThat(fake.metaSends()).as("Dad Coach never calls Meta itself").isEmpty();
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).as("the platform gates, not Dad Coach").isEmpty();
        assertThat(fake.platformSends()).hasSize(1);
        FakeServers.Call call = fake.platformSends().get(0);
        assertThat(storedAtPost.get()).as("the exact bytes are durable before the POST").isEqualTo(call.body());
        assertThat(call.header("X-API-Key")).isEqualTo("test-platform-key-0123456789");
        Map<String, Object> row = link(f);
        assertThat(row).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", null)
                .containsEntry("provider_message_id", WAMID).containsEntry("platform_message_id", messageId)
                .containsEntry("platform_state", "OWNED").containsEntry("platform_status", "ACCEPTED")
                .containsEntry("platform_request", null).containsEntry("platform_posts", 1).containsEntry("platform_next_at", null);

        JsonNode r = body(call);
        UUID id = (UUID) row.get("id");
        assertThat(r.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(r.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(r.path("userId").asText()).isEqualTo("whatsapp:" + f.getPhone());
        assertThat(r.path("to").asText()).isEqualTo(f.getPhone());
        assertThat(r.path("idempotencyKey").asText()).isEqualTo("login-link:" + id);
        assertThat(r.path("kind").asText()).isEqualTo("LOGIN_LINK");
        assertThat(r.path("content").path("type").asText()).isEqualTo("link_button");
        assertThat(r.path("content").path("text").asText()).isEqualTo(LoginLinkDelivery.FATHER_TEXT);
        assertThat(r.path("content").path("footer").asText()).isEqualTo(LoginLinkDelivery.FOOTER);
        assertThat(r.path("content").path("link").path("title").asText()).isEqualTo(LoginLinkDelivery.FATHER_LABEL);
        String url = r.path("content").path("link").path("url").asText();
        assertThat(url).startsWith("https://app.dadcoach.test/auth/consume#token=");
        assertThat(r.path("fallbackText").asText()).contains(url).startsWith(LoginLinkDelivery.FATHER_TEXT.strip());
        assertThat(r.has("template")).as("D-053: never a Meta template name").isFalse();
        assertThat(r.has("fallbackTemplates")).isFalse();
        assertThat(r.path("templateData").path("variant").asText()).as("the kind's default message type").isEmpty();
        assertThat(r.path("templateData").path("named").path("text").asText())
                .isEqualTo(LoginLinkDelivery.TEMPLATE_LINE_PREFIX.strip() + " " + url);
        assertThat(r.path("windowPolicy").asText()).isEqualTo("AUTO");
        assertThat(r.path("outsideWindowWithoutTemplate").asText()).isEqualTo("FAIL");
        assertThat(r.path("windowHint").path("lastInboundAt").asText()).isEqualTo("2026-11-03T09:00:00Z");
        assertThat(r.path("holdPolicy").asText()).isEqualTo("GATEWAY");
        assertThat(r.path("timeline").path("correlationId").asText()).isEqualTo("dashboard-link:" + id);
        assertThat(r.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(r.path("timeline").path("turnCorrelationId").asText()).isEqualTo("turn-77");
        assertThat(r.path("timeline").path("content").asText()).doesNotContain("token").doesNotContain("http");
        assertThat(r.has("dryRun")).as("D-053: no SHADOW, no dry run").isFalse();
        assertThat(r.path("personRef").asText()).isEqualTo(com.dadcoach.integration.platform.lifecycle.PersonRefs.of(f.getId()));
        assertThat(r.path("timeline").path("record").asBoolean()).isTrue();
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).as("the platform records the timeline row itself").isEmpty();
        // the link works
        String token = url.substring(url.indexOf("token=") + 6);
        assertThat(links.use(token)).isPresent();
    }

    @Test
    @DisplayName("PLATFORM HELD / QUEUED / FAILED / UNKNOWN map onto the link row (spec §7.4)")
    void platformAnswersMapOntoTheRow() {
        Father held = father("+19995550808");
        fake.onPlatformSend(c -> answer(200, UUID.randomUUID().toString(), "HELD", null, "ON_ANOTHER_PRODUCT", 42L));
        assertThat(links.sendTo(held.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(held)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "HELD")
                .containsEntry("gateway_hold_id", "held:42").containsEntry("platform_status", "HELD");
        assertThat(link(held).get("platform_next_at")).as("looked up by key if no report comes").isNotNull();

        Father queued = father("+19995550809");
        fake.onPlatformSend(c -> answer(202, UUID.randomUUID().toString(), "QUEUED", null, null, null));
        assertThat(links.sendTo(queued.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(queued)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "QUEUED")
                .containsEntry("platform_state", "OWNED");
        assertThat(links.sendTo(queued.getPhone())).as("on its way: no second card stacked under it")
                .isEqualTo(LoginLinkService.SendOutcome.ALREADY_SENT);

        Father failed = father("+19995550810");
        fake.onPlatformSend(c -> answer(200, UUID.randomUUID().toString(), "FAILED", null, "WINDOW_CLOSED", null));
        assertThat(links.sendTo(failed.getPhone())).isEqualTo(LoginLinkService.SendOutcome.FAILED);
        assertThat(link(failed)).containsEntry("delivery_status", "FAILED").containsEntry("delivery_error", "PLATFORM_WINDOW_CLOSED");

        Father unknown = father("+19995550811");
        fake.onPlatformSend(c -> answer(200, UUID.randomUUID().toString(), "UNKNOWN", null, "SEND_UNKNOWN_TIMEOUT", null));
        assertThat(links.sendTo(unknown.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(unknown)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "UNKNOWN")
                .containsEntry("delivery_error", "PLATFORM_SEND_UNKNOWN_TIMEOUT");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("NOT_CREATED: 503 messaging.disabled - pending (on its way, the bytes kept), ERROR logged; the job re-POSTs the same bytes")
    void disabledIsPendingAndRePosted() throws Exception {
        platformProperties.setDeliveryReports(true);
        Father f = father("+19995550812");
        fake.onPlatformSend(c -> FakeServers.platformDisabled());

        assertThat(links.sendTo(f.getPhone())).as("on its way: the platform will be asked again")
                .isEqualTo(LoginLinkService.SendOutcome.SENT);

        assertThat(fake.platformSends()).as("no immediate re-POST of a kill switch").hasSize(1);
        assertThat(link(f)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "QUEUED")
                .containsEntry("platform_state", "NOT_CREATED").containsEntry("platform_message_id", null);
        assertThat((String) link(f).get("platform_request")).isEqualTo(fake.platformSends().get(0).body());
        assertThat(event("messaging.platform_disabled").getLevel().toString()).isEqualTo("ERROR");
        assertThat(links.sendTo(f.getPhone())).as("pending, not stuck: no second card")
                .isEqualTo(LoginLinkService.SendOutcome.ALREADY_SENT);

        assertThat(platform.reconcileDue()).as("not due yet").isZero();
        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(link(f)).as("still disabled: still pending").containsEntry("platform_state", "NOT_CREATED");

        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> accepted(messageId));
        clock.advance(Duration.ofMinutes(2));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.platformSends().get(2).body()).as("the same bytes, the same key").isEqualTo(fake.platformSends().get(0).body());
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("platform_message_id", messageId)
                .containsEntry("provider_message_id", WAMID).containsEntry("receipt_status", null)
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).as("the platform records the timeline row itself").isEmpty();
    }

    @Test
    @DisplayName("NOT_CREATED past its relevance (1 h for a link): FAILED PLATFORM_NOT_CREATED, never posted again")
    void notCreatedEndsWhenNoLongerRelevant() {
        Father f = father("+19995550830");
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        links.sendTo(f.getPhone());
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofMinutes(10));
            platform.reconcileDue();
        }
        assertThat(link(f)).containsEntry("delivery_status", "FAILED").containsEntry("delivery_error", "PLATFORM_NOT_CREATED")
                .containsEntry("platform_state", "GAVE_UP").containsEntry("platform_request", null)
                .containsEntry("platform_next_at", null);
        int posts = fake.platformSends().size();
        assertThat(posts).as("re-POSTed while relevant, with backoff").isBetween(4, 8);
        rateLimiter.reset();
        fake.onPlatformSend(c -> accepted(UUID.randomUUID().toString()));
        assertThat(links.sendTo(f.getPhone())).as("a FAILED link is not on his screen: a new card")
                .isEqualTo(LoginLinkService.SendOutcome.SENT);
    }

    @Test
    @DisplayName("NOT_CREATED: a link revoked meanwhile is never posted again - FAILED PLATFORM_NOT_CREATED")
    void aRevokedLinkEnds() {
        Father f = father("+19995550831");
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        links.sendTo(f.getPhone());
        assertThat(link(f)).containsEntry("platform_state", "NOT_CREATED");
        jdbc.update("UPDATE login_link SET revoked_at = now(), revoked_reason = 'LOGOUT_ALL' WHERE father_id = ?", f.getId());
        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).as("a revoked link is never posted again").hasSize(1);
        assertThat(link(f)).containsEntry("delivery_status", "FAILED").containsEntry("delivery_error", "PLATFORM_NOT_CREATED");
    }

    @Test
    @DisplayName("NOT_CREATED: 401/403 (refused before any handler) - pending, ERROR messaging.platform_auth_failed")
    void authFailureIsNotCreated() {
        Father f = father("+19995550813");
        fake.onPlatformSend(c -> new Reply(403, "{\"error\":\"integration.product_violation\"}"));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(link(f)).containsEntry("platform_state", "NOT_CREATED").containsEntry("receipt_status", "QUEUED");
        assertThat(event("messaging.platform_auth_failed").getLevel().toString()).isEqualTo("ERROR");

        fake.onPlatformSend(c -> new Reply(401, "{}"));
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(link(f)).containsEntry("platform_state", "NOT_CREATED");
    }

    @Test
    @DisplayName("A request that cannot be built (a bug): FAILED REQUEST_BUILD_FAILED + ERROR, nothing posted")
    void requestBuildFailureFails() {
        Father f = father("+19995550832");
        platform.setRequestFaultForTests(new IllegalStateException("bug"));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.FAILED);
        assertThat(fake.platformSends()).isEmpty();
        assertThat(link(f)).containsEntry("delivery_status", "FAILED").containsEntry("delivery_error", "REQUEST_BUILD_FAILED");
        assertThat(event("messaging.login_link.request_failed").getLevel().toString()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("D-053: a staff user's link goes through the platform too - no person, no timeline row, the staff line for the template")
    void staffLinkThroughThePlatform() throws Exception {
        UUID staffId = UUID.randomUUID();
        jdbc.update("INSERT INTO staff_user (id, phone, display_name) VALUES (?, ?, 'צוות')",
                staffId, "+19995550833");
        assertThat(links.sendTo("+19995550833")).isEqualTo(LoginLinkService.SendOutcome.SENT);

        assertThat(fake.platformSends()).hasSize(1);
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("kind").asText()).isEqualTo("LOGIN_LINK");
        assertThat(r.path("to").asText()).isEqualTo("+19995550833");
        assertThat(r.has("personRef")).isFalse();
        assertThat(r.path("content").path("text").asText()).isEqualTo(LoginLinkDelivery.STAFF_TEXT);
        assertThat(r.path("content").path("link").path("title").asText()).isEqualTo(LoginLinkDelivery.STAFF_LABEL);
        assertThat(r.path("windowPolicy").asText()).as("the platform decides window vs template").isEqualTo("AUTO");
        assertThat(r.path("templateData").path("variant").asText()).isEmpty();
        assertThat(r.path("templateData").path("named").path("text").asText())
                .startsWith(LoginLinkDelivery.STAFF_TEMPLATE_LINE_PREFIX.strip()).contains("auth/consume#token=");
        assertThat(r.path("timeline").path("record").asBoolean()).as("a staff user has no conversation").isFalse();
        Map<String, Object> row = jdbc.queryForMap("SELECT delivery_status, platform_state, provider_message_id FROM login_link "
                + "WHERE staff_user_id = ?", staffId);
        assertThat(row).containsEntry("delivery_status", "SENT").containsEntry("platform_state", "OWNED");
        assertThat((String) row.get("provider_message_id")).startsWith("wamid.platform.");

        // a staff link nothing was created for is re-POSTed like a father's
        rateLimiter.reset();
        jdbc.update("DELETE FROM login_link WHERE staff_user_id = ?", staffId);
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        links.sendTo("+19995550833");
        fake.onPlatformSend(fake::accept);
        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT platform_state FROM login_link WHERE staff_user_id = ?", String.class, staffId))
                .isEqualTo("OWNED");
    }

    @Test
    @DisplayName("PLATFORM 400/422: FAILED PLATFORM_<error>, never sent directly; 409 on a first attempt: PLATFORM_KEY_CONFLICT")
    void refusalsFailWithoutDirect() {
        Father unusable = father("+19995550814");
        fake.onPlatformSend(c -> new Reply(422, "{\"error\":\"messaging.template_unusable\",\"template\":\"x\",\"reason\":\"NOT_FOUND\"}"));
        assertThat(links.sendTo(unusable.getPhone())).isEqualTo(LoginLinkService.SendOutcome.FAILED);
        assertThat(link(unusable)).containsEntry("delivery_status", "FAILED")
                .containsEntry("delivery_error", "PLATFORM_TEMPLATE_UNUSABLE_NOT_FOUND").containsEntry("platform_state", "REFUSED")
                .containsEntry("platform_request", null);

        Father conflict = father("+19995550815");
        fake.onPlatformSend(c -> new Reply(409, "{\"error\":\"messaging.idempotency_conflict\",\"messageId\":\""
                + UUID.randomUUID() + "\",\"status\":\"ACCEPTED\",\"providerMessageId\":\"wamid.other\",\"replayed\":true}"));
        assertThat(links.sendTo(conflict.getPhone())).isEqualTo(LoginLinkService.SendOutcome.FAILED);
        assertThat(link(conflict)).containsEntry("delivery_error", "PLATFORM_KEY_CONFLICT").containsEntry("provider_message_id", null);

        assertThat(fake.platformSends()).as("no re-POST of a refusal").hasSize(2);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM ambiguous (edge 502): the same bytes re-POSTed, never sent directly; reconciled by key and adopted")
    void ambiguousIsNeverSentDirectly() throws Exception {
        Father f = father("+19995550816");
        fake.onPlatformSend(c -> EDGE_502);

        assertThat(links.sendTo(f.getPhone())).as("on its way").isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.platformSends()).as("one immediate re-POST").hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        Map<String, Object> row = link(f);
        assertThat(row).containsEntry("platform_state", "AMBIGUOUS").containsEntry("delivery_status", "SENT")
                .containsEntry("receipt_status", "QUEUED").containsEntry("platform_posts", 2);
        assertThat((String) row.get("platform_request")).isEqualTo(fake.platformSends().get(0).body());
        assertThat(links.sendTo(f.getPhone())).as("no second card while it may be on its way")
                .isEqualTo(LoginLinkService.SendOutcome.ALREADY_SENT);

        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).as("the last re-POST").hasSize(3);
        assertThat(fake.platformSends().get(2).body()).isEqualTo(fake.platformSends().get(0).body());

        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).as("waits for MESSAGING_RECONCILE_AFTER").isZero();
        clock.advance(Duration.ofMinutes(4));
        fake.onPlatformLookup(c -> new Reply(500, "{}"));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformLookups()).hasSize(1);
        assertThat(fake.platformLookups().get(0)).containsEntry("workerKey", "dad_3")
                .containsEntry("idempotencyKey", "login-link:" + linkId(f));
        assertThat(link(f)).containsEntry("platform_state", "AMBIGUOUS");

        String messageId = UUID.randomUUID().toString();
        fake.onPlatformLookup(c -> accepted(messageId));
        clock.advance(Duration.ofMinutes(5));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("platform_message_id", messageId)
                .containsEntry("provider_message_id", WAMID).containsEntry("receipt_status", null)
                .containsEntry("platform_request", null);
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.metaSends()).as("never sent directly").isEmpty();
        assertThat(logged("messaging.login_link.reconciled")).isTrue();
    }

    @Test
    @DisplayName("Ambiguous, then the lookup says not found (nothing created): the stored bytes are POSTed again - one message")
    void ambiguousThenNotFoundRePostsTheStoredBytes() {
        Father f = father("+19995550817");
        fake.onPlatformSend(c -> new Reply(504, "Gateway Timeout", "text/plain", null));
        links.sendTo(f.getPhone());
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(3);

        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> accepted(messageId));
        clock.advance(Duration.ofMinutes(5));
        assertThat(platform.reconcileDue()).isEqualTo(1);   // default lookup: 404 messaging.message_not_found
        assertThat(fake.platformLookups()).hasSize(1);
        assertThat(fake.platformSends()).hasSize(4);
        assertThat(fake.platformSends().get(3).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("platform_message_id", messageId)
                .containsEntry("delivery_status", "SENT").containsEntry("platform_request", null);
        assertThat(logged("messaging.login_link.not_found_repost")).isTrue();

        clock.advance(Duration.ofMinutes(10));
        assertThat(platform.reconcileDue()).isZero();
        assertThat(fake.platformSends()).hasSize(4);
    }

    @Test
    @DisplayName("PLATFORM ambiguous with a lookup that never answers: UNKNOWN after an hour, never sent")
    void ambiguousGivesUpAsUnknown() {
        Father f = father("+19995550818");
        fake.onPlatformSend(c -> EDGE_502);
        fake.onPlatformLookup(c -> EDGE_502);
        links.sendTo(f.getPhone());
        for (int i = 0; i < 20; i++) {
            clock.advance(Duration.ofMinutes(5));
            platform.reconcileDue();
        }
        assertThat(link(f)).containsEntry("platform_state", "GAVE_UP").containsEntry("receipt_status", "UNKNOWN")
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.platformSends()).hasSize(3);
    }

    @Test
    @DisplayName("PLATFORM timeout: no re-POST on the turn's thread; the job re-POSTs the exact bytes and adopts the replay")
    void timeoutThenReplay() {
        messaging.setReadTimeoutMs(300);
        Father f = father("+19995550819");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return accepted(messageId);
        });
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(link(f)).containsEntry("platform_state", "AMBIGUOUS").containsEntry("platform_posts", 1);

        messaging.setReadTimeoutMs(12_000);
        fake.onPlatformSend(c -> {
            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("messageId", messageId);
            replay.put("replayed", true);
            replay.put("status", "DELIVERED");
            replay.put("providerMessageId", WAMID);
            try {
                return Reply.json(new ObjectMapper().writeValueAsString(replay));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("receipt_status", "DELIVERED")
                .containsEntry("provider_message_id", WAMID).containsEntry("platform_message_id", messageId);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM 409 after an ambiguous attempt: adopted (the existing message is this one), never FAILED")
    void conflictAfterAmbiguousIsAdopted() {
        Father f = father("+19995550820");
        String messageId = UUID.randomUUID().toString();
        List<Reply> replies = new ArrayList<>(List.of(EDGE_502, new Reply(409, "{\"error\":\"messaging.idempotency_conflict\","
                + "\"message\":\"x\",\"messageId\":\"" + messageId + "\",\"status\":\"ACCEPTED\",\"providerMessageId\":\""
                + WAMID + "\",\"reason\":null,\"errorCode\":null,\"replayed\":true}")));
        fake.onPlatformSend(c -> replies.remove(0));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("delivery_status", "SENT")
                .containsEntry("platform_message_id", messageId).containsEntry("provider_message_id", WAMID);
        assertThat(event("messaging.platform_conflict_adopted").getLevel().toString()).isEqualTo("WARN");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("Never written / disabled on a first attempt: NOT_CREATED; after an ambiguous one: ambiguous (review M1)")
    void neverWrittenOnlyFirst() {
        var never = new com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer(
                com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind.NEVER_WRITTEN, 0, null,
                "ConnectException", null);
        assertThat(LoginLinkPlatform.verdict(never, false)).isEqualTo(LoginLinkPlatform.Verdict.NOT_CREATED);
        assertThat(LoginLinkPlatform.verdict(never, true)).isEqualTo(LoginLinkPlatform.Verdict.AMBIGUOUS);
        var disabled = new com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer(
                com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind.ANSWERED, 503,
                json.createObjectNode().put("error", "messaging.disabled"), null, null);
        assertThat(LoginLinkPlatform.verdict(disabled, false)).isEqualTo(LoginLinkPlatform.Verdict.NOT_CREATED);
        assertThat(LoginLinkPlatform.verdict(disabled, true)).as("review M1").isEqualTo(LoginLinkPlatform.Verdict.AMBIGUOUS);
    }

    @Test
    @DisplayName("PLATFORM HELD that never gets a report: looked up by key after an hour and applied")
    void heldWithoutReportIsLookedUp() {
        Father f = father("+19995550821");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(200, messageId, "HELD", null, "ON_ANOTHER_PRODUCT", 7L));
        links.sendTo(f.getPhone());
        clock.advance(Duration.ofMinutes(30));
        assertThat(platform.reconcileDue()).isZero();
        clock.advance(Duration.ofMinutes(31));
        fake.onPlatformLookup(c -> accepted(messageId));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(link(f)).containsEntry("receipt_status", null).containsEntry("provider_message_id", WAMID)
                .containsEntry("platform_status", "ACCEPTED").containsEntry("platform_next_at", null);
    }

    @Test
    @DisplayName("Review M1: an ambiguous POST, then 503 messaging.disabled on the re-POST - ambiguous until the lookup says not found, then pending")
    void disabledAfterAmbiguousWaitsForTheLookup() {
        Father f = father("+19995550827");
        List<Reply> replies = new ArrayList<>(List.of(EDGE_502));
        Reply disabled = FakeServers.platformDisabled();
        fake.onPlatformSend(c -> replies.isEmpty() ? disabled : replies.remove(0));

        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(link(f)).as("the first POST may still be in flight on another instance").containsEntry("platform_state", "AMBIGUOUS");

        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(link(f)).containsEntry("platform_state", "AMBIGUOUS");

        clock.advance(Duration.ofMinutes(4));
        assertThat(platform.reconcileDue()).isEqualTo(1);       // the lookup: 404 not found = never created
        assertThat(fake.platformLookups()).hasSize(1);
        assertThat(fake.platformSends()).as("re-POSTed: the platform holds nothing under the key").hasSize(4);
        assertThat(link(f)).containsEntry("platform_state", "NOT_CREATED").containsEntry("delivery_status", "SENT");
    }

    @Test
    @DisplayName("Review L1: an ambiguous link adopted as QUEUED keeps its follow-up lookup until the platform says more")
    void adoptedQueuedKeepsItsLookup() {
        Father f = father("+19995550828");
        String messageId = UUID.randomUUID().toString();
        List<Reply> replies = new ArrayList<>(List.of(EDGE_502, answer(202, messageId, "QUEUED", null, null, null)));
        fake.onPlatformSend(c -> replies.remove(0));
        links.sendTo(f.getPhone());
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("platform_status", "QUEUED")
                .containsEntry("receipt_status", "QUEUED").containsEntry("platform_message_id", messageId);
        assertThat(link(f).get("platform_next_at")).as("looked up if no report comes").isNotNull();

        clock.advance(Duration.ofMinutes(5));
        fake.onPlatformLookup(c -> answer(200, messageId, "QUEUED", null, null, null));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(link(f).get("platform_next_at")).as("still queued: looked up again").isNotNull();

        clock.advance(Duration.ofMinutes(5));
        fake.onPlatformLookup(c -> accepted(messageId));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(link(f)).containsEntry("platform_status", "ACCEPTED").containsEntry("provider_message_id", WAMID)
                .containsEntry("platform_next_at", null);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("Review L1: the lookup of an ambiguous link answering QUEUED adopts it and keeps looking")
    void reconcileAnsweringQueuedKeepsLooking() {
        Father f = father("+19995550829");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> EDGE_502);
        links.sendTo(f.getPhone());
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        clock.advance(Duration.ofMinutes(5));
        fake.onPlatformLookup(c -> answer(200, messageId, "QUEUED", null, null, null));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(link(f)).containsEntry("platform_state", "OWNED").containsEntry("platform_status", "QUEUED")
                .containsEntry("platform_request", null);
        assertThat(link(f).get("platform_next_at")).isNotNull();
        assertThat(fake.metaSends()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- reports

    @Test
    @DisplayName("Delivery reports move the row forward only; Meta's own receipts on the webhook change nothing (D-053)")
    void reportsAreForwardOnly() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father("+19995550822");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(202, messageId, "QUEUED", null, null, null));
        links.sendTo(f.getPhone());
        UUID id = linkId(f);

        assertThat(json(report(id, messageId, "HELD", null, "ON_ANOTHER_PRODUCT")).path("applied").asBoolean()).isTrue();
        assertThat(link(f)).containsEntry("receipt_status", "HELD");
        assertThat(json(report(id, messageId, "ACCEPTED", WAMID, null)).path("applied").asBoolean()).isTrue();
        assertThat(link(f)).containsEntry("receipt_status", null).containsEntry("provider_message_id", WAMID)
                .containsEntry("platform_next_at", null);

        // D-053: Meta's own receipts are the platform's - one still forwarded by the gateway changes nothing here
        webhook(receipt(WAMID, "read", f.getPhone()));
        webhook(receipt(WAMID, "failed", f.getPhone()));
        assertThat(link(f)).containsEntry("receipt_status", null).containsEntry("delivery_status", "SENT");
        assertThat(json(report(id, messageId, "READ", WAMID, null)).path("applied").asBoolean()).isTrue();
        assertThat(link(f)).containsEntry("receipt_status", "READ");
        MvcResult stale = report(id, messageId, "DELIVERED", WAMID, null);
        assertThat(json(stale).path("known").asBoolean()).isTrue();
        assertThat(json(stale).path("applied").asBoolean()).as("never back").isFalse();
        assertThat(json(report(id, messageId, "FAILED", WAMID, "META_131026")).path("applied").asBoolean())
                .as("FAILED only before DELIVERED").isFalse();
        assertThat(link(f)).containsEntry("receipt_status", "READ").containsEntry("delivery_status", "SENT");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("A report that beats the sync answer is applied; the later QUEUED answer does not move it back")
    void reportBeforeTheAnswer() {
        Father f = father("+19995550823");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> {
            UUID id = (UUID) link(f).get("id");
            platform.applyReport(LoginLinkPlatform.key(id), messageId, toNode(reportBody(id, messageId, "ACCEPTED", WAMID, null)));
            return answer(202, messageId, "QUEUED", null, null, null);
        });
        links.sendTo(f.getPhone());
        assertThat(link(f)).containsEntry("platform_status", "ACCEPTED").containsEntry("provider_message_id", WAMID)
                .containsEntry("receipt_status", null).containsEntry("platform_state", "OWNED");
    }

    @Test
    @DisplayName("A FAILED report after acceptance marks the link FAILED (his next request sends a new card)")
    void failedReport() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father("+19995550824");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> accepted(messageId));
        links.sendTo(f.getPhone());
        report(linkId(f), messageId, "FAILED", WAMID, "META_131047");
        assertThat(link(f)).containsEntry("delivery_status", "FAILED").containsEntry("delivery_error", "PLATFORM_META_131047")
                .containsEntry("receipt_status", "FAILED");
        rateLimiter.reset();
        fake.onPlatformSend(c -> accepted(UUID.randomUUID().toString()));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
    }

    @Test
    @DisplayName("Reports: switch off = 404 and no change, then the platform's retry applies; duplicate key; unknown; matched by messageId; 400; 401")
    void reportEndpointContract() throws Exception {
        Father f = father("+19995550825");
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(202, messageId, "QUEUED", null, null, null));
        links.sendTo(f.getPhone());
        UUID id = linkId(f);

        MvcResult off = report(id, messageId, "ACCEPTED", WAMID, null);
        assertThat(off.getResponse().getStatus()).isEqualTo(404);
        assertThat(link(f)).containsEntry("receipt_status", "QUEUED");

        messaging.setDeliveryStatusEnabled(true);
        MvcResult on = report(id, messageId, "ACCEPTED", WAMID, null);
        assertThat(on.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(on).path("applied").asBoolean()).isTrue();
        assertThat(json(on).path("known").asBoolean()).isTrue();
        MvcResult again = report(id, messageId, "ACCEPTED", WAMID, null);
        assertThat(json(again).path("duplicate").asBoolean()).isTrue();

        // matched by the platform's message id alone
        Map<String, Object> byId = reportBody(null, messageId, "DELIVERED", WAMID, null);
        MvcResult delivered = report(byId, CALLBACK_KEY, "delivery:" + messageId + ":DELIVERED");
        assertThat(json(delivered).path("applied").asBoolean()).isTrue();
        assertThat(link(f)).containsEntry("receipt_status", "DELIVERED");

        String other = UUID.randomUUID().toString();
        MvcResult unknown = report(reportBody(UUID.randomUUID(), other, "ACCEPTED", "wamid.x", null), CALLBACK_KEY,
                "delivery:" + other + ":ACCEPTED");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(unknown).path("known").asBoolean()).isFalse();

        assertThat(report(reportBody(id, messageId, "READ", WAMID, null), CALLBACK_KEY, null).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(report(reportBody(id, messageId, "READ", WAMID, null), CALLBACK_KEY, "delivery:someone-else:READ")
                .getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> noStatus = reportBody(id, messageId, null, WAMID, null);
        assertThat(report(noStatus, CALLBACK_KEY, "delivery:" + messageId + ":X").getResponse().getStatus()).isEqualTo(400);
        assertThat(report(reportBody(id, messageId, "READ", WAMID, null), null, "delivery:" + messageId + ":READ")
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(report(reportBody(id, messageId, "READ", WAMID, null), TOOL_KEY, "delivery:" + messageId + ":READ")
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(link(f)).containsEntry("receipt_status", "DELIVERED");
    }

    @Test
    @DisplayName("A report for a link sent before D-053 by Dad Coach itself (state DIRECT) is never applied")
    void reportForALegacyDirectRowIsIgnored() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father("+19995550826");
        links.sendTo(f.getPhone());
        jdbc.update("UPDATE login_link SET platform_state = 'DIRECT', platform_message_id = NULL WHERE father_id = ?", f.getId());
        String messageId = UUID.randomUUID().toString();
        MvcResult r = report(linkId(f), messageId, "FAILED", null, "NO_TEMPLATE");
        assertThat(json(r).path("applied").asBoolean()).isFalse();
        assertThat(link(f)).containsEntry("delivery_status", "SENT");
        assertThat(event("messaging.login_link.not_platform").getLevel().toString()).isEqualTo("WARN");
    }

    // ------------------------------------------------------------------------------------------------- more helpers

    private JsonNode toNode(Map<String, Object> body) {
        return json.valueToTree(body);
    }

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        int status = mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    private static String receipt(String wamid, String status, String recipientE164) {
        return Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"15550000000\","
                + "\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid + "\",\"status\":\"" + status
                + "\",\"timestamp\":\"1793700100\",\"recipient_id\":\"" + recipientE164.substring(1) + "\"}]}");
    }
}
