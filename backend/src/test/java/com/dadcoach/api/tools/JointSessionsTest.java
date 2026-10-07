package com.dadcoach.api.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.web.father.HomeService;
import com.dadcoach.web.father.HomeView;
import com.dadcoach.web.father.ProgressService;
import com.dadcoach.web.father.SessionView;
import com.dadcoach.web.father.SessionsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * One session with several children (production 2026-10-07: "יום שישי שעה וחצי עם מטר ונעם" became two sessions,
 * 90+90 minutes of coverage and every reminder twice). A booking at exactly the slot of one of his scheduled
 * sessions joins it; time where sessions overlap (legacy pairs) counts once; every view names all the children.
 * The clock is Tuesday 2026-11-03 12:00 in Israel; the week is Sunday 11-01 .. Saturday 11-07.
 */
class JointSessionsTest extends AbstractIntegrationTest {

    static final ZoneId IL = ZoneId.of("Asia/Jerusalem");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FatherRepository fathers;
    @Autowired HomeService home;
    @Autowired SessionsService sessionsPage;
    @Autowired ProgressService progress;

    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    private Father father;
    private Child matar;
    private Child noam;

    record Call(int status, JsonNode body) {
        boolean success() { return body.path("success").asBoolean(); }
        JsonNode data() { return body.path("data"); }
    }

