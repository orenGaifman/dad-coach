package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-034: what the father reads rests on what really happened in this turn. The platform's turn is FakeServers - its
 * "tools" act on the real database inside the turn, as the platform's tool calls do - and the reply goes through the
 * real inbound pipeline (ClaimGuard, ReplyStyleGuard, the Hebrew retry, CoachMentions).
 */
class TruthfulRepliesTest extends AbstractIntegrationTest {

    static final String PHONE = "+19995550700";
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QualityTimeService qualityTime;

    Father father;
    Child itamar;
    int wamid;

    @BeforeEach
    void oren() {
        father = data.father(PHONE, "אורן", com.dadcoach.father.FatherStatus.ACTIVE);
        itamar = data.child(father, "איתמר", 6);
        data.endpoint(father, true);
    }

    /** The coach's turn: {@code tools} runs inside it (like a tool call), then it answers {@code reply}. */
    void turn(Consumer<Void> tools, String... replies) {
        AtomicInteger n = new AtomicInteger();
        fake.onTurn(c -> {
            if (n.get() == 0 && tools != null) {
                tools.accept(null);
            }
            String reply = replies[Math.min(n.getAndIncrement(), replies.length - 1)];
            try {
                return FakeServers.Reply.json(json.writeValueAsString(Map.of("instanceId", "11111111-1111-1111-1111-111111111111",
                        "currentStateKey", "ACTIVE_COACHING", "responseContent", reply, "responseType", "text",
                        "metadata", Map.of("responseOutcome", "GENERATED"), "isDuplicate", false)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    void says(String text) throws Exception {
        byte[] raw = Webhooks.text(PHONE, "wamid.t" + (++wamid), text).getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andExpect(status().isOk());
    }

    String lastSent() throws Exception {
        JsonNode sent = json.readTree(fake.metaSends().get(fake.metaSends().size() - 1).body());
        return sent.path("text").path("body").asText();
    }

    @Test
    @DisplayName("D-2: 'קובע את זה' with nothing booked - he reads the confirm question, and so does his conversation")
    void aBookingThatDidNotHappenIsNeverConfirmed() throws Exception {
        turn(null, IDENTITY + "מעולה, קובע את זה - יום שישי 09:00-10:30 עם איתמר 💪\nאעדכן אותך בבוקר ושעה לפני כדי שתהיה מוכן.");
        says("כן");
        assertThat(lastSent()).isEqualTo(IDENTITY + "רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם איתמר.\nלקבוע?");
        assertThat(fake.recordedOutbound()).singleElement().satisfies(c -> assertThat(c.body()).contains("רק מוודא"));
    }

    @Test
    @DisplayName("D-3: a booking that happened goes out as the ready confirmation, not the model's minutes and reminders")
    void aRealBookingIsConfirmedInTheReadyShape() throws Exception {
        // Tuesday 2026-11-03 12:00 in Israel; Friday 09:00 has no morning reminder
        Instant friday9 = LocalDate.of(2026, 11, 6).atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Jerusalem")).toInstant();
        jdbc.update("INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status) "
                + "VALUES (?, '2026-11-01', 2, 0, 'WHITE', 'ACTIVE')", father.getId());
        turn(v -> qualityTime.scheduleQualityTime(father.getId(), itamar.getId(), friday9, Duration.ofMinutes(90)),
                IDENTITY + "קבעתי! יום שישי 09:00-10:30 עם איתמר 💪\nהשבוע מכוסה במלואו - 90 מתוך 120 דקות ✅ אעדכן אותך בבוקר.");
        says("כן");
        assertThat(lastSent()).isEqualTo(IDENTITY + "קבעתי 🎉 *יום שישי 6.11 ב-09:00*, שעה וחצי עם איתמר.\n"
                + "אזכיר לך שעה לפני, ואשאל אחר כך איך היה.\nהשבוע: שעה וחצי מתוך שעתיים.");
    }

    @Test
    @DisplayName("D-2: 'שלחתי לך את הכפתור' when no button went out - the product sends it, so it is true")
    void aButtonItSaysItSentIsSent() throws Exception {
        turn(null, IDENTITY + "אפשר לתקן את הגיל בדף שלך. שלחתי לך את הכפתור 🙂");
        says("תעדכן שאיתמר בן 10");
        List<FakeServers.Call> sends = fake.metaSends();
        assertThat(sends).hasSize(2);
        assertThat(sends.get(0).body()).contains("cta_url").contains("כניסה לדף שלי");
        assertThat(lastSent()).isEqualTo(IDENTITY + "אפשר לתקן את הגיל בדף שלך. שלחתי לך את הכפתור 🙂");
    }

    @Test
    @DisplayName("'רק רגע' never goes: nothing runs after the reply")
    void noPromiseOfLater() throws Exception {
        turn(null, IDENTITY + "וואו, לא ידעתי על מטר ונעם 😊 רק רגע ונמשיך משם.");
        says("מטר בת 8 נעם בת 5");
        assertThat(lastSent()).isEqualTo(IDENTITY + "וואו, לא ידעתי על מטר ונעם 🙂");
    }

    @Test
    @DisplayName("B-4: an English reply is written again in Hebrew once; still English - one short Hebrew line, never silence")
    void englishIsNeverSilence() throws Exception {
        turn(null, "Noted — is there something specific I can help you adjust?", IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");
        says("לא הכל טוב");
        assertThat(fake.turns()).hasSize(2);
        assertThat(json.readTree(fake.turns().get(1).body()).path("content").asText()).startsWith("[הודעת מערכת");
        assertThat(json.readTree(fake.turns().get(1).body()).path("correlationId").asText()).endsWith(":he");
        assertThat(lastSent()).isEqualTo(IDENTITY + "בסדר גמור, השבוע נשאר כמו שהוא 🙂");

        fake.reset();
        turn(null, "Fine as is, no change needed.");
        says("טוב");
        assertThat(lastSent()).isEqualTo(IDENTITY + "סליחה, התבלבלתי בניסוח.\nאפשר לכתוב לי שוב מה צריך?");
    }

    @Test
    @DisplayName("D-5, D-6: a session named today and a missing goal raised today are noted for the next turn")
    void whatWasSaidTodayIsNoted() throws Exception {
        Instant today17 = LocalDate.of(2026, 11, 3).atTime(17, 0).atZone(java.time.ZoneId.of("Asia/Jerusalem")).toInstant();
        qualityTime.scheduleQualityTime(father.getId(), itamar.getId(), today17, Duration.ofMinutes(30));
        turn(null, IDENTITY + "היי אורן 🙂 היום ב-17:00 עם איתמר.\nעוד אין יעד לשבוע, כמה שעות בא לך?");
        says("היי");
        assertThat(jdbc.queryForObject("SELECT mentioned_on FROM quality_time", LocalDate.class)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(jdbc.queryForObject("SELECT goal_asked_on FROM father WHERE id = ?", LocalDate.class, father.getId()))
                .isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("D-9, D-10: the vocabulary and no ' - ' between sentences, on every reply")
    void theStandardOnEveryReply() throws Exception {
        turn(null, IDENTITY + "נכון - אם זה לא יקרה, זה לא ייספר 😊 👍");
        says("ואז לא תמצא לי את היעד כאילו היה?");
        assertThat(lastSent()).isEqualTo(IDENTITY + "נכון, אם זה לא יקרה, זה לא ייספר 🙂");
    }
}
