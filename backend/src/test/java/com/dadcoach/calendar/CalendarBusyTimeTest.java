package com.dadcoach.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.support.FakeServers.Call;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.AvailableSlotFinder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-056: reading his Google Calendar works (the URL is encoded once - Google answered 400 to every read before) and
 * only time he is really in counts: free-slot suggestions and the conflict line of a booking skip all-day events,
 * events marked "free", declined invitations and Dad Coach's own events. A meeting never refuses a booking - the
 * confirmation tells him. A move refused by the date rules leaves the old session and its Google event as they were.
 */
class CalendarBusyTimeTest extends AbstractIntegrationTest {

    static final ZoneId IL = ZoneId.of("Asia/Jerusalem");
    // the pinned clock is Tuesday 2026-11-03 12:00 in Israel (UTC+2); weekday windows are 17:00-20:00
    static final Instant TUESDAY_17 = Instant.parse("2026-11-03T15:00:00Z");
    static final Instant WEDNESDAY_17 = Instant.parse("2026-11-04T15:00:00Z");

    @Autowired AvailableSlotFinder slotFinder;
    @Autowired QualityTimeService sessions;
    @Autowired FatherRepository fathers;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** His Google Calendar: answers events.list like Google (400 on a bad timeMin, only overlapping events). */
    private final List<Map<String, Object>> calendar = new CopyOnWriteArrayList<>();
    private final AtomicInteger created = new AtomicInteger();

    @BeforeEach
    void googleCalendar() {
        calendar.clear();
        fake.onGoogle(this::google);
    }

