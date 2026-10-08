package com.dadcoach.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.support.FakeServers;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** The father's area: the home is the weekly plan; sessions, children, progress, settings, deletion. */
class FatherAreaTest extends AbstractWebIntegrationTest {

    @Autowired
    WeeklyGoalService weeklyGoals;
    @Autowired
    FatherRepository fathers;
    @Autowired
    ObjectMapper json;

    /** יואב: נועה (7) and איתי (4), a 3-hour goal, one session done this week, one ahead, one awaiting confirmation. */
    record Seed(long father, long noa, long itai, UUID done, UUID ahead, UUID awaiting) {
    }

    String content(FakeServers.Call call) {
        try {
            return json.readTree(call.body()).path("content").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Seed seedYoav() {
        long father = newFather("יואב");
        long noa = newChild(father, "נועה", 7);
        long itai = newChild(father, "איתי", 4);
        LocalDate weekStart = weeklyGoals.weekStartFor(fathers.findById(father).orElseThrow(), clock.instant());
        UUID done = newSession(father, noa, -240, 60, "COMPLETED");
        UUID awaiting = newSession(father, itai, -120, 45, "SCHEDULED");
        UUID ahead = newSession(father, noa, 120, 60, "SCHEDULED");
        jdbc.update("""
                INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status)
                VALUES (?, ?, 3, 60, 'WHITE', 'ACTIVE')""", father, weekStart);
        jdbc.update("UPDATE father SET total_quality_times_completed = 3, current_belt = 'YELLOW', current_streak_weeks = 2 WHERE id = ?", father);
        return new Seed(father, noa, itai, done, ahead, awaiting);
    }

    @Test
    @DisplayName("home = the weekly plan: goal, credited minutes, coverage, next session, awaiting, belt progress")
    void home() throws Exception {
        Seed s = seedYoav();
        Browser browser = signInFather(s.father());
        mvc.perform(browser.on(get("/api/father/home")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("יואב"))
                .andExpect(jsonPath("$.goal.exists").value(true))
                .andExpect(jsonPath("$.goal.targetHours").value(3))
                .andExpect(jsonPath("$.coverage.targetMinutes").value(180))
                .andExpect(jsonPath("$.coverage.completedMinutes").value(60))
                .andExpect(jsonPath("$.coverage.plannedMinutes").value(60))
                .andExpect(jsonPath("$.coverage.uncoveredMinutes").value(60))
                .andExpect(jsonPath("$.coverage.awaitingMinutes").value(45))
                .andExpect(jsonPath("$.nextSession.id").value(s.ahead().toString()))
                .andExpect(jsonPath("$.nextSession.childName").value("נועה"))
                .andExpect(jsonPath("$.awaitingConfirmation[0].id").value(s.awaiting().toString()))
                .andExpect(jsonPath("$.awaitingConfirmation[0].canConfirm").value(true))
                .andExpect(jsonPath("$.sessionsThisWeek.length()").value(3))
                .andExpect(jsonPath("$.progress.belt").value("YELLOW"))
                .andExpect(jsonPath("$.progress.nextBelt").value("ORANGE"))
                .andExpect(jsonPath("$.progress.sessionsToNextBelt").value(7))
                .andExpect(jsonPath("$.progress.streakWeeks").value(2))
                .andExpect(jsonPath("$.children.length()").value(2))
                .andExpect(jsonPath("$.coachWhatsApp").value("+19995550100"));
    }

    @Test
    @DisplayName("the sessions list gives every session of this week the same phase as the home (one week truth)")
    void phasesAgreeWithTheWeeklyPlan() throws Exception {
        Seed s = seedYoav();
        Browser browser = signInFather(s.father());
        JsonNode home = read(browser, "/api/father/home");
        JsonNode list = read(browser, "/api/father/sessions");
        Map<String, String> listed = new HashMap<>();
        for (String section : new String[] {"upcoming", "awaiting", "past"}) {
            list.get(section).forEach(v -> listed.put(v.get("id").asText(), v.get("phase").asText()));
        }
        assertThat(home.get("sessionsThisWeek")).hasSize(3);
        home.get("sessionsThisWeek").forEach(v -> assertThat(listed.get(v.get("id").asText()))
                .as(v.get("id").asText()).isEqualTo(v.get("phase").asText()));
        assertThat(listed.get(s.done().toString())).isEqualTo("COMPLETED");
        assertThat(listed.get(s.ahead().toString())).isEqualTo("UPCOMING");
        assertThat(listed.get(s.awaiting().toString())).isEqualTo("AWAITING_CONFIRMATION");
    }

    @Test
    @DisplayName("'it happened' credits the week, the streak and the belt count exactly like the AI tool")
    void confirm() throws Exception {
        Seed s = seedYoav();
        Browser browser = signInFather(s.father());
        mvc.perform(browser.on(post("/api/father/sessions/" + s.awaiting() + "/confirm"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"בנינו מגדל לגו\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.beltEarned").doesNotExist());
        // D-034: his AI conversation learns what he did on the page (history only, nothing sent on WhatsApp)
        assertThat(fake.recordedOutbound()).singleElement().satisfies(c -> assertThat(content(c))
                .contains("📊 בדף שלך: סימנת שהמפגש של *").contains("עם איתי היה."));
        assertThat(fake.metaSends()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?", String.class, s.awaiting())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT completion_notes FROM quality_time WHERE id = ?", String.class, s.awaiting())).isEqualTo("בנינו מגדל לגו");
        assertThat(jdbc.queryForObject("SELECT actual_minutes FROM weekly_goal WHERE father_id = ?", Integer.class, s.father())).isEqualTo(105);
        assertThat(jdbc.queryForObject("SELECT total_quality_times_completed FROM father WHERE id = ?", Integer.class, s.father())).isEqualTo(4);
        mvc.perform(browser.on(get("/api/father/home"))).andExpect(jsonPath("$.coverage.completedMinutes").value(105));

        // an upcoming session cannot be confirmed yet; a completed one not again
        mvc.perform(browser.on(post("/api/father/sessions/" + s.ahead() + "/confirm"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_CONFIRMABLE"));
        mvc.perform(browser.on(post("/api/father/sessions/" + s.awaiting() + "/confirm"))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("B-1: a belt earned on the page is told on the page, once, and noted in his conversation")
    void confirmThatEarnsABelt() throws Exception {
        Seed s = seedYoav();
        jdbc.update("UPDATE father SET total_quality_times_completed = 2, current_belt = 'WHITE' WHERE id = ?", s.father());
        Browser browser = signInFather(s.father());
        mvc.perform(browser.on(post("/api/father/sessions/" + s.awaiting() + "/confirm"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.beltEarned").value("חגורה צהובה"));
        assertThat(fake.recordedOutbound()).singleElement().satisfies(c -> assertThat(content(c)).contains("עלית ל*חגורה צהובה*."));
    }

    @Test
    @DisplayName("cancel an upcoming session; a completed one cannot be cancelled")
    void cancel() throws Exception {
        Seed s = seedYoav();
        Browser browser = signInFather(s.father());
        mvc.perform(browser.on(post("/api/father/sessions/" + s.ahead() + "/cancel"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?", String.class, s.ahead())).isEqualTo("CANCELLED");
        assertThat(fake.recordedOutbound()).singleElement().satisfies(c -> assertThat(content(c))
                .contains("📊 בדף שלך: ביטלת את המפגש של *היום, ").contains("עם נועה."));
        mvc.perform(browser.on(get("/api/father/home"))).andExpect(jsonPath("$.coverage.plannedMinutes").value(0))
                .andExpect(jsonPath("$.sessionsThisWeek.length()").value(2)); // the cancelled one leaves the home's week list
        mvc.perform(browser.on(get("/api/father/sessions"))).andExpect(jsonPath("$.past[?(@.id == '" + s.ahead() + "')].phase").value("CANCELLED"));
        mvc.perform(browser.on(post("/api/father/sessions/" + s.done() + "/cancel"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("children: add, the AI tool's rules (unique name, age 0-25), edit")
    void children() throws Exception {
        long father = newFather("אלון");
        Browser browser = signInFather(father);
        String created = mvc.perform(browser.on(post("/api/father/children")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"מיה\",\"age\":5}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.age").value(5)).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        mvc.perform(browser.on(post("/api/father/children")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"מִיה\",\"age\":3}"))
                .andExpect(status().isCreated());
        mvc.perform(browser.on(post("/api/father/children")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"מיה\",\"age\":3}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CHILD"));
        mvc.perform(browser.on(post("/api/father/children")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"תום\",\"age\":40}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(browser.on(put("/api/father/children/" + id)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"מיה רוז\",\"age\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("מיה רוז")).andExpect(jsonPath("$.age").value(6));
        mvc.perform(browser.on(get("/api/father/children"))).andExpect(jsonPath("$.length()").value(2));
        // D-034: each change is noted in his conversation with the coach, so "בן כמה מיה?" is answered from it
        assertThat(fake.recordedOutbound()).extracting(this::content).anySatisfy(b -> assertThat(b)
                .contains("📊 בדף שלך: הוספת את מיה, בגיל 5.")).anySatisfy(b -> assertThat(b)
                .contains("📊 בדף שלך: עדכנת את מיה: השם עכשיו מיה רוז, בגיל 6."));
    }

    @Test
    @DisplayName("progress: the belt ladder, achievements from his history, weekly history")
    void progress() throws Exception {
        Seed s = seedYoav();
        jdbc.update("UPDATE quality_time SET completion_notes = 'ציירנו' WHERE id = ?", s.done());
        mvc.perform(signInFather(s.father()).on(get("/api/father/progress")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.belts.length()").value(7))
                .andExpect(jsonPath("$.belts[1].belt").value("YELLOW"))
                .andExpect(jsonPath("$.belts[1].current").value(true))
                .andExpect(jsonPath("$.achievements[0].key").value("first-session"))
                .andExpect(jsonPath("$.achievements[0].earned").value(true))
                .andExpect(jsonPath("$.achievements[3].key").value("shared-a-moment"))
                .andExpect(jsonPath("$.achievements[3].earned").value(true))
                .andExpect(jsonPath("$.weeks[0].targetHours").value(3));
    }

    @Test
    @DisplayName("settings: name and timezone; a bad timezone is refused; calendar connect needs Google configured")
    void settings() throws Exception {
        long father = newFather("ניר");
        Browser browser = signInFather(father);
        mvc.perform(browser.on(put("/api/father/settings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ניר כהן\",\"timezone\":\"Europe/London\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("ניר כהן")).andExpect(jsonPath("$.timezone").value("Europe/London"));
        mvc.perform(browser.on(put("/api/father/settings")).contentType(MediaType.APPLICATION_JSON).content("{\"timezone\":\"Mars/Base\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
        mvc.perform(browser.on(post("/api/father/calendar/connect"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("delete my data: the typed word is required; then DELETED, signed out, and the platform deletion queued (purge after)")
    void deleteMyData() throws Exception {
        long father = newFather("איל", "ONBOARDING");
        Browser browser = signInFather(father);
        mvc.perform(browser.on(post("/api/father/delete-my-data")).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"כן\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONFIRMATION_MISMATCH"));
        mvc.perform(browser.on(post("/api/father/delete-my-data")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"מחיקה\"}")).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT purge_local FROM platform_person_deletion WHERE father_id = ?", Boolean.class, father)).isTrue();
        mvc.perform(browser.on(get("/api/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("training: no media host configured = nothing to show, and nothing to record")
    void trainingHidden() throws Exception {
        Browser browser = signInFather(newFather("טל"));
        mvc.perform(browser.on(get("/api/father/training"))).andExpect(jsonPath("$.available").value(false));
        mvc.perform(browser.on(post("/api/father/training/welcome/progress")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}")).andExpect(status().isNotFound());
        mvc.perform(browser.on(get("/api/me"))).andExpect(jsonPath("$.capabilities.length()").value(1));
    }

    private JsonNode read(Browser browser, String path) throws Exception {
        return json.readTree(mvc.perform(browser.on(get(path))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
