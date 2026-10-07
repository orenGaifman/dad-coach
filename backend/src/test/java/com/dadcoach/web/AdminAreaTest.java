package com.dadcoach.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Capability gates, cross-father isolation, view-as parity, the admin screens, deactivate -> typed delete. */
class AdminAreaTest extends AbstractWebIntegrationTest {

    @Test
    @DisplayName("a father cannot reach the admin API; a staff user without a father has no father area")
    void areas() throws Exception {
        Browser father = signInFather(newFather("אבי"));
        mvc.perform(father.on(get("/api/admin/overview"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_STAFF"));
        mvc.perform(father.on(get("/api/admin/fathers"))).andExpect(status().isForbidden());
        mvc.perform(father.on(post("/api/admin/fathers/1/deactivate"))).andExpect(status().isForbidden());

        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.on(get("/api/father/home"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_FATHER"));
        mvc.perform(staff.on(get("/api/admin/overview"))).andExpect(status().isOk()).andExpect(jsonPath("$.pendingDeletions").isNumber());
    }

    @Test
    @DisplayName("another father's session or child is a 404, never touched")
    void crossFather() throws Exception {
        long mine = newFather("ראשון");
        long other = newFather("שני");
        long otherChild = newChild(other, "רוני", 6);
        UUID otherSession = newSession(other, otherChild, -90, 30, "SCHEDULED");
        Browser browser = signInFather(mine);
        mvc.perform(browser.on(post("/api/father/sessions/" + otherSession + "/confirm"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(browser.on(post("/api/father/sessions/" + otherSession + "/cancel"))).andExpect(status().isNotFound());
        mvc.perform(browser.on(put("/api/father/children/" + otherChild)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?", String.class, otherSession)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT name FROM child WHERE id = ?", String.class, otherChild)).isEqualTo("רוני");
    }

    @Test
    @DisplayName("view-as: the admin's view of a father's home is byte-for-byte his own home")
    void viewAsParity() throws Exception {
        long father = newFather("יואב");
        long noa = newChild(father, "נועה", 7);
        newSession(father, noa, -180, 60, "COMPLETED");
        newSession(father, noa, -60, 30, "SCHEDULED");
        newSession(father, noa, 90, 60, "SCHEDULED");
        String own = mvc.perform(signInFather(father).on(get("/api/father/home"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String viewed = mvc.perform(signIn(newStaff("צוות")).on(get("/api/admin/fathers/" + father + "/home"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(viewed).isEqualTo(own);
        mvc.perform(signIn(newStaff("צוות 2")).on(get("/api/admin/fathers/999999999/home"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("admin screens answer: fathers (search), detail, platform panel (not configured), integrations, undelivered, training")
    void screens() throws Exception {
        long father = newFather("מחפשים אותי");
        newChild(father, "דנה", 9);
        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.on(get("/api/admin/fathers").param("q", "מחפשים"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(father)).andExpect(jsonPath("$[0].children").value(1));
        String digits = phoneOf(father).substring(phoneOf(father).length() - 6);
        mvc.perform(staff.on(get("/api/admin/fathers").param("q", digits))).andExpect(jsonPath("$[0].id").value(father));
        mvc.perform(staff.on(get("/api/admin/fathers/" + father))).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("מחפשים אותי"))
                .andExpect(jsonPath("$.children[0].name").value("דנה"))
                .andExpect(jsonPath("$.deleteConfirmation").value("מחפשים אותי"));
        mvc.perform(staff.on(get("/api/admin/fathers/" + father + "/platform"))).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(staff.on(get("/api/admin/fathers/424242424"))).andExpect(status().isNotFound());
        mvc.perform(staff.on(get("/api/admin/integrations"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.opsApiConfigured").value(true)).andExpect(jsonPath("$.platform.enabled").value(true));
        // a failed login-link delivery shows up as undelivered
        mvc.perform(post("/api/auth/request-link").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phoneOf(father) + "\"}"));
        mvc.perform(staff.on(get("/api/admin/undelivered"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fatherId == " + father + ")].kind").value("LOGIN_LINK"));
        mvc.perform(staff.on(get("/api/admin/training"))).andExpect(jsonPath("$.mediaConfigured").value(false))
                .andExpect(jsonPath("$.videos.length()").value(5));
    }

    @Test
    @DisplayName("deactivate first (PAUSED, signed out, status remembered), then a typed permanent delete: data purged, platform deletion queued")
    void deactivateThenDelete() throws Exception {
        long father = newFather("למחיקה", "ONBOARDING");
        long child = newChild(father, "ילד", 3);
        newSession(father, child, 60, 30, "SCHEDULED");
        Browser fatherBrowser = signInFather(father);
        Browser staff = signIn(newStaff("צוות"));

        mvc.perform(staff.on(delete("/api/admin/fathers/" + father)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"למחיקה\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEACTIVATE_FIRST"));

        mvc.perform(staff.on(post("/api/admin/fathers/" + father + "/deactivate"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father)).isEqualTo("PAUSED");
        mvc.perform(fatherBrowser.on(get("/api/me"))).andExpect(status().isUnauthorized());
        mvc.perform(staff.on(post("/api/admin/fathers/" + father + "/reactivate"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father)).isEqualTo("ONBOARDING");
        mvc.perform(staff.on(post("/api/admin/fathers/" + father + "/deactivate"))).andExpect(status().isNoContent());

        mvc.perform(staff.on(delete("/api/admin/fathers/" + father)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"משהו אחר\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONFIRMATION_MISMATCH"));
        mvc.perform(staff.on(delete("/api/admin/fathers/" + father)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"למחיקה\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM father WHERE id = ?", Long.class, father)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM child WHERE father_id = ?", Long.class, father)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_time WHERE father_id = ?", Long.class, father)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dashboard_session WHERE father_id = ?", Long.class, father)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_person_deletion WHERE father_id = ? AND completed_at IS NULL",
                Long.class, father)).isEqualTo(1);
        mvc.perform(staff.on(get("/api/admin/deletions"))).andExpect(status().isOk()).andExpect(jsonPath("$.pending").isNumber());
    }
}
