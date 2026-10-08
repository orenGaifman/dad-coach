package com.dadcoach.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.support.FakeServers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The templates screen: each catalog template beside its live state at Meta, and Meta's approval as the only way a
 * template becomes sendable - registered when Meta approves the catalog's exact body, withdrawn when Meta stops.
 */
class AdminTemplatesTest extends AbstractWebIntegrationTest {

    @Autowired ObjectMapper json;

    private static Map<String, Object> template(String name, String status, String category, String previous,
                                                String reason, String body) {
        Map<String, Object> t = new java.util.LinkedHashMap<>();
        t.put("name", name);
        t.put("language", "he");
        t.put("status", status);
        t.put("category", category);
        if (previous != null) {
            t.put("previous_category", previous);
        }
        t.put("rejected_reason", reason == null ? "NONE" : reason);
        t.put("components", List.of(Map.of("type", "BODY", "text", body)));
        return t;
    }

    private static String bodyOf(String name) {
        return WhatsAppTemplateCatalog.require(name).body();
    }

    private void metaHas(Map<String, Object>... templates) {
        FakeServers.INSTANCE.onMetaTemplates(c -> {
            try {
                return FakeServers.Reply.json(json.writeValueAsString(Map.of("data", List.of(templates))));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private String registered(String name) {
        List<String> s = jdbc.queryForList("SELECT status FROM template_messages WHERE template_name = ?", String.class, name);
        return s.isEmpty() ? null : s.get(0);
    }

    @Test
    @DisplayName("staff see every catalog template; before Meta is read its state is UNKNOWN; a father is forbidden")
    void listing() throws Exception {
        mvc.perform(signInFather(newFather("אבא")).on(get("/api/admin/templates"))).andExpect(status().isForbidden());
        mvc.perform(signIn(newStaff("צוות")).on(get("/api/admin/templates"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.wabaId").value("200000000000002"))
                .andExpect(jsonPath("$.metaConfigured").value(true))
                .andExpect(jsonPath("$.templates.length()").value(WhatsAppTemplateCatalog.ALL.size()))
                .andExpect(jsonPath("$.templates[0].name").value("dad_coach_update_he"))
                .andExpect(jsonPath("$.templates[0].body").value(WhatsAppTemplateCatalog.UPDATE_HE_BODY))
                .andExpect(jsonPath("$.templates[0].general").value(true))
                .andExpect(jsonPath("$.templates[0].ready").value(false))
                .andExpect(jsonPath("$.templates[2].quickReplies[0]").value("רוצה רעיונות"));
    }

    @Test
    @DisplayName("sync: Meta's status, category and re-filing show; only an approved template with the catalog's body is registered")
    void syncShowsMetaAndRegistersOnlyWhatMetaApproved() throws Exception {
        metaHas(template("dad_coach_update_he", "APPROVED", "UTILITY", null, null, bodyOf("dad_coach_update_he")),
                template("dad_coach_session_morning_he", "PENDING", "MARKETING", "UTILITY", null, bodyOf("dad_coach_session_morning_he")),
                template("dad_coach_session_hour_before_he", "APPROVED", "UTILITY", null, null, bodyOf("dad_coach_session_hour_before_he")),
                template("dad_coach_session_follow_up_he", "REJECTED", "UTILITY", null, "INVALID_FORMAT", bodyOf("dad_coach_session_follow_up_he")));
        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshedAt").isNotEmpty())
                .andExpect(jsonPath("$.templates[0].metaStatus").value("APPROVED"))
                // this test server sends another general template by name, so this one is approved but not in use
                .andExpect(jsonPath("$.templates[0].inUseAs").value(TEMPLATE))
                .andExpect(jsonPath("$.templates[0].ready").value(false))
                .andExpect(jsonPath("$.templates[1].metaStatus").value("PENDING"))
                .andExpect(jsonPath("$.templates[1].metaCategory").value("MARKETING"))
                .andExpect(jsonPath("$.templates[1].metaPreviousCategory").value("UTILITY"))
                .andExpect(jsonPath("$.templates[1].ready").value(false))
                .andExpect(jsonPath("$.templates[2].metaStatus").value("APPROVED"))
                .andExpect(jsonPath("$.templates[2].metaBodyMatches").value(true))
                .andExpect(jsonPath("$.templates[2].ready").value(true))
                .andExpect(jsonPath("$.templates[3].metaStatus").value("REJECTED"))
                .andExpect(jsonPath("$.templates[3].metaRejectedReason").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.templates[3].ready").value(false));
        assertThat(registered("dad_coach_update_he")).isEqualTo("APPROVED");
        assertThat(registered("dad_coach_session_hour_before_he")).isEqualTo("APPROVED");
        assertThat(registered("dad_coach_session_morning_he")).isNull();
        assertThat(registered("dad_coach_session_follow_up_he")).isNull();
    }

    @Test
    @DisplayName("approved at Meta with another body: never registered, and withdrawn if it was")
    void bodyMustMatch() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        metaHas(template("dad_coach_session_morning_he", "APPROVED", "UTILITY", null, null, "❤️ דאד קואץ׳:\nנוסח אחר {{1}} {{2}} 🙂"));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[1].metaBodyMatches").value(false))
                .andExpect(jsonPath("$.templates[1].ready").value(false));
        assertThat(registered("dad_coach_session_morning_he")).isNull();
        metaHas(template("dad_coach_session_morning_he", "APPROVED", "UTILITY", null, null, bodyOf("dad_coach_session_morning_he")));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(jsonPath("$.templates[1].ready").value(true));
        metaHas(template("dad_coach_session_morning_he", "APPROVED", "UTILITY", null, null, "❤️ דאד קואץ׳:\nנוסח אחר {{1}} {{2}} 🙂"));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(jsonPath("$.templates[1].ready").value(false));
        assertThat(registered("dad_coach_session_morning_he")).isEqualTo("BODY_DIFFERS");
    }

    @Test
    @DisplayName("Meta pauses or drops an approved template: it is withdrawn from sending at the next sync")
    void withdrawnWhenMetaStops() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        metaHas(template("dad_coach_update_he", "APPROVED", "UTILITY", null, null, bodyOf("dad_coach_update_he")));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk());
        assertThat(registered("dad_coach_update_he")).isEqualTo("APPROVED");
        metaHas(template("dad_coach_update_he", "PAUSED", "UTILITY", null, null, bodyOf("dad_coach_update_he")));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].metaStatus").value("PAUSED"))
                .andExpect(jsonPath("$.templates[0].ready").value(false));
        assertThat(registered("dad_coach_update_he")).isEqualTo("PAUSED");
        metaHas();
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].metaStatus").value("MISSING"));
        assertThat(registered("dad_coach_update_he")).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("the old manual 'mark approved' is gone: only Meta's approval registers a template")
    void noManualApproval() throws Exception {
        mvc.perform(signIn(newStaff("צוות")).on(post("/api/admin/templates/dad_coach_update_he/approve")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        assertThat(registered("dad_coach_update_he")).isNull();
    }
}
