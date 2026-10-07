package com.dadcoach.whatsapp.buttons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WhatsApp session buttons end to end: the platform's timer callback goes out with "dc:" reply buttons for the right
 * session, and a tap that comes back through the (signed) webhook is handled by Dad Coach - completed, handed to the
 * coach, answered with ideas, or refused as stale - while ordinary text keeps going to the coach unchanged.
 */
class SessionButtonsTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QualityTimeRepository sessions;

    private Father father;
    private Child noa;

    private void setUpFather(String phone, boolean windowOpen) {
        father = data.activeFather(phone);
        noa = data.child(father, "נועה", 6);
        data.endpoint(father, windowOpen);
    }

    private QualityTime session(Duration fromNow, int minutes) {
        Instant start = clock.instant().plus(fromNow);
        return sessions.saveAndFlush(new QualityTime(father, noa, start, start.plus(Duration.ofMinutes(minutes))));
    }

    private void callback(String triggerId, String state, String content) throws Exception {
        String body = "{\"triggerId\":\"" + triggerId + "\",\"workflowInstanceId\":\"w1\",\"userId\":\"whatsapp:" + father.getPhone()
                + "\",\"channel\":\"whatsapp\",\"targetStateKey\":\"" + state + "\",\"responseContent\":\"" + content + "\"}";
        mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
    }

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andExpect(status().isOk());
    }

    private String tap(String wamid, String id, String title) {
        return Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"from\":\"" + father.getPhone().substring(1)
                + "\",\"id\":\"" + wamid + "\",\"timestamp\":\"1793700000\",\"type\":\"interactive\",\"interactive\":"
                + "{\"type\":\"button_reply\",\"button_reply\":{\"id\":\"" + id + "\",\"title\":\"" + title + "\"}}}]}");
    }

    private JsonNode lastSend() throws Exception {
        return json.readTree(fake.metaSends().get(fake.metaSends().size() - 1).body());
    }

    private String lastSentText() throws Exception {
        return lastSend().path("text").path("body").asText();
    }

    private String statusOf(QualityTime qt) {
        return jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?", String.class, qt.getId());
    }

    // --- the buttons going out ---

    @Test
    void theFollowUpAfterASessionCarriesDoneAndMissedForTheSessionThatJustEnded() throws Exception {
        setUpFather("+19995550600", true);
        session(Duration.ofDays(-2), 60); // an older one still awaiting - not the one being asked about
        QualityTime justEnded = session(Duration.ofMinutes(-90), 60);

        callback("f-1", "SESSION_FOLLOW_UP", "❤️ דאד קואץ׳:\\nנו, איך היה לכם עם נועה?");

        JsonNode sent = lastSend();
        assertThat(sent.path("type").asText()).isEqualTo("interactive");
        assertThat(sent.path("interactive").path("type").asText()).isEqualTo("button");
        assertThat(sent.path("interactive").path("body").path("text").asText()).isEqualTo("❤️ דאד קואץ׳:\nנו, איך היה לכם עם נועה?");
        JsonNode buttons = sent.path("interactive").path("action").path("buttons");
        assertThat(buttons).hasSize(2);
        assertThat(buttons.get(0).path("reply").path("id").asText()).isEqualTo("dc:done:" + justEnded.getId());
        assertThat(buttons.get(0).path("reply").path("title").asText()).isEqualTo("היה מעולה");
        assertThat(buttons.get(1).path("reply").path("id").asText()).isEqualTo("dc:missed:" + justEnded.getId());
        assertThat(buttons.get(1).path("reply").path("title").asText()).isEqualTo("לא יצא");
    }

    @Test
    void theOneHourReminderCarriesIdeasForTheNextSession() throws Exception {
        setUpFather("+19995550601", true);
        QualityTime next = session(Duration.ofMinutes(60), 45);
        session(Duration.ofDays(1), 45);

        callback("r-1", "SESSION_REMINDER_1H", "❤️ דאד קואץ׳:\\nעוד שעה הזמן שלך ושל נועה. יש לך כבר רעיון מה תעשו?");

        JsonNode buttons = lastSend().path("interactive").path("action").path("buttons");
        assertThat(buttons).hasSize(1);
        assertThat(buttons.get(0).path("reply").path("id").asText()).isEqualTo("dc:ideas:" + next.getId());
        assertThat(buttons.get(0).path("reply").path("title").asText()).isEqualTo("רוצה רעיונות");
    }

    @Test
    void otherMessagesNoMatchingSessionOrAClosedWindowGoOutWithoutButtons() throws Exception {
        setUpFather("+19995550602", true);
        session(Duration.ofMinutes(60), 45);
        callback("m-1", "SESSION_MORNING_REMINDER", "בוקר טוב! היום ב-13:00 עם נועה");
        assertThat(lastSend().path("type").asText()).isEqualTo("text");
        callback("f-2", "SESSION_FOLLOW_UP", "נו, איך היה?"); // nothing has ended
        assertThat(lastSend().path("type").asText()).isEqualTo("text");

        setUpFather("+19995550603", false);
        session(Duration.ofMinutes(-90), 60);
        callback("f-3", "SESSION_FOLLOW_UP", "נו, איך היה לכם עם נועה?");
        assertThat(lastSend().path("type").asText()).isEqualTo("template"); // outside the window: the template, no buttons
    }

    // --- the taps coming back ---

    @Test
    void doneCompletesTheSessionWithNoAiTurnAndASecondTapSaysItIsAlreadyRecorded() throws Exception {
        setUpFather("+19995550604", true);
        QualityTime qt = session(Duration.ofMinutes(-90), 60);

        webhook(tap("wamid.done1", "dc:done:" + qt.getId(), "היה מעולה"));

        assertThat(statusOf(qt)).isEqualTo("COMPLETED");
        assertThat(fake.turns()).isEmpty();
        assertThat(lastSentText()).isEqualTo("❤️ דאד קואץ׳:\nאיזה כיף! רשמתי את הזמן שלך עם נועה ✅\nהשבוע: שעה.");
        JsonNode recorded = json.readTree(fake.recordedOutbound().get(0).body());
        assertThat(recorded.path("content").asText()).startsWith("❤️ דאד קואץ׳:\nאיזה כיף!");
        assertThat(recorded.path("correlationId").asText()).isEqualTo("wamid.done1");

        webhook(tap("wamid.done2", "dc:done:" + qt.getId(), "היה מעולה"));
        assertThat(lastSentText()).isEqualTo("❤️ דאד קואץ׳:\nכבר רשום אצלי ✅");
        assertThat(fake.turns()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT total_quality_times_completed FROM father WHERE id = ?", Integer.class,
                father.getId())).isEqualTo(1);
    }

    @Test
    void aSessionWithTwoChildrenIsDoneOnceAndItsIdeasNameBoth() throws Exception {
        setUpFather("+19995550609", true);
        Child matar = data.child(father, "מטר", 3);
        QualityTime ended = session(Duration.ofMinutes(-90), 60);
        ended.addChild(matar);
        sessions.saveAndFlush(ended);
        QualityTime next = session(Duration.ofMinutes(60), 30);
        next.addChild(matar);
        sessions.saveAndFlush(next);

        webhook(tap("wamid.joint1", "dc:done:" + ended.getId(), "היה מעולה"));
        assertThat(statusOf(ended)).isEqualTo("COMPLETED");
        assertThat(lastSentText()).isEqualTo("❤️ דאד קואץ׳:\nאיזה כיף! רשמתי את הזמן שלך עם נועה ומטר ✅\nהשבוע: שעה.");
        assertThat(jdbc.queryForObject("SELECT total_quality_times_completed FROM father WHERE id = ?", Integer.class,
                father.getId())).isEqualTo(1);

        webhook(tap("wamid.joint2", "dc:ideas:" + next.getId(), "רוצה רעיונות"));
        assertThat(lastSentText()).startsWith("❤️ דאד קואץ׳:\nכמה רעיונות לזמן שלך עם נועה ומטר:\n\n• ");
        assertThat(fake.turns()).isEmpty();
    }

    @Test
    void missedGoesToTheCoachAsHisWordsAndLeavesTheSessionForTheCoachToRecord() throws Exception {
        setUpFather("+19995550605", true);
        QualityTime qt = session(Duration.ofMinutes(-90), 60);

        webhook(tap("wamid.missed", "dc:missed:" + qt.getId(), "לא יצא"));

        assertThat(fake.turns()).hasSize(1);
        JsonNode turn = json.readTree(fake.turns().get(0).body());
        assertThat(turn.path("content").asText()).isEqualTo("לא יצא");
        assertThat(turn.path("messageType").asText()).isEqualTo("button_reply");
        assertThat(statusOf(qt)).isEqualTo("SCHEDULED");
    }

    @Test
    void ideasAnswersWithIdeasForThatChildThatFitTheSession() throws Exception {
        setUpFather("+19995550606", true);
        QualityTime qt = session(Duration.ofMinutes(60), 30);

        webhook(tap("wamid.ideas", "dc:ideas:" + qt.getId(), "רוצה רעיונות"));

        assertThat(fake.turns()).isEmpty();
        String reply = lastSentText();
        assertThat(reply).startsWith("❤️ דאד קואץ׳:\nכמה רעיונות לזמן שלך עם נועה:\n\n• ");
        assertThat(reply.split("\n• ")).hasSize(4);
        assertThat(reply).endsWith("בהצלחה! 💪");
        assertThat(fake.recordedOutbound()).hasSize(1);
    }

    @Test
    void aTapOnSomeoneElsesCancelledOrNotYetStartedSessionGetsOneFixedLineAndNoTurn() throws Exception {
        setUpFather("+19995550607", true);
        QualityTime ahead = session(Duration.ofMinutes(60), 30);
        QualityTime cancelled = session(Duration.ofMinutes(-90), 30);
        jdbc.update("UPDATE quality_time SET status = 'CANCELLED' WHERE id = ?", cancelled.getId());
        Father me = father;
        setUpFather("+19995550608", true);
        QualityTime others = session(Duration.ofMinutes(-90), 30);
        father = me;

        webhook(tap("wamid.s1", "dc:done:" + others.getId(), "היה מעולה"));
        webhook(tap("wamid.s2", "dc:done:" + cancelled.getId(), "היה מעולה"));
        webhook(tap("wamid.s3", "dc:done:" + ahead.getId(), "היה מעולה"));
        webhook(tap("wamid.s4", "dc:ideas:" + cancelled.getId(), "רוצה רעיונות"));
        webhook(tap("wamid.s5", "dc:done:" + UUID.randomUUID(), "היה מעולה"));

        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(json.readTree(fake.metaSends().get(i).body()).path("text").path("body").asText())
                    .isEqualTo(SessionButtonTaps.STALE_REPLY);
        }
        assertThat(statusOf(others)).isEqualTo("SCHEDULED");
        assertThat(statusOf(ahead)).isEqualTo("SCHEDULED");
    }

    @Test
    void aTemplateQuickReplyIsReadFromItsPayload() throws Exception {
        setUpFather("+19995550609", true);
        QualityTime qt = session(Duration.ofMinutes(-90), 60);

        webhook(Webhooks.envelope("{\"messages\":[{\"from\":\"" + father.getPhone().substring(1) + "\",\"id\":\"wamid.tpl\","
                + "\"timestamp\":\"1793700000\",\"type\":\"button\",\"button\":{\"payload\":\"dc:done:" + qt.getId()
                + "\",\"text\":\"היה מעולה\"}}]}"));

        assertThat(statusOf(qt)).isEqualTo("COMPLETED");
        assertThat(fake.turns()).isEmpty();
    }

    @Test
    void anUnknownButtonIdAndOrdinaryTextStillGoToTheCoach() throws Exception {
        setUpFather("+19995550610", true);

        webhook(tap("wamid.unknown", "dc:checkin:keep", "נשאיר"));
        webhook(Webhooks.text(father.getPhone(), "wamid.text", "היה מעולה"));

        assertThat(fake.turns()).hasSize(2);
        assertThat(json.readTree(fake.turns().get(0).body()).path("content").asText()).isEqualTo("נשאיר");
        assertThat(json.readTree(fake.turns().get(1).body()).path("content").asText()).isEqualTo("היה מעולה");
        assertThat(json.readTree(fake.turns().get(1).body()).path("messageType").asText()).isEqualTo("text");
    }

    // --- the id ---

    @Test
    void everyButtonIdRoundTripsAndStartsWithThePrefix() {
        UUID id = UUID.randomUUID();
        for (SessionButton.Action action : SessionButton.Action.values()) {
            SessionButton button = new SessionButton(action, id);
            assertThat(button.id()).startsWith("dc:");
            assertThat(SessionButton.parse(button.id())).contains(button);
            assertThat(button.toReplyButton().title().length()).isLessThanOrEqualTo(20);
        }
        assertThat(SessionButton.parse(null)).isEmpty();
        assertThat(SessionButton.parse("היה מעולה")).isEmpty();
        assertThat(SessionButton.parse("fu:done:" + id)).isEmpty();
        assertThat(SessionButton.parse("dc:done")).isEmpty();
        assertThat(SessionButton.parse("dc:nope:" + id)).isEmpty();
        assertThat(SessionButton.parse("dc:done:not-a-uuid")).isEmpty();
    }

    @Test
    void hebrewDurations() {
        assertThat(SessionButtonTaps.hebrewDuration(45)).isEqualTo("45 דקות");
        assertThat(SessionButtonTaps.hebrewDuration(60)).isEqualTo("שעה");
        assertThat(SessionButtonTaps.hebrewDuration(90)).isEqualTo("שעה וחצי");
        assertThat(SessionButtonTaps.hebrewDuration(150)).isEqualTo("שעתיים וחצי");
        assertThat(SessionButtonTaps.hebrewDuration(195)).isEqualTo("3 שעות ו-15 דקות");
    }
}
