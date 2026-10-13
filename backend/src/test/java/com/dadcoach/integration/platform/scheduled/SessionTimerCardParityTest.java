package com.dadcoach.integration.platform.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-049 parity (Unified Workflow Phase 5.3 slice D): for the same data, the session timers send exactly what they sent
 * before - the same decision (a message or nothing) and the same message, with its buttons or its template.
 *
 * <p>"Before" is the AI turn with a scripted model that follows its state's rules on the data (it suppresses where a
 * rule says so, otherwise it writes either the ready line or its own words), then the scheduled callback. "After" is the
 * platform's tool-only turn - {@code dc_session_timer_card}'s {@code message.text}, or nothing - then the same
 * callback; and, for the hour before, the guard in front of the unchanged AI turn.</p>
 */
class SessionTimerCardParityTest extends AbstractIntegrationTest {

    static final String MORNING = "SESSION_MORNING_REMINDER";
    static final String HOUR = "SESSION_REMINDER_1H";
    static final String FOLLOW_UP = "SESSION_FOLLOW_UP";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QualityTimeRepository sessions;
    @Autowired ScheduledReplies replies;
    @Autowired WorkflowPlatformProperties properties;

    private Father father;
    private Child noa;
    private Child yuval;
    private int triggers;
    private int fathers;

    @AfterEach
    void guardOff() {
        properties.setSessionTimersGuard(false);
    }

    // ------------------------------------------------------------------------------------------------ fixtures

    private void newFather(boolean windowOpen) {
        father = data.activeFather("+1999555" + String.format("%04d", 7000 + (++fathers)));
        noa = data.child(father, "נועה", 6);
        yuval = data.child(father, "יובל", 8);
        data.endpoint(father, windowOpen);
    }

    private QualityTime session(Child child, Duration startFromNow, int minutes) {
        Instant start = clock.instant().plus(startFromNow);
        return sessions.saveAndFlush(new QualityTime(father, child, start, start.plus(Duration.ofMinutes(minutes))));
    }

    private void status(QualityTime qt, String status) {
        jdbc.update("UPDATE quality_time SET status = ? WHERE id = ?", status, qt.getId());
    }

    /** One data situation of a father's sessions, built on a fresh father. */
    record Scenario(String name, Consumer<SessionTimerCardParityTest> build) {
    }

    static List<Scenario> scenarios() {
        List<Scenario> all = new ArrayList<>();
        all.add(new Scenario("no session", t -> { }));
        all.add(new Scenario("one later today", t -> t.session(t.noa, Duration.ofHours(3), 60)));
        all.add(new Scenario("two later today, two children", t -> {
            t.session(t.noa, Duration.ofHours(3), 60);
            t.session(t.yuval, Duration.ofHours(5), 60);
        }));
        all.add(new Scenario("later today, cancelled", t -> t.status(t.session(t.noa, Duration.ofHours(3), 60), "CANCELLED")));
        all.add(new Scenario("tomorrow only", t -> t.session(t.noa, Duration.ofDays(1), 60)));
        all.add(new Scenario("in progress", t -> t.session(t.noa, Duration.ofMinutes(-30), 60)));
        all.add(new Scenario("ended 40 min ago", t -> t.session(t.noa, Duration.ofMinutes(-100), 60)));
        all.add(new Scenario("ended 2 h ago", t -> t.session(t.noa, Duration.ofMinutes(-180), 60)));
        all.add(new Scenario("ended 2 h 59 min ago", t -> t.session(t.noa, Duration.ofMinutes(-239), 60)));
        all.add(new Scenario("ended 3 h 01 min ago", t -> t.session(t.noa, Duration.ofMinutes(-241), 60)));
        all.add(new Scenario("ended 4 h ago (a late timer)", t -> t.session(t.noa, Duration.ofMinutes(-300), 60)));
        all.add(new Scenario("ended 13 h ago", t -> t.session(t.noa, Duration.ofHours(-14), 60)));
        all.add(new Scenario("ended 40 min ago, already completed", t ->
                t.status(t.session(t.noa, Duration.ofMinutes(-100), 60), "COMPLETED")));
        all.add(new Scenario("back to back: ended 90 and 30 min ago", t -> {
            t.session(t.noa, Duration.ofMinutes(-150), 60);
            t.session(t.yuval, Duration.ofMinutes(-60), 30);
        }));
        all.add(new Scenario("ended 40 min ago and one later today", t -> {
            t.session(t.noa, Duration.ofMinutes(-100), 60);
            t.session(t.yuval, Duration.ofHours(4), 60);
        }));
        return all;
    }