    Call tool(String key, Map<String, Object> params) throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-" + calls.incrementAndGet());
        body.put("idempotency_key", idempotencyKey);
        body.put("user_id", father.getPhone());
        body.put("execution_context", Map.of("currentStateKey", "ACTIVE_COACHING"));
        body.put("parameters", params);
        MvcResult r = mvc.perform(post("/api/tools/" + key).header("X-API-Key", TOOL_KEY)
                .header("X-Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn();
        Call call = new Call(r.getResponse().getStatus(), json.readTree(r.getResponse().getContentAsByteArray()));
        assertThat(call.success()).as(key + " " + call.body()).isTrue();
        return call;
    }

    JsonNode weeklyPlan() throws Exception {
        String body = mvc.perform(post("/api/context/weekly_plan_context").header("X-API-Key", TOOL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"config\":{\"phone\":\"" + father.getPhone() + "\"}}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    static String at(String date, String time) {
        return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(IL).toOffsetDateTime().toString();
    }

    private void twoDaughters(String phone) {
        father = data.activeFather(phone);
        matar = data.child(father, "מטר", 7);
        noam = data.child(father, "נעם", 5);
    }

    private Call book(Child child, String date, String time, int minutes) throws Exception {
        return tool("schedule_quality_time", Map.of("child_id", child.getId(), "start_time", at(date, time),
                "duration_minutes", minutes));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** A legacy row as production has them: one child, written straight to the table. */
    private UUID legacySession(Child child, String date, String start, String end, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO quality_time (id, father_id, child_id, scheduled_start, scheduled_end, status, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'SCHEDULED', ?::timestamptz, now())",
                id, father.getId(), child.getId(), at(date, start), at(date, end), createdAt);
        return id;
    }

    // ── booking ─────────────────────────────────────────────────────────────

    @Test
    void theSecondChildForTheSameSlotJoinsTheSessionInsteadOfASecondOne() throws Exception {
        twoDaughters("+19995550700");
        tool("set_weekly_goal", Map.of("target_hours", 2));

        Call first = book(matar, "2026-11-06", "09:00", 90);
        assertThat(first.data().has("joined_existing_session")).isFalse();
        assertThat(first.data().has("note")).isFalse();
        String id = first.data().path("quality_time_id").asText();

        Call second = book(noam, "2026-11-06", "09:00", 90);
        JsonNode d = second.data();
        assertThat(d.path("quality_time_id").asText()).isEqualTo(id);
        assertThat(d.path("joined_existing_session").asBoolean()).isTrue();
        assertThat(d.has("child_already_in_session")).isFalse();
        assertThat(d.path("child_name").asText()).isEqualTo("מטר ונעם");
        assertThat(d.path("child_names").toString()).isEqualTo("[\"מטר\",\"נעם\"]");
        assertThat(d.path("note").asText()).contains("added to the existing session");
        assertThat(d.path("timers")).isEqualTo(first.data().path("timers")); // re-arming for the same id = one set
        assertThat(d.path("week_coverage").path("planned_minutes").asInt()).isEqualTo(90);
        assertThat(d.path("week_coverage").path("uncovered_minutes").asInt()).isEqualTo(30);

        assertThat(count("SELECT count(*) FROM quality_time")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM quality_time_child WHERE quality_time_id = ?::uuid AND child_id = ?", id,
                noam.getId())).isEqualTo(1);

        // the same booking again - and the first child again - change nothing
        Call again = book(noam, "2026-11-06", "09:00", 90);
        assertThat(again.data().path("quality_time_id").asText()).isEqualTo(id);
        assertThat(again.data().path("child_already_in_session").asBoolean()).isTrue();
        assertThat(again.data().path("note").asText()).contains("already in this session");
        assertThat(book(matar, "2026-11-06", "09:00", 90).data().path("child_already_in_session").asBoolean()).isTrue();
        assertThat(count("SELECT count(*) FROM quality_time")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM quality_time_child")).isEqualTo(1);

        JsonNode plan = weeklyPlan();
        assertThat(plan.path("next_session").asText()).startsWith("UPCOMING | מטר ונעם | 90 min | FRIDAY 2026-11-06 09:00-10:30")
                .endsWith("id=" + id);

        // another length or time is another session
        assertThat(book(noam, "2026-11-06", "09:00", 60).data().path("quality_time_id").asText()).isNotEqualTo(id);
    }

    @Test
    void twoBookingsForTheSameSlotAtTheSameMomentStillEndInOneSession() throws Exception {
        twoDaughters("+19995550706");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> book(matar, "2026-11-06", "09:00", 90));
            var b = pool.submit(() -> book(noam, "2026-11-06", "09:00", 90));
            assertThat(a.get().data().path("quality_time_id").asText()).isEqualTo(b.get().data().path("quality_time_id").asText());
        } finally {
            pool.shutdown();
        }
        assertThat(count("SELECT count(*) FROM quality_time")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM quality_time_child")).isEqualTo(1);
    }

    @Test
    void aJointSessionIsCompletedOnceAndCreditedOnce() throws Exception {
        twoDaughters("+19995550701");
        tool("set_weekly_goal", Map.of("target_hours", 1));
        String id = book(matar, "2026-11-02", "18:00", 60).data().path("quality_time_id").asText();
        book(noam, "2026-11-02", "18:00", 60);

        Call done = tool("complete_quality_time", Map.of("quality_time_id", id, "notes", "בנינו מגדל"));
        assertThat(done.data().path("week_coverage").path("completed_minutes").asInt()).isEqualTo(60);
        assertThat(count("SELECT actual_minutes FROM weekly_goal WHERE father_id = ?", father.getId())).isEqualTo(60);
        assertThat(count("SELECT total_quality_times_completed FROM father WHERE id = ?", father.getId())).isEqualTo(1);

        // both daughters had their time: the "every child" achievement
        Father reloaded = fathers.findById(father.getId()).orElseThrow();
        assertThat(progress.progress(reloaded).achievements()).anyMatch(a -> a.key().equals("every-child") && a.earned());
    }

    @Test
    void reschedulingMovesEveryChildAndOntoAnotherSessionsExactSlotMergesThem() throws Exception {
        twoDaughters("+19995550702");
        Child itamar = data.child(father, "איתמר", 9);
        String joint = book(matar, "2026-11-06", "09:00", 90).data().path("quality_time_id").asText();
        book(noam, "2026-11-06", "09:00", 90);

        Call moved = tool("reschedule_quality_time", Map.of("quality_time_id", joint, "new_start_time", at("2026-11-05", "18:00")));
        String movedId = moved.data().path("new_quality_time_id").asText();
        assertThat(moved.data().path("child_name").asText()).isEqualTo("מטר ונעם");
        assertThat(moved.data().has("joined_existing_session")).isFalse();
        assertThat(count("SELECT count(*) FROM quality_time_child WHERE quality_time_id = ?::uuid", movedId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?::uuid", String.class, joint)).isEqualTo("CANCELLED");

        String his = book(itamar, "2026-11-04", "17:00", 90).data().path("quality_time_id").asText();
        Call merged = tool("reschedule_quality_time", Map.of("quality_time_id", movedId, "new_start_time", at("2026-11-04", "17:00")));
        assertThat(merged.data().path("new_quality_time_id").asText()).isEqualTo(his);
        assertThat(merged.data().path("joined_existing_session").asBoolean()).isTrue();
        assertThat(merged.data().path("child_name").asText()).isEqualTo("איתמר, מטר ונעם");
        assertThat(merged.data().path("note").asText()).contains("another session of his");
        assertThat(merged.data().path("week_coverage").path("planned_minutes").asInt()).isEqualTo(90);
        assertThat(count("SELECT count(*) FROM quality_time WHERE status = 'SCHEDULED'")).isEqualTo(1);
    }

    // ── legacy overlapping sessions ─────────────────────────────────────────

    @Test
    void overlappingLegacySessionsCountTheirWallClockTimeOnce() throws Exception {
        twoDaughters("+19995550703");
        tool("set_weekly_goal", Map.of("target_hours", 2));
        legacySession(matar, "2026-11-06", "09:00", "10:30", "2026-11-03T08:00:00Z");
        legacySession(noam, "2026-11-06", "09:00", "10:30", "2026-11-03T08:00:01Z");
        legacySession(noam, "2026-11-06", "10:00", "11:00", "2026-11-03T08:00:02Z"); // overlaps by 30 minutes
        legacySession(matar, "2026-11-05", "18:00", "18:30", "2026-11-03T08:00:03Z"); // its own time

        JsonNode coverage = weeklyPlan().path("coverage");
        assertThat(coverage.path("planned_minutes").asInt()).isEqualTo(150); // 09:00-11:00 + 30, not 90+90+60+30
        assertThat(coverage.path("uncovered_minutes").asInt()).isZero();
        assertThat(coverage.path("is_covered").asBoolean()).isTrue();
    }

    @Test
    void completingBothOfALegacyPairCreditsTheSlotOnce() throws Exception {
        twoDaughters("+19995550704");
        tool("set_weekly_goal", Map.of("target_hours", 2));
        UUID a = legacySession(matar, "2026-11-02", "18:00", "19:30", "2026-11-01T08:00:00Z");
        UUID b = legacySession(noam, "2026-11-02", "18:00", "19:30", "2026-11-01T08:00:01Z");
        UUID c = legacySession(noam, "2026-11-03", "09:00", "10:00", "2026-11-01T08:00:02Z");

        assertThat(weeklyPlan().path("coverage").path("awaiting_confirmation_minutes").asInt()).isEqualTo(150);
        tool("complete_quality_time", Map.of("quality_time_id", a.toString()));
        tool("complete_quality_time", Map.of("quality_time_id", b.toString()));
        assertThat(count("SELECT actual_minutes FROM weekly_goal WHERE father_id = ?", father.getId())).isEqualTo(90);
        Call third = tool("complete_quality_time", Map.of("quality_time_id", c.toString()));
        assertThat(third.data().path("week_coverage").path("completed_minutes").asInt()).isEqualTo(150);
        assertThat(count("SELECT total_quality_times_completed FROM father WHERE id = ?", father.getId())).isEqualTo(3);
    }

    // ── what he sees ────────────────────────────────────────────────────────

    @Test
    void theHomeAndTheSessionsPageNameBothChildren() throws Exception {
        twoDaughters("+19995550705");
        tool("set_weekly_goal", Map.of("target_hours", 2));
        book(matar, "2026-11-06", "09:00", 90);
        book(noam, "2026-11-06", "09:00", 90);

        Father reloaded = fathers.findById(father.getId()).orElseThrow();
        HomeView view = home.home(reloaded);
        assertThat(view.nextSession().childName()).isEqualTo("מטר ונעם");
        assertThat(view.nextSession().childIds()).containsExactly(matar.getId(), noam.getId());
        assertThat(view.coverage().plannedMinutes()).isEqualTo(90);
        List<SessionView> upcoming = sessionsPage.list(reloaded).upcoming();
        assertThat(upcoming).hasSize(1);
        assertThat(upcoming.get(0).childName()).isEqualTo("מטר ונעם");
    }
}
