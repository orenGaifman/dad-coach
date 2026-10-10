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
 * D-039 (Phase 3.4): contract tests of what Dad Coach reports to the platform's conversation timeline, against the fake
 * platform (FakeServers): the turn outcome, the replacement of a reply, code-answered messages, product sends and the
 * scheduled-callback answer - and, with the switch off, exactly the pre-Phase-3 calls. Path by path:
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
    @Autowired com.dadcoach.weeklygoal.BeltPromotionNotifier belts;

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

    String lastSentText() throws Exception {
        JsonNode sent = json.readTree(fake.metaSends().get(fake.metaSends().size() - 1).body());
        return sent.path("text").path("body").asText();
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
    @DisplayName("a reply sent as the platform wrote it: turn-outcome AS_IS with Meta's wamid, nothing else")
    void aReplySentAsWrittenIsAsIs() throws Exception {
        turn(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        String id = says("טוב");

        assertThat(lastSentText()).isEqualTo(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        assertThat(fake.recordedOutbound()).isEmpty();
        assertThat(fake.recordedInbound()).isEmpty();
        JsonNode outcome = body(fake.turnOutcomes().get(0));
        assertThat(fake.turnOutcomes()).hasSize(1);
        assertThat(outcome.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(outcome.path("outcome").asText()).isEqualTo("AS_IS");
        assertThat(outcome.path("providerMessageId").asText()).startsWith("wamid.out.");
        assertThat(outcome.path("deliveryStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(outcome.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(outcome.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(outcome.path("userId").asText()).isEqualTo("whatsapp:" + PHONE);
        assertThat(outcome.path("channelId").asText()).isEqualTo("whatsapp");
        assertThat(outcome.has("supersededTurns")).isFalse();
        // the father's own turn carries no "internal"
        assertThat(body(fake.turns().get(0)).has("internal")).isFalse();
    }

    @Test
    @DisplayName("ClaimGuard changed the reply: the sent text replaces the draft (replacesDraft) - no ':corrected' row")
    void aClaimCorrectionReplacesTheDraft() throws Exception {
        turn(IDENTITY + "מעולה, קובע את זה - יום שישי 09:00-10:30 עם איתמר 💪\nאעדכן אותך בבוקר ושעה לפני כדי שתהיה מוכן.");
        String id = says("כן");

        assertThat(lastSentText()).isEqualTo(IDENTITY + "רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם איתמר.\nלקבוע?");
        assertThat(fake.recordedOutbound()).hasSize(1);
        JsonNode delivered = body(fake.recordedOutbound().get(0));
        assertThat(delivered.path("correlationId").asText()).isEqualTo(id + ":delivered");
        assertThat(delivered.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(delivered.path("replacesDraft").asBoolean()).isTrue();
        assertThat(delivered.path("content").asText()).isEqualTo("רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם איתמר.\nלקבוע?");
        assertThat(delivered.path("kind").asText()).isEqualTo("REPLY");
        assertThat(delivered.path("providerMessageId").asText()).startsWith("wamid.out.");
        assertThat(delivered.path("deliveryStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(delivered.path("personRef").asText()).isNotBlank();
        assertThat(delivered.path("metadata").path("timezone").asText()).isEqualTo("Asia/Jerusalem");
        assertThat(fake.turnOutcomes()).isEmpty();
        assertThat(fake.recordedOutbound()).noneMatch(c -> c.body().contains(":corrected"));
    }

    @Test
    @DisplayName("ReplyStyleGuard changed the reply ('רק רגע' removed): a replacement too")
    void aStyleCorrectionReplacesTheDraft() throws Exception {
        turn(IDENTITY + "וואו, לא ידעתי על מטר ונעם 😊 רק רגע ונמשיך משם.");
        String id = says("מטר בת 8 נעם בת 5");

        JsonNode delivered = body(fake.recordedOutbound().get(0));
        assertThat(delivered.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(delivered.path("replacesDraft").asBoolean()).isTrue();
        assertThat(delivered.path("content").asText()).isEqualTo("וואו, לא ידעתי על מטר ונעם 🙂");
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

        JsonNode delivered = body(fake.recordedOutbound().get(0));
        assertThat(fake.recordedOutbound()).hasSize(1);
        assertThat(delivered.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(delivered.path("replacesDraft").asBoolean()).isTrue();
        assertThat(delivered.path("content").asText()).isEqualTo("בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        assertThat(delivered.path("supersededTurns")).hasSize(1);
        assertThat(delivered.path("supersededTurns").get(0).asText()).isEqualTo(id + ":he");
        assertThat(fake.turnOutcomes()).isEmpty();
    }

    @Test
    @DisplayName("still not Hebrew after the rewrite: the fixed line replaces the draft, ':he' superseded")
    void theNotHebrewLineReplacesTheDraft() throws Exception {
        turn("Fine as is, no change needed.");
        String id = says("טוב");

        JsonNode delivered = body(fake.recordedOutbound().get(0));
        assertThat(delivered.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(delivered.path("replacesDraft").asBoolean()).isTrue();
        assertThat(delivered.path("content").asText()).isEqualTo("סליחה, התבלבלתי בניסוח.\nאפשר לכתוב לי שוב מה צריך?");
        assertThat(delivered.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(delivered.path("supersededTurns").get(0).asText()).isEqualTo(id + ":he");
    }

    @Test
    @DisplayName("'I sent you the button' only: the card is recorded with its button (never the link), the turn DROPPED")
    void theSentLineIsDroppedAndTheCardRecorded() throws Exception {
        turn(IDENTITY + "שלחתי לך את הכפתור לדף שלך 🙂");
        String id = says("איפה הדף שלי?");

        assertThat(fake.metaSends()).hasSize(1); // only the card
        List<FakeServers.Call> timeline = fake.timelineCalls();
        assertThat(timeline).hasSize(2);
        JsonNode card = body(timeline.get(0));
        assertThat(timeline.get(0).path()).isEqualTo("/api/v1/worker/messages/outbound");
        assertThat(card.path("correlationId").asText()).startsWith("dashboard-link:");
        assertThat(card.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(card.has("replacesDraft")).isFalse();
        assertThat(card.path("content").asText()).isEqualTo("📊 *הדף שלך בדאד קואץ׳*\nהשבוע, הילדים וההתקדמות, הכול בדף אחד.");
        assertThat(card.path("kind").asText()).isEqualTo("DASHBOARD_LINK");
        assertThat(card.path("buttons")).hasSize(1);
        assertThat(card.path("buttons").get(0).path("type").asText()).isEqualTo("url");
        assertThat(card.path("buttons").get(0).path("title").asText()).isEqualTo("כניסה לדף שלי");
        assertThat(card.path("providerMessageId").asText()).startsWith("wamid.out.");
        assertThat(timeline.get(0).body()).doesNotContain("consume").doesNotContain("token").doesNotContain("https://");

        JsonNode dropped = body(timeline.get(1));
        assertThat(timeline.get(1).path()).isEqualTo("/api/v1/worker/messages/turn-outcome");
        assertThat(dropped.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(dropped.path("outcome").asText()).isEqualTo("DROPPED");
        assertThat(dropped.path("reason").asText()).isEqualTo("SENT_LINE_CARD");
    }

    @Test
    @DisplayName("DC-B4: a duplicate answer carrying the first attempt's reply goes out once and is reported AS_IS on that turn")
    void aReplayedDuplicateIsAsIsOnItsTurn() throws Exception {
        fake.onTurn(c -> FakeServers.Reply.json(reply(IDENTITY + "בסדר גמור 🙂").body()
                .replace("\"isDuplicate\":false", "\"isDuplicate\":true")));
        String id = says("טוב");
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.turnOutcomes()).hasSize(1);
        JsonNode outcome = body(fake.turnOutcomes().get(0));
        assertThat(outcome.path("turnCorrelationId").asText()).isEqualTo(id);
        assertThat(outcome.path("outcome").asText()).isEqualTo("AS_IS");
        assertThat(fake.recordedOutbound()).isEmpty();
    }

    @Test
    @DisplayName("a reply Meta refused is not reported: the redelivery answers it and reports then")
    void aRefusedReplyIsNotReported() throws Exception {
        fake.onMetaSend(c -> new FakeServers.Reply(400, "{\"error\":{\"code\":131047,\"message\":\"Re-engagement message\"}}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        says("טוב");
        assertThat(fake.timelineCalls()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- code-answered

    @Test
    @DisplayName("a ready question: his message (/messages/inbound) first, then the answer - no turn")
    void aReadyAnswerIsHisMessageThenTheAnswer() throws Exception {
        Instant friday9 = LocalDate.of(2026, 11, 6).atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Jerusalem")).toInstant();
        qualityTime.scheduleQualityTime(father.getId(), itamar.getId(), friday9, Duration.ofMinutes(90));
        jdbc.update("INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status) "
                + "VALUES (?, '2026-11-01', 3, 0, 'WHITE', 'ACTIVE')", father.getId());
        fake.reset(); // the booking above armed timers; only this message's calls count
        String id = says("מה יש לי השבוע?");

        assertThat(fake.turns()).isEmpty();
        List<FakeServers.Call> timeline = fake.timelineCalls();
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).path()).isEqualTo("/api/v1/worker/messages/inbound");
        JsonNode inbound = body(timeline.get(0));
        assertThat(inbound.path("correlationId").asText()).isEqualTo(id);
        assertThat(inbound.path("content").asText()).isEqualTo("מה יש לי השבוע?");
        assertThat(inbound.path("messageType").asText()).isEqualTo("text");
        assertThat(inbound.has("structured")).isFalse();
        assertThat(inbound.path("workflowKey").asText()).isEqualTo("dad-coach-3");

        JsonNode answer = body(timeline.get(1));
        assertThat(timeline.get(1).path()).isEqualTo("/api/v1/worker/messages/outbound");
        assertThat(answer.path("correlationId").asText()).isEqualTo(id);
        assertThat(answer.path("kind").asText()).isEqualTo("READY_ANSWER");
        assertThat(answer.path("content").asText()).startsWith("השבוע: שעה וחצי מתוך 3 שעות.");
        assertThat(answer.path("content").asText()).doesNotContain("דאד קואץ׳:");
        assertThat(answer.path("providerMessageId").asText()).startsWith("wamid.out.");
        assertThat(answer.has("turnCorrelationId")).isFalse();
    }

    @Test
    @DisplayName("a tap with a fixed reply: the tap (button_reply + structured) then the reply, in place of the old record")
    void aTapIsRecordedAsHisTapThenTheReply() throws Exception {
        String buttonId = "dc:done:" + UUID.randomUUID();
        webhook(tap("wamid.tap1", buttonId, "היה מעולה"));
        settle();

        assertThat(fake.turns()).isEmpty();
        List<FakeServers.Call> timeline = fake.timelineCalls();
        assertThat(timeline).hasSize(2);
        JsonNode inbound = body(timeline.get(0));
        assertThat(timeline.get(0).path()).isEqualTo("/api/v1/worker/messages/inbound");
        assertThat(inbound.path("content").asText()).isEqualTo("היה מעולה");
        assertThat(inbound.path("messageType").asText()).isEqualTo("button_reply");
        assertThat(inbound.path("structured").path("buttonId").asText()).isEqualTo(buttonId);
        assertThat(inbound.path("structured").path("title").asText()).isEqualTo("היה מעולה");
        JsonNode reply = body(timeline.get(1));
        assertThat(reply.path("correlationId").asText()).isEqualTo("wamid.tap1");
        assertThat(reply.path("kind").asText()).isEqualTo("BUTTON_REPLY");
        assertThat(reply.path("providerMessageId").asText()).startsWith("wamid.out.");
    }

    @Test
    @DisplayName("a picture without words: his message as [photo], then the fixed line")
    void aPictureIsAMarkerThenTheLine() throws Exception {
        webhook(Webhooks.image(PHONE, "wamid.pic1", ""));
        settle();
        List<FakeServers.Call> timeline = fake.timelineCalls();
        assertThat(timeline).hasSize(2);
        JsonNode inbound = body(timeline.get(0));
        assertThat(inbound.path("content").asText()).isEqualTo("[photo]");
        assertThat(inbound.path("messageType").asText()).isEqualTo("image");
        JsonNode line = body(timeline.get(1));
        assertThat(line.path("correlationId").asText()).isEqualTo("wamid.pic1:reply");
        assertThat(line.path("kind").asText()).isEqualTo("FIXED_LINE");
        assertThat(line.path("content").asText()).isEqualTo(lastSentText().substring(IDENTITY.length()));
    }

    @Test
    @DisplayName("a voice note not heard: [voice note] (audio), then the line asking again")
    void aVoiceNoteNotHeardIsReported() throws Exception {
        fake.onSpeechToText(c -> FakeServers.Reply.json("{\"language_code\":\"heb\",\"text\":\"  \"}"));
        webhook(Webhooks.audio(PHONE, "wamid.voice1"));
        settle();
        List<FakeServers.Call> timeline = fake.timelineCalls();
        assertThat(timeline).hasSize(2);
        assertThat(body(timeline.get(0)).path("content").asText()).isEqualTo("[voice note]");
        assertThat(body(timeline.get(0)).path("messageType").asText()).isEqualTo("audio");
        assertThat(body(timeline.get(1)).path("content").asText()).contains("לא שמעתי מילים");
    }

    @Test
    @DisplayName("a reaction, a typed deletion request and a stranger's picture: nothing is reported")
    void nothingForAReactionADeletionOrAStranger() throws Exception {
        webhook(Webhooks.reaction(PHONE, "wamid.re1", "❤️"));
        webhook(Webhooks.image("+19995550999", "wamid.stranger1", ""));
        webhook(Webhooks.text(PHONE, "wamid.del1", "מחק את המידע שלי"));
        settle();
        assertThat(fake.metaSends()).hasSize(2); // the stranger's line and the deletion confirmation
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
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("a card from the login page: recorded with its button title, not linked to a turn, never the link")
    void aCardFromTheLoginPageIsRecorded() throws Exception {
        links.requestLink(PHONE, null, "10.0.0.9");
        settle();
        assertThat(fake.recordedOutbound()).hasSize(1);
        JsonNode card = body(fake.recordedOutbound().get(0));
        assertThat(card.path("kind").asText()).isEqualTo("DASHBOARD_LINK");
        assertThat(card.has("turnCorrelationId")).isFalse();
        assertThat(card.path("buttons").get(0).path("title").asText()).isEqualTo("כניסה לדף שלי");
        assertThat(fake.recordedOutbound().get(0).body()).doesNotContain("consume").doesNotContain("token=");
    }

    @Test
    @DisplayName("a belt promotion (unused since D-030): kind BELT_PROMOTION with its wamid, in place of the old record")
    void aBeltPromotionIsRecordedWithItsDelivery() throws Exception {
        belts.sendPromotionNotification(new com.dadcoach.weeklygoal.WeeklyGoalService.BeltPromotionResult(father.getId(), true,
                com.dadcoach.workflow.Belt.WHITE, com.dadcoach.workflow.Belt.WHITE.getNextBelt(), 120, 120, 1, false));
        settle();
        assertThat(fake.recordedOutbound()).hasSize(1);
        JsonNode promo = body(fake.recordedOutbound().get(0));
        assertThat(promo.path("correlationId").asText()).startsWith("belt-promotion:" + father.getId());
        assertThat(promo.path("kind").asText()).isEqualTo("BELT_PROMOTION");
        assertThat(promo.path("content").asText()).startsWith("💪");
        assertThat(promo.path("providerMessageId").asText()).startsWith("wamid.out.");
    }

    // ------------------------------------------------------------------------------------------------- scheduled

    @Test
    @DisplayName("the daily check sent as written: the callback answers outcome AS_IS with the wamid")
    void theDailyCheckAsWrittenIsAsIs() throws Exception {
        JsonNode a = answer(callback("t-asis", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?"));
        assertThat(a.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(a.path("outcome").asText()).isEqualTo("AS_IS");
        assertThat(a.path("providerMessageId").asText()).startsWith("wamid.out.");
        assertThat(a.path("deliveryStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(a.has("deliveredContent")).isFalse();
        assertThat(fake.timelineCalls()).isEmpty(); // the answer is the report
    }

    @Test
    @DisplayName("a timer's text replaced by the ready message (with its buttons): deliveredContent + buttons")
    void aReplacedTimerTextIsDeliveredContent() throws Exception {
        Instant start = clock.instant().minus(Duration.ofMinutes(90));
        QualityTime ended = sessions.saveAndFlush(new QualityTime(father, itamar, start, start.plus(Duration.ofMinutes(60))));
        JsonNode a = answer(callback("t-follow", "SESSION_FOLLOW_UP", IDENTITY + "היי! איך היה היום? ספר לי הכול"));
        assertThat(a.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(a.has("outcome")).isFalse();
        assertThat(a.path("deliveredContent").asText()).isEqualTo(lastSentInteractiveBody().substring(IDENTITY.length()));
        assertThat(a.path("deliveredContent").asText()).contains("איתמר");
        assertThat(a.path("kind").asText()).isEqualTo("SCHEDULED");
        assertThat(a.path("buttons")).hasSize(2);
        assertThat(a.path("buttons").get(0).path("id").asText()).isEqualTo("dc:done:" + ended.getId());
        assertThat(a.path("buttons").get(0).path("title").asText()).isEqualTo("היה מעולה");
        assertThat(a.path("providerMessageId").asText()).startsWith("wamid.out.");
    }

    String lastSentInteractiveBody() throws Exception {
        JsonNode sent = json.readTree(fake.metaSends().get(fake.metaSends().size() - 1).body());
        return sent.path("interactive").path("body").path("text").asText();
    }

    @Test
    @DisplayName("outside the window the template carries it: deliveredContent = the {{1}} line, template {name, params}")
    void aTemplateSendIsDeliveredContentWithTheTemplate() throws Exception {
        jdbc.update("UPDATE communication_endpoints SET session_opens_at = ?, session_closes_at = ? WHERE channel_identity = ?",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(4))),
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(3))), PHONE);
        JsonNode a = answer(callback("t-tpl", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!\nאיך הולך השבוע?"));
        assertThat(json.readTree(fake.metaSends().get(0).body()).path("type").asText()).isEqualTo("template");
        assertThat(a.path("deliveredContent").asText()).isEqualTo("בוקר טוב! איך הולך השבוע?");
        assertThat(a.path("template").path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(a.path("template").path("params").get(0).asText()).isEqualTo("בוקר טוב! איך הולך השבוע?");
        assertThat(a.path("kind").asText()).isEqualTo("SCHEDULED");
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
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("a send that failed: outcome FAILED with the reason; a replayed trigger answers as before")
    void aFailedSendIsFailedAndAReplayCarriesNoReport() throws Exception {
        fake.onMetaSend(c -> new FakeServers.Reply(400, "{\"error\":{\"code\":131026,\"message\":\"Message undeliverable\"}}"));
        JsonNode failed = answer(callback("t-fail", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("outcome").asText()).isEqualTo("FAILED");
        assertThat(failed.path("reason").asText()).isNotBlank();
        assertThat(failed.has("providerMessageId")).isFalse();

        JsonNode replay = answer(callback("t-fail", "ACTIVE_COACHING", IDENTITY + "בוקר טוב!"));
        assertThat(replay.has("outcome")).isFalse();
        assertThat(replay.has("deliveredContent")).isFalse();
    }

    // ------------------------------------------------------------------------------------------------- the sender

    @Test
    @DisplayName("reports are retried (3 attempts) on 5xx and never block the reply; a 4xx is not retried")
    void reportsAreRetriedAndNeverBlock() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        fake.onTimelineReports(c -> calls.incrementAndGet() < 3 ? new FakeServers.Reply(503, "{}") : FakeServers.Reply.json("{}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        says("טוב");
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(fake.turnOutcomes()).hasSize(3);

        fake.reset();
        fake.onTimelineReports(c -> new FakeServers.Reply(503, "{}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        says("טוב שוב");
        assertThat(fake.turnOutcomes()).hasSize(3); // given up after 3 (timeline.report_failed)

        fake.reset();
        fake.onTimelineReports(c -> new FakeServers.Reply(404, "{\"error\":\"timeline.turn_not_found\"}"));
        turn(IDENTITY + "בסדר גמור 🙂");
        says("ועוד פעם");
        assertThat(fake.turnOutcomes()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
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
        turn(IDENTITY + "בסדר גמור 🙂");
        says("טוב");
        assertThat(fake.metaSends()).hasSize(1);
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
        @DisplayName("no inbound / turn-outcome calls, no 'internal', the old ':corrected' row and the old tap record")
        void exactlyTheOldCalls() throws Exception {
            turn(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
            says("טוב");
            turn("Noted — is there something specific I can help you adjust?", IDENTITY + "בסדר גמור 🙂");
            says("לא הכל טוב");
            turn(IDENTITY + "מעולה, קובע את זה - יום שישי 09:00-10:30 עם איתמר 💪\nאעדכן אותך בבוקר ושעה לפני כדי שתהיה מוכן.");
            String corrected = says("כן");
            webhook(tap("wamid.tapoff", "dc:done:" + UUID.randomUUID(), "היה מעולה"));
            notes.childAdded(father, "נועה", 5);
            settle();

            assertThat(fake.recordedInbound()).isEmpty();
            assertThat(fake.turnOutcomes()).isEmpty();
            for (FakeServers.Call turn : fake.turns()) {
                assertThat(body(turn).has("internal")).isFalse();
            }
            List<JsonNode> outbound = bodies(fake.recordedOutbound());
            assertThat(outbound).extracting(b -> b.path("correlationId").asText())
                    .containsExactly(corrected + ":corrected", "wamid.tapoff", outbound.get(2).path("correlationId").asText());
            assertThat(outbound.get(2).path("correlationId").asText()).startsWith("dashboard:child-added:");
            for (JsonNode b : outbound) {
                // the pre-Phase-3 body: no Phase 3 field at all
                assertThat(b.has("replacesDraft") && !b.get("replacesDraft").isNull()).isFalse();
                assertThat(b.has("kind") && !b.get("kind").isNull()).isFalse();
                assertThat(b.has("providerMessageId") && !b.get("providerMessageId").isNull()).isFalse();
            }
            assertThat(outbound.get(0).path("content").asText()).startsWith(IDENTITY + "רק מוודא");
        }

        @Test
        @DisplayName("the scheduled-callback answer is exactly {status, detail}")
        void theCallbackAnswerIsUnchanged() throws Exception {
            JsonNode a = answer(callback("t-off", "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?"));
            List<String> fields = new ArrayList<>();
            a.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactly("status", "detail");
            JsonNode skipped = answer(callback("t-off2", "SESSION_REMINDER_1H", IDENTITY + "עוד שעה"));
            List<String> skippedFields = new ArrayList<>();
            skipped.fieldNames().forEachRemaining(skippedFields::add);
            assertThat(skippedFields).containsExactly("status", "detail");
        }
    }
}
