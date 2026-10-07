package com.dadcoach.api.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.father.FatherStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The AI tools bound in dad-coach-3, through the real endpoint (playbook §11): actor only from user_id, idempotency
 * (reserve → execute once → replay; another payload → 409), business rejections as success:false with nothing
 * changed, WhatsApp onboarding (F1), booking without Google Calendar (D-007) and reporting time already spent.
 * The clock is Tuesday 2026-11-03 12:00 in Israel; the week is Sunday 11-01 .. Saturday 11-07.
 */
class ToolsTest extends AbstractIntegrationTest {

    static final ZoneId IL = ZoneId.of("Asia/Jerusalem");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private int calls;

    record Call(int status, JsonNode body) {
        boolean success() { return body.path("success").asBoolean(); }
        JsonNode data() { return body.path("data"); }
        String error() { return body.path("error_code").asText(); }
    }

    Call tool(String key, String userId, Map<String, Object> params, String idempotencyKey) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-" + (++calls));
        body.put("idempotency_key", idempotencyKey);
        body.put("user_id", userId);
        body.put("execution_context", Map.of("currentStateKey", "ACTIVE_COACHING"));
        body.put("parameters", params);
        MvcResult r = mvc.perform(post("/api/tools/" + key).header("X-API-Key", TOOL_KEY)
                .header("X-Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn();
        return new Call(r.getResponse().getStatus(), json.readTree(r.getResponse().getContentAsByteArray()));
    }

    Call tool(String key, String userId, Map<String, Object> params) throws Exception {
        return tool(key, userId, params, UUID.randomUUID().toString());
    }

    static String at(String date, String time) {
        return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(IL).toOffsetDateTime().toString();
    }

    private Father fatherWithChild(String phone) {
        Father f = data.activeFather(phone);
        data.child(f, "נועה", 6);
        return f;
    }

    private long childId(Father f) {
        return jdbc.queryForObject("SELECT id FROM child WHERE father_id = ?", Long.class, f.getId());
    }

    // ── actor & envelope ────────────────────────────────────────────────────

    @Test
    void theActorIsOnlyTheWhatsAppNumberNeverANumericId() throws Exception {
        Father f = fatherWithChild("+19995550200");
        assertThat(tool("get_activity_ideas", String.valueOf(f.getId()), Map.of()).status()).isEqualTo(400);
        assertThat(tool("get_activity_ideas", "not-a-phone", Map.of()).status()).isEqualTo(400);
        assertThat(tool("get_activity_ideas", f.getPhone(), Map.of()).success()).isTrue();
        assertThat(tool("get_activity_ideas", "whatsapp:" + f.getPhone(), Map.of()).success()).isTrue();
    }

    @Test
    void aDeletedFatherIsRefusedAndAnUnknownOneMustSaveHisProfileFirst() throws Exception {
        Father deleted = data.father("+19995550201", "x", FatherStatus.DELETED);
        assertThat(tool("add_child", deleted.getPhone(), Map.of("name", "a", "age", 3)).status()).isEqualTo(403);
        Call unknown = tool("add_child", "+19995550202", Map.of("name", "a", "age", 3));
        assertThat(unknown.success()).isFalse();
        assertThat(unknown.error()).isEqualTo("FATHER_NOT_FOUND");
    }

    @Test
    void anUnknownToolIs404() throws Exception {
        assertThat(tool("show_progress", "+19995550203", Map.of()).status()).isEqualTo(404);
    }

    // ── onboarding on WhatsApp (D-002, F1) ──────────────────────────────────

    @Test
    void saveUserProfileCreatesTheFatherWithHisEndpointAndTheFirstChildActivatesHim() throws Exception {
        String phone = "+19995550210";
        Call saved = tool("save_user_profile", phone, Map.of("displayName", "רועי", "timezone", "Asia/Jerusalem", "locale", ""));
        assertThat(saved.success()).isTrue();
        assertThat(saved.data().path("displayName").asText()).isEqualTo("רועי");
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE phone = ?", String.class, phone)).isEqualTo("ONBOARDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_endpoints WHERE channel_identity = ? "
                + "AND channel = 'WHATSAPP' AND is_primary AND session_closes_at IS NOT NULL", Integer.class, phone)).isEqualTo(1);

        Call child = tool("add_child", phone, Map.of("name", "מאיה", "age", 4, "gender", "girl", "interests", java.util.List.of("ציור")));
        assertThat(child.success()).isTrue();
        assertThat(child.data().path("childCount").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE phone = ?", String.class, phone)).isEqualTo("ACTIVE");

        Call again = tool("add_child", phone, Map.of("name", "מאיה", "age", 4));
        assertThat(again.error()).isEqualTo("DUPLICATE_CHILD");
        assertThat(tool("save_user_profile", phone, Map.of("displayName", "רועי", "timezone", "Mars/Olympus")).error())
                .isEqualTo("VALIDATION_ERROR");
    }

    // ── idempotency (playbook §11.2) ────────────────────────────────────────

