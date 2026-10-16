package com.dadcoach.integration.platform.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.template.TemplateText;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.channel.DeliveryStatusController;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
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
 * D-046 (Unified Workflow Phase 6.3-DC, DC2) / D-053 (6.4): the platform's scheduled-response callbacks sent through the
 * platform's Send API - the only sender - against the real rows, the real callback endpoint and a fake platform
 * (FakeServers). No real WhatsApp; the test base proves fake Meta never receives a send. Every test also counts the
 * POSTs that could have reached him.
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
    void tearDown() {
        platform.setRequestFaultForTests(null);
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

    // ------------------------------------------------------------------------------------------------- PLATFORM

    @Test
    @DisplayName("ACCEPTED: bytes committed before the POST; the session's message type + values + payloads and the one line "
            + "for the general template (never a Meta name), window hint, notAfter, REPLACES_DRAFT; the answer is {platformMessageId}")
    void platformAcceptedFollowUp() throws Exception {
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
        assertThat(q.has("template")).as("D-053: never a Meta template name").isFalse();
        assertThat(q.has("fallbackTemplates")).isFalse();
        assertThat(q.toString()).doesNotContain("expectedBody").doesNotContain(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE);
        JsonNode own = q.path("templateData");
        assertThat(own.path("variant").asText()).isEqualTo("session_follow_up_he");
        assertThat(own.path("values")).hasSize(1);
        assertThat(own.path("values").get(0).asText()).isEqualTo("איתמר");
        assertThat(own.path("payloads")).hasSize(2);
        assertThat(own.path("payloads").get(0).asText()).isEqualTo("dc:done:" + session.getId());
        assertThat(own.path("payloads").get(1).asText()).isEqualTo("dc:missed:" + session.getId());
        assertThat(own.path("named").path("text").asText()).isEqualTo(TemplateText.asTemplateParameter(text));
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
        assertThat(q.has("dryRun")).as("D-053: no SHADOW, no dry run").isFalse();
    }

    @Test
    @DisplayName("Message types and notAfter per message: daily check (the default type, end of his day), hour-before "
            + "(its type, the session's start), morning (the day's last session's start)")
    void platformTemplatesAndNotAfterPerMessage() throws Exception {
        fake.onPlatformSend(c -> answer(202, UUID.randomUUID().toString(), "QUEUED", null, null, null, null));

        callback("t-daily", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!\nאיך הולך השבוע?");
        JsonNode daily = body(fake.platformSends().get(0));
        assertThat(daily.path("content").path("type").asText()).isEqualTo("text");
        assertThat(daily.path("templateData").path("variant").asText()).isEmpty();
        assertThat(daily.path("templateData").path("named").path("text").asText()).isEqualTo("בוקר טוב! איך הולך השבוע?");
        assertThat(daily.path("templateData").has("values")).isFalse();
        assertThat(daily.has("template")).isFalse();
        assertThat(daily.path("notAfter").asText()).as("midnight in Israel").isEqualTo("2026-11-03T22:00:00Z");
        assertThat(row("t-daily")).as("QUEUED leaves it SENDING (spec 7.4) and keeps a lookup")
                .containsEntry("status", "SENDING").containsEntry("platform_status", "QUEUED")
                .containsEntry("platform_state", "OWNED");
        assertThat(row("t-daily").get("platform_next_at")).isNotNull();

        Instant soon = clock.instant().plus(Duration.ofMinutes(60));
        QualityTime next = sessions.saveAndFlush(new QualityTime(father, itamar, soon, soon.plus(Duration.ofMinutes(60))));
        callback("t-1h", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה הזמן שלך ושל איתמר");
        JsonNode hour = body(fake.platformSends().get(1));
        assertThat(hour.path("templateData").path("variant").asText()).isEqualTo("session_hour_before_he");
        assertThat(hour.path("templateData").path("payloads").get(0).asText()).isEqualTo("dc:ideas:" + next.getId());
        assertThat(hour.path("templateData").path("named").path("text").asText()).startsWith("עוד שעה הזמן שלך ושל איתמר")
                .doesNotContain("\n");
        assertThat(Instant.parse(hour.path("notAfter").asText())).isEqualTo(soon);

        Instant at15 = Instant.parse("2026-11-03T13:00:00Z");
        Instant at17 = Instant.parse("2026-11-03T15:00:00Z");
        sessions.saveAndFlush(new QualityTime(father, itamar, at15, at15.plus(Duration.ofMinutes(45))));
        sessions.saveAndFlush(new QualityTime(father, itamar, at17, at17.plus(Duration.ofMinutes(45))));
        callback("t-morning", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        JsonNode morning = body(fake.platformSends().get(2));
        assertThat(morning.path("templateData").path("variant").asText()).isEqualTo("session_morning_he");
        assertThat(Instant.parse(morning.path("notAfter").asText())).as("the day's last session's start").isEqualTo(at17);
        assertThat(fake.metaSends()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- D-067

    @Autowired com.dadcoach.auth.LoginLinkService links;

    QualityTime sessionToday() {
        Instant at15 = Instant.parse("2026-11-03T13:00:00Z");   // 15:00 in Israel, today
        return sessions.saveAndFlush(new QualityTime(father, itamar, at15, at15.plus(Duration.ofMinutes(45))));
    }

    List<Map<String, Object>> morningLinks() {
        return jdbc.queryForList("SELECT id, token_hash, created_at, expires_at, revoked_at, revoked_reason, delivery_status "
                + "FROM login_link WHERE father_id = ? ORDER BY created_at", father.getId());
    }

    static String token(JsonNode request) {
        return request.path("templateData").path("named").path("link").asText();
    }

    @Test
    @DisplayName("D-067: the morning goes as one link_button - the text exactly as without it, his dashboard label, no "
            + "footer, sensitive; the token is minted for the send, only its hash is kept, never in the stored bytes, the "
            + "row or the timeline; tapping it signs him in")
    void morningCarriesTheDashboardButton() throws Exception {
        sessionToday();
        String messageId = UUID.randomUUID().toString();
        AtomicReference<String> storedAtPost = new AtomicReference<>();
        fake.onPlatformSend(c -> {
            storedAtPost.set(jdbc.queryForObject("SELECT platform_request FROM scheduled_response_delivery WHERE trigger_id = ?",
                    String.class, "t-dash"));
            return accepted(messageId);
        });

        callback("t-dash", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");

        assertThat(fake.platformSends()).hasSize(1);
        String posted = fake.platformSends().get(0).body();
        JsonNode q = json.readTree(posted);
        String text = IDENTITY + com.dadcoach.replies.CoachReplies.morning(List.of("15:00"), "איתמר");
        String token = token(q);
        assertThat(token).isNotBlank();
        String url = q.path("content").path("link").path("url").asText();
        assertThat(q.path("content").path("type").asText()).isEqualTo("link_button");
        assertThat(q.path("content").path("text").asText()).as("the morning's text, unchanged").isEqualTo(text);
        assertThat(q.path("content").path("link").path("title").asText()).isEqualTo("כניסה לדף שלי");
        assertThat(url).endsWith("/auth/consume#token=" + token);
        assertThat(q.path("content").has("footer")).isFalse();
        assertThat(q.path("fallbackText").asText()).isEqualTo(text + "\n" + url);
        assertThat(q.path("sensitive").asBoolean()).isTrue();
        assertThat(q.path("timeline").path("content").asText()).isEqualTo(text.substring(IDENTITY.length()));
        JsonNode data = q.path("templateData");
        assertThat(data.path("variant").asText()).isEqualTo("session_morning_he");
        assertThat(data.path("values").get(0).asText()).isEqualTo("15:00");
        assertThat(data.path("values").get(1).asText()).isEqualTo("איתמר");
        assertThat(data.path("named").path("text").asText()).isEqualTo(TemplateText.asTemplateParameter(text));

        // rule 5: the stored bytes are the posted ones but for the token
        assertThat(storedAtPost.get()).doesNotContain(token).contains(ScheduledPlatformSend.LINK_TOKEN_MARK);
        assertThat(ScheduledPlatformSend.withToken(storedAtPost.get(), token)).isEqualTo(posted);
        assertThat(jdbc.queryForObject("SELECT row_to_json(d)::text FROM scheduled_response_delivery d WHERE trigger_id = ?",
                String.class, "t-dash")).doesNotContain(token);
        assertThat(row("t-dash")).containsEntry("status", "ACCEPTED").containsEntry("platform_request", null);

        List<Map<String, Object>> rows = morningLinks();
        assertThat(rows).as("one link for the morning that went out").hasSize(1);
        assertThat(rows.get(0).get("token_hash")).isEqualTo(com.dadcoach.auth.TokenHashing.sha256Hex(token));
        assertThat(((java.sql.Timestamp) rows.get(0).get("expires_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link WHERE token_hash = ?", Integer.class, token)).isZero();
        assertThat(links.use(token)).as("the button signs him in").hasValueSatisfying(subject ->
                assertThat(subject.fatherId()).isEqualTo(father.getId()));
    }

    @Test
    @DisplayName("D-067: a morning that is not sent issues no link - no session (skipped), already reminded today, refused "
            + "by the platform (its link is ended); other messages never carry one")
    void noLinkWithoutAMorning() throws Exception {
        fake.onPlatformSend(c -> accepted(UUID.randomUUID().toString()));
        callback("t-none", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        assertThat(fake.platformSends()).as("no session today: nothing sent").isEmpty();
        assertThat(morningLinks()).isEmpty();

        sessionToday();
        callback("t-first", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        assertThat(morningLinks()).hasSize(1);
        callback("t-again", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        assertThat(row("t-again")).containsEntry("failure_reason", SessionTimerCard.ALREADY_REMINDED_TODAY);
        assertThat(morningLinks()).as("the second morning of the day is quiet: no link").hasSize(1);

        callback("t-daily", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!\nאיך הולך השבוע?");
        JsonNode daily = json.readTree(fake.platformSends().get(fake.platformSends().size() - 1).body());
        assertThat(daily.path("content").path("type").asText()).isEqualTo("text");
        assertThat(daily.has("sensitive")).isFalse();
        assertThat(morningLinks()).hasSize(1);

        jdbc.update("UPDATE quality_time SET morning_reminded_on = NULL WHERE father_id = ?", father.getId());
        fake.onPlatformSend(c -> new Reply(422, "{\"error\":\"messaging.template_unusable\",\"reason\":\"NOT_FOUND\"}"));
        callback("t-refused", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        assertThat(row("t-refused")).containsEntry("status", "FAILED");
        List<Map<String, Object>> rows = morningLinks();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get("revoked_at")).as("nothing was created: nobody holds it").isNotNull();
        assertThat(rows.get(1).get("revoked_reason")).isEqualTo("NOT_SENT");
        assertThat(rows.get(0).get("revoked_at")).isNull();
    }

    @Test
    @DisplayName("D-067: a morning nothing was created for keeps the mark, never the token, in its stored bytes; the job's "
            + "re-POST carries a newly minted link")
    void notCreatedMorningRePostsWithANewLink() throws Exception {
        sessionToday();
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        callback("t-dash-off", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב");
        assertThat(row("t-dash-off")).containsEntry("status", "SENDING").containsEntry("platform_state", "NOT_CREATED");
        String first = token(json.readTree(fake.platformSends().get(0).body()));
        String stored = (String) row("t-dash-off").get("platform_request");
        assertThat(stored).doesNotContain(first).contains(ScheduledPlatformSend.LINK_TOKEN_MARK);

        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> accepted(messageId));
        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).hasSize(2);
        String again = fake.platformSends().get(1).body();
        String second = token(json.readTree(again));
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(again).isEqualTo(ScheduledPlatformSend.withToken(stored, second));
        assertThat(row("t-dash-off")).containsEntry("status", "ACCEPTED").containsEntry("platform_request", null);
        assertThat(links.use(second)).isPresent();
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
    @DisplayName("The request cannot be built (a bug): nothing posted, FAILED REQUEST_BUILD_FAILED + ERROR, answered FAILED")
    void requestFailureFails() throws Exception {
        platformProperties.setDeliveryReports(true);
        platform.setRequestFaultForTests(new IllegalStateException("broken"));
        JsonNode a = answer(callback("t-nobody", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(a.path("status").asText()).isEqualTo("FAILED");
        assertThat(a.path("outcome").asText()).isEqualTo("FAILED");
        assertThat(a.path("reason").asText()).isEqualTo("REQUEST_BUILD_FAILED");
        assertThat(fake.platformSends()).isEmpty();
        assertThat(row("t-nobody")).containsEntry("status", "FAILED").containsEntry("failure_reason", "REQUEST_BUILD_FAILED")
                .containsEntry("platform_state", null).containsEntry("platform_request", null);
        assertThat(event("messaging.scheduled.request_failed").getLevel().toString()).isEqualTo("ERROR");
        assertThat(platform.reconcileDue()).isZero();
    }

    @Test
    @DisplayName("PLATFORM (review N2): the platform's callback retry while the first /send is still in flight answers "
            + "SENDING and sends nothing")
    void retryWhileTheFirstSendIsInFlight() throws Exception {
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
    @DisplayName("NOT_CREATED: 503 messaging.disabled - the row stays SENDING with its bytes, answered {} (on its way), "
            + "ERROR logged; the job re-POSTs the same bytes until accepted")
    void disabledIsPendingAndRePosted() throws Exception {
        platformProperties.setDeliveryReports(true);
        fake.onPlatformSend(c -> FakeServers.platformDisabled());

        MvcResult r = callback("t-off", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?");

        assertThat(answerText(r)).as("on its way: the platform will record it").isEqualTo("{}");
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(row("t-off")).containsEntry("status", "SENDING").containsEntry("platform_state", "NOT_CREATED")
                .containsEntry("platform_message_id", null);
        assertThat((String) row("t-off").get("platform_request")).isEqualTo(fake.platformSends().get(0).body());
        assertThat(event("messaging.platform_disabled").getLevel().toString()).isEqualTo("ERROR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduled_response_delivery WHERE status = 'FAILED'", Integer.class))
                .as("NOT_CREATED is pending, never failed or stuck").isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(row("t-off")).containsEntry("platform_state", "NOT_CREATED");

        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> accepted(messageId));
        clock.advance(Duration.ofMinutes(2));
        assertThat(platform.reconcileDue()).isEqualTo(1);
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.platformSends().get(2).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(row("t-off")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", "OWNED")
                .containsEntry("platform_message_id", messageId).containsEntry("provider_message_id", WAMID)
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.timelineCalls()).as("the platform records it; Dad Coach reports nothing").isEmpty();
    }

    @Test
    @DisplayName("NOT_CREATED: 401 (ERROR messaging.platform_auth_failed) and never written are pending too; past notAfter "
            + "FAILED PLATFORM_NOT_CREATED, never posted again")
    void notCreatedUntilNotAfter() throws Exception {
        Instant start = clock.instant().plus(Duration.ofMinutes(20));
        sessions.saveAndFlush(new QualityTime(father, itamar, start, start.plus(Duration.ofMinutes(60))));
        fake.onPlatformSend(c -> new Reply(401, "{\"error\":\"unauthorized\"}"));
        callback("t-auth", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה הזמן שלך ושל איתמר");
        assertThat(row("t-auth")).containsEntry("status", "SENDING").containsEntry("platform_state", "NOT_CREATED");
        assertThat(event("messaging.platform_auth_failed").getLevel().toString()).isEqualTo("ERROR");

        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(2);
        clock.advance(Duration.ofMinutes(25));                    // the session started: the reminder is no longer true
        platform.reconcileDue();
        assertThat(fake.platformSends()).as("never posted after notAfter").hasSize(2);
        assertThat(row("t-auth")).containsEntry("status", "FAILED").containsEntry("failure_reason", "PLATFORM_NOT_CREATED")
                .containsEntry("platform_state", "GAVE_UP").containsEntry("platform_request", null)
                .containsEntry("platform_next_at", null);
        clock.advance(Duration.ofMinutes(30));
        assertThat(platform.reconcileDue()).isZero();
    }

    @Test
    @DisplayName("PLATFORM 422 (request wrong): FAILED PLATFORM_<error>_<reason>, never sent directly, answered FAILED as today")
    void refusalFailsWithoutDirect() throws Exception {
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
    @DisplayName("Ambiguous, then never created (lookup 404) and still true: the stored bytes are POSTed again - one message, "
            + "the platform records it")
    void notFoundIsRePostedOnce() throws Exception {
        platformProperties.setDeliveryReports(true);
        QualityTime session = endedSession();
        fake.onPlatformSend(c -> new Reply(504, "<html>Gateway Timeout</html>", "text/html", null));
        callback("t-late", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה?");
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(3);
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> accepted(messageId));
        clock.advance(Duration.ofMinutes(4));
        assertThat(platform.reconcileDue()).isEqualTo(1);           // 404 messaging.message_not_found: never created

        assertThat(fake.platformSends()).hasSize(4);
        JsonNode sent = body(fake.platformSends().get(3));
        assertThat(fake.platformSends().get(3).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(sent.path("content").path("buttons").get(0).path("id").asText()).isEqualTo("dc:done:" + session.getId());
        assertThat(sent.path("timeline").path("correlationId").asText()).isEqualTo("callback:scheduled-transition:t-late");
        assertThat(row("t-late")).containsEntry("status", "ACCEPTED").containsEntry("platform_state", "OWNED")
                .containsEntry("platform_request", null);
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).as("the platform records it, not Dad Coach").isEmpty();

        clock.advance(Duration.ofMinutes(10));
        platform.reconcileDue();
        assertThat(fake.platformSends()).hasSize(4);
    }

    @Test
    @DisplayName("Never created but past notAfter (the session already started): FAILED PLATFORM_NOT_CREATED, never posted again")
    void notCreatedTooLateIsNeverSent() throws Exception {
        Instant start = clock.instant().plus(Duration.ofMinutes(20));
        sessions.saveAndFlush(new QualityTime(father, itamar, start, start.plus(Duration.ofMinutes(60))));
        fake.onPlatformSend(c -> EDGE_502);
        callback("t-old", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה הזמן שלך ושל איתמר");
        clock.advance(Duration.ofMinutes(1));
        platform.reconcileDue();
        clock.advance(Duration.ofMinutes(24));
        platform.reconcileDue();

        assertThat(fake.platformLookups()).hasSize(1);
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(row("t-old")).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "PLATFORM_NOT_CREATED").containsEntry("platform_state", "GAVE_UP")
                .containsEntry("platform_request", null).containsEntry("platform_next_at", null);
    }

    @Test
    @DisplayName("PLATFORM lookup never answering: UNKNOWN after an hour, never sent")
    void unansweredLookupGivesUpUnknown() throws Exception {
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
    @DisplayName("D-053 (R5): a trigger id the platform's key shape refuses travels as h:<sha256>; a report by that key finds the row")
    void oddKeysAreHashed() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(202, messageId, "QUEUED", null, null, null, null));
        callback("t odd#1", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        String key = body(fake.platformSends().get(0)).path("idempotencyKey").asText();
        assertThat(key).matches("h:[0-9a-f]{32}").isEqualTo(PlatformSendFlow.platformKey("scheduled-response:t odd#1"));
        JsonNode r = json.readTree(report(key, UUID.randomUUID().toString(), "SCHEDULED", "DELIVERED", WAMID)
                .getResponse().getContentAsString());
        assertThat(r.path("known").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("PLATFORM 409 after an ambiguous attempt: adopted, never FAILED; 409 on the first attempt: PLATFORM_KEY_CONFLICT")
    void conflicts() throws Exception {
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
    @DisplayName("Delivery reports (kind SCHEDULED, by key or by messageId) move the row forward only; a legacy DIRECT row is never moved")
    void deliveryReports() throws Exception {
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

        fake.onPlatformSend(fake::accept);
        callback("t-dir", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!");
        jdbc.update("UPDATE scheduled_response_delivery SET platform_state = 'DIRECT', platform_message_id = NULL "
                + "WHERE trigger_id = 't-dir'");   // a row Dad Coach sent itself before D-053
        report("scheduled-response:t-dir", UUID.randomUUID().toString(), "SCHEDULED", "FAILED", null);
        assertThat(row("t-dir")).as("a row Dad Coach sent itself").containsEntry("status", "ACCEPTED");

        // D-053: Meta's own receipts on the webhook are the platform's - one still forwarded changes nothing here
        String receipt = com.dadcoach.support.Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"statuses\":[{\"id\":\""
                + WAMID + "\",\"status\":\"failed\",\"timestamp\":\"1793700100\",\"recipient_id\":\"19995550950\"}]}");
        byte[] raw = receipt.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", com.dadcoach.support.Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(row("t-rep")).containsEntry("status", "READ");
    }

}
