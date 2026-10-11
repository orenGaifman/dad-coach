package com.dadcoach.integration.platform.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.channel.DeliveryStatusController;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.FakeServers.Reply;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
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
 * D-046 (Unified Workflow Phase 6.3-DC, DC2): the platform's scheduled-response callbacks sent through the platform's
 * Send API, against the real rows, the real callback endpoint and a fake platform + fake Meta (FakeServers). No real
 * WhatsApp. Every test also counts what could have reached him: Meta sends made by Dad Coach itself, and POSTs.
 */
class ScheduledPlatformSendTest extends AbstractIntegrationTest {

    static final String PHONE = "+19995550950";
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final String WAMID = "wamid.HBgMRDQ1LVNDSEVEVUxFRAA";
    static final Reply EDGE_502 = new Reply(502, "<html><body>Bad Gateway</body></html>", "text/html", null);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessagingSettings messaging;
    @Autowired WorkflowPlatformProperties platformProperties;
    @Autowired TimelineReports timelineReports;
    @Autowired ScheduledPlatformSend platform;
    @Autowired PlatformSendFlow flow;
    @Autowired QualityTimeRepository sessions;

    Father father;
    Child itamar;
    private ListAppender<ILoggingEvent> logs;
    private Logger platformLog;

    @BeforeEach
    void setUp() {
        father = data.father(PHONE, "אורן", com.dadcoach.father.FatherStatus.ACTIVE);
        itamar = data.child(father, "איתמר", 6);
        data.endpoint(father, true);
        platformLog = (Logger) LoggerFactory.getLogger(ScheduledPlatformSend.class);
        logs = new ListAppender<>();
        logs.start();
        platformLog.addAppender(logs);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (int i = 0; i < 100 && !flow.shadowIdle(); i++) {
            Thread.sleep(50);
        }
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(10))).isTrue();
        platformLog.detachAppender(logs);
    }

    // ------------------------------------------------------------------------------------------------- helpers

    MvcResult callback(String triggerId, String state, String content) throws Exception {
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w1", "userId",
                "whatsapp:" + PHONE, "channel", "whatsapp", "targetStateKey", state, "responseContent", content));
        MvcResult r = mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r;
    }

    String answerText(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    JsonNode answer(MvcResult r) throws Exception {
        return json.readTree(answerText(r));
    }

    Map<String, Object> row(String triggerId) {
        return jdbc.queryForMap("SELECT id, status, failure_reason, provider_message_id, gateway_hold_id, delivery_mode, "
                + "platform_message_id, platform_state, platform_status, platform_request, platform_posts, platform_next_at "
                + "FROM scheduled_response_delivery WHERE trigger_id = ?", triggerId);
    }

    JsonNode body(FakeServers.Call call) throws Exception {
        return json.readTree(call.body());
    }

    static Reply answer(int http, String messageId, String status, String wamid, String reason, Long heldId, String window) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("messageId", messageId);
        b.put("idempotencyKey", "ignored-by-dad-coach");
        b.put("replayed", false);
        b.put("status", status);
        b.put("providerMessageId", wamid);
        b.put("reason", reason);
        b.put("errorCode", null);
        b.put("channelWindow", window);
        b.put("template", null);
        b.put("heldId", heldId);
        try {
            return new Reply(http, new ObjectMapper().writeValueAsString(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Reply accepted(String messageId) {
        return answer(200, messageId, "ACCEPTED", WAMID, null, null, "SESSION");
    }

    QualityTime endedSession() {
        Instant start = clock.instant().minus(Duration.ofMinutes(90));
        return sessions.saveAndFlush(new QualityTime(father, itamar, start, start.plus(Duration.ofMinutes(60))));
    }

    boolean logged(String message) {
        return logs.list.stream().anyMatch(e -> message.equals(e.getMessage()));
    }

    ILoggingEvent event(String message) {
        return logs.list.stream().filter(e -> message.equals(e.getMessage())).findFirst().orElseThrow();
    }

    static String kv(ILoggingEvent e, String key) {
        return e.getKeyValuePairs() == null ? null : e.getKeyValuePairs().stream().filter(p -> p.key.equals(key))
                .map(p -> String.valueOf(p.value)).findFirst().orElse(null);
    }

    void awaitShadow() throws InterruptedException {
        for (int i = 0; i < 100 && !(flow.shadowIdle() && !fake.platformSends().isEmpty()); i++) {
            Thread.sleep(50);
        }
        for (int i = 0; i < 100 && !flow.shadowIdle(); i++) {
            Thread.sleep(50);
        }
    }

    MvcResult report(String key, String messageId, String kind, String status, String wamid) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("messageId", messageId);
        b.put("idempotencyKey", key);
        b.put("workerKey", "dad_3");
        b.put("kind", kind);
        b.put("status", status);
        b.put("providerMessageId", wamid);
        b.put("reason", null);
        b.put("channelWindow", "SESSION");
        b.put("reportSeq", 1);
        return mvc.perform(post(DeliveryStatusController.PATH).contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", CALLBACK_KEY).header("X-Idempotency-Key", "delivery:" + messageId + ":" + status)
                .content(json.writeValueAsString(b))).andReturn();
    }

    // ------------------------------------------------------------------------------------------------- DIRECT

    @Test
    @DisplayName("DIRECT (default): today's path - Meta sends, the Send API is never asked, no platform column, D-039 answer")
    void directIsTodaysPath() throws Exception {
        platformProperties.setDeliveryReports(true);
        assertThat(messaging.scheduled()).isEqualTo(Mode.DIRECT);

        JsonNode a = answer(callback("t-direct", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?"));

        assertThat(a.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(a.path("outcome").asText()).isEqualTo("AS_IS");
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.platformSends()).isEmpty();
        assertThat(fake.platformLookups()).isEmpty();
        assertThat(row("t-direct")).containsEntry("status", "ACCEPTED").containsEntry("platform_message_id", null)
                .containsEntry("platform_state", null).containsEntry("platform_request", null)
                .containsEntry("platform_posts", 0).containsEntry("platform_next_at", null);
        assertThat(platform.reconcileDue()).isZero();
    }

    @Test
    @DisplayName("DIRECT and SHADOW: Meta gets the same bytes and the callback the same answer, byte for byte ({status, detail})")
    void directAndShadowAreByteIdentical() throws Exception {
        endedSession();
        MvcResult direct = callback("t-d", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה?");
        String directMeta = fake.metaSends().get(0).body();
        assertThat(answerText(direct)).isEqualTo("{\"status\":\"DELIVERED\",\"detail\":\"Delivered\"}");
        assertThat(json.readTree(directMeta).path("interactive").path("action").path("buttons")).hasSize(2);

        fake.reset();
        messaging.setScheduled(Mode.SHADOW);
        fake.onPlatformSend(c -> Reply.json("{\"wouldBe\":\"SEND\",\"channelWindow\":\"SESSION\",\"template\":null}"));
        MvcResult shadow = callback("t-s", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה?");
        awaitShadow();
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.metaSends().get(0).body()).isEqualTo(directMeta);
        assertThat(answerText(shadow)).isEqualTo(answerText(direct));
        for (String column : List.of("status", "failure_reason", "gateway_hold_id", "delivery_mode", "platform_message_id",
                "platform_state", "platform_status", "platform_request", "platform_posts", "platform_next_at")) {
            assertThat(row("t-s").get(column)).as(column).isEqualTo(row("t-d").get(column));
        }
    }

    // ------------------------------------------------------------------------------------------------- SHADOW

    @Test
    @DisplayName("SHADOW: the dry run (same request, dryRun) runs off the callback's thread; a different window is a logged mismatch")
    void shadowIsOffThreadAndLogsMismatches() throws Exception {
        messaging.setScheduled(Mode.SHADOW);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> asked = new AtomicReference<>();
        fake.onPlatformSend(c -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            asked.set("asked");
            return Reply.json("{\"wouldBe\":\"SEND\",\"channelWindow\":\"TEMPLATE\",\"template\":{\"name\":\"" + TEMPLATE + "\"}}");
        });
        long started = System.nanoTime();
        JsonNode a = answer(callback("t-shadow", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat((System.nanoTime() - started) / 1_000_000).as("the callback never waits for the dry run").isLessThan(5_000);
        assertThat(asked.get()).isNull();
        release.countDown();
        awaitShadow();

        assertThat(a.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.platformSends()).hasSize(1);
        JsonNode dry = body(fake.platformSends().get(0));
        assertThat(dry.path("dryRun").asBoolean()).isTrue();
        assertThat(dry.path("idempotencyKey").asText()).isEqualTo("scheduled-response:t-shadow");
        assertThat(dry.path("kind").asText()).isEqualTo("SCHEDULED");
        assertThat(row("t-shadow")).containsEntry("platform_state", null).containsEntry("status", "ACCEPTED");
        ILoggingEvent mismatch = event("messaging.shadow.mismatch");
        assertThat(kv(mismatch, "field")).isEqualTo("window");
        assertThat(kv(mismatch, "product")).isEqualTo("SESSION");
        assertThat(kv(mismatch, "kind")).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("SHADOW (review M2): a platform refusal (422 / 400) while Dad Coach's send went out is a mismatch; "
            + "when Dad Coach's send failed too it is a match")
    void shadowRefusalIsAMismatch() throws Exception {
        messaging.setScheduled(Mode.SHADOW);
        fake.onPlatformSend(c -> new Reply(422, "{\"error\":\"messaging.template_unusable\",\"template\":\"x\",\"reason\":\"NOT_FOUND\"}"));
        callback("t-sr", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        awaitShadow();
        ILoggingEvent refused = event("messaging.shadow.mismatch");
        assertThat(kv(refused, "field")).isEqualTo("refused");
        assertThat(kv(refused, "code")).isEqualTo("PLATFORM_TEMPLATE_UNUSABLE_NOT_FOUND");
        assertThat(kv(refused, "kind")).isEqualTo("SCHEDULED");
        assertThat(logged("messaging.shadow.match")).isFalse();

        logs.list.clear();
        fake.reset();
        fake.onPlatformSend(c -> new Reply(400, "{\"error\":\"messaging.invalid_request\",\"field\":\"content.text\"}"));
        fake.onMetaSend(c -> new Reply(400, "{\"error\":{\"code\":131026,\"message\":\"Message undeliverable\"}}"));
        callback("t-sr2", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        awaitShadow();
        assertThat(logged("messaging.shadow.mismatch")).as("both refuse it").isFalse();
        assertThat(logged("messaging.shadow.match")).isTrue();
    }

    @Test
    @DisplayName("SHADOW lanes per kind (review N3): a SCHEDULED burst that fills its lane drops (counted, logged) only "
            + "SCHEDULED comparisons; a LOGIN_LINK comparison still runs")
    void shadowLanesArePerKind() throws Exception {
        org.slf4j.Logger testLog = LoggerFactory.getLogger("shadow-lanes-test");
        ListAppender<ILoggingEvent> lanes = new ListAppender<>();
        lanes.start();
        ((Logger) testLog).addAppender(lanes);
        CountDownLatch release = new CountDownLatch(1);
        fake.onPlatformSend(c -> Reply.json("{\"wouldBe\":\"SEND\",\"channelWindow\":\"SESSION\",\"template\":null}"));
        PlatformSendFlow.ShadowFacts sent = new PlatformSendFlow.ShadowFacts(true, false, null, null);
        long droppedBefore = flow.shadowDropped("SCHEDULED");
        try {
            flow.shadow("SCHEDULED", testLog, "id", "blocker", () -> {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "{}";
            }, sent);
            for (int i = 0; i < 105; i++) {
                flow.shadow("SCHEDULED", testLog, "id", "burst-" + i, () -> "{}", sent);
            }
            assertThat(flow.shadowDropped("SCHEDULED") - droppedBefore).as("the burst beyond the queue is dropped")
                    .isGreaterThanOrEqualTo(5);
            flow.shadow("LOGIN_LINK", testLog, "id", "link", () -> "{\"kind\":\"LOGIN_LINK\"}", sent);
            for (int i = 0; i < 100 && lanes.list.stream().noneMatch(e -> "messaging.shadow.match".equals(e.getMessage())
                    && "LOGIN_LINK".equals(kv(e, "kind"))); i++) {
                Thread.sleep(50);
            }
            assertThat(lanes.list).as("the link's comparison ran while the SCHEDULED lane was stuck")
                    .anyMatch(e -> "messaging.shadow.match".equals(e.getMessage()) && "LOGIN_LINK".equals(kv(e, "kind")));
        } finally {
            release.countDown();
            for (int i = 0; i < 200 && !flow.shadowIdle(); i++) {
                Thread.sleep(50);
            }
            ((Logger) testLog).detachAppender(lanes);
        }
    }

    // ------------------------------------------------------------------------------------------------- PLATFORM

    @Test
    @DisplayName("PLATFORM ACCEPTED: bytes committed before the POST; the session template + buttons, the general fallback, "
            + "window hint, notAfter, REPLACES_DRAFT; Dad Coach sends and reports nothing; the answer is {platformMessageId}")
    void platformAcceptedFollowUp() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);
        QualityTime session = endedSession();
        String messageId = UUID.randomUUID().toString();
        AtomicReference<String> storedAtPost = new AtomicReference<>();
        fake.onPlatformSend(c -> {
            storedAtPost.set(jdbc.queryForObject("SELECT platform_request FROM scheduled_response_delivery WHERE trigger_id = ?",
                    String.class, "t-follow"));
            return accepted(messageId);
        });

        MvcResult r = callback("t-follow", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה היום? ספר לי הכול");

        assertThat(answerText(r)).as("the platform records the timeline row: no outcome, no deliveredContent")
                .isEqualTo("{\"platformMessageId\":\"" + messageId + "\"}");
        assertThat(fake.metaSends()).as("Dad Coach never calls Meta itself").isEmpty();
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).as("the platform gates").isEmpty();
        assertThat(fake.platformSends()).hasSize(1);
        FakeServers.Call call = fake.platformSends().get(0);
        assertThat(storedAtPost.get()).as("the exact bytes are durable before the POST").isEqualTo(call.body());
        assertThat(row("t-follow")).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", WAMID)
                .containsEntry("delivery_mode", "FREE_FORM").containsEntry("platform_message_id", messageId)
                .containsEntry("platform_state", "OWNED").containsEntry("platform_status", "ACCEPTED")
                .containsEntry("platform_request", null).containsEntry("platform_posts", 1).containsEntry("platform_next_at", null);
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.timelineCalls()).as("no second record").isEmpty();
        assertThat(jdbc.queryForObject("SELECT mentioned_on IS NOT NULL FROM quality_time WHERE id = ?", Boolean.class,
                session.getId())).as("the coach's mentions are noted as for a message handed over").isTrue();

        JsonNode q = body(call);
        assertThat(q.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(q.path("userId").asText()).isEqualTo("whatsapp:" + PHONE);
        assertThat(q.path("to").asText()).isEqualTo(PHONE);
        assertThat(q.path("idempotencyKey").asText()).isEqualTo("scheduled-response:t-follow");
        assertThat(q.path("kind").asText()).isEqualTo("SCHEDULED");
        assertThat(q.path("messageClass").asText()).isEqualTo("UTILITY");
        JsonNode content = q.path("content");
        assertThat(content.path("type").asText()).isEqualTo("buttons");
        String text = content.path("text").asText();
        assertThat(text).startsWith(IDENTITY).contains("איתמר");
        assertThat(content.path("buttons")).hasSize(2);
        assertThat(content.path("buttons").get(0).path("id").asText()).isEqualTo("dc:done:" + session.getId());
        assertThat(content.path("buttons").get(0).path("title").asText()).isEqualTo("היה מעולה");
        JsonNode own = q.path("template");
        assertThat(own.path("name").asText()).isEqualTo(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE);
        assertThat(own.path("language").asText()).isEqualTo("he");
        assertThat(own.path("bodyParams").get(0).asText()).isEqualTo("איתמר");
        assertThat(own.path("buttons")).hasSize(2);
        assertThat(own.path("buttons").get(0).path("subType").asText()).isEqualTo("quick_reply");
        assertThat(own.path("buttons").get(0).path("index").asInt()).isZero();
        assertThat(own.path("buttons").get(0).path("payload").asText()).isEqualTo("dc:done:" + session.getId());
        assertThat(own.path("buttons").get(1).path("payload").asText()).isEqualTo("dc:missed:" + session.getId());
        assertThat(own.path("expectedBody").asText())
                .isEqualTo(WhatsAppTemplateCatalog.require(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE).body());
        assertThat(q.path("fallbackTemplates")).hasSize(1);
        JsonNode general = q.path("fallbackTemplates").get(0);
        assertThat(general.path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(general.path("bodyParams").get(0).asText()).isEqualTo(ProactiveSender.asTemplateParameter(text));
        assertThat(general.has("buttons")).isFalse();
        assertThat(q.path("windowPolicy").asText()).isEqualTo("AUTO");
        assertThat(q.path("outsideWindowWithoutTemplate").asText()).isEqualTo("FAIL");
        assertThat(q.path("windowHint").path("lastInboundAt").asText()).as("session_closes_at - 24 h")
                .isEqualTo("2026-11-03T09:00:00Z");
        assertThat(q.path("holdPolicy").asText()).isEqualTo("GATEWAY");
        assertThat(Instant.parse(q.path("notAfter").asText())).as("12 h after the session ended")
                .isEqualTo(session.getScheduledEnd().plus(Duration.ofHours(12)));
        JsonNode t = q.path("timeline");
        assertThat(t.path("record").asBoolean()).isTrue();
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo("scheduled-transition:t-follow");
        assertThat(t.path("correlationId").asText()).isEqualTo("callback:scheduled-transition:t-follow");
        assertThat(t.path("content").asText()).isEqualTo(text.substring(IDENTITY.length()));
        assertThat(q.path("wait").asText()).isEqualTo("ACCEPTED");
        assertThat(q.path("dryRun").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("PLATFORM templates and notAfter per message: daily check (general only, end of his day), hour-before "
            + "(its template, the session's start), morning (the day's last session's start)")
    void platformTemplatesAndNotAfterPerMessage() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        fake.onPlatformSend(c -> answer(202, UUID.randomUUID().toString(), "QUEUED", null, null, null, null));

        callback("t-daily", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!\nאיך הולך השבוע?");
        JsonNode daily = body(fake.platformSends().get(0));
        assertThat(daily.path("content").path("type").asText()).isEqualTo("text");
        assertThat(daily.path("template").path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(daily.path("template").path("bodyParams").get(0).asText()).isEqualTo("בוקר טוב! איך הולך השבוע?");
        assertThat(daily.has("fallbackTemplates")).isFalse();
        assertThat(daily.path("notAfter").asText()).as("midnight in Israel").isEqualTo("2026-11-03T22:00:00Z");
        assertThat(row("t-daily")).as("QUEUED leaves it SENDING (spec 7.4) and keeps a lookup")
                .containsEntry("status", "SENDING").containsEntry("platform_status", "QUEUED")
                .containsEntry("platform_state", "OWNED");
        assertThat(row("t-daily").get("platform_next_at")).isNotNull();

        Instant soon = clock.instant().plus(Duration.ofMinutes(60));
        QualityTime next = sessions.saveAndFlush(new QualityTime(father, itamar, soon, soon.plus(Duration.ofMinutes(60))));
        callback("t-1h", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה הזמן שלך ושל איתמר");
        JsonNode hour = body(fake.platformSends().get(1));
        assertThat(hour.path("template").path("name").asText()).isEqualTo(WhatsAppTemplateCatalog.SESSION_HOUR_BEFORE_HE);
        assertThat(hour.path("template").path("buttons").get(0).path("payload").asText()).isEqualTo("dc:ideas:" + next.getId());
        assertThat(hour.path("fallbackTemplates").get(0).path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(Instant.parse(hour.path("notAfter").asText())).isEqualTo(soon);

        Instant at15 = Instant.parse("2026-11-03T13:00:00Z");
        Instant at17 = Instant.parse("2026-11-03T15:00:00Z");
        sessions.saveAndFlush(new QualityTime(father, itamar, at15, at15.plus(Duration.ofMinutes(45))));
        sessions.saveAndFlush(new QualityTime(father, itamar, at17, at17.plus(Duration.ofMinutes(45))));
        callback("t-morning", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        JsonNode morning = body(fake.platformSends().get(2));
        assertThat(morning.path("template").path("name").asText()).isEqualTo(WhatsAppTemplateCatalog.SESSION_MORNING_HE);
        assertThat(Instant.parse(morning.path("notAfter").asText())).as("the day's last session's start").isEqualTo(at17);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM notAfter is never closer than 15 minutes: a message sent now is never refused for it")
    void notAfterHasAFloor() {
        Instant soon = clock.instant().plus(Duration.ofMinutes(5));
        sessions.saveAndFlush(new QualityTime(father, itamar, soon, soon.plus(Duration.ofMinutes(60))));
        assertThat(platform.notAfter(father, "SESSION_REMINDER_1H")).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("PLATFORM HELD / QUEUED (then looked up) / FAILED / UNKNOWN map onto the row (spec §7.4)")
    void platformAnswersMapOntoTheRow() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        fake.onPlatformSend(c -> answer(200, UUID.randomUUID().toString(), "HELD", null, "ON_ANOTHER_PRODUCT", 42L, null));
        JsonNode held = answer(callback("t-held", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(held.has("platformMessageId")).isTrue();
        assertThat(held.has("status")).isFalse();
        assertThat(row("t-held")).containsEntry("status", "HELD").containsEntry("gateway_hold_id", "held:42")
                .containsEntry("platform_status", "HELD");
        assertThat(row("t-held").get("platform_next_at")).as("looked up by key if no report comes").isNotNull();

        String queuedId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(202, queuedId, "QUEUED", null, null, null, null));
        callback("t-queued", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        assertThat(row("t-queued")).containsEntry("status", "SENDING").containsEntry("platform_status", "QUEUED");
        clock.advance(Duration.ofMinutes(6));
        fake.onPlatformLookup(c -> answer(200, queuedId, "ACCEPTED", WAMID, null, null, "TEMPLATE"));
        platform.reconcileDue();
        assertThat(fake.platformLookups()).extracting(m -> m.get("idempotencyKey")).contains("scheduled-response:t-queued");
        assertThat(row("t-queued")).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", WAMID)
                .containsEntry("delivery_mode", "TEMPLATE").containsEntry("platform_next_at", null);

        fake.onPlatformSend(c -> answer(200, UUID.randomUUID().toString(), "FAILED", null, "WINDOW_CLOSED", null, null));
        JsonNode failed = answer(callback("t-failed", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(failed.has("platformMessageId")).as("the platform recorded it (as failed) - nothing to report").isTrue();
        assertThat(failed.has("outcome")).isFalse();
        assertThat(row("t-failed")).containsEntry("status", "FAILED").containsEntry("failure_reason", "PLATFORM_WINDOW_CLOSED");

        fake.onPlatformSend(c -> answer(200, UUID.randomUUID().toString(), "UNKNOWN", null, "SEND_UNKNOWN_TIMEOUT", null, null));
        callback("t-unknown", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        assertThat(row("t-unknown")).containsEntry("status", "UNKNOWN")
                .containsEntry("failure_reason", "PLATFORM_SEND_UNKNOWN_TIMEOUT");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM, the request cannot be built (review N1): nothing posted, sent today's way with today's answer")
    void requestFailureSendsDirect() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        platform.setRequestFaultForTests(new IllegalStateException("broken"));
        try {
            JsonNode a = answer(callback("t-nobody", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
            assertThat(a.path("status").asText()).isEqualTo("DELIVERED");
            assertThat(fake.platformSends()).isEmpty();
            assertThat(fake.metaSends()).hasSize(1);
            assertThat(row("t-nobody")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", null)
                    .containsEntry("platform_request", null);
            assertThat(logged("messaging.scheduled.request_failed")).isTrue();
        } finally {
            platform.setRequestFaultForTests(null);
        }
    }

    @Test
    @DisplayName("PLATFORM (review N2): the platform's callback retry while the first /send is still in flight answers "
            + "SENDING and sends nothing")
    void retryWhileTheFirstSendIsInFlight() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        String messageId = UUID.randomUUID().toString();
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fake.onPlatformSend(c -> {
            inFlight.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return accepted(messageId);
        });
        java.util.concurrent.ExecutorService first = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<MvcResult> firstCallback = first.submit(
                    () -> callback("t-race", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
            assertThat(inFlight.await(10, TimeUnit.SECONDS)).isTrue();

            JsonNode retry = answer(callback("t-race", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
            assertThat(retry.path("status").asText()).isEqualTo("SENDING");
            assertThat(retry.path("detail").asText()).isEqualTo("Delivery already in progress");
            assertThat(fake.platformSends()).hasSize(1);

            release.countDown();
            assertThat(answerText(firstCallback.get(15, TimeUnit.SECONDS)))
                    .isEqualTo("{\"platformMessageId\":\"" + messageId + "\"}");
        } finally {
            release.countDown();
            first.shutdownNow();
        }
        assertThat(fake.platformSends()).as("never a second request").hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        assertThat(row("t-race")).containsEntry("status", "ACCEPTED").containsEntry("platform_posts", 1);
    }

    @Test
    @DisplayName("PLATFORM 503 messaging.disabled (nothing created): sent once today's way - gate, Meta, today's D-039 answer")
    void disabledFallsBackDirect() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);   // the fake platform is inert by default, as production is today

        JsonNode a = answer(callback("t-off", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?"));

        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).as("the direct path keeps the gate").hasSize(1);
        assertThat(a.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(a.path("outcome").asText()).as("Dad Coach sent it, so it reports it, as today").isEqualTo("AS_IS");
        assertThat(row("t-off")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", "DIRECT")
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
        assertThat(platform.reconcileDue()).isZero();
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    @DisplayName("PLATFORM 422 (request wrong): FAILED PLATFORM_<error>_<reason>, never sent directly, answered FAILED as today")
    void refusalFailsWithoutDirect() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);
        fake.onPlatformSend(c -> new Reply(422, "{\"error\":\"messaging.template_unusable\",\"template\":\"x\",\"reason\":\"NOT_FOUND\"}"));

        JsonNode a = answer(callback("t-422", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));

        assertThat(fake.platformSends()).as("no re-POST of a wrong request").hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        assertThat(row("t-422")).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "PLATFORM_TEMPLATE_UNUSABLE_NOT_FOUND").containsEntry("platform_state", "REFUSED")
                .containsEntry("platform_request", null);
        assertThat(a.path("status").asText()).isEqualTo("FAILED");
        assertThat(a.path("outcome").asText()).as("nothing was created: the turn is reported failed").isEqualTo("FAILED");
        assertThat(a.path("reason").asText()).isEqualTo("PLATFORM_TEMPLATE_UNUSABLE_NOT_FOUND");
    }

    @Test
    @DisplayName("PLATFORM edge 502: identical re-POSTs, never direct, answer {}; a replayed trigger POSTs nothing; "
            + "the job re-POSTs, then the lookup adopts it")
    void ambiguousIsRepostedAndReconciled() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> EDGE_502);

        MvcResult r = callback("t-502", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");

        assertThat(answerText(r)).as("the platform may have it").isEqualTo("{}");
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(fake.metaSends()).isEmpty();
        assertThat(row("t-502")).containsEntry("status", "SENDING").containsEntry("platform_state", "AMBIGUOUS")
                .containsEntry("platform_posts", 2);

        JsonNode replay = answer(callback("t-502", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(replay.path("status").asText()).isEqualTo("SENDING");
        assertThat(fake.platformSends()).as("the trigger's key: never a second request").hasSize(2);

        clock.advance(Duration.ofMinutes(1));
        fake.onPlatformSend(c -> new Reply(500, "{}"));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.platformSends().get(2).body()).isEqualTo(fake.platformSends().get(0).body());

        clock.advance(Duration.ofMinutes(4));
        fake.onPlatformLookup(c -> accepted(messageId));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(row("t-502")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", "OWNED")
                .containsEntry("platform_message_id", messageId).containsEntry("provider_message_id", WAMID);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM never created (lookup 404) and still true: sent directly exactly once, reported as the turn's replacement")
    void notCreatedIsSentLateOnce() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);
        QualityTime session = endedSession();
        fake.onPlatformSend(c -> new Reply(504, "<html>Gateway Timeout</html>", "text/html", null));
        callback("t-late", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה?");
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        clock.advance(Duration.ofMinutes(4));
        assertThat(platform.reconcileDue()).isEqualTo(1);           // 404 messaging.message_not_found: never created

        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.metaSends()).hasSize(1);
        JsonNode sent = json.readTree(fake.metaSends().get(0).body());
        assertThat(sent.path("interactive").path("action").path("buttons").get(0).path("reply").path("id").asText())
                .isEqualTo("dc:done:" + session.getId());
        assertThat(row("t-late")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", "DIRECT")
                .containsEntry("platform_request", null);
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).hasSize(1);
        JsonNode rec = body(fake.recordedOutbound().get(0));
        assertThat(rec.path("correlationId").asText()).isEqualTo("callback:scheduled-transition:t-late");
        assertThat(rec.path("turnCorrelationId").asText()).isEqualTo("scheduled-transition:t-late");
        assertThat(rec.path("replacesDraft").asBoolean()).isTrue();
        assertThat(rec.path("kind").asText()).isEqualTo("SCHEDULED");

        clock.advance(Duration.ofMinutes(10));
        platform.reconcileDue();
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    @DisplayName("PLATFORM never created but past notAfter (the session already started): FAILED, never sent late")
    void notCreatedTooLateIsNeverSent() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        Instant start = clock.instant().plus(Duration.ofMinutes(20));
        sessions.saveAndFlush(new QualityTime(father, itamar, start, start.plus(Duration.ofMinutes(60))));
        fake.onPlatformSend(c -> EDGE_502);
        callback("t-old", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה הזמן שלך ושל איתמר");
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        clock.advance(Duration.ofMinutes(24));
        platform.reconcileDue();

        assertThat(fake.platformLookups()).hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        assertThat(row("t-old")).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "PLATFORM_NOT_CREATED_TOO_LATE").containsEntry("platform_state", "GAVE_UP")
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
    }

    @Test
    @DisplayName("PLATFORM lookup never answering: UNKNOWN after an hour, never sent")
    void unansweredLookupGivesUpUnknown() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        fake.onPlatformSend(c -> EDGE_502);
        fake.onPlatformLookup(c -> new Reply(503, "<html>down</html>", "text/html", null));
        callback("t-gone", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        for (int i = 0; i < 14; i++) {
            clock.advance(Duration.ofMinutes(5));
            platform.reconcileDue();
        }
        assertThat(fake.metaSends()).isEmpty();
        assertThat(row("t-gone")).containsEntry("status", "UNKNOWN")
                .containsEntry("failure_reason", "PLATFORM_RECONCILE_GAVE_UP").containsEntry("platform_state", "GAVE_UP");
    }

    @Test
    @DisplayName("D-047: a hanging platform never holds the job - lookups time out (5 s, here 300 ms), a pass stops at its "
            + "budget (15 s, here 1 s) and the rows left are taken by the next passes; nothing is sent")
    void aHangingPlatformNeverHoldsTheJob() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        fake.onPlatformSend(c -> EDGE_502);
        int rows = 8;
        for (int i = 0; i < rows; i++) {
            callback("t-hang-" + i, "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).as("the job's re-POSTs answer at once").isEqualTo(rows);
        assertThat(fake.platformSends()).hasSize(3 * rows);

        messaging.setLookupReadTimeoutMs(300);
        messaging.setReconcilePassBudget(Duration.ofSeconds(1));
        CountDownLatch hang = new CountDownLatch(1);
        fake.onPlatformLookup(c -> {
            try {
                hang.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Reply(404, "{\"error\":\"messaging.message_not_found\"}");
        });
        try {
            clock.advance(Duration.ofMinutes(4));
            long started = System.nanoTime();
            int first = platform.reconcileDue();
            long tookMs = (System.nanoTime() - started) / 1_000_000;
            assertThat(first).as("the pass stopped at its budget").isBetween(1, rows - 1);
            assertThat(tookMs).as("budget + one lookup timeout, not a batch of timeouts").isLessThan(2_500);
            assertThat(logged("messaging.scheduled.reconcile_budget_spent")).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduled_response_delivery WHERE platform_next_at <= ?",
                    Integer.class, java.sql.Timestamp.from(clock.instant()))).as("the rows left are still due")
                    .isEqualTo(rows - first);

            int total = first;
            for (int pass = 0; pass < 10 && total < rows; pass++) {
                long passStarted = System.nanoTime();
                total += platform.reconcileDue();
                assertThat((System.nanoTime() - passStarted) / 1_000_000).isLessThan(2_500);
            }
            assertThat(total).as("every row looked up by the following passes").isEqualTo(rows);
            assertThat(fake.platformLookups()).hasSize(rows);
        } finally {
            hang.countDown();
        }
        assertThat(fake.metaSends()).as("a lookup that timed out proves nothing: never sent").isEmpty();
        for (int i = 0; i < rows; i++) {
            assertThat(row("t-hang-" + i)).containsEntry("status", "SENDING").containsEntry("platform_state", "AMBIGUOUS");
        }
    }

    @Test
    @DisplayName("PLATFORM 409 after an ambiguous attempt: adopted, never FAILED; 409 on the first attempt: PLATFORM_KEY_CONFLICT")
    void conflicts() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        String messageId = UUID.randomUUID().toString();
        List<Reply> replies = new ArrayList<>(List.of(EDGE_502, new Reply(409, "{\"error\":\"messaging.idempotency_conflict\","
                + "\"messageId\":\"" + messageId + "\",\"status\":\"ACCEPTED\",\"providerMessageId\":\"" + WAMID
                + "\",\"replayed\":true}")));
        fake.onPlatformSend(c -> replies.remove(0));
        JsonNode adopted = answer(callback("t-409", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(adopted.path("platformMessageId").asText()).isEqualTo(messageId);
        assertThat(row("t-409")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", "OWNED");
        assertThat(event("messaging.platform_conflict_adopted").getLevel().toString()).isEqualTo("WARN");

        fake.onPlatformSend(c -> new Reply(409, "{\"error\":\"messaging.idempotency_conflict\",\"messageId\":\"x\"}"));
        callback("t-409b", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        assertThat(row("t-409b")).containsEntry("status", "FAILED").containsEntry("failure_reason", "PLATFORM_KEY_CONFLICT");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("Delivery reports (kind SCHEDULED, by key or by messageId) move the row forward only; a DIRECT row is never moved")
    void deliveryReports() throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        messaging.setDeliveryStatusEnabled(true);
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(202, messageId, "QUEUED", null, null, null, null));
        callback("t-rep", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");

        JsonNode delivered = json.readTree(report("scheduled-response:t-rep", messageId, "SCHEDULED", "DELIVERED", WAMID)
                .getResponse().getContentAsString());
        assertThat(delivered.path("known").asBoolean()).isTrue();
        assertThat(delivered.path("applied").asBoolean()).isTrue();
        assertThat(row("t-rep")).containsEntry("status", "DELIVERED").containsEntry("provider_message_id", WAMID)
                .containsEntry("platform_next_at", null);

        JsonNode stale = json.readTree(report("scheduled-response:t-rep", messageId, "SCHEDULED", "ACCEPTED", WAMID)
                .getResponse().getContentAsString());
        assertThat(stale.path("applied").asBoolean()).isFalse();
        JsonNode read = json.readTree(report(null, messageId, null, "READ", WAMID).getResponse().getContentAsString());
        assertThat(read.path("known").asBoolean()).as("no kind: matched by the platform's message id").isTrue();
        assertThat(row("t-rep")).containsEntry("status", "READ");

        messaging.setScheduled(Mode.DIRECT);
        callback("t-dir", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        report("scheduled-response:t-dir", UUID.randomUUID().toString(), "SCHEDULED", "FAILED", null);
        assertThat(row("t-dir")).as("a row Dad Coach sent itself").containsEntry("status", "ACCEPTED");
    }
}