    @Test
    void theSameKeyAndPayloadReplaysAnotherPayloadIs409() throws Exception {
        Father f = data.activeFather("+19995550220");
        Call first = tool("add_child", f.getPhone(), Map.of("name", "עומר", "age", 7), "key-1");
        Call replay = tool("add_child", f.getPhone(), Map.of("name", "עומר", "age", 7), "key-1");
        assertThat(first.success()).isTrue();
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM child WHERE father_id = ?", Integer.class, f.getId())).isEqualTo(1);

        Call other = tool("add_child", f.getPhone(), Map.of("name", "שירה", "age", 2), "key-1");
        assertThat(other.status()).isEqualTo(409);
        assertThat(other.body().path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        // the same key from ANOTHER father is never answered with this father's stored response
        Father g = data.activeFather("+19995550221");
        assertThat(tool("add_child", g.getPhone(), Map.of("name", "עומר", "age", 7), "key-1").status()).isEqualTo(409);
    }

    @Test
    void aSideEffectingToolNeedsTheIdempotencyHeaderToMatchTheBody() throws Exception {
        Father f = data.activeFather("+19995550222");
        Map<String, Object> body = Map.of("execution_id", "e", "idempotency_key", "k-body", "user_id", f.getPhone(),
                "parameters", Map.of("name", "a", "age", 3));
        int status = mvc.perform(post("/api/tools/add_child").header("X-API-Key", TOOL_KEY).header("X-Idempotency-Key", "k-other")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM child", Integer.class)).isZero();
    }

    // ── sessions without Google Calendar (D-007) ────────────────────────────

    @Test
    void aFatherWithoutACalendarBooksReschedulesCancelsAndCompletes() throws Exception {
        Father f = fatherWithChild("+19995550230");
        tool("set_weekly_goal", f.getPhone(), Map.of("target_hours", 2));

        Call booked = tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f),
                "start_time", at("2026-11-04", "17:30"), "duration_minutes", 60));
        assertThat(booked.success()).as(booked.body().toString()).isTrue();
        JsonNode d = booked.data();
        assertThat(d.path("quality_time_id").asText()).hasSize(36);
        assertThat(d.path("calendar_event_created").asBoolean()).isFalse();
        assertThat(d.path("child_name").asText()).isEqualTo("נועה");
        assertThat(d.path("local_date").asText()).isEqualTo("2026-11-04");
        assertThat(d.path("local_start").asText()).isEqualTo("17:30");
        assertThat(d.path("timezone").asText()).isEqualTo("Asia/Jerusalem");
        assertThat(d.path("timers").path("session_morning_reminder").asText()).isEqualTo("2026-11-04T06:00:00Z");
        assertThat(d.path("timers").path("session_reminder_1h").asText()).isEqualTo("2026-11-04T14:30:00Z");
        assertThat(d.path("timers").path("session_follow_up").asText()).isEqualTo("2026-11-04T17:00:00Z");
        assertThat(d.path("week_coverage").path("planned_minutes").asInt()).isEqualTo(60);
        String id = d.path("quality_time_id").asText();

