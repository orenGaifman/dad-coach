package com.dadcoach.whatsapp.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.channel.DeliveryStatusController;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-048 (Unified Workflow Phase 6.3-DC, step DC4) / D-053 (6.4): the lines answering a father's WhatsApp message - the
 * coach's reply, Dad Coach's fixed lines (incl. the "something went wrong" line and the deletion answer), a session
 * button's answer, an answer from data - through the platform's Send API, the only sender, against a fake platform
 * (FakeServers). No real WhatsApp; the test base proves fake Meta never receives a send. The request and mapping per
 * kind, the latency of the turn's thread, ambiguity never sending a line twice, and the answer table for lines without a
 * ledger (nothing created: one re-POST after ≤ 2 s, then nothing sent and the message unanswered).
 */
class ReplyPlatformSendTest extends AbstractIntegrationTest {

    /** A number per test: the inbound rate limit (20 a minute per number) runs on the pinned clock. */
    static final AtomicInteger NUMBERS = new AtomicInteger();
    String PHONE;
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final List<String> KINDS = List.of(MessagingSettings.REPLY, MessagingSettings.FIXED_LINE,
            MessagingSettings.BUTTON_REPLY, MessagingSettings.READY_ANSWER);
    static final Reply EDGE_502 = new Reply(502, "<html><body>Bad Gateway</body></html>", "text/html", null);
    /** The inbound messages' Meta timestamp (Webhooks): the window hint. */
    static final Instant INBOUND_AT = Instant.ofEpochSecond(1793700000L);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessagingSettings messaging;
    @Autowired WorkflowPlatformProperties properties;
    @Autowired TimelineReports reports;
    @Autowired PlatformSendFlow flow;
    @Autowired QualityTimeService qualityTime;

    Father father;
    Child itamar;
    int wamid;

    @BeforeEach
    void setUp() {
        PHONE = String.format("+1999557%04d", NUMBERS.incrementAndGet());
        reports.setRetryDelay(Duration.ofMillis(10));
        father = data.father(PHONE, "אורן", com.dadcoach.father.FatherStatus.ACTIVE);
        itamar = data.child(father, "איתמר", 6);
        data.endpoint(father, true);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        settle();
        reports.setRetryDelay(Duration.ofSeconds(1));
    }

    // ------------------------------------------------------------------------------------------------- helpers

    void settle() throws InterruptedException {
        assertThat(reports.awaitIdle(Duration.ofSeconds(10))).isTrue();
    }

    void turn(String... replies) {
        AtomicInteger n = new AtomicInteger();
        fake.onTurn(c -> reply(replies[Math.min(n.getAndIncrement(), replies.length - 1)]));
    }