    private Reply google(Call c) {
        try {
            if (c.path().equals("/google/token")) {
                return Reply.json("{\"access_token\":\"fresh-access\",\"expires_in\":3600}");
            }
            if (c.method().equals("POST")) {
                JsonNode body = json.readTree(c.body());
                String id = "own-" + created.incrementAndGet();
                Map<String, Object> event = timed(id, OffsetDateTime.parse(body.path("start").path("dateTime").asText()).toInstant(),
                        OffsetDateTime.parse(body.path("end").path("dateTime").asText()).toInstant());
                event.put("description", body.path("description").asText());
                event.put("extendedProperties", json.convertValue(body.path("extendedProperties"), Map.class));
                calendar.add(event);
                return Reply.json("{\"id\":\"" + id + "\"}");
            }
            if (c.method().equals("DELETE")) {
                String id = c.path().substring(c.path().lastIndexOf('/') + 1);
                calendar.removeIf(e -> e.get("id").equals(id));
                return new Reply(204, "");
            }
            if (c.rejectedEventsQuery() != null) {
                return c.rejectedEventsQuery();
            }
            if (c.path().endsWith("/events")) {
                Instant from = OffsetDateTime.parse(c.param("timeMin")).toInstant();
                Instant to = OffsetDateTime.parse(c.param("timeMax")).toInstant();
                List<Map<String, Object>> items = new ArrayList<>();
                for (Map<String, Object> e : calendar) {
                    if (overlaps(e, from, to)) {
                        items.add(e);
                    }
                }
                return Reply.json(json.writeValueAsString(Map.of("items", items)));
            }
            return Reply.json("{}");
        } catch (Exception e) {
            return new Reply(500, "{}");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean overlaps(Map<String, Object> e, Instant from, Instant to) {
        Map<String, String> start = (Map<String, String>) e.get("start");
        if (start.containsKey("date")) {
            return true; // all-day: always in a day's listing here
        }
        Instant s = OffsetDateTime.parse(start.get("dateTime")).toInstant();
        Instant end = OffsetDateTime.parse(((Map<String, String>) e.get("end")).get("dateTime")).toInstant();
        return s.isBefore(to) && end.isAfter(from);
    }

    private static Map<String, Object> timed(String id, Instant start, Instant end) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("summary", "event " + id);
        e.put("start", Map.of("dateTime", start.atZone(IL).toOffsetDateTime().toString()));
        e.put("end", Map.of("dateTime", end.atZone(IL).toOffsetDateTime().toString()));
        return e;
    }

    /** Busy: a work meeting 18:00-19:00. Never busy: a birthday all day, a "free" block, an invitation he declined. */
    private void aBusyDay(Instant fivePm) {
        calendar.add(timed("meeting", fivePm.plus(Duration.ofHours(1)), fivePm.plus(Duration.ofHours(2))));
        String day = fivePm.atZone(IL).toLocalDate().toString();
        calendar.add(Map.of("id", "birthday", "summary", "יום הולדת",
                "start", Map.of("date", day), "end", Map.of("date", fivePm.atZone(IL).toLocalDate().plusDays(1).toString())));
        Map<String, Object> free = timed("free-block", fivePm, fivePm.plus(Duration.ofHours(3)));
        free.put("transparency", "transparent");
        calendar.add(free);
        Map<String, Object> declined = timed("declined", fivePm, fivePm.plus(Duration.ofHours(1)));
        declined.put("attendees", List.of(Map.of("email", "dad@example.com", "self", true, "responseStatus", "declined")));
        calendar.add(declined);
    }

    private Father connected(String phone) {
        Father f = data.activeFather(phone);
        f.setGoogleCalendarEnabled(true);
        f.setGoogleRefreshToken("refresh");
        f.setGoogleAccessToken("access");
        f.setGoogleTokenExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return fathers.saveAndFlush(f);
    }

    private long eventCreates() {
        return fake.googleEventCreates().size();
    }

    @Test
    @DisplayName("free slots go around a real meeting; all-day, 'free' and declined events take no time")
    void freeSlotsAvoidOnlyTimeHeIsIn() {
        Father f = connected("+19995550910");
        aBusyDay(TUESDAY_17);

        List<AvailableSlot> today = slotFinder.find(f, IL, 1);

        assertThat(today).extracting(AvailableSlot::startTime, AvailableSlot::endTime).containsExactly(
                org.assertj.core.groups.Tuple.tuple(TUESDAY_17, TUESDAY_17.plus(Duration.ofHours(1))),
                org.assertj.core.groups.Tuple.tuple(TUESDAY_17.plus(Duration.ofHours(2)), TUESDAY_17.plus(Duration.ofHours(3))));
        Call read = fake.calls("/google/calendar/").stream().filter(c -> c.method().equals("GET")).findFirst().orElseThrow();
        assertThat(read.param("timeMin")).as("decoded once, as Google reads it").matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d.*");
    }

    @Test
    @DisplayName("booking into a meeting is booked with its event, and the confirmation says he has something then")
    void aMeetingIsToldNotRefused() throws Exception {
        Father f = connected("+19995550911");
        Child noa = data.child(f, "נועה", 7);
        aBusyDay(WEDNESDAY_17);

        JsonNode booked = tool(f, "schedule_quality_time", Map.of("child_id", noa.getId(),
                "start_time", local(WEDNESDAY_17.plus(Duration.ofMinutes(30))), "duration_minutes", 60));

        assertThat(booked.path("success").asBoolean()).as(booked.toString()).isTrue();
        assertThat(booked.path("data").path("calendar_conflict").asBoolean()).isTrue();
        assertThat(booked.path("data").path("calendar_event_created").asBoolean()).isTrue();
        assertThat(booked.path("data").path("reply").asText().split("\n")[1])
                .isEqualTo("שים לב: ביומן שלך יש כבר משהו בשעה הזאת. רוצה שנמצא שעה אחרת?");
    }

    @Test
    @DisplayName("two children booked at once into a meeting: the second child's confirmation keeps the line")
    void twoChildrenAtOnceKeepTheLine() throws Exception {
        Father f = connected("+19995550915");
        Child noa = data.child(f, "נועה", 7);
        Child itai = data.child(f, "איתי", 9);
        aBusyDay(WEDNESDAY_17);
        String at = local(WEDNESDAY_17.plus(Duration.ofHours(1)));

        tool(f, "schedule_quality_time", Map.of("child_id", noa.getId(), "start_time", at, "duration_minutes", 60));
        JsonNode second = tool(f, "schedule_quality_time", Map.of("child_id", itai.getId(), "start_time", at, "duration_minutes", 60));

        assertThat(second.path("success").asBoolean()).as(second.toString()).isTrue();
        assertThat(second.path("data").path("reply").asText()).contains("איתי")
                .contains("שים לב: ביומן שלך יש כבר משהו בשעה הזאת.");
    }

    @Test
    @DisplayName("over an all-day, a 'free' and a declined event: booked with no conflict line")
    void eventsThatTakeNoTimeAreNoConflict() {
        Father f = connected("+19995550914");
        Child noa = data.child(f, "נועה", 7);
        aBusyDay(WEDNESDAY_17);

        ScheduleQualityTimeResult booked = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_17,
                Duration.ofHours(1));

        assertThat(booked.calendarConflict()).isFalse();
        assertThat(booked.calendarEventId()).isEqualTo("own-" + created.get());
        assertThat(booked.calendarError()).isNull();
    }