        Call moved = tool("reschedule_quality_time", f.getPhone(), Map.of("quality_time_id", id, "new_start_time", at("2026-11-05", "18:00")));
        assertThat(moved.success()).as(moved.body().toString()).isTrue();
        String newId = moved.data().path("new_quality_time_id").asText();
        assertThat(moved.data().path("local_start").asText()).isEqualTo("18:00");
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?::uuid", String.class, id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT scheduled_end - scheduled_start FROM quality_time WHERE id = ?::uuid", String.class, newId))
                .isEqualTo("01:00:00"); // keeps its 60 minutes

        Call cancelled = tool("cancel_quality_time", f.getPhone(), Map.of("quality_time_id", newId));
        assertThat(cancelled.success()).isTrue();
        assertThat(cancelled.data().path("week_coverage").path("planned_minutes").asInt()).isZero();

        Call unknown = tool("cancel_quality_time", f.getPhone(), Map.of("quality_time_id", UUID.randomUUID().toString()));
        assertThat(unknown.error()).isEqualTo("NOT_FOUND");
        assertThat(unknown.body().path("error_message").asText()).contains("scheduled sessions are: none");
    }

    @Test
    void aRefusedRescheduleLeavesTheOriginalSessionAsItWas() throws Exception {
        Father f = fatherWithChild("+19995550231");
        String id = tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f),
                "start_time", at("2026-11-04", "17:30"), "duration_minutes", 30)).data().path("quality_time_id").asText();
        Call refused = tool("reschedule_quality_time", f.getPhone(), Map.of("quality_time_id", id,
                "new_start_time", at("2026-10-20", "17:00")));
        assertThat(refused.error()).isEqualTo("ONLY_THIS_WEEK");
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?::uuid", String.class, id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_time", Integer.class)).isEqualTo(1);
    }

    @Test
    void anotherFathersSessionOrChildIsUnknown() throws Exception {
        Father f = fatherWithChild("+19995550232");
        Father other = fatherWithChild("+19995550233");
        String id = tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f),
                "start_time", at("2026-11-04", "17:30"))).data().path("quality_time_id").asText();
        assertThat(tool("cancel_quality_time", other.getPhone(), Map.of("quality_time_id", id)).error()).isEqualTo("NOT_FOUND");
        assertThat(tool("schedule_quality_time", other.getPhone(), Map.of("child_id", childId(f),
                "start_time", at("2026-11-04", "18:30"))).error()).isEqualTo("NOT_FOUND");
    }

    @Test
    void availableSlotsWithoutACalendarAreFamilyTimeWindowsMinusHisSessions() throws Exception {
        Father f = fatherWithChild("+19995550234");
        tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f), "start_time", at("2026-11-04", "18:00"),
                "duration_minutes", 60));
        Call slots = tool("show_available_slots", f.getPhone(), Map.of("days_ahead", 5));
        assertThat(slots.success()).isTrue();
        assertThat(slots.data().path("calendar_connected").asBoolean()).isFalse();
        java.util.List<String> windows = new java.util.ArrayList<>();
        slots.data().path("slots").forEach(s -> windows.add(s.path("local_date").asText() + " "
                + s.path("local_start").asText() + "-" + s.path("local_end").asText()));
        assertThat(windows).containsExactly(
                "2026-11-03 17:00-20:00",           // Tuesday evening (it is noon now)
                "2026-11-04 17:00-18:00", "2026-11-04 19:00-20:00", // Wednesday around his session
                "2026-11-05 17:00-20:00",           // Thursday
                "2026-11-06 09:00-13:00",           // Friday
                "2026-11-07 09:00-12:00", "2026-11-07 16:00-19:00"); // Saturday
    }

    @Test
    void timeAlreadySpentThisWeekIsRecordedAndCompletedForCredit() throws Exception {
        Father f = fatherWithChild("+19995550235");
        tool("set_weekly_goal", f.getPhone(), Map.of("target_hours", 1));

        // "ביליתי שעה עם נועה אתמול" - Monday evening, never booked
        Call past = tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f),
                "start_time", at("2026-11-02", "18:00"), "duration_minutes", 60));
        assertThat(past.success()).as(past.body().toString()).isTrue();
        assertThat(past.data().path("timers").isEmpty()).isTrue();
        String id = past.data().path("quality_time_id").asText();

        Call done = tool("complete_quality_time", f.getPhone(), Map.of("quality_time_id", id, "notes", "בנינו מגדל"));
        assertThat(done.success()).as(done.body().toString()).isTrue();
        assertThat(done.data().path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.data().path("week_coverage").path("completed_minutes").asInt()).isEqualTo(60);
        assertThat(done.data().path("week_coverage").path("is_covered").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT actual_minutes FROM weekly_goal WHERE father_id = ?", Integer.class, f.getId()))
                .isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT total_quality_times_completed FROM father WHERE id = ?", Integer.class, f.getId()))
                .isEqualTo(1);
    }

    @Test
    void aSessionBeforeThisWeekIsRefused() throws Exception {
        Father f = fatherWithChild("+19995550236");
        Call before = tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f),
                "start_time", at("2026-10-31", "18:00"))); // last Saturday
        assertThat(before.success()).isFalse();
        assertThat(before.error()).isEqualTo("ONLY_THIS_WEEK");
        assertThat(before.body().path("error_message").asText()).contains("only from this week");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_time", Integer.class)).isZero();
    }

    @Test
    void thePastSessionShowsAsAwaitingConfirmationInTheWeeklyPlan() throws Exception {
        Father f = fatherWithChild("+19995550237");
        tool("schedule_quality_time", f.getPhone(), Map.of("child_id", childId(f), "start_time", at("2026-11-02", "18:00"),
                "duration_minutes", 45));
        String plan = mvc.perform(post("/api/context/weekly_plan_context").header("X-API-Key", TOOL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"config\":{\"phone\":\"" + f.getPhone() + "\"}}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(plan).contains("AWAITING_CONFIRMATION");
    }

    // ── weekly goal ─────────────────────────────────────────────────────────

    @Test
    void theWeeklyGoalIsCreateOnceAndTheSameTargetIsIdempotent() throws Exception {
        Father f = fatherWithChild("+19995550240");
        Call set = tool("set_weekly_goal", f.getPhone(), Map.of("target_hours", 3));
        assertThat(set.data().path("week_start_date").asText()).isEqualTo("2026-11-01");
        assertThat(set.data().path("already_existed").asBoolean()).isFalse();
        assertThat(tool("set_weekly_goal", f.getPhone(), Map.of("target_hours", "3")).data().path("already_existed").asBoolean()).isTrue();
        assertThat(tool("set_weekly_goal", f.getPhone(), Map.of("target_hours", 4)).error()).isEqualTo("INVALID_STATE");
        assertThat(tool("set_weekly_goal", f.getPhone(), Map.of("target_hours", "")).error()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void activityIdeasAreReadOnlyAndForHisOwnChild() throws Exception {
        Father f = fatherWithChild("+19995550241");
        Call ideas = tool("get_activity_ideas", f.getPhone(), Map.of("child_id", childId(f), "activity_type", "indoor"));
        assertThat(ideas.data().path("ideas").size()).isBetween(1, 3);
        assertThat(ideas.data().path("child_age").asInt()).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency", Integer.class)).isZero();
    }
}