    Reply reply(String content) {
        try {
            return Reply.json(json.writeValueAsString(Map.of("instanceId", "11111111-1111-1111-1111-111111111111",
                    "currentStateKey", "ACTIVE_COACHING", "responseContent", content, "responseType", "text",
                    "metadata", Map.of("responseOutcome", "GENERATED"), "isDuplicate", false)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String says(String text) throws Exception {
        String id = "wamid.rp" + (++wamid);
        webhook(Webhooks.text(PHONE, id, text));
        settle();
        return id;
    }

    void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andExpect(status().isOk());
    }

    String tap(String id, String buttonId, String title) {
        return Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"from\":\"" + PHONE.substring(1)
                + "\",\"id\":\"" + id + "\",\"timestamp\":\"1793700000\",\"type\":\"interactive\",\"interactive\":"
                + "{\"type\":\"button_reply\",\"button_reply\":{\"id\":\"" + buttonId + "\",\"title\":\"" + title + "\"}}}]}");
    }

    JsonNode body(FakeServers.Call call) throws Exception {
        return json.readTree(call.body());
    }

    static Reply platform(String status) {
        return platform(status, null);
    }

    static Reply platform(String status, String reason) {
        return Reply.json("{\"messageId\":\"" + UUID.randomUUID() + "\",\"status\":\"" + status + "\""
                + ("ACCEPTED".equals(status) ? ",\"providerMessageId\":\"wamid.p" + UUID.randomUUID() + "\"" : "")
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
                + ",\"channelWindow\":\"SESSION\"}");
    }

    /** The calls of one kind of interest, in arrival order: Meta sends, timeline reports, the Send API. */
    List<String> order() {
        List<String> out = new ArrayList<>();
        for (FakeServers.Call c : fake.calls("/")) {
            if (c.path().equals("/api/v1/worker/messages/send")) {
                out.add("send");
            } else if (c.path().equals("/api/v1/worker/messages/inbound")) {
                out.add("inbound");
            } else if (c.path().equals("/api/v1/worker/messages/outbound") || c.path().equals("/api/v1/worker/messages/turn-outcome")) {
                out.add("report");
            } else if (c.path().endsWith("/messages") && !c.path().startsWith("/api/")) {
                out.add("meta");
            }
        }
        return out;
    }

    void readyQuestionSetUp() {
        Instant friday9 = LocalDate.of(2026, 11, 6).atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Jerusalem")).toInstant();
        qualityTime.scheduleQualityTime(father.getId(), itamar.getId(), friday9, Duration.ofMinutes(90));
        jdbc.update("INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status) "
                + "VALUES (?, '2026-11-01', 3, 0, 'WHITE', 'ACTIVE')", father.getId());
    }

    /** One message per kind, the same wamid every time (the dedup claims are cleared between runs). */
    void everyKind() throws Exception {
        // D-053: every kind through the platform
        jdbc.execute("TRUNCATE tool_idempotency");
        turn(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        webhook(Webhooks.text(PHONE, "wamid.k.reply", "טוב"));                           // REPLY
        webhook(Webhooks.image(PHONE, "wamid.k.pic", ""));                               // FIXED_LINE
        webhook(tap("wamid.k.tap", "dc:done:" + new UUID(7, 7), "היה מעולה"));            // BUTTON_REPLY
        webhook(Webhooks.text(PHONE, "wamid.k.ready", "מה יש לי השבוע?"));                // READY_ANSWER
        settle();
    }

    // ------------------------------------------------------------------------------------------------- PLATFORM

    @Test
    @DisplayName("PLATFORM REPLY as written: one /send (key reply:<wamid>:0, SERVICE, text, SESSION_ONLY, his message's "
            + "time as the hint, GATEWAY, ACCEPTED, TURN_AS_IS on the turn); no Meta, no gate, no timeline report")
    void platformReplyAsWritten() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        turn(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        String id = says("טוב");

        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).isEmpty();
        assertThat(fake.timelineCalls()).isEmpty();
        assertThat(fake.platformSends()).hasSize(1);
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("idempotencyKey").asText()).isEqualTo("reply:" + id + ":0");
        assertThat(r.path("kind").asText()).isEqualTo("REPLY");
        assertThat(r.path("messageClass").asText()).isEqualTo("SERVICE");
        assertThat(r.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(r.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(r.path("userId").asText()).isEqualTo("whatsapp:" + PHONE);
        assertThat(r.path("to").asText()).isEqualTo(PHONE);
        assertThat(r.path("personRef").asText()).isNotBlank();
        // D-050 (5.4 R-4): the father's timezone seeds a conversation the platform creates
        assertThat(r.path("timezone").asText()).isNotBlank();
        assertThat(java.time.ZoneId.of(r.path("timezone").asText())).isNotNull();
        assertThat(r.path("content").path("type").asText()).isEqualTo("text");
        assertThat(r.path("content").path("text").asText()).isEqualTo(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        assertThat(r.has("template")).isFalse();
        assertThat(r.path("windowPolicy").asText()).isEqualTo("SESSION_ONLY");
        assertThat(r.path("outsideWindowWithoutTemplate").asText()).isEqualTo("FAIL");
        Instant now = clock.instant();
        assertThat(r.path("windowHint").path("lastInboundAt").asText())
                .isEqualTo((INBOUND_AT.isAfter(now) ? now : INBOUND_AT).toString());
        assertThat(r.path("holdPolicy").asText()).isEqualTo("GATEWAY");
        assertThat(r.path("wait").asText()).isEqualTo("ACCEPTED");
        assertThat(r.path("dryRun").asBoolean()).isFalse();
        JsonNode t = r.path("timeline");
        assertThat(t.path("record").asBoolean()).isTrue();
        assertThat(t.path("mode").asText()).isEqualTo("TURN_AS_IS");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.has("correlationId")).isFalse();

        // answered: Meta's redelivery of the message is dropped, nothing is posted again
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        assertThat(fake.platformSends()).hasSize(1);
    }

    @Test
    @DisplayName("PLATFORM REPLY corrected by Dad Coach's guards: REPLACES_DRAFT <wamid>:delivered with the sent text, "
            + "no ':corrected' record, no report")
    void platformReplyCorrectedReplacesTheDraft() throws Exception {
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        turn(IDENTITY + "וואו, לא ידעתי על מטר ונעם 😊 רק רגע ונמשיך משם.");
        String id = says("מטר בת 8 נעם בת 5");   // D-039 off: today this path writes the legacy ':corrected' row

        JsonNode t = body(fake.platformSends().get(0)).path("timeline");
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("correlationId").asText()).isEqualTo(id + ":delivered");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.path("content").asText()).isEqualTo("וואו, לא ידעתי על מטר ונעם 🙂");
        assertThat(t.has("supersededTurns")).isFalse();
        assertThat(fake.timelineCalls()).isEmpty();
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM REPLY after the Hebrew rewrite: REPLACES_DRAFT superseding '<wamid>:he'; the not-Hebrew line is "
            + "FIXED_LINE (its own switch)")
    void platformRewriteAndNotHebrewLine() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        turn("Noted — is there something specific I can help you adjust?", IDENTITY + "בסדר גמור 🙂");
        String id = says("לא הכל טוב");
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("kind").asText()).isEqualTo("REPLY");
        assertThat(r.path("timeline").path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(r.path("timeline").path("supersededTurns").get(0).asText()).isEqualTo(id + ":he");
        assertThat(fake.timelineCalls()).isEmpty();

        fake.reset();
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        turn("Fine as is, no change needed.");
        String id2 = says("טוב");
        JsonNode line = body(fake.platformSends().get(0));
        assertThat(line.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(line.path("content").path("text").asText()).isEqualTo(InboundMessageHandler.NOT_HEBREW_FALLBACK);
        assertThat(line.path("timeline").path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(line.path("timeline").path("correlationId").asText()).isEqualTo(id2 + ":delivered");
        assertThat(line.path("timeline").path("supersededTurns").get(0).asText()).isEqualTo(id2 + ":he");
        assertThat(fake.timelineCalls()).isEmpty();
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM FIXED_LINE for a picture: his message reported first (synchronously), then the /send with "
            + "NEW_ROW <wamid>:reply; no outbound report")
    void platformFixedLineHisMessageFirst() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        webhook(Webhooks.image(PHONE, "wamid.pic9", ""));
        settle();

        assertThat(order()).containsExactly("inbound", "send");
        assertThat(body(fake.recordedInbound().get(0)).path("content").asText()).isEqualTo("[photo]");
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("idempotencyKey").asText()).isEqualTo("reply:wamid.pic9:0");
        assertThat(r.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(r.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(r.path("timeline").path("correlationId").asText()).isEqualTo("wamid.pic9:reply");
        assertThat(r.path("timeline").path("content").asText())
                .isEqualTo(r.path("content").path("text").asText().substring(IDENTITY.length()));
        assertThat(fake.recordedOutbound()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM FIXED_LINE to a stranger: not recorded on any timeline, no person, no report")
    void platformFixedLineToAStranger() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        webhook(Webhooks.image("+19995550999", "wamid.stranger9", ""));
        settle();
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("timeline").path("record").asBoolean()).isFalse();
        assertThat(r.path("timeline").has("mode")).isFalse();
        assertThat(r.has("personRef")).isFalse();
        assertThat(fake.timelineCalls()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM BUTTON_REPLY: his tap (button_reply) first, then the /send NEW_ROW <wamid>; neither the D-039 "
            + "report nor (D-039 off) the legacy record")
    void platformButtonReply() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        String buttonId = "dc:done:" + UUID.randomUUID();
        webhook(tap("wamid.tap9", buttonId, "היה מעולה"));
        settle();
        assertThat(order()).containsExactly("inbound", "send");
        JsonNode inbound = body(fake.recordedInbound().get(0));
        assertThat(inbound.path("messageType").asText()).isEqualTo("button_reply");
        assertThat(inbound.path("structured").path("buttonId").asText()).isEqualTo(buttonId);
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("kind").asText()).isEqualTo("BUTTON_REPLY");
        assertThat(r.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(r.path("timeline").path("correlationId").asText()).isEqualTo("wamid.tap9");

        fake.reset();
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        properties.setDeliveryReports(false);
        webhook(tap("wamid.tap10", buttonId, "היה מעולה"));
        settle();
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.timelineCalls()).isEmpty();   // no legacy recordSent: the platform records it
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM READY_ANSWER: his question first, then the answer NEW_ROW <wamid>; no turn, no report")
    void platformReadyAnswer() throws Exception {
        properties.setDeliveryReports(true);
        readyQuestionSetUp();
        fake.reset();
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        String id = says("מה יש לי השבוע?");
        assertThat(fake.turns()).isEmpty();
        assertThat(order()).containsExactly("inbound", "send");
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("kind").asText()).isEqualTo("READY_ANSWER");
        assertThat(r.path("content").path("text").asText()).startsWith(IDENTITY + "השבוע: שעה וחצי מתוך 3 שעות.");
        assertThat(r.path("timeline").path("correlationId").asText()).isEqualTo(id);
        assertThat(r.path("timeline").path("content").asText()).startsWith("השבוע: שעה וחצי מתוך 3 שעות.");
    }

    @Test
    @DisplayName("D-053: the 'something went wrong' line goes through the platform (FIXED_LINE, keys of its own); "
            + "the platform not taking it: one re-POST, then nothing sent")
    void platformDownLineThroughThePlatform() throws Exception {
        fake.onTurn(c -> new Reply(500, "{\"error\":\"down\"}"));
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(1);
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(r.path("windowPolicy").asText()).isEqualTo("SESSION_ONLY");
        assertThat(r.path("content").path("text").asText()).isEqualTo(InboundMessageHandler.PLATFORM_DOWN_REPLY);
        assertThat(r.path("idempotencyKey").asText()).as("its own keys: the redelivery's reply never replays it")
                .isEqualTo("reply:" + id + ":down:0");
        assertThat(r.path("timeline").path("record").asBoolean()).isFalse();

        // the redelivery (the platform was unavailable: unanswered) gets the coach's reply under the reply key
        fake.onTurn(c -> reply(IDENTITY + "בסדר גמור 🙂"));
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        assertThat(fake.sendRequests()).extracting(n -> n.path("idempotencyKey").asText())
                .containsExactly("reply:" + id + ":down:0", "reply:" + id + ":0");

        // the platform cannot take the line either: one re-POST (≤ 2 s), then nothing - WARN messaging.platform_not_created
        fake.reset();
        fake.onTurn(c -> new Reply(500, "{\"error\":\"down\"}"));
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        String id2 = says("טוב");
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(body(fake.platformSends().get(0)).path("idempotencyKey").asText()).isEqualTo("reply:" + id2 + ":down:0");
    }

    @Test
    @DisplayName("D-053 (R7): the platform cannot take the deletion answer (503 disabled, then 401) - exactly one identical "
            + "re-POST, nothing else sent, and the deletion happens all the same")
    void aDeletionWhoseAnswerFailsStillDeletes() throws Exception {
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        says("מחק את המידע שלי");
        assertThat(fake.platformSends()).as("one attempt + one re-POST").hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(body(fake.platformSends().get(0)).has("userId")).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father.getId()))
                .isEqualTo("DELETED");

