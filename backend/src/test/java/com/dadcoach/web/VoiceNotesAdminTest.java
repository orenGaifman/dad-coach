package com.dadcoach.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** D-027: the admin's voice-notes card - on by default (no row), off and on again, the team only, never the key. */
class VoiceNotesAdminTest extends AbstractWebIntegrationTest {

    private String setting() {
        return jdbc.queryForList("SELECT value FROM system_setting WHERE key = 'voice_notes.enabled'", String.class)
                .stream().findFirst().orElse(null);
    }

    @Test
    @DisplayName("on by default with no row; off and on again; the key is never in the answer")
    void theSwitch() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        String body = mvc.perform(staff.on(get("/api/admin/integrations"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceNotes.enabled").value(true))
                .andExpect(jsonPath("$.voiceNotes.configured").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(ELEVENLABS_KEY);
        assertThat(setting()).isNull();

        mvc.perform(staff.on(put("/api/admin/integrations/voice-notes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.configured").value(true));
        assertThat(setting()).isEqualTo("false");
        mvc.perform(staff.on(get("/api/admin/integrations"))).andExpect(jsonPath("$.voiceNotes.enabled").value(false));

        mvc.perform(staff.on(put("/api/admin/integrations/voice-notes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        assertThat(setting()).isEqualTo("true");
    }

    @Test
    @DisplayName("a request without 'enabled' is a 400 and changes nothing")
    void badRequest() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.on(put("/api/admin/integrations/voice-notes")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(staff.on(put("/api/admin/integrations/voice-notes")).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":\"maybe\"}"))
                .andExpect(status().isBadRequest());
        assertThat(setting()).isNull();
    }

    @Test
    @DisplayName("a father, a visitor and a write without the CSRF echo cannot touch it")
    void theTeamOnly() throws Exception {
        Browser father = signInFather(newFather("אבי"));
        mvc.perform(father.on(put("/api/admin/integrations/voice-notes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_STAFF"));
        mvc.perform(put("/api/admin/integrations/voice-notes").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().is4xxClientError());
        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.withoutCsrf(put("/api/admin/integrations/voice-notes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isForbidden());
        assertThat(setting()).isNull();
    }
}
