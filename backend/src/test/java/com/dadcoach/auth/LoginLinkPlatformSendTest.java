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
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
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
 * D-045 (Unified Workflow Phase 6.3-DC, DC1): a father's dashboard link through the platform's Send API, against the
 * real rows and a fake platform (FakeServers: {@code /messages/send}, the lookup by key, Meta). No real WhatsApp.
 * Every test also checks how many messages could have reached him: Meta sends made by Dad Coach itself, and POSTs.
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
    void tearDown() throws InterruptedException {
        for (int i = 0; i < 100 && !platform.shadowIdle(); i++) {
            Thread.sleep(50);
        }
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

    private void awaitShadow() throws InterruptedException {
        for (int i = 0; i < 100 && !(platform.shadowIdle() && !fake.platformSends().isEmpty()); i++) {
            Thread.sleep(50);
        }
        for (int i = 0; i < 100 && !platform.shadowIdle(); i++) {
            Thread.sleep(50);
        }
    }

    /** A Meta send body with the phone and the sign-in token masked (both differ per father / per link). */
    private String masked(FakeServers.Call metaSend, Father f) {
        return metaSend.body().replace(f.getPhone().substring(1), "PHONE").replaceAll("token=[A-Za-z0-9_-]+", "token=TOKEN");
    }

    // ------------------------------------------------------------------------------------------------- DIRECT

    @Test
    @DisplayName("DIRECT (default): today's path - one Meta send, nothing asked of the Send API, no platform column written")
    void directIsTodaysPath() {
        platformProperties.setDeliveryReports(true);
        Father f = father("+19995550801");
        assertThat(messaging.loginLink()).isEqualTo(Mode.DIRECT);

        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);

        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.platformSends()).isEmpty();
        assertThat(fake.platformLookups()).isEmpty();
        assertThat((String) link(f).get("provider_message_id")).startsWith("wamid.out.");
        assertThat(link(f)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", null)
                .containsEntry("platform_message_id", null).containsEntry("platform_state", null)
                .containsEntry("platform_request", null).containsEntry("platform_posts", 0).containsEntry("platform_next_at", null);
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).hasSize(1);   // D-039 timeline report, as before
        assertThat(fake.recordedOutbound().get(0).body()).contains("dashboard-link:" + linkId(f));
    }

    @Test
    @DisplayName("DIRECT and SHADOW send Meta the same bytes (phone and token aside), and the same as with no Send API at all")
    void directAndShadowSendMetaTheSameBytes() throws Exception {
        Father direct = father("+19995550802");
        links.sendTo(direct.getPhone());
        String directBody = masked(fake.metaSends().get(0), direct);
        Map<String, Object> directRow = link(direct);

        fake.reset();
        messaging.setLoginLink(Mode.SHADOW);
        Father shadow = father("+19995550803");
        links.sendTo(shadow.getPhone());
        awaitShadow();
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(masked(fake.metaSends().get(0), shadow)).isEqualTo(directBody);
        Map<String, Object> shadowRow = link(shadow);
        for (String column : List.of("delivery_status", "delivery_error", "receipt_status", "gateway_hold_id",
                "platform_message_id", "platform_state", "platform_status", "platform_request", "platform_posts")) {
            assertThat(shadowRow.get(column)).as(column).isEqualTo(directRow.get(column));
        }
        assertThat(json.readTree(directBody).path("interactive").path("type").asText()).isEqualTo("cta_url");
    }

    // ------------------------------------------------------------------------------------------------- SHADOW

    @Test
    @DisplayName("SHADOW: the dry run runs after the send, off the send thread, and never changes the row or sends twice")
    void shadowDryRunIsOffTheSendThread() throws Exception {
        messaging.setLoginLink(Mode.SHADOW);
        Father f = father("+19995550804");
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> dryRunThreadSaw = new AtomicReference<>();
        fake.onPlatformSend(c -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dryRunThreadSaw.set("asked");
            return Reply.json("{\"wouldBe\":\"SEND\",\"channelWindow\":\"SESSION\",\"template\":null,\"reason\":null}");
        });

        long started = System.nanoTime();
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        long tookMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(tookMs).as("the send never waits for the dry run").isLessThan(5_000);
        assertThat(dryRunThreadSaw.get()).as("the dry run is still waiting when the send has returned").isNull();
        release.countDown();
        awaitShadow();

        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.platformSends()).hasSize(1);
        JsonNode dry = body(fake.platformSends().get(0));
        assertThat(dry.path("dryRun").asBoolean()).isTrue();
        assertThat(dry.path("idempotencyKey").asText()).isEqualTo(LoginLinkPlatform.key(linkId(f)));
        assertThat(dry.path("kind").asText()).isEqualTo("LOGIN_LINK");
        assertThat(dry.path("content").path("type").asText()).isEqualTo("link_button");
        assertThat(link(f)).containsEntry("platform_state", null).containsEntry("platform_request", null)
                .containsEntry("delivery_status", "SENT");
        assertThat(logged("messaging.shadow.match")).isTrue();
        assertThat(logged("messaging.shadow.mismatch")).isFalse();
    }

    @Test
    @DisplayName("SHADOW: a different decision is logged as a mismatch (hold, window), still one message")
    void shadowLogsMismatches() throws Exception {
        messaging.setLoginLink(Mode.SHADOW);
        Father held = father("+19995550805");
        fake.onPlatformSend(c -> Reply.json("{\"wouldBe\":\"HOLD\",\"channelWindow\":null,\"holdWhy\":\"ON_ANOTHER_PRODUCT\"}"));
        links.sendTo(held.getPhone());
        awaitShadow();
        ILoggingEvent hold = event("messaging.shadow.mismatch");
        assertThat(kv(hold, "field")).isEqualTo("hold");
        assertThat(kv(hold, "product")).isEqualTo("SEND");

        logs.list.clear();
        fake.reset();
        rateLimiter.reset();
        fake.onPlatformSend(c -> Reply.json("{\"wouldBe\":\"SEND\",\"channelWindow\":\"TEMPLATE\",\"template\":{\"name\":\"x\"}}"));
        Father window = father("+19995550806");
        links.sendTo(window.getPhone());
        awaitShadow();
        ILoggingEvent w = event("messaging.shadow.mismatch");
        assertThat(kv(w, "field")).isEqualTo("window");
        assertThat(kv(w, "product")).isEqualTo("SESSION");
        assertThat(kv(w, "platform")).isEqualTo("TEMPLATE");
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    @DisplayName("SHADOW (review M2): the platform would refuse the request (422) while Dad Coach's send went out - a mismatch, not a match")
    void shadowRefusalIsAMismatch() throws Exception {
        messaging.setLoginLink(Mode.SHADOW);
        Father f = father("+19995550840");
        fake.onPlatformSend(c -> new Reply(422, "{\"error\":\"messaging.template_unusable\",\"template\":\"x\",\"reason\":\"NOT_FOUND\"}"));
        links.sendTo(f.getPhone());
        awaitShadow();
        ILoggingEvent refused = event("messaging.shadow.mismatch");
        assertThat(kv(refused, "field")).isEqualTo("refused");
        assertThat(kv(refused, "code")).isEqualTo("PLATFORM_TEMPLATE_UNUSABLE_NOT_FOUND");
        assertThat(kv(refused, "product")).isEqualTo("SEND");
        assertThat(logged("messaging.shadow.match")).isFalse();
        assertThat(fake.metaSends()).hasSize(1);
    }

    // ------------------------------------------------------------------------------------------------- PLATFORM

    @Test
    @DisplayName("PLATFORM ACCEPTED: the platform sends it - the bytes were committed before the POST, the wamid is kept, Dad Coach sends and reports nothing")
    void platformAccepted() throws Exception {
        messaging.setLoginLink(Mode.PLATFORM);
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
        assertThat(r.path("template").path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(r.path("template").path("language").asText()).isEqualTo("he");
        assertThat(r.path("template").path("bodyParams").get(0).asText()).contains(url);
        assertThat(r.path("windowPolicy").asText()).isEqualTo("AUTO");
        assertThat(r.path("outsideWindowWithoutTemplate").asText()).isEqualTo("FAIL");
        assertThat(r.path("windowHint").path("lastInboundAt").asText()).isEqualTo("2026-11-03T09:00:00Z");
        assertThat(r.path("holdPolicy").asText()).isEqualTo("GATEWAY");
        assertThat(r.path("timeline").path("correlationId").asText()).isEqualTo("dashboard-link:" + id);
        assertThat(r.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(r.path("timeline").path("turnCorrelationId").asText()).isEqualTo("turn-77");
        assertThat(r.path("timeline").path("content").asText()).doesNotContain("token").doesNotContain("http");
        assertThat(r.path("dryRun").asBoolean()).isFalse();
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).as("the platform records the timeline row itself").isEmpty();
        // the link works
        String token = url.substring(url.indexOf("token=") + 6);
        assertThat(links.use(token)).isPresent();
    }

    @Test
    @DisplayName("PLATFORM HELD / QUEUED / FAILED / UNKNOWN map onto the link row (spec §7.4)")
    void platformAnswersMapOntoTheRow() {
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("PLATFORM 503 messaging.disabled (nothing created): Dad Coach sends it itself, once, today's way")
    void disabledFallsBackDirect() {
        messaging.setLoginLink(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);
        Father f = father("+19995550812");   // the fake platform is inert by default, as production is today

        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);

        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).as("the direct path keeps the gate").hasSize(1);
        assertThat(link(f)).containsEntry("delivery_status", "SENT").containsEntry("platform_state", "DIRECT")
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
        assertThat((String) link(f).get("provider_message_id")).startsWith("wamid.out.");
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).as("Dad Coach reports its own send, as today").hasSize(1);
        assertThat(platform.reconcileDue()).isZero();
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    @DisplayName("PLATFORM 401/403 (refused before any handler): direct, with an ERROR log")
    void authFailureFallsBackDirect() {
        messaging.setLoginLink(Mode.PLATFORM);
        Father f = father("+19995550813");
        fake.onPlatformSend(c -> new Reply(403, "{\"error\":\"integration.product_violation\"}"));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(link(f)).containsEntry("platform_state", "DIRECT");
        assertThat(event("messaging.platform_auth_failed").getLevel().toString()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("PLATFORM 400/422: FAILED PLATFORM_<error>, never sent directly; 409 on a first attempt: PLATFORM_KEY_CONFLICT")
    void refusalsFailWithoutDirect() {
        messaging.setLoginLink(Mode.PLATFORM);
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
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("PLATFORM ambiguous, then the lookup says not found: nothing was created, so Dad Coach sends it itself - once")
    void ambiguousThenNotFoundSendsDirectlyOnce() {
        messaging.setLoginLink(Mode.PLATFORM);
        Father f = father("+19995550817");
        fake.onPlatformSend(c -> new Reply(504, "Gateway Timeout", "text/plain", null));
        links.sendTo(f.getPhone());
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.metaSends()).isEmpty();

        clock.advance(Duration.ofMinutes(5));
        assertThat(platform.reconcileDue()).isEqualTo(1);   // default lookup: 404 messaging.message_not_found
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(link(f)).containsEntry("platform_state", "DIRECT").containsEntry("delivery_status", "SENT")
                .containsEntry("receipt_status", null).containsEntry("platform_request", null);

        clock.advance(Duration.ofMinutes(10));
        assertThat(platform.reconcileDue()).isZero();
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    @DisplayName("PLATFORM ambiguous with a lookup that never answers: UNKNOWN after an hour, never sent")
    void ambiguousGivesUpAsUnknown() {
        messaging.setLoginLink(Mode.PLATFORM);
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
        messaging.setLoginLink(Mode.PLATFORM);
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
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("PLATFORM never-written first attempt (no platform reachable) is direct; after an ambiguous one it is not")
    void neverWrittenOnlyFirst() {
        var never = new com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer(
                com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind.NEVER_WRITTEN, 0, null,
                "ConnectException", null);
        assertThat(LoginLinkPlatform.verdict(never, false)).isEqualTo(LoginLinkPlatform.Verdict.DIRECT);
        assertThat(LoginLinkPlatform.verdict(never, true)).isEqualTo(LoginLinkPlatform.Verdict.AMBIGUOUS);
        var disabled = new com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer(
                com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind.ANSWERED, 503,
                json.createObjectNode().put("error", "messaging.disabled"), null, null);
        assertThat(LoginLinkPlatform.verdict(disabled, false)).isEqualTo(LoginLinkPlatform.Verdict.DIRECT);
        assertThat(LoginLinkPlatform.verdict(disabled, true)).as("review M1").isEqualTo(LoginLinkPlatform.Verdict.AMBIGUOUS);
        assertThat(platform.sends(new SignInSubject(null, UUID.randomUUID()))).as("staff links stay direct").isFalse();
    }

    @Test
    @DisplayName("PLATFORM HELD that never gets a report: looked up by key after an hour and applied")
    void heldWithoutReportIsLookedUp() {
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("Review M1: an ambiguous POST, then 503 messaging.disabled on the re-POST - never direct until the lookup says not found")
    void disabledAfterAmbiguousWaitsForTheLookup() {
        messaging.setLoginLink(Mode.PLATFORM);
        Father f = father("+19995550827");
        List<Reply> replies = new ArrayList<>(List.of(EDGE_502));
        Reply disabled = new Reply(503, "{\"error\":\"messaging.disabled\",\"reason\":\"DISABLED\"}");
        fake.onPlatformSend(c -> replies.isEmpty() ? disabled : replies.remove(0));

        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).as("the first POST may still be in flight on another instance").isEmpty();
        assertThat(link(f)).containsEntry("platform_state", "AMBIGUOUS");

        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.metaSends()).isEmpty();
        assertThat(link(f)).containsEntry("platform_state", "AMBIGUOUS");

        clock.advance(Duration.ofMinutes(4));
        assertThat(platform.reconcileDue()).isEqualTo(1);       // the lookup: 404 not found = never created
        assertThat(fake.platformLookups()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(link(f)).containsEntry("platform_state", "DIRECT").containsEntry("delivery_status", "SENT");
        clock.advance(Duration.ofMinutes(10));
        platform.reconcileDue();
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    @DisplayName("Review L1: an ambiguous link adopted as QUEUED keeps its follow-up lookup until the platform says more")
    void adoptedQueuedKeepsItsLookup() {
        messaging.setLoginLink(Mode.PLATFORM);
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
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("Delivery reports move the row forward only, with Meta's own receipts in between")
    void reportsAreForwardOnly() throws Exception {
        messaging.setLoginLink(Mode.PLATFORM);
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

        // Meta's own "read" receipt (forwarded by the gateway) matches the wamid as before, ahead of the platform
        webhook(receipt(WAMID, "read", f.getPhone()));
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
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("Report tx: Meta's failed receipt that beat the report is applied once the report links its wamid - "
            + "committed (the work after the report's own transaction is never lost)")
    void keptReceiptAppliedAfterTheReportCommits() throws Exception {
        messaging.setLoginLink(Mode.PLATFORM);
        messaging.setDeliveryStatusEnabled(true);
        Father f = father("+19995550829");
        String messageId = UUID.randomUUID().toString();
        String wamid = "wamid.reporttx." + UUID.randomUUID();
        fake.onPlatformSend(c -> answer(202, messageId, "QUEUED", null, null, null));
        links.sendTo(f.getPhone());
        UUID id = linkId(f);

        webhook(receipt(wamid, "failed", f.getPhone()));   // no row holds the wamid yet: kept
        assertThat(link(f)).containsEntry("provider_message_id", null);

        assertThat(json(report(id, messageId, "ACCEPTED", wamid, null)).path("applied").asBoolean()).isTrue();

        assertThat(link(f)).as("the report's move and the kept receipt, both committed")
                .containsEntry("platform_status", "ACCEPTED").containsEntry("provider_message_id", wamid)
                .containsEntry("receipt_status", "FAILED").containsEntry("delivery_status", "FAILED");
    }

    @Test
    @DisplayName("A FAILED report after acceptance marks the link FAILED (his next request sends a new card)")
    void failedReport() throws Exception {
        messaging.setLoginLink(Mode.PLATFORM);
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
        messaging.setLoginLink(Mode.PLATFORM);
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
    @DisplayName("A report for a link Dad Coach sent itself (DIRECT fallback) is never applied")
    void reportForADirectRowIsIgnored() throws Exception {
        messaging.setLoginLink(Mode.PLATFORM);
        messaging.setDeliveryStatusEnabled(true);
        Father f = father("+19995550826");
        links.sendTo(f.getPhone());                         // inert platform: direct
        assertThat(link(f)).containsEntry("platform_state", "DIRECT");
        String messageId = UUID.randomUUID().toString();
        MvcResult r = report(linkId(f), messageId, "FAILED", null, "NO_TEMPLATE");
        assertThat(json(r).path("applied").asBoolean()).isFalse();
        assertThat(link(f)).containsEntry("delivery_status", "SENT");
        assertThat(event("messaging.login_link.platform_after_direct").getLevel().toString()).isEqualTo("ERROR");
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
