package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.replies.TimerClaims;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-037: a reply promises a reminder only when the platform holds it. The booking turn is FakeServers (its "tools" act
 * on the real database inside the turn); after it returns, Dad Coach reads the session's pending timers from the
 * platform's worker API, arms the missing ones, and builds the reminder line from what the platform confirmed. The fake
 * platform keeps its timers in memory (same dedupe as the real one) or is told to refuse / be down.
 */
class ArmedTimersTest extends AbstractIntegrationTest {

    static final String PHONE = "+19995550710";
    static final String USER = "whatsapp:" + PHONE;
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final ZoneId IL = ZoneId.of("Asia/Jerusalem");
    /** Friday 6.11 09:00 in Israel: no morning reminder (before 10:00) - the hour before (08:00) and the follow-up (11:00). */
    static final Instant FRIDAY_9 = LocalDate.of(2026, 11, 6).atTime(9, 0).atZone(IL).toInstant();
    static final String BOOKED = "קבעתי 🎉 *יום שישי 6.11 ב-09:00*, שעה וחצי עם איתמר.";
    static final String PLANNED_LINE = "אזכיר לך שעה לפני, ואשאל אחר כך איך היה.";
    static final String WEEK = "השבוע: שעה וחצי מתוך שעתיים.";
    /** What the tool returned and the model copies word for word - with the PLANNED timers. */
    static final String MODEL_COPY = IDENTITY + BOOKED + "\n" + PLANNED_LINE + "\n" + WEEK;

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
        jdbc.update("INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status) "
                + "VALUES (?, '2026-11-01', 2, 0, 'WHITE', 'ACTIVE')", father.getId());
    }

    // ---- booking ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the model armed nothing: Dad Coach arms both planned timers after the turn and the reply says them")
    void missingTimersAreArmedAndThenPromised() throws Exception {
        AtomicReference<String> booked = bookFriday9(null);
        says("כן");
        String session = booked.get();

        assertThat(lastSent()).isEqualTo(MODEL_COPY);
        assertThat(fake.pendingTimers()).extracting(FakeServers.Timer::transitionKey, FakeServers.Timer::scheduledAt,
                        FakeServers.Timer::referenceId, FakeServers.Timer::source)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("session_reminder_1h", FRIDAY_9.minus(Duration.ofHours(1)), session, "PRODUCT"),
                        org.assertj.core.groups.Tuple.tuple("session_follow_up", FRIDAY_9.plus(Duration.ofMinutes(120)), session, "PRODUCT"));
        assertThat(fake.scheduledTransitionCalls("GET")).hasSize(1);
        assertThat(fake.scheduledTransitionCalls("POST")).hasSize(2);
        JsonNode arm = json.readTree(fake.scheduledTransitionCalls("POST").get(0).body());
        assertThat(arm.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(arm.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(arm.path("userId").asText()).isEqualTo(USER);
        assertThat(arm.path("referenceType").asText()).isEqualTo("quality_time");
    }

    @Test
    @DisplayName("the model armed both in its turn: one read confirms them, nothing is armed twice")
    void timersTheModelArmedAreConfirmedNotArmedAgain() throws Exception {
        bookFriday9(id -> {
            fake.armAsModel(USER, "session_reminder_1h", FRIDAY_9.minus(Duration.ofHours(1)), id);
            fake.armAsModel(USER, "session_follow_up", FRIDAY_9.plus(Duration.ofMinutes(120)), id);
        });
        says("כן");

        assertThat(lastSent()).isEqualTo(MODEL_COPY);
        assertThat(fake.scheduledTransitionCalls("POST")).isEmpty();
        assertThat(fake.pendingTimers()).hasSize(2).allMatch(t -> t.source().equals("AI_TOOL"));
    }

    @Test
    @DisplayName("the model armed one at a wrong time: it is moved to the planned time (the platform's dedupe), one copy")
    void aTimerAtTheWrongTimeIsMoved() throws Exception {
        bookFriday9(id -> fake.armAsModel(USER, "session_reminder_1h", FRIDAY_9.minus(Duration.ofHours(3)), id));
        says("כן");

        assertThat(lastSent()).isEqualTo(MODEL_COPY);
        assertThat(fake.pendingTimers()).hasSize(2);
        assertThat(fake.pendingTimers()).filteredOn(t -> t.transitionKey().equals("session_reminder_1h")).singleElement()
                .satisfies(t -> assertThat(t.scheduledAt()).isEqualTo(FRIDAY_9.minus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("the platform refuses every timer (409, not on the conversation's state): the reply promises none and says so")
    void refusedTimersAreNeverPromised() throws Exception {
        fake.onScheduledTransitions(c -> c.method().equals("GET")
                ? FakeServers.Reply.json("{\"pending\":[]}")
                : new FakeServers.Reply(409, "{\"error\":\"TRANSITION_NOT_ON_CURRENT_STATE\",\"currentStateKey\":\"ONBOARDING\"}"));
        bookFriday9(null);
        says("כן");

        assertThat(lastSent()).isEqualTo(IDENTITY + BOOKED + "\n" + TimerClaims.NOT_SET + "\n" + WEEK);
        assertThat(lastSent()).doesNotContain("אזכיר").doesNotContain("אשאל");
        // his conversation gets the corrected text too, so the next turn does not repeat the promise
        assertThat(fake.recordedOutbound()).singleElement()
                .satisfies(c -> assertThat(json.readTree(c.body()).path("content").asText()).isEqualTo(lastSent()));
    }

    @Test
    @DisplayName("only the hour-before was confirmed: the reply promises only that")
    void onlyConfirmedTimersArePromised() throws Exception {
        fake.onScheduledTransitions(c -> {
            if (c.method().equals("GET")) {
                return FakeServers.Reply.json("{\"pending\":[]}");
            }
            if (c.body().contains("session_follow_up")) {
                return new FakeServers.Reply(422, "{\"error\":\"TRANSITION_REFUSED\"}");
            }
            return FakeServers.Reply.json("{\"triggerId\":\"t-1\",\"transitionKey\":\"session_reminder_1h\",\"scheduledAt\":\""
                    + FRIDAY_9.minus(Duration.ofHours(1)) + "\"}");
        });
        bookFriday9(null);
        says("כן");

        assertThat(lastSent()).isEqualTo(IDENTITY + BOOKED + "\nאזכיר לך שעה לפני.\n" + WEEK);
    }

    @Test
    @DisplayName("the platform is down (503) or older, without this API (404): no reminder is promised")
    void platformDownOrOlderMeansNoPromise() throws Exception {
        for (int code : new int[] {503, 404}) {
            fake.reset();
            jdbc.update("DELETE FROM quality_time");
            fake.onScheduledTransitions(c -> new FakeServers.Reply(code, "{}"));
            bookFriday9(null);
            says("כן");
            assertThat(lastSent()).as("HTTP " + code).isEqualTo(IDENTITY + BOOKED + "\n" + TimerClaims.NOT_SET + "\n" + WEEK);
        }
    }

    @Test
    @DisplayName("the model wrote its own reminder promise instead of the ready line: the ready confirmation with the confirmed timers")
    void aPromiseOfItsOwnIsReplaced() throws Exception {
        fake.onScheduledTransitions(c -> new FakeServers.Reply(503, "{}"));
        turn(v -> book(FRIDAY_9), IDENTITY + "קבעתי! שישי ב-09:00 עם איתמר 💪 אזכיר לך בבוקר ושעה לפני.");
        says("כן");
        assertThat(lastSent()).isEqualTo(IDENTITY + BOOKED + "\n" + TimerClaims.NOT_SET + "\n" + WEEK);
    }

    // ---- a move -----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a move: the old session's timers are cancelled, the new one's armed, and the reply says what the platform holds")
    void aMoveMovesTheTimers() throws Exception {
        Instant thursday17 = LocalDate.of(2026, 11, 5).atTime(17, 0).atZone(IL).toInstant();
        String old = book(thursday17);
        fake.armAsModel(USER, "session_morning_reminder", LocalDate.of(2026, 11, 5).atTime(8, 0).atZone(IL).toInstant(), old);
        fake.armAsModel(USER, "session_reminder_1h", thursday17.minus(Duration.ofHours(1)), old);
        fake.armAsModel(USER, "session_follow_up", thursday17.plus(Duration.ofMinutes(120)), old);

        AtomicReference<String> moved = new AtomicReference<>();
        turn(v -> {
            qualityTime.cancelQualityTime(java.util.UUID.fromString(old));
            moved.set(book(FRIDAY_9));
        }, IDENTITY + "הזזתי לשישי ב-09:00 💪 אזכיר לך בבוקר.");
        says("תזיז לשישי בתשע");

        assertThat(lastSent()).isEqualTo(IDENTITY + "הזזתי ל*יום שישי 6.11 ב-09:00*, שעה וחצי עם איתמר.\n" + PLANNED_LINE
                + "\n" + WEEK);
        assertThat(fake.scheduledTransitionCalls("DELETE")).singleElement()
                .satisfies(c -> assertThat(c.path()).isEqualTo("/api/v1/worker/scheduled-transitions"));
        assertThat(fake.pendingTimers()).extracting(FakeServers.Timer::referenceId).containsOnly(moved.get());
        assertThat(fake.pendingTimers()).extracting(FakeServers.Timer::transitionKey)
                .containsExactlyInAnyOrder("session_reminder_1h", "session_follow_up");
    }

    // ---- "מתי התזכורת?" and the weekly plan ---------------------------------------------------------------------------

    @Test
    @DisplayName("'מתי התזכורת?': the times the platform holds (not the policy's); platform down - no time, said honestly")
    void theReminderQuestionReadsThePlatform() throws Exception {
        String session = book(FRIDAY_9);
        // armed at 07:30 (not the policy's 08:00): the answer tells the real time
        fake.armAsModel(USER, "session_reminder_1h", FRIDAY_9.minus(Duration.ofMinutes(90)), session);
        says("מתי התזכורת?");
        assertThat(lastSent()).isEqualTo(IDENTITY + "אזכיר לך *ביום שישי 6.11 ב-07:30*, שעה לפני המפגש עם איתמר.");
        assertThat(fake.turns()).isEmpty();

        fake.onScheduledTransitions(c -> new FakeServers.Reply(503, "{}"));
        says("מתי התזכורת?");
        assertThat(lastSent()).isEqualTo(IDENTITY + "כרגע אני לא מצליח לבדוק את התזכורות שלך.\nאפשר לשאול שוב עוד כמה דקות.");
    }

    @Test
    @DisplayName("weekly_plan_context: upcoming_reminders and reminder_reply come from the platform's pending timers")
    void theWeeklyPlanShowsWhatThePlatformHolds() throws Exception {
        String session = book(FRIDAY_9);
        fake.armAsModel(USER, "session_follow_up", FRIDAY_9.plus(Duration.ofMinutes(120)), session);

        JsonNode plan = weeklyPlan();
        assertThat(plan.path("upcoming_reminders").path("2026-11-06 09:00").asText()).isEqualTo("שאלה איך היה ב-11:00");
        assertThat(plan.path("ready_replies").path("reminder_reply")).hasSize(2);
        assertThat(plan.path("ready_replies").path("reminder_reply").get(0).asText())
                .isEqualTo("למפגש עם איתמר לא קבועה כרגע תזכורת.");
        assertThat(fake.scheduledTransitionCalls("GET")).hasSize(1);

        fake.onScheduledTransitions(c -> new FakeServers.Reply(503, "{}"));
        plan = weeklyPlan();
        assertThat(plan.path("upcoming_reminders").path("2026-11-06 09:00").asText()).isEqualTo("לא ניתן לבדוק כרגע");
        assertThat(plan.path("ready_replies").path("reminder_reply").get(0).asText())
                .isEqualTo("כרגע אני לא מצליח לבדוק את התזכורות שלך.");
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** Books Friday 09:00 inside the turn; {@code alsoInTurn} gets the new session id (e.g. to play the model arming). */
    AtomicReference<String> bookFriday9(Consumer<String> alsoInTurn) {
        AtomicReference<String> id = new AtomicReference<>();
        turn(v -> {
            id.set(book(FRIDAY_9));
            if (alsoInTurn != null) {
                alsoInTurn.accept(id.get());
            }
        }, MODEL_COPY);
        return id;
    }

    String book(Instant start) {
        ScheduleQualityTimeResult r = qualityTime.scheduleQualityTime(father.getId(), itamar.getId(), start, Duration.ofMinutes(90));
        return r.qualityTimeId().toString();
    }

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
        byte[] raw = Webhooks.text(PHONE, "wamid.at" + (++wamid), text).getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andExpect(status().isOk());
    }

    String lastSent() throws Exception {
        List<FakeServers.Call> sends = fake.metaSends();
        JsonNode sent = json.readTree(sends.get(sends.size() - 1).body());
        return sent.path("text").path("body").asText();
    }

    JsonNode weeklyPlan() throws Exception {
        return json.readTree(mvc.perform(post("/api/context/weekly_plan_context").header("X-API-Key", TOOL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"config\":{\"phone\":\"" + PHONE + "\"}}"))
                .andReturn().getResponse().getContentAsByteArray()).path("data");
    }
}