        // another father, the platform refusing the key (401): the same
        fake.reset();
        Father other = data.father(String.format("+1999558%04d", NUMBERS.incrementAndGet()), "דן",
                com.dadcoach.father.FatherStatus.ACTIVE);
        fake.onPlatformSend(c -> new Reply(401, "{\"error\":\"unauthorized\"}"));
        webhook(Webhooks.text(other.getPhone(), "wamid.del-401", "DELETE MY DATA"));
        settle();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, other.getId()))
                .isEqualTo("DELETED");
    }

    @Test
    @DisplayName("D-053 (R7): the deletion answer goes through the platform BEFORE the deletion - FIXED_LINE, to only "
            + "(no userId / personRef / personName), sensitive, never recorded, wait ACCEPTED")
    void deletionAnswerBeforeTheDeletion() throws Exception {
        properties.setDeliveryReports(true);
        java.util.concurrent.atomic.AtomicReference<String> statusAtSend = new java.util.concurrent.atomic.AtomicReference<>();
        fake.onPlatformSend(c -> {
            statusAtSend.set(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father.getId()));
            return fake.accept(c);
        });
        says("מחק את המידע שלי");
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(statusAtSend.get()).as("sent before the deletion was requested").isNotEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father.getId()))
                .isEqualTo("DELETED");
        JsonNode r = body(fake.platformSends().get(0));
        assertThat(r.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(r.path("to").asText()).isEqualTo(PHONE);
        assertThat(r.has("userId")).isFalse();
        assertThat(r.has("personRef")).isFalse();
        assertThat(r.has("personName")).isFalse();
        assertThat(r.path("sensitive").asBoolean()).isTrue();
        assertThat(r.path("timeline").path("record").asBoolean()).isFalse();
        assertThat(r.path("wait").asText()).isEqualTo("ACCEPTED");
        assertThat(r.path("windowPolicy").asText()).isEqualTo("SESSION_ONLY");
        assertThat(r.path("content").path("text").asText()).contains("אני מוחק עכשיו את כל המידע שלך");
        assertThat(fake.timelineCalls()).as("never reported (D-039)").isEmpty();

        // a number with no account gets its answer the same way (nothing to delete)
        fake.reset();
        String stranger = "+19995579999";
        webhook(Webhooks.text(stranger, "wamid.del-stranger", "DELETE MY DATA"));
        settle();
        assertThat(fake.platformSends()).hasSize(1);
        JsonNode s2 = body(fake.platformSends().get(0));
        assertThat(s2.has("userId")).isFalse();
        assertThat(s2.path("content").path("text").asText()).contains("למספר הזה אין חשבון");
    }

    @Test
    @DisplayName("A long reply is cut to WhatsApp's 4096 exactly as DIRECT does (never refused for its length)")
    void longReplyIsCutAsToday() throws Exception {
        fake.onPlatformSend(c -> platform("ACCEPTED"));
        turn(IDENTITY + "א".repeat(5000));
        says("ספר לי");
        String text = body(fake.platformSends().get(0)).path("content").path("text").asText();
        assertThat(text).hasSize(4096).endsWith("...");
    }

    // ------------------------------------------------------------------------------------------------- the answer

    @Test
    @DisplayName("HELD / QUEUED / UNKNOWN: answered (the platform has it) - a redelivery is dropped, nothing re-posted")
    void heldQueuedUnknownAreAnswered() throws Exception {
        turn(IDENTITY + "בסדר גמור 🙂");
        for (String status : List.of("HELD", "QUEUED", "UNKNOWN")) {
            fake.reset();
            turn(IDENTITY + "בסדר גמור 🙂");
            fake.onPlatformSend(c -> Reply.json("{\"messageId\":\"" + UUID.randomUUID() + "\",\"status\":\"" + status
                    + "\",\"heldId\":7}"));
            String id = says("טוב");
            webhook(Webhooks.text(PHONE, id, "טוב"));
            settle();
            assertThat(fake.platformSends()).as(status).hasSize(1);
            assertThat(fake.metaSends()).as(status).isEmpty();
        }
    }

    /**
     * A fake platform with memory, as the real one: a key it has seen is replayed ({@code replayed:true}, same message
     * id, same state); a new key gets {@code answers}' next state (status, reason) - the last one repeats.
     */
    java.util.function.Function<FakeServers.Call, Reply> platformWithMemory(String[]... answers) {
        Map<String, String> seen = new java.util.concurrent.ConcurrentHashMap<>();
        AtomicInteger next = new AtomicInteger();
        return c -> {
            try {
                String key = json.readTree(c.body()).path("idempotencyKey").asText();
                String replay = seen.get(key);
                if (replay != null) {
                    return Reply.json(replay.replace("\"replayed\":false", "\"replayed\":true"));
                }
                String[] a = answers[Math.min(next.getAndIncrement(), answers.length - 1)];
                String body = "{\"messageId\":\"" + UUID.randomUUID() + "\",\"idempotencyKey\":\"" + key
                        + "\",\"replayed\":false,\"status\":\"" + a[0] + "\""
                        + ("ACCEPTED".equals(a[0]) ? ",\"providerMessageId\":\"wamid.p" + next.get() + "\"" : "")
                        + (a.length > 1 ? ",\"reason\":\"" + a[1] + "\"" : "") + "}";
                seen.put(key, body);
                return Reply.json(body);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    @DisplayName("FAILED with a final reason (Meta decided: 131026): unanswered - Meta's redelivery posts the same key and "
            + "bytes, the platform replays the FAILED; never resent, never sent directly")
    void failedIsUnansweredAndTheRedeliveryReplays() throws Exception {
        fake.onPlatformSend(platformWithMemory(new String[] {"FAILED", "META_131026"}));
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("FAILED with a resendable reason (SENDER_UNAVAILABLE): Meta's redelivery replays it, then sends the line "
            + "again ONCE as <key>~r1 with retryOf (its own timeline row); answered - a further redelivery is dropped")
    void resendableFailedIsResentOnceOnTheRedelivery() throws Exception {
        properties.setDeliveryReports(true);
        var memory = platformWithMemory(new String[] {"FAILED", "SENDER_UNAVAILABLE"}, new String[] {"ACCEPTED"});
        java.util.concurrent.atomic.AtomicReference<String> failedMessageId = new java.util.concurrent.atomic.AtomicReference<>();
        fake.onPlatformSend(c -> {
            Reply r = memory.apply(c);
            try {
                failedMessageId.compareAndSet(null, json.readTree(r.body()).path("messageId").asText());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return r;
        });
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(1);   // FAILED on the first processing: unanswered, nothing more

        webhook(Webhooks.text(PHONE, id, "טוב"));       // Meta's redelivery
        settle();
        assertThat(fake.platformSends()).hasSize(3);
        JsonNode first = body(fake.platformSends().get(0));
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        JsonNode resend = body(fake.platformSends().get(2));
        assertThat(resend.path("idempotencyKey").asText()).isEqualTo("reply:" + id + ":0~r1");
        assertThat(resend.path("retryOf").asText()).isEqualTo(failedMessageId.get());
        assertThat(resend.path("content")).isEqualTo(first.path("content"));
        assertThat(resend.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(resend.path("timeline").path("correlationId").asText()).isEqualTo(id + ":delivered~r1");
        assertThat(resend.path("timeline").path("content").asText()).isEqualTo("בסדר גמור 🙂");
        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.timelineCalls()).isEmpty();      // the platform records both messages

        webhook(Webhooks.text(PHONE, id, "טוב"));       // answered now: dropped
        settle();
        assertThat(fake.platformSends()).hasSize(3);
    }

    @Test
    @DisplayName("A resend that FAILS is final: later redeliveries replay the key and its ~r1 - never a ~r2")
    void aFailedResendIsFinal() throws Exception {
        fake.onPlatformSend(platformWithMemory(new String[] {"FAILED", "MESSAGING_DISABLED"}));
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        webhook(Webhooks.text(PHONE, id, "טוב"));
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        List<String> keys = new ArrayList<>();
        for (FakeServers.Call c : fake.platformSends()) {
            keys.add(body(c).path("idempotencyKey").asText());
        }
        String key = "reply:" + id + ":0";
        assertThat(keys).containsExactly(key, key, key + "~r1", key, key + "~r1");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("NOT_CREATED (503 messaging.disabled): one re-POST of the same bytes after ≤ 2 s, then nothing sent - "
            + "unanswered, so Meta's redelivery posts the same key (and gets through once the platform takes it)")
    void disabledIsRePostedOnceThenUnanswered() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(fake.turnOutcomes()).as("nothing reached him: nothing reported").isEmpty();
        assertThat(fake.recordedOutbound()).isEmpty();

        fake.onPlatformSend(fake::accept);
        webhook(Webhooks.text(PHONE, id, "טוב"));   // unanswered: the redelivery is processed again
        settle();
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.platformSends().get(2).body()).as("the same key, the same line")
                .isEqualTo(fake.platformSends().get(0).body());
        webhook(Webhooks.text(PHONE, id, "טוב"));   // answered now: dropped
        settle();
        assertThat(fake.platformSends()).hasSize(3);

        // a picture's fixed line: his message is recorded first, then the POSTs
        fake.reset();
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        webhook(Webhooks.image(PHONE, "wamid.pic503", ""));
        settle();
        assertThat(order()).containsExactly("inbound", "send", "send");
        assertThat(fake.recordedOutbound()).isEmpty();
    }

    @Test
    @DisplayName("NOT_CREATED (401 / never written-like): ERROR messaging.platform_auth_failed, one re-POST, unanswered")
    void authFailureIsNotCreated() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(ReplyPlatformSend.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            fake.onPlatformSend(c -> new Reply(401, "{\"error\":\"unauthorized\"}"));
            turn(IDENTITY + "בסדר גמור 🙂");
            String id = says("טוב");
            assertThat(fake.platformSends()).hasSize(2);
            assertThat(logs.list).anyMatch(e -> "messaging.platform_auth_failed".equals(e.getMessage())
                    && e.getLevel() == ch.qos.logback.classic.Level.ERROR);
            assertThat(logs.list).anyMatch(e -> "messaging.platform_not_created".equals(e.getMessage())
                    && e.getLevel() == ch.qos.logback.classic.Level.WARN);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency WHERE scope = 'WHATSAPP_INBOUND' "
                    + "AND idempotency_key = ? AND status = 'SUCCEEDED'", Integer.class, id)).as("unanswered").isZero();
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    @DisplayName("422 refused: nothing sent at all (never directly), the message unanswered")
    void refusedSendsNothing() throws Exception {
        fake.onPlatformSend(c -> new Reply(422, "{\"error\":\"messaging.param_invalid\",\"field\":\"content.text\"}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        // unanswered: the redelivery is processed again (and refused again - still nothing sent)
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- ambiguity

    @Test
    @DisplayName("An edge 502: one immediate identical re-POST, then answered as on its way - never Meta, never a third "
            + "POST, a redelivery dropped")
    void edge502NeverSendsTwice() throws Exception {
        fake.onPlatformSend(c -> EDGE_502);
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(fake.metaSends()).isEmpty();
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("Latency: a platform that does not answer holds the turn's thread for the reply timeout only (1 s here), "
            + "is never re-POSTed after the timeout and never sent directly")
    void slowPlatformIsBoundedAndNeverDoubled() throws Exception {
        messaging.setReplyReadTimeoutMs(1000);
        fake.onPlatformSend(c -> {
            sleep(4000);
            return platform("ACCEPTED");
        });
        turn(IDENTITY + "בסדר גמור 🙂");
        long started = System.nanoTime();
        webhook(Webhooks.text(PHONE, "wamid.slow1", "טוב"));
        long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(ms).isBetween(900L, 3000L);
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        // answered (it may have gone out): the redelivery is dropped, nothing re-posted
        webhook(Webhooks.text(PHONE, "wamid.slow1", "טוב"));
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("D-053 (R5): a message id the platform's key shape refuses is replaced by h:<sha256> - the same on the redelivery")
    void oddInboundIdsAreHashed() throws Exception {
        turn(IDENTITY + "בסדר גמור 🙂");
        String odd = "wamid with spaces#" + UUID.randomUUID();
        webhook(Webhooks.text(PHONE, odd, "טוב"));
        settle();
        String key = body(fake.platformSends().get(0)).path("idempotencyKey").asText();
        assertThat(key).matches("h:[0-9a-f]{32}").isEqualTo(PlatformSendFlow.platformKey("reply:" + odd + ":0"));
    }

    @Test
    @DisplayName("The reply timeout: 5 s by default, never below 1 s nor above the Send API's own")
    void replyTimeoutBounds() {
        assertThat(messaging.replyReadTimeoutMs()).isEqualTo(5000);
        messaging.setReplyReadTimeoutMs(10);
        assertThat(messaging.replyReadTimeoutMs()).isEqualTo(1000);
        messaging.setReplyReadTimeoutMs(60000);
        assertThat(messaging.replyReadTimeoutMs()).isEqualTo(12000);
    }

    @Test
    @DisplayName("A 409 carrying the existing message (an earlier processing of the same message used the key): adopted, "
            + "nothing sent")
    void conflictIsAdopted() throws Exception {
        fake.onPlatformSend(c -> new Reply(409, "{\"error\":\"messaging.idempotency_conflict\",\"messageId\":\""
                + UUID.randomUUID() + "\",\"status\":\"ACCEPTED\",\"providerMessageId\":\"wamid.first\",\"replayed\":true}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        webhook(Webhooks.text(PHONE, id, "טוב"));   // answered: dropped
        settle();
        assertThat(fake.platformSends()).hasSize(1);
    }

    @Test
    @DisplayName("A 429: re-POSTed once after its Retry-After (≤ 2 s), same bytes; only 429s (or Retry-After > 2 s) = "
            + "nothing created: unanswered, the redelivery posts the same key; never sent directly")
    void rateLimitedOnce() throws Exception {
        AtomicInteger n = new AtomicInteger();
        fake.onPlatformSend(c -> n.getAndIncrement() == 0
                ? new Reply(429, "{\"error\":\"messaging.rate_limited\"}") : platform("ACCEPTED"));
        turn(IDENTITY + "בסדר גמור 🙂");
        says("טוב");
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(fake.metaSends()).isEmpty();

        // only 429s: nothing was created (the platform checks the key before its rate limit) - unanswered, so Meta's
        // redelivery posts the same key again (and here gets it through); never sent directly
        fake.reset();
        AtomicInteger m = new AtomicInteger();
        fake.onPlatformSend(c -> m.getAndIncrement() < 2
                ? new Reply(429, "{\"error\":\"messaging.rate_limited\"}") : platform("ACCEPTED"));
        turn(IDENTITY + "בסדר גמור 🙂");
        String id = says("טוב");
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).isEmpty();
        webhook(Webhooks.text(PHONE, id, "טוב"));
        settle();
        assertThat(fake.platformSends()).hasSize(3);
        assertThat(fake.platformSends().get(2).body()).isEqualTo(fake.platformSends().get(0).body());
        webhook(Webhooks.text(PHONE, id, "טוב"));   // answered now: dropped
        settle();
        assertThat(fake.platformSends()).hasSize(3);

        // a Retry-After above 2 s: not waited for on the turn's thread - one POST, unanswered
        fake.reset();
        fake.onPlatformSend(c -> rateLimited(30));
        turn(IDENTITY + "בסדר גמור 🙂");
        String id2 = says("טוב");
        assertThat(fake.platformSends()).hasSize(1);
        webhook(Webhooks.text(PHONE, id2, "טוב"));
        settle();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).isEmpty();
    }

    /** A 429 with a Retry-After header. */
    static Reply rateLimited(int seconds) {
        return new Reply(429, "{\"error\":\"messaging.rate_limited\"}", "application/json", null).withRetryAfter(seconds);
    }

    @Test
    @DisplayName("A delivery report for a reply (no ledger) is answered known:false, changes nothing, and is logged at "
            + "INFO with its key, status and reason (a late FAILED after the 5 s wait is visible)")
    void reportsAreUnknownAndLogged() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(DeliveryStatusController.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            String messageId = UUID.randomUUID().toString();
            String report = json.writeValueAsString(Map.of("messageId", messageId, "idempotencyKey", "reply:wamid.x:0",
                    "kind", "REPLY", "status", "FAILED", "reason", "META_131026", "resendable", false));
            JsonNode a = json.readTree(mvc.perform(post(DeliveryStatusController.PATH).contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", CALLBACK_KEY).header("X-Idempotency-Key", "delivery:" + messageId + ":FAILED")
                    .content(report)).andReturn().getResponse().getContentAsString());
            assertThat(a.path("known").asBoolean()).isFalse();
            assertThat(a.path("applied").asBoolean()).isFalse();
            var unmatched = logs.list.stream().filter(e -> "messaging.delivery_status.unmatched".equals(e.getMessage()))
                    .toList();
            assertThat(unmatched).hasSize(1);
            assertThat(unmatched.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
            String pairs = String.valueOf(unmatched.get(0).getKeyValuePairs());
            assertThat(pairs).contains("reply:wamid.x:0").contains("FAILED").contains("META_131026").contains("REPLY");
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    @DisplayName("His message is marked recorded only when the synchronous report landed: when it failed (500) its "
            + "background retry records it; nothing is reported by Dad Coach for a line the platform did not create")
    void hisMessageIsReportedAgainWhenTheSynchronousAttemptFailed() throws Exception {
        properties.setDeliveryReports(true);
        fake.onPlatformSend(c -> FakeServers.platformDisabled());
        AtomicInteger inbound = new AtomicInteger();
        fake.onTimelineReports(c -> c.path().endsWith("/inbound") && inbound.getAndIncrement() == 0
                ? new Reply(500, "{\"error\":\"boom\"}") : Reply.json("{\"recorded\":true}"));
        webhook(Webhooks.image(PHONE, "wamid.pic500", ""));
        settle();
        // the failed synchronous attempt and its background retry (the platform records an inbound id once)
        assertThat(fake.recordedInbound()).hasSize(2);
        assertThat(fake.recordedInbound()).allMatch(c -> c.body().contains("wamid.pic500"));
        assertThat(fake.recordedOutbound()).isEmpty();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
