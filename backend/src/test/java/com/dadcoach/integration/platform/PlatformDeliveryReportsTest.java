package com.dadcoach.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.dadcoach.web.father.DashboardNotes;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-039 (Phase 3.4) / D-048 / D-053: what reaches the platform's conversation timeline for every message Dad Coach
 * answers or sends, against the fake platform (FakeServers). Since D-053 the platform's Send API is the only sender and
 * records each line itself from the request's {@code timeline} (TURN_AS_IS, REPLACES_DRAFT, NEW_ROW); Dad Coach reports
 * only what it alone knows: his message before a line with no turn ({@code /messages/inbound}), a turn nothing of
 * which was sent (DROPPED), the dashboard's notes, and the scheduled-callback answer. Path by path:
 * docs/architecture/PHASE3_DADCOACH_SPEC.md.
 */
class PlatformDeliveryReportsTest extends AbstractIntegrationTest {

    static final String PHONE = "+19995550900";
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WorkflowPlatformProperties properties;
    @Autowired TimelineReports reports;
    @Autowired QualityTimeService qualityTime;
    @Autowired QualityTimeRepository sessions;
    @Autowired DashboardNotes notes;
    @Autowired com.dadcoach.auth.LoginLinkService links;

    Father father;
    Child itamar;
    int wamid;

    @BeforeEach
    void switchOn() {
        properties.setDeliveryReports(true);
        reports.setRetryDelay(Duration.ofMillis(10));
        father = data.father(PHONE, "אורן", com.dadcoach.father.FatherStatus.ACTIVE);
        itamar = data.child(father, "איתמר", 6);
        data.endpoint(father, true);
    }

    @AfterEach
    void switchOff() {
        settle();
        properties.setDeliveryReports(false);
        reports.setRetryDelay(Duration.ofSeconds(1));
        reports.setTimeouts(Duration.ofSeconds(100), Duration.ofSeconds(10), Duration.ofSeconds(115));
    }

    // ------------------------------------------------------------------------------------------------- helpers

    void settle() {
        assertThat(reports.awaitIdle(Duration.ofSeconds(10))).isTrue();
    }

    /** The coach's turn answers these replies, one per /execute, the last repeated. */
    void turn(String... replies) {
        AtomicInteger n = new AtomicInteger();
        fake.onTurn(c -> reply(replies[Math.min(n.getAndIncrement(), replies.length - 1)]));
    }