    // ------------------------------------------------------------------------------------------------ the two paths

    /**
     * What one scheduled turn hands the platform's Send API (D-053: the only sender): nothing, or the content's type, text
     * and buttons, and the template the platform's mapping would use outside the window (the message type and its values).
     */
    record Sent(String type, String text, List<String> buttons, String template) {
        static final Sent NOTHING = new Sent("NOTHING", null, List.of(), null);
    }

    private Sent callback(String state, String content) throws Exception {
        int before = fake.sentMessages().size();
        String trigger = "parity-" + nextTrigger();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("triggerId", trigger);
        body.put("workflowInstanceId", "w1");
        body.put("userId", "whatsapp:" + father.getPhone());
        body.put("channel", "whatsapp");
        body.put("targetStateKey", state);
        body.put("responseContent", content);
        MvcResult r = mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + trigger).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn();
        assertThat(r.getResponse().getStatus()).as(state + " callback").isEqualTo(200);
        if (fake.sentMessages().size() == before) {
            return Sent.NOTHING;
        }
        assertThat(fake.sentMessages()).as("one send per callback").hasSize(before + 1);
        JsonNode sent = fake.sentMessages().get(before);
        List<String> buttons = new ArrayList<>();
        sent.path("content").path("buttons").forEach(b -> buttons.add(b.path("id").asText()));
        return new Sent(sent.path("content").path("type").asText(), sent.path("content").path("text").asText(), buttons,
                sent.path("templateData").toString());
    }

    private synchronized int nextTrigger() {
        return ++triggers;
    }

    JsonNode card(String timer) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-" + (++triggers));
        body.put("idempotency_key", "tool-only:" + triggers);
        body.put("user_id", father == null ? "+19995559999" : father.getPhone());
        body.put("execution_context", Map.of("correlationId", "tool-only:" + triggers));
        body.put("parameters", Map.of("timer", timer, "firedAt", "", "referenceId", ""));
        // read-only: no X-Idempotency-Key header is needed
        MvcResult r = mvc.perform(post("/api/tools/dc_session_timer_card").header("X-API-Key", TOOL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = json.readTree(r.getResponse().getContentAsByteArray());
        assertThat(response.path("success").asBoolean()).as(response.toString()).isTrue();
        return response.path("data");
    }

    /** After: the platform's tool-only turn - the tool's message is the turn's reply, a null message sends nothing. */
    private Sent toolOnlyTurn(String state) throws Exception {
        JsonNode data = card(state);
        if (data.path("message").isNull() || data.path("message").isMissingNode()) {
            assertThat(data.path("suppressReason").asText()).isNotBlank();
            return Sent.NOTHING;
        }
        return callback(state, data.path("message").path("text").asText());
    }

    /** Before: the AI turn - a model that keeps its state's rules, then the callback. */
    private Sent aiTurn(String state, boolean ruleSaysSuppress, String modelText) throws Exception {
        return ruleSaysSuppress ? Sent.NOTHING : callback(state, modelText);
    }

    /** What the state's quiet rule decides from the data alone (fresh conversation: nothing said yet today). */
    private boolean ruleSaysSuppress(String state) {
        Instant now = clock.instant();
        List<QualityTime> scheduled = sessions.findByFatherIdAndStatus(father.getId(),
                com.dadcoach.qualitytime.QualityTimeStatus.SCHEDULED);
        return switch (state) {
            // quiet-without-session-today: sessions_today is "none"
            case MORNING -> scheduled.stream().noneMatch(qt -> qt.getScheduledEnd().isAfter(now)
                    && qt.getScheduledStart().atZone(java.time.ZoneId.of("Asia/Jerusalem")).toLocalDate()
                    .equals(now.atZone(java.time.ZoneId.of("Asia/Jerusalem")).toLocalDate()));
            // ask-only-when-awaiting: no session that ended N min ago with N up to about 180 (the latest one is asked about)
            case FOLLOW_UP -> scheduled.stream().filter(qt -> !qt.getScheduledEnd().isAfter(now))
                    .map(QualityTime::getScheduledEnd).max(Instant::compareTo)
                    .map(end -> end.isBefore(now.minus(Duration.ofMinutes(180)))).orElse(true);
            default -> throw new IllegalArgumentException(state);
        };
    }

    /** Forget what the first path's callback noted, so the second path starts from the same data. */
    private void resetNotes() {
        jdbc.update("UPDATE quality_time SET mentioned_on = NULL, morning_reminded_on = NULL, follow_up_asked_at = NULL "
                + "WHERE father_id = ?", father.getId());
        jdbc.update("UPDATE father SET goal_asked_on = NULL WHERE id = ?", father.getId());
    }

    private String ready(String state) {
        return replies.plan(father, state).map(ScheduledReplies.Planned::text).orElse(null);
    }

    // ------------------------------------------------------------------------------------------------ tests

    @Test
    @DisplayName("morning and follow-up: same decision and same message (text, buttons, template) as the AI turn, over every data situation")
    void toolOnlyEqualsTheAiTurn() throws Exception {
        int compared = 0;
        int sentCount = 0;
        for (boolean windowOpen : new boolean[] {true, false}) {
            for (Scenario scenario : scenarios()) {
                for (String state : List.of(MORNING, FOLLOW_UP)) {
                    newFather(windowOpen);
                    scenario.build().accept(this);
                    String readyLine = ready(state);
                    boolean suppress = ruleSaysSuppress(state);
                    // the model's text: the ready line (with and without the identity line) or its own words
                    List<String> modelTexts = new ArrayList<>();
                    modelTexts.add(readyLine == null ? "בוקר טוב! תזכורת קטנה 🙂" : readyLine);
                    modelTexts.add(ScheduledResponseController.IDENTITY + (readyLine == null ? "נו, איך היה?" : readyLine));
                    modelTexts.add("היי! רק רציתי להזכיר לך משהו חשוב לגבי היום 😊");
                    for (String modelText : modelTexts) {
                        String where = scenario.name() + " / " + state + " / window " + windowOpen + " / model: " + modelText;
                        resetNotes();
                        Sent before = aiTurn(state, suppress, modelText);
                        resetNotes();
                        Sent after = toolOnlyTurn(state);
                        assertThat(after).as(where).isEqualTo(before);
                        compared++;
                        if (before != Sent.NOTHING) {
                            sentCount++;
                        }
                    }
                }
            }
        }
        assertThat(compared).isEqualTo(2 * scenarios().size() * 2 * 3);
        assertThat(sentCount).as("the grid sends in some cells and not in others").isBetween(1, compared - 1);
    }

    @Test
    @DisplayName("two sessions on one day wake the morning twice (both 08:00): one reminder, as when the model saw the first")
    void secondMorningOfTheDaySendsNothing() throws Exception {
        newFather(true);
        clock.set(Instant.parse("2026-11-03T06:00:00Z")); // 08:00 in Israel
        session(noa, Duration.ofHours(7), 60);
        session(yuval, Duration.ofHours(9), 60);

        Sent first = toolOnlyTurn(MORNING);
        assertThat(first.text()).contains("נועה").contains("יובל");
        clock.advance(Duration.ofMinutes(1)); // the next poll: one trigger per conversation per poll
        JsonNode second = card(MORNING);
        assertThat(second.path("message").isNull()).isTrue();
        assertThat(second.path("suppressReason").asText()).isEqualTo(SessionTimerCard.ALREADY_REMINDED_TODAY);

        // before: the model saw the first reminder in the conversation and kept quiet (quiet-without-session-today)
        // the record decides it: without it the second morning would go again
        jdbc.update("UPDATE quality_time SET morning_reminded_on = NULL WHERE father_id = ?", father.getId());
        assertThat(card(MORNING).path("message").path("text").asText()).isNotBlank();
    }

    @Test
    @DisplayName("D-058: two mornings of one day pass the card before either is sent, their callbacks race: one reminder")
    void racingMorningsSendOne() throws Exception {
        newFather(true);
        clock.set(Instant.parse("2026-11-03T06:00:00Z")); // 08:00 in Israel
        session(noa, Duration.ofHours(7), 60);
        session(yuval, Duration.ofHours(9), 60);
        // both tool-only turns built their card before either callback (the platform released the conversation)
        String text = card(MORNING).path("message").path("text").asText();
        assertThat(card(MORNING).path("message").path("text").asText()).isEqualTo(text).isNotBlank();

        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Sent>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return callback(MORNING, text);
                }));
            }
            for (java.util.concurrent.Future<Sent> r : results) {
                r.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(fake.sendRequests("SCHEDULED")).as("one morning reminder").hasSize(1);
        assertThat(jdbc.queryForList("SELECT failure_reason FROM scheduled_response_delivery WHERE father_id = ? "
                + "AND status = 'FAILED'", String.class, father.getId())).containsExactly(SessionTimerCard.ALREADY_REMINDED_TODAY);
        assertThat(jdbc.queryForList("SELECT morning_reminded_on FROM quality_time WHERE father_id = ?", java.sql.Date.class,
                father.getId())).extracting(java.sql.Date::toLocalDate).containsOnly(java.time.LocalDate.parse("2026-11-03"));
    }

    @Autowired SessionTimerCard timerCards;

    @Test
    @DisplayName("D-058: the claim is atomic - of four concurrent claims of the same sessions exactly one wins, every round")
    void claimIsAtomic() throws Exception {
        newFather(true);
        clock.set(Instant.parse("2026-11-03T06:00:00Z"));
        List<java.util.UUID> ids = List.of(session(noa, Duration.ofHours(7), 60).getId(),
                session(yuval, Duration.ofHours(9), 60).getId());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 20; round++) {
                jdbc.update("UPDATE quality_time SET morning_reminded_on = NULL WHERE father_id = ?", father.getId());
                java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(4);
                List<java.util.concurrent.Future<Boolean>> claims = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    // alternate the order the sessions are named in (the lock order is fixed by id)
                    List<java.util.UUID> named = i % 2 == 0 ? ids : List.of(ids.get(1), ids.get(0));
                    claims.add(pool.submit(() -> {
                        start.await();
                        return timerCards.claimMorning(father, named).isPresent();
                    }));
                }
                int won = 0;
                for (java.util.concurrent.Future<Boolean> c : claims) {
                    won += c.get(30, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0;
                }
                assertThat(won).as("round " + round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("D-058: a morning the platform refused (nothing created) gives its claim back: the next morning goes")
    void refusedMorningGivesTheClaimBack() throws Exception {
        newFather(true);
        clock.set(Instant.parse("2026-11-03T06:00:00Z"));
        session(noa, Duration.ofHours(7), 60);
        String text = card(MORNING).path("message").path("text").asText();
        fake.onPlatformSend(c -> new com.dadcoach.support.FakeServers.Reply(422,
                "{\"error\":\"messaging.param_invalid\",\"field\":\"content\"}"));
        callback(MORNING, text);
        assertThat(jdbc.queryForObject("SELECT morning_reminded_on FROM quality_time WHERE father_id = ?", java.sql.Date.class,
                father.getId())).as("claim given back").isNull();

        fake.onPlatformSend(fake::accept);
        clock.advance(Duration.ofMinutes(1));
        assertThat(card(MORNING).path("message").path("text").asText()).isEqualTo(text);
        int before = fake.sentMessages().size();
        callback(MORNING, text);
        assertThat(fake.sentMessages()).hasSize(before + 1);
        assertThat(jdbc.queryForObject("SELECT morning_reminded_on FROM quality_time WHERE father_id = ?", java.sql.Date.class,
                father.getId())).isNotNull();
    }

    @Test
    @DisplayName("a session added after the morning reminder is not 'already reminded': a later morning names them all again")
    void aNewSessionIsNotAlreadyReminded() throws Exception {
        newFather(true);
        clock.set(Instant.parse("2026-11-03T06:00:00Z"));
        session(noa, Duration.ofHours(7), 60);
        toolOnlyTurn(MORNING);
        session(yuval, Duration.ofHours(9), 60);
        JsonNode again = card(MORNING);
        assertThat(again.path("message").path("text").asText()).contains("נועה").contains("יובל");
        // the reminder of yesterday is not today's
        newFather(true);
        session(noa, Duration.ofHours(3), 60);
        jdbc.update("UPDATE quality_time SET morning_reminded_on = DATE '2026-11-02' WHERE father_id = ?", father.getId());
        assertThat(card(MORNING).path("message").path("text").asText()).isNotBlank();
    }

    @Test
    @DisplayName("one session wakes the follow-up twice: asked once within three hours, as when the model saw its question")
    void followUpAskedOnce() throws Exception {
        newFather(true);
        session(noa, Duration.ofMinutes(-150), 60);   // ended 90 min ago - its (late) follow-up fires now
        session(yuval, Duration.ofMinutes(-60), 30);  // ended 30 min ago - the latest, the one asked about

        Sent first = toolOnlyTurn(FOLLOW_UP);
        assertThat(first.text()).contains("יובל");
        clock.advance(Duration.ofMinutes(1));
        JsonNode second = card(FOLLOW_UP);
        assertThat(second.path("message").isNull()).isTrue();
        assertThat(second.path("suppressReason").asText()).isEqualTo(SessionTimerCard.ALREADY_ASKED);

        // the rule's window is three hours: rescheduled to end later, the same session is asked about again after it
        jdbc.update("UPDATE quality_time SET follow_up_asked_at = ? WHERE father_id = ?",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofHours(3)).minusSeconds(60)), father.getId());
        assertThat(card(FOLLOW_UP).path("message").path("text").asText()).contains("יובל");
    }

    @Test
    @DisplayName("each suppress reason is named, and a late follow-up (ended over 3 h ago) is NOT_JUST_ENDED")
    void reasons() throws Exception {
        newFather(true);
        assertThat(card(MORNING).path("suppressReason").asText()).isEqualTo(SessionTimerCard.NO_VALID_SESSION);
        assertThat(card(FOLLOW_UP).path("suppressReason").asText()).isEqualTo(SessionTimerCard.NO_VALID_SESSION);
        assertThat(card(HOUR).path("suppressReason").asText()).isEqualTo(SessionTimerCard.NO_VALID_SESSION);
        session(noa, Duration.ofMinutes(-300), 60);
        JsonNode late = card(FOLLOW_UP);
        assertThat(late.path("suppressReason").asText()).isEqualTo(SessionTimerCard.NOT_JUST_ENDED);
        assertThat(late.path("message").isNull()).isTrue();
        // the callback alone would still have sent it (its plan looks back 12 hours): the model's rule kept it quiet
        assertThat(ready(FOLLOW_UP)).isNotNull();
    }

    // ------------------------------------------------------------------------------------------------ the hour before

    /**
     * The hour-before guard declines only where nothing went out before. Before = the model writes only for a session
     * starting in about 30-90 minutes (remind-only-imminent), and the callback, a moment or two hours later (an outbox
     * retry), sends only when its plan still has a session.
     */
    @Test
    @DisplayName("hour before: a decline only where nothing went out (guard at t, callback at t, t+30 s, t+2 h); flag off always proceeds")
    void hourBeforeGuardParity() throws Exception {
        int[] startsInMinutes = {-30, 10, 29, 31, 45, 60, 89, 91, 150, 179, 180, 181, 240, 600};
        Duration[] callbackDelays = {Duration.ZERO, Duration.ofSeconds(30), Duration.ofHours(2)};
        int declines = 0;
        int proceeds = 0;
        for (int minutes : startsInMinutes) {
            for (boolean cancelled : new boolean[] {false, true}) {
                for (Duration delay : callbackDelays) {
                    clock.set(TUESDAY_NOON_IL);
                    newFather(true);
                    QualityTime qt = session(noa, Duration.ofMinutes(minutes), 45);
                    if (cancelled) {
                        status(qt, "CANCELLED");
                    }
                    String where = "starts in " + minutes + " min, cancelled " + cancelled + ", callback +" + delay;

                    properties.setSessionTimersGuard(false);
                    JsonNode off = card(HOUR);
                    assertThat(off.path("proceed").asBoolean()).as(where + " (flag off)").isTrue();
                    assertThat(off.path("guard").asText()).isEqualTo("OFF");

                    properties.setSessionTimersGuard(true);
                    JsonNode guard = card(HOUR);
                    boolean proceed = guard.path("proceed").asBoolean();
                    // same instant: decline exactly when the callback's own plan is empty (its NO_VALID_SESSION)
                    assertThat(!proceed).as(where + " decline <=> callback skip at the same instant").isEqualTo(ready(HOUR) == null);

                    boolean modelWrites = !cancelled && minutes >= 30 && minutes <= 90;
                    clock.advance(delay);
                    boolean callbackSends = ready(HOUR) != null;
                    boolean sentBefore = modelWrites && callbackSends;
                    if (!proceed) {
                        assertThat(sentBefore).as(where + ": the guard declined a reminder that went out before").isFalse();
                        declines++;
                    } else {
                        proceeds++;
                    }
                }
            }
        }
        assertThat(declines).isPositive();
        assertThat(proceeds).isPositive();
    }

    @Test
    @DisplayName("hour before: when the guard proceeds the AI turn and its callback are unchanged - the wish is kept (D-15)")
    void hourBeforeKeepsTheWish() throws Exception {
        properties.setSessionTimersGuard(true);
        newFather(true);
        session(noa, Duration.ofMinutes(60), 45);
        assertThat(card(HOUR).path("proceed").asBoolean()).isTrue();
        Sent sent = callback(HOUR, "❤️ דאד קואץ׳:\nעוד שעה הזמן שלך ושל נועה 🙂\nבהצלחה עם הבישול!");
        assertThat(sent.text()).endsWith("\nבהצלחה עם הבישול!");
        assertThat(sent.buttons()).hasSize(1);
        // the hour before notes nothing of its own (only the mentions it always noted)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_time WHERE father_id = ? AND (morning_reminded_on IS NOT NULL "
                + "OR follow_up_asked_at IS NOT NULL)", Integer.class, father.getId())).isZero();
    }

    // ------------------------------------------------------------------------------------------------ the envelope

    @Test
    @DisplayName("the tool: read-only (no idempotency header), an unknown number or a bad timer, the state key never trusted")
    void envelope() throws Exception {
        father = null;
        JsonNode unknown = card(MORNING);
        assertThat(unknown.path("message").isNull()).isTrue();
        assertThat(unknown.path("suppressReason").asText()).isEqualTo("UNKNOWN_RECIPIENT");

        newFather(true);
        session(noa, Duration.ofHours(3), 60);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-x");
        body.put("idempotency_key", "tool-only:x");
        body.put("user_id", father.getPhone());
        body.put("execution_context", Map.of("currentStateKey", FOLLOW_UP)); // the timer parameter decides, not this
        body.put("parameters", Map.of("timer", MORNING));
        JsonNode response = json.readTree(mvc.perform(post("/api/tools/dc_session_timer_card").header("X-API-Key", TOOL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn().getResponse()
                .getContentAsByteArray());
        assertThat(response.path("data").path("message").path("text").asText()).isEqualTo(ready(MORNING));
        assertThat(response.path("data").path("timer").asText()).isEqualTo(MORNING);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency", Integer.class)).isZero();

        body.put("parameters", Map.of("timer", "ACTIVE_COACHING"));
        JsonNode bad = json.readTree(mvc.perform(post("/api/tools/dc_session_timer_card").header("X-API-Key", TOOL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn().getResponse()
                .getContentAsByteArray());
        assertThat(bad.path("success").asBoolean()).isFalse();
        assertThat(bad.path("error_code").asText()).isEqualTo("INVALID_PARAMETERS");
        assertThat(Objects.requireNonNull(bad.path("error_message").asText())).contains("timer");
    }
}