    @Test
    @DisplayName("his own Dad Coach events are no conflict: a child joining, a move onto part of its time, a leftover event")
    void hisOwnEventsAreNotConflicts() throws Exception {
        Father f = connected("+19995550912");
        Child noa = data.child(f, "נועה", 7);
        Child itai = data.child(f, "איתי", 9);
        ScheduleQualityTimeResult booked = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_17,
                Duration.ofHours(1));
        assertThat(sessions.scheduleQualityTime(f.getId(), itai.getId(), WEDNESDAY_17, Duration.ofHours(1))
                .joinedExistingSession()).isTrue();
        // an event of a cancelled session that Google kept (its delete failed), and an old one with no marker
        Map<String, Object> leftover = timed("leftover", WEDNESDAY_17.plus(Duration.ofMinutes(30)), WEDNESDAY_17.plus(Duration.ofHours(2)));
        leftover.put("extendedProperties", Map.of("private", Map.of("dadCoach", "1")));
        calendar.add(leftover);
        Map<String, Object> old = timed("old", WEDNESDAY_17.plus(Duration.ofMinutes(30)), WEDNESDAY_17.plus(Duration.ofHours(2)));
        old.put("description", "דאד קואץ׳ - זמן איכות מתוכנן. תהנו מהרגע יחד!");
        calendar.add(old);
        Map<String, Object> older = timed("older", WEDNESDAY_17.plus(Duration.ofMinutes(30)), WEDNESDAY_17.plus(Duration.ofHours(2)));
        older.put("description", "Dad Coach - זמן איכות מתוכנן. תהנו מהרגע יחד!");
        calendar.add(older);

        JsonNode moved = tool(f, "reschedule_quality_time", Map.of("quality_time_id", booked.qualityTimeId().toString(),
                "new_start_time", local(WEDNESDAY_17.plus(Duration.ofMinutes(30)))));

        assertThat(moved.path("success").asBoolean()).as(moved.toString()).isTrue();
        assertThat(moved.path("data").has("calendar_conflict")).isFalse();
        assertThat(moved.path("data").path("reply").asText()).doesNotContain("שים לב");
        assertThat(calendar).extracting(e -> e.get("id")).doesNotContain(booked.calendarEventId());
    }

    @Test
    @DisplayName("a move refused by the date rules changes nothing: the old session and its Google event stay")
    void aRefusedMoveKeepsTheOldEvent() throws Exception {
        Father f = connected("+19995550913");
        Child noa = data.child(f, "נועה", 7);
        ScheduleQualityTimeResult booked = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_17,
                Duration.ofHours(1));
        long createsBefore = eventCreates();

        JsonNode moved = tool(f, "reschedule_quality_time", Map.of("quality_time_id", booked.qualityTimeId().toString(),
                "new_start_time", local(WEDNESDAY_17.plus(Duration.ofDays(120)))));

        assertThat(moved.path("success").asBoolean()).isFalse();
        assertThat(moved.path("error_code").asText()).isEqualTo("TOO_FAR_AHEAD");
        assertThat(fake.calls("/google/calendar/").stream().filter(c -> c.method().equals("DELETE"))).isEmpty();
        assertThat(calendar).extracting(e -> e.get("id")).contains(booked.calendarEventId());
        assertThat(eventCreates()).isEqualTo(createsBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?::uuid", String.class,
                booked.qualityTimeId().toString())).isEqualTo("SCHEDULED");
    }

    private static String local(Instant at) {
        return at.atZone(IL).toOffsetDateTime().toString();
    }

    private JsonNode tool(Father f, String key, Map<String, Object> params) throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-" + idempotencyKey);
        body.put("idempotency_key", idempotencyKey);
        body.put("user_id", f.getPhone());
        body.put("execution_context", Map.of("currentStateKey", "ACTIVE_COACHING"));
        body.put("parameters", params);
        return json.readTree(mvc.perform(post("/api/tools/" + key).header("X-API-Key", TOOL_KEY)
                .header("X-Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn().getResponse().getContentAsByteArray());
    }
}