    FakeServers.Reply reply(String content) {
        try {
            return FakeServers.Reply.json(json.writeValueAsString(Map.of("instanceId", "11111111-1111-1111-1111-111111111111",
                    "currentStateKey", "ACTIVE_COACHING", "responseContent", content, "responseType", "text",
                    "metadata", Map.of("responseOutcome", "GENERATED"), "isDuplicate", false)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String says(String text) throws Exception {
        String id = "wamid.r" + (++wamid);
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

    List<JsonNode> bodies(List<FakeServers.Call> calls) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (FakeServers.Call c : calls) {
            out.add(body(c));
        }
        return out;
    }

    /** The last /send request: what the platform sends and records. */
    JsonNode lastSend() {
        List<JsonNode> sends = fake.sendRequests();
        return sends.get(sends.size() - 1);
    }

    /** The order of the timeline-relevant calls: his message reported, a /send, a Dad Coach report. */
    List<String> order() {
        List<String> out = new ArrayList<>();
        for (FakeServers.Call c : fake.calls("/")) {
            switch (c.path()) {
                case "/api/v1/worker/messages/send" -> out.add("send");
                case "/api/v1/worker/messages/inbound" -> out.add("inbound");
                case "/api/v1/worker/messages/outbound", "/api/v1/worker/messages/turn-outcome" -> out.add("report");
                default -> { }
            }
        }
        return out;
    }

    MvcResult callback(String triggerId, String state, String content) throws Exception {
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w1", "userId",
                "whatsapp:" + PHONE, "channel", "whatsapp", "targetStateKey", state, "responseContent", content));
        MvcResult r = mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        settle();
        return r;
    }

    JsonNode answer(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------------------------------------- turn replies

    @Test
    @DisplayName("a reply sent as the platform wrote it: the /send records it TURN_AS_IS on the turn; Dad Coach reports nothing")
    void aReplySentAsWrittenIsAsIs() throws Exception {
        turn(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        String id = says("טוב");

        JsonNode sent = lastSend();
        assertThat(sent.path("kind").asText()).isEqualTo("REPLY");
        assertThat(sent.path("content").path("text").asText()).isEqualTo(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        assertThat(sent.path("timeline").path("record").asBoolean()).isTrue();
        assertThat(sent.path("timeline").path("mode").asText()).isEqualTo("TURN_AS_IS");
        assertThat(sent.path("timeline").path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(sent.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(sent.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(sent.path("userId").asText()).isEqualTo("whatsapp:" + PHONE);
        assertThat(sent.path("channelId").asText()).isEqualTo("whatsapp");
        assertThat(sent.path("timeline").has("supersededTurns")).isFalse();
        assertThat(fake.timelineCalls()).as("the platform records it").isEmpty();
        // the father's own turn carries no "internal"
        assertThat(body(fake.turns().get(0)).has("internal")).isFalse();
    }

    @Test
    @DisplayName("ClaimGuard changed the reply: the sent text replaces the draft (REPLACES_DRAFT <wamid>:delivered) - no ':corrected' row")
    void aClaimCorrectionReplacesTheDraft() throws Exception {
        turn(IDENTITY + "מעולה, קובע את זה - יום שישי 09:00-10:30 עם איתמר 💪\nאעדכן אותך בבוקר ושעה לפני כדי שתהיה מוכן.");
        String id = says("כן");

        JsonNode sent = lastSend();
        assertThat(sent.path("content").path("text").asText())
                .isEqualTo(IDENTITY + "רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם איתמר.\nלקבוע?");
        JsonNode t = sent.path("timeline");
        assertThat(t.path("correlationId").asText()).isEqualTo(id + ":delivered");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("content").asText()).isEqualTo("רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם איתמר.\nלקבוע?");
        assertThat(sent.path("kind").asText()).isEqualTo("REPLY");
        assertThat(sent.path("personRef").asText()).isNotBlank();
        assertThat(fake.timelineCalls()).isEmpty();
        assertThat(fake.sendRequests().toString()).doesNotContain(":corrected");
    }

    @Test
    @DisplayName("ReplyStyleGuard changed the reply ('רק רגע' removed): a replacement too")
    void aStyleCorrectionReplacesTheDraft() throws Exception {
        turn(IDENTITY + "וואו, לא ידעתי על מטר ונעם 😊 רק רגע ונמשיך משם.");
        String id = says("מטר בת 8 נעם בת 5");

        JsonNode t = lastSend().path("timeline");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("content").asText()).isEqualTo("וואו, לא ידעתי על מטר ונעם 🙂");
        assertThat(fake.turnOutcomes()).isEmpty();
    }

    @Test
    @DisplayName("the Hebrew rewrite runs as an internal turn; what was sent replaces the original and supersedes ':he'")
    void theHebrewRewriteIsInternalAndSuperseded() throws Exception {
        turn("Noted — is there something specific I can help you adjust?", IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        String id = says("לא הכל טוב");

        assertThat(fake.turns()).hasSize(2);
        assertThat(body(fake.turns().get(0)).has("internal")).isFalse();
        JsonNode rewrite = body(fake.turns().get(1));
        assertThat(rewrite.path("correlationId").asText()).isEqualTo(id + ":he");
        assertThat(rewrite.path("internal").asBoolean()).isTrue();

        assertThat(fake.sentMessages()).hasSize(1);
        JsonNode t = lastSend().path("timeline");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("content").asText()).isEqualTo("בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        assertThat(t.path("supersededTurns")).hasSize(1);
        assertThat(t.path("supersededTurns").get(0).asText()).isEqualTo(id + ":he");
        assertThat(fake.timelineCalls()).isEmpty();
    }

    @Test
    @DisplayName("still not Hebrew after the rewrite: the fixed line replaces the draft, ':he' superseded")
    void theNotHebrewLineReplacesTheDraft() throws Exception {
        turn("Fine as is, no change needed.");
        String id = says("טוב");

        JsonNode sent = lastSend();
        assertThat(sent.path("kind").asText()).isEqualTo("FIXED_LINE");
        JsonNode t = sent.path("timeline");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("content").asText()).isEqualTo("סליחה, התבלבלתי בניסוח.\nאפשר לכתוב לי שוב מה צריך?");
        assertThat(t.path("supersededTurns").get(0).asText()).isEqualTo(id + ":he");
    }

    @Test
    @DisplayName("'I sent you the button' only: the card is recorded by the platform (never the link), Dad Coach reports the turn DROPPED")
    void theSentLineIsDroppedAndTheCardRecorded() throws Exception {
        turn(IDENTITY + "שלחתי לך את הכפתור לדף שלך 🙂");
        String id = says("איפה הדף שלי?");

        assertThat(fake.sentMessages()).as("only the card").hasSize(1);
        JsonNode card = lastSend();
        assertThat(card.path("kind").asText()).isEqualTo("LOGIN_LINK");
        JsonNode t = card.path("timeline");
        assertThat(t.path("correlationId").asText()).startsWith("dashboard-link:");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(t.path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(t.path("content").asText()).isEqualTo("📊 *הדף שלך בדאד קואץ׳*\nהשבוע, הילדים וההתקדמות, הכול בדף אחד.");
        assertThat(t.toString()).doesNotContain("consume").doesNotContain("token").doesNotContain("https://");

        assertThat(fake.timelineCalls()).hasSize(1);
        JsonNode dropped = body(fake.turnOutcomes().get(0));
        assertThat(dropped.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(dropped.path("outcome").asText()).isEqualTo("DROPPED");
        assertThat(dropped.path("reason").asText()).isEqualTo("SENT_LINE_CARD");
    }

    @Test
    @DisplayName("DC-B4: a duplicate answer carrying the first attempt's reply goes out once, TURN_AS_IS on that turn")
    void aReplayedDuplicateIsAsIsOnItsTurn() throws Exception {
        fake.onTurn(c -> FakeServers.Reply.json(reply(IDENTITY + "בסדר גמור 🙂").body()
                .replace("\"isDuplicate\":false", "\"isDuplicate\":true")));
        String id = says("טוב");
        assertThat(fake.sentMessages()).hasSize(1);
        assertThat(lastSend().path("timeline").path("mode").asText()).isEqualTo("TURN_AS_IS");
        assertThat(lastSend().path("timeline").path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(fake.timelineCalls()).isEmpty();
    }

    @Test
    @DisplayName("a reply the platform refused is not reported: the redelivery answers it")
    void aRefusedReplyIsNotReported() throws Exception {
        fake.onPlatformSend(c -> new FakeServers.Reply(422, "{\"error\":\"messaging.param_invalid\",\"field\":\"content\"}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        says("טוב");
        assertThat(fake.timelineCalls()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- code-answered

    @Test
    @DisplayName("a ready question: his message (/messages/inbound) first, then the answer (NEW_ROW) - no turn")
    void aReadyAnswerIsHisMessageThenTheAnswer() throws Exception {
        Instant friday9 = LocalDate.of(2026, 11, 6).atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Jerusalem")).toInstant();
        qualityTime.scheduleQualityTime(father.getId(), itamar.getId(), friday9, Duration.ofMinutes(90));
        jdbc.update("INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status) "
                + "VALUES (?, '2026-11-01', 3, 0, 'WHITE', 'ACTIVE')", father.getId());
        fake.reset(); // the booking above armed timers; only this message's calls count
        String id = says("מה יש לי השבוע?");

        assertThat(fake.turns()).isEmpty();
        assertThat(order()).containsExactly("inbound", "send");
        JsonNode inbound = body(fake.recordedInbound().get(0));
        assertThat(inbound.path("correlationId").asText()).isEqualTo(id);
        assertThat(inbound.path("content").asText()).isEqualTo("מה יש לי השבוע?");
        assertThat(inbound.path("messageType").asText()).isEqualTo("text");
        assertThat(inbound.has("structured")).isFalse();
        assertThat(inbound.path("workflowKey").asText()).isEqualTo("dad-coach-3");

        JsonNode answer = lastSend();
        assertThat(answer.path("kind").asText()).isEqualTo("READY_ANSWER");
        assertThat(answer.path("timeline").path("correlationId").asText()).isEqualTo(id);
        assertThat(answer.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(answer.path("timeline").path("content").asText()).startsWith("השבוע: שעה וחצי מתוך 3 שעות.")
                .doesNotContain("דאד קואץ׳:");
        assertThat(answer.path("timeline").has("turnCorrelationId")).isFalse();
    }

    @Test
    @DisplayName("a tap with a fixed reply: the tap (button_reply + structured) then the reply (NEW_ROW)")
    void aTapIsRecordedAsHisTapThenTheReply() throws Exception {
        String buttonId = "dc:done:" + UUID.randomUUID();
        webhook(tap("wamid.tap1", buttonId, "היה מעולה"));
        settle();

        assertThat(fake.turns()).isEmpty();
        assertThat(order()).containsExactly("inbound", "send");
        JsonNode inbound = body(fake.recordedInbound().get(0));
        assertThat(inbound.path("content").asText()).isEqualTo("היה מעולה");
        assertThat(inbound.path("messageType").asText()).isEqualTo("button_reply");
        assertThat(inbound.path("structured").path("buttonId").asText()).isEqualTo(buttonId);
        assertThat(inbound.path("structured").path("title").asText()).isEqualTo("היה מעולה");
        JsonNode reply = lastSend();
        assertThat(reply.path("kind").asText()).isEqualTo("BUTTON_REPLY");
        assertThat(reply.path("timeline").path("correlationId").asText()).isEqualTo("wamid.tap1");
    }

    @Test
    @DisplayName("a picture without words: his message as [photo], then the fixed line")
    void aPictureIsAMarkerThenTheLine() throws Exception {
        webhook(Webhooks.image(PHONE, "wamid.pic1", ""));
        settle();
        assertThat(order()).containsExactly("inbound", "send");
        JsonNode inbound = body(fake.recordedInbound().get(0));
        assertThat(inbound.path("content").asText()).isEqualTo("[photo]");
        assertThat(inbound.path("messageType").asText()).isEqualTo("image");
        JsonNode line = lastSend();
        assertThat(line.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(line.path("timeline").path("correlationId").asText()).isEqualTo("wamid.pic1:reply");
        assertThat(line.path("timeline").path("content").asText())
                .isEqualTo(line.path("content").path("text").asText().substring(IDENTITY.length()));
    }

    @Test
    @DisplayName("a voice note not heard: [voice note] (audio), then the line asking again")
    void aVoiceNoteNotHeardIsReported() throws Exception {
        fake.onSpeechToText(c -> FakeServers.Reply.json("{\"language_code\":\"heb\",\"text\":\"  \"}"));
        webhook(Webhooks.audio(PHONE, "wamid.voice1"));
        settle();
        assertThat(order()).containsExactly("inbound", "send");
        assertThat(body(fake.recordedInbound().get(0)).path("content").asText()).isEqualTo("[voice note]");
        assertThat(body(fake.recordedInbound().get(0)).path("messageType").asText()).isEqualTo("audio");
        assertThat(lastSend().path("timeline").path("content").asText()).contains("לא שמעתי מילים");
    }

    @Test
    @DisplayName("a reaction, a typed deletion request and a stranger's picture: nothing is recorded")
    void nothingForAReactionADeletionOrAStranger() throws Exception {
        webhook(Webhooks.reaction(PHONE, "wamid.re1", "❤️"));
        webhook(Webhooks.image("+19995550999", "wamid.stranger1", ""));
        webhook(Webhooks.text(PHONE, "wamid.del1", "מחק את המידע שלי"));
        settle();
        assertThat(fake.sentMessages()).as("the stranger's line and the deletion confirmation").hasSize(2);
        assertThat(fake.sendRequests()).allMatch(r -> !r.path("timeline").path("record").asBoolean());
        assertThat(fake.timelineCalls()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- product sends

    @Test
    @DisplayName("a dashboard note: kind DASHBOARD_NOTE, no provider id, no delivery status (history only)")
    void aDashboardNoteHasNoDeliveryStatus() throws Exception {
        notes.childAdded(father, "נועה", 5);
        settle();
        assertThat(fake.recordedOutbound()).hasSize(1);
        JsonNode note = body(fake.recordedOutbound().get(0));
        assertThat(note.path("content").asText()).isEqualTo("📊 בדף שלך: הוספת את נועה, בגיל 5.");
        assertThat(note.path("kind").asText()).isEqualTo("DASHBOARD_NOTE");
        assertThat(note.has("providerMessageId")).isFalse();
        assertThat(note.has("deliveryStatus")).isFalse();
        assertThat(note.has("turnCorrelationId")).isFalse();
        assertThat(fake.platformSends()).isEmpty();
    }

    @Test
    @DisplayName("a card from the login page: recorded by the platform as a row of its own, not linked to a turn, never the link")
    void aCardFromTheLoginPageIsRecorded() throws Exception {
        links.requestLink(PHONE, null, "10.0.0.9");
        settle();
        assertThat(fake.recordedOutbound()).isEmpty();
        JsonNode t = lastSend().path("timeline");
        assertThat(lastSend().path("kind").asText()).isEqualTo("LOGIN_LINK");
        assertThat(t.path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(t.has("turnCorrelationId")).isFalse();
        assertThat(t.toString()).doesNotContain("consume").doesNotContain("token=");
    }

    // ------------------------------------------------------------------------------------------------- scheduled

    @Test
    @DisplayName("the daily check: the platform records it (REPLACES_DRAFT on the trigger's turn); the answer is its message id")
    void theDailyCheckIsRecordedByThePlatform() throws Exception {
        JsonNode a = answer(callback("t-asis", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?"));
        assertThat(a.has("platformMessageId")).isTrue();
        assertThat(a.has("outcome")).isFalse();
        assertThat(a.has("deliveredContent")).isFalse();
        JsonNode t = lastSend().path("timeline");
        assertThat(t.path("mode").asText()).isEqualTo("REPLACES_DRAFT");
        assertThat(t.path("turnCorrelationId").asText()).isEqualTo("scheduled-transition:t-asis");
        assertThat(t.path("content").asText()).isEqualTo("בוקר טוב! איך הולך השבוע?");
        assertThat(fake.timelineCalls()).isEmpty();
    }

    @Test
    @DisplayName("a timer's text replaced by the ready message (with its buttons): the sent text and buttons go to the platform")
    void aReplacedTimerTextIsWhatIsSent() throws Exception {
        Instant start = clock.instant().minus(Duration.ofMinutes(90));
        QualityTime ended = sessions.saveAndFlush(new QualityTime(father, itamar, start, start.plus(Duration.ofMinutes(60))));
        JsonNode a = answer(callback("t-follow", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה היום? ספר לי הכול"));
        assertThat(a.has("platformMessageId")).isTrue();
        JsonNode sent = lastSend();
        String text = sent.path("content").path("text").asText();
        assertThat(sent.path("timeline").path("content").asText()).isEqualTo(text.substring(IDENTITY.length())).contains("איתמר");
        assertThat(sent.path("content").path("buttons")).hasSize(2);
        assertThat(sent.path("content").path("buttons").get(0).path("id").asText()).isEqualTo("dc:done:" + ended.getId());
        assertThat(sent.path("content").path("buttons").get(0).path("title").asText()).isEqualTo("היה מעולה");
    }

    @Test
    @DisplayName("a timer with no valid session (SKIPPED) and a blocked English text: outcome DROPPED with the reason")
    void skippedAndBlockedAreDropped() throws Exception {
        JsonNode skipped = answer(callback("t-skip", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה הזמן שלך ושל איתמר"));
        assertThat(skipped.path("status").asText()).isEqualTo("SKIPPED");
        assertThat(skipped.path("outcome").asText()).isEqualTo("DROPPED");
        assertThat(skipped.path("reason").asText()).isEqualTo("NO_VALID_SESSION");

        JsonNode blocked = answer(callback("t-en", "ACTIVE_COACHING", "I should check in with the father about his week."));
        assertThat(blocked.path("status").asText()).isEqualTo("FAILED");
        assertThat(blocked.path("outcome").asText()).isEqualTo("DROPPED");
        assertThat(blocked.path("reason").asText()).isEqualTo("BLOCKED_NOT_HEBREW");
        assertThat(fake.platformSends()).isEmpty();
    }

    @Test
    @DisplayName("a send the platform refused: outcome FAILED with the reason; a replayed trigger answers as before")
    void aRefusedSendIsFailedAndAReplayCarriesNoReport() throws Exception {
        fake.onPlatformSend(c -> new FakeServers.Reply(422, "{\"error\":\"messaging.param_invalid\",\"field\":\"content\"}"));
        JsonNode failed = answer(callback("t-fail", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("outcome").asText()).isEqualTo("FAILED");
        assertThat(failed.path("reason").asText()).isEqualTo("PLATFORM_PARAM_INVALID");
        assertThat(failed.has("providerMessageId")).isFalse();

        JsonNode replay = answer(callback("t-fail", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(replay.has("outcome")).isFalse();
        assertThat(replay.has("deliveredContent")).isFalse();
    }

    // ------------------------------------------------------------------------------------------------- the sender

    @Test
    @DisplayName("reports are retried (3 attempts) on 5xx and never block anything; a 4xx is not retried")
    void reportsAreRetriedAndNeverBlock() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        fake.onTimelineReports(c -> calls.incrementAndGet() < 3 ? new FakeServers.Reply(503, "{}") : FakeServers.Reply.json("{}"));
        java.time.Instant before = java.time.Instant.now();
        notes.childAdded(father, "נועה", 5);
        settle();
        assertThat(fake.recordedOutbound()).hasSize(3);
        // D-064: each attempt carries the time the note was made - the one that lands late stands where it happened
        List<String> times = new java.util.ArrayList<>();
        for (FakeServers.Call c : fake.recordedOutbound()) {
            times.add(body(c).path("occurredAt").asText());
        }
        assertThat(times).containsOnly(times.get(0));
        assertThat(java.time.Instant.parse(times.get(0))).isBetween(before, java.time.Instant.now());

        fake.reset();
        fake.onTimelineReports(c -> new FakeServers.Reply(503, "{}"));
        notes.childAdded(father, "יובל", 7);
        settle();
        assertThat(fake.recordedOutbound()).hasSize(3); // given up after 3 (timeline.report_failed)

        fake.reset();
        fake.onTimelineReports(c -> new FakeServers.Reply(404, "{\"error\":\"timeline.turn_not_found\"}"));
        turn(IDENTITY + "שלחתי לך את הכפתור לדף שלך 🙂");
        says("איפה הדף שלי?");
        assertThat(fake.turnOutcomes()).hasSize(1);
        assertThat(fake.sentMessages()).hasSize(1);
    }

    @Test
    @DisplayName("a turn-tied report that times out is never retried (outcome unknown); another report's timeout is retried")
    void aTimedOutTurnReportIsNotRetried() throws Exception {
        reports.setTimeouts(Duration.ofMillis(300), Duration.ofMillis(300), Duration.ofSeconds(5));
        fake.onTimelineReports(c -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return FakeServers.Reply.json("{}");
        });
        turn(IDENTITY + "שלחתי לך את הכפתור לדף שלך 🙂");
        says("איפה הדף שלי?");
        assertThat(fake.sentMessages()).hasSize(1);
        assertThat(fake.turnOutcomes()).hasSize(1); // timeline.report_outcome_unknown, no second attempt

        notes.childAdded(father, "נועה", 5);
        settle();
        assertThat(fake.recordedOutbound()).hasSize(3); // not tied to a turn: a timeout is retried
    }

    // ------------------------------------------------------------------------------------------------- switch off

    @Nested
    @DisplayName("with the switch off")
    class SwitchOff {

        @BeforeEach
        void off() {
            properties.setDeliveryReports(false);
        }

        @Test
        @DisplayName("no inbound / turn-outcome calls, no 'internal'; the platform records the lines it sends; the note's old record")
        void exactlyTheOldCalls() throws Exception {
            turn(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
            says("טוב");
            turn("Noted — is there something specific I can help you adjust?", IDENTITY + "בסדר גמור 🙂");
            says("לא הכל טוב");
            webhook(tap("wamid.tapoff", "dc:done:" + UUID.randomUUID(), "היה מעולה"));
            notes.childAdded(father, "נועה", 5);
            settle();

            assertThat(fake.recordedInbound()).isEmpty();
            assertThat(fake.turnOutcomes()).isEmpty();
            for (FakeServers.Call turn : fake.turns()) {
                assertThat(body(turn).has("internal")).isFalse();
            }
            List<JsonNode> outbound = bodies(fake.recordedOutbound());
            assertThat(outbound).hasSize(1);
            assertThat(outbound.get(0).path("correlationId").asText()).startsWith("dashboard:child-added:");
            // the pre-Phase-3 body: no Phase 3 field at all
            assertThat(outbound.get(0).has("replacesDraft") && !outbound.get(0).get("replacesDraft").isNull()).isFalse();
            assertThat(outbound.get(0).has("kind") && !outbound.get(0).get("kind").isNull()).isFalse();
            assertThat(fake.sentMessages()).hasSize(3);
            assertThat(fake.sendRequests()).allMatch(r -> r.path("timeline").path("record").asBoolean());
        }

        @Test
        @DisplayName("the scheduled-callback answer: {platformMessageId} for a message the platform has, {status, detail} otherwise")
        void theCallbackAnswer() throws Exception {
            JsonNode a = answer(callback("t-off", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?"));
            List<String> fields = new ArrayList<>();
            a.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactly("platformMessageId");
            JsonNode skipped = answer(callback("t-off2", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה"));
            List<String> skippedFields = new ArrayList<>();
            skipped.fieldNames().forEachRemaining(skippedFields::add);
            assertThat(skippedFields).containsExactly("status", "detail");
        }
    }
}
