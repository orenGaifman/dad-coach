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
 * The templates screen: each catalog template beside its live state. D-053: the state is the platform's (its copy of
 * the WABA's templates and its kind→template mapping, {@code /api/v1/worker/whatsapp/templates} and
 * {@code /messaging/kinds}); Dad Coach no longer reads Meta, and nothing registers a template for sending here.
 */
class AdminTemplatesTest extends AbstractWebIntegrationTest {

    @Autowired ObjectMapper json;

    private void platformHas(List<Map<String, Object>> templates, List<Map<String, Object>> kinds) {
        FakeServers.INSTANCE.onPlatformTemplates(c -> {
            try {
                return FakeServers.Reply.json(json.writeValueAsString(c.path().endsWith("/templates")
                        ? Map.of("senderStatus", "OK", "wabaId", "300000000000003", "templates", templates)
                        : Map.of("product", "dad_coach", "snapshot", true, "kinds", kinds)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Map<String, Object> template(String name, String status, String category) {
        return Map.of("name", name, "language", "he", "status", status, "category", category);
    }

    private static Map<String, Object> kind(String kind, String variant, String template, boolean usable) {
        return Map.of("kind", kind, "variant", variant, "templateUsable", usable, "candidates",
                List.of(Map.of("template", template, "language", "he", "status", usable ? "APPROVED" : "PENDING",
                        "usable", usable)));
    }

    @Test
    @DisplayName("staff see every catalog template with the platform's state (default: the seed, all approved); a father is forbidden")
    void listing() throws Exception {
        mvc.perform(signInFather(newFather("אבא")).on(get("/api/admin/templates"))).andExpect(status().isForbidden());
        mvc.perform(signIn(newStaff("צוות")).on(get("/api/admin/templates"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.wabaId").value("200000000000002"))
                .andExpect(jsonPath("$.metaConfigured").value(true))
                .andExpect(jsonPath("$.refreshedAt").isNotEmpty())
                .andExpect(jsonPath("$.templates.length()").value(WhatsAppTemplateCatalog.ALL.size()))
                .andExpect(jsonPath("$.templates[0].name").value("dad_coach_update_he"))
                .andExpect(jsonPath("$.templates[0].body").value(WhatsAppTemplateCatalog.UPDATE_HE_BODY))
                .andExpect(jsonPath("$.templates[0].general").value(true))
                .andExpect(jsonPath("$.templates[0].metaStatus").value("APPROVED"))
                .andExpect(jsonPath("$.templates[0].ready").value(true))
                .andExpect(jsonPath("$.templates[0].usedFor.length()").value(5))
                .andExpect(jsonPath("$.templates[2].quickReplies[0]").value("רוצה רעיונות"))
                .andExpect(jsonPath("$.templates[2].urlButton").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.templates[2].definition.components[1].buttons[0].type").value("QUICK_REPLY"))
                // D-067: the morning's dashboard twin, its URL button and the definition submitted to Meta
                .andExpect(jsonPath("$.templates[4].name").value("dad_coach_session_morning_dash_he"))
                .andExpect(jsonPath("$.templates[4].body").value(WhatsAppTemplateCatalog.require(
                        WhatsAppTemplateCatalog.SESSION_MORNING_HE).body()))
                .andExpect(jsonPath("$.templates[4].quickReplies.length()").value(0))
                .andExpect(jsonPath("$.templates[4].urlButton").value("כניסה לדף שלי"))
                .andExpect(jsonPath("$.templates[4].urlButtonUrl").value(org.hamcrest.Matchers.endsWith("/auth/consume#token={{1}}")))
                .andExpect(jsonPath("$.templates[4].usedFor[0]").value("SCHEDULED/session_morning_he"))
                .andExpect(jsonPath("$.templates[4].definition.name").value("dad_coach_session_morning_dash_he"))
                .andExpect(jsonPath("$.templates[4].definition.category").value("UTILITY"))
                .andExpect(jsonPath("$.templates[4].definition.components[1].buttons[0].type").value("URL"))
                .andExpect(jsonPath("$.templates[4].definition.components[1].buttons[0].text").value("כניסה לדף שלי"));
        assertThat(FakeServers.INSTANCE.calls("/api/v1/worker/whatsapp/templates")).hasSize(1);
        assertThat(FakeServers.INSTANCE.calls("/api/v1/worker/messaging/kinds")).hasSize(1);
        assertThat(FakeServers.INSTANCE.calls("/v")).as("never Meta").isEmpty();
    }

    @Test
    @DisplayName("sync: the platform's status, category and mapping show; ready = a mapped candidate usable now")
    void syncShowsThePlatformsView() throws Exception {
        platformHas(List.of(template("dad_coach_update_he", "APPROVED", "UTILITY"),
                        template("dad_coach_session_morning_he", "PENDING", "MARKETING"),
                        template("dad_coach_session_hour_before_he", "APPROVED", "UTILITY")),
                List.of(kind("SCHEDULED", "", "dad_coach_update_he", true),
                        kind("SCHEDULED", "session_morning_he", "dad_coach_session_morning_he", false),
                        kind("SCHEDULED", "session_hour_before_he", "dad_coach_session_hour_before_he", true)));
        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.wabaId").value("300000000000003"))
                .andExpect(jsonPath("$.refreshedAt").isNotEmpty())
                .andExpect(jsonPath("$.templates[0].metaStatus").value("APPROVED"))
                .andExpect(jsonPath("$.templates[0].inUseAs").value("SCHEDULED"))
                .andExpect(jsonPath("$.templates[0].ready").value(true))
                .andExpect(jsonPath("$.templates[1].metaStatus").value("PENDING"))
                .andExpect(jsonPath("$.templates[1].metaCategory").value("MARKETING"))
                .andExpect(jsonPath("$.templates[1].ready").value(false))
                .andExpect(jsonPath("$.templates[2].metaStatus").value("APPROVED"))
                .andExpect(jsonPath("$.templates[2].inUseAs").value("SCHEDULED/session_hour_before_he"))
                .andExpect(jsonPath("$.templates[2].ready").value(true))
                .andExpect(jsonPath("$.templates[3].metaStatus").value("MISSING"))
                .andExpect(jsonPath("$.templates[3].ready").value(false));
    }

    @Test
    @DisplayName("the platform cannot be read: the last good view stays, the error shows; none yet = UNKNOWN")
    void platformDown() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        FakeServers.INSTANCE.onPlatformTemplates(c -> new FakeServers.Reply(503, "{}"));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].metaStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.templates[0].ready").value(false))
                .andExpect(jsonPath("$.lastError").value("HTTP_503"));
        FakeServers.INSTANCE.onPlatformTemplates(null);
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(jsonPath("$.templates[0].metaStatus").value("APPROVED"));
        FakeServers.INSTANCE.onPlatformTemplates(c -> new FakeServers.Reply(503, "{}"));
        mvc.perform(staff.on(post("/api/admin/templates/sync"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].metaStatus").value("APPROVED"))
                .andExpect(jsonPath("$.lastError").value("HTTP_503"));
    }

    @Test
    @DisplayName("a failed read is not retried for 60 s: an outage never costs two blocking GETs on every admin page")
    void aFailureIsCachedBriefly() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        FakeServers.INSTANCE.onPlatformTemplates(c -> new FakeServers.Reply(503, "{}"));
        mvc.perform(staff.on(get("/api/admin/templates"))).andExpect(jsonPath("$.lastError").value("HTTP_503"));
        mvc.perform(staff.on(get("/api/admin/templates"))).andExpect(jsonPath("$.lastError").value("HTTP_503"));
        assertThat(FakeServers.INSTANCE.calls("/api/v1/worker/whatsapp/templates")).as("asked once").hasSize(1);
        FakeServers.INSTANCE.onPlatformTemplates(null);
        clock.advance(java.time.Duration.ofSeconds(61));
        mvc.perform(staff.on(get("/api/admin/templates"))).andExpect(jsonPath("$.templates[0].metaStatus").value("APPROVED"))
                .andExpect(jsonPath("$.templates[0].metaBodyMatches").value(org.hamcrest.Matchers.nullValue()));
        assertThat(FakeServers.INSTANCE.calls("/api/v1/worker/whatsapp/templates")).hasSize(2);
    }

    @Test
    @DisplayName("the old manual 'mark approved' is gone: nothing here makes a template sendable")
    void noManualApproval() throws Exception {
        mvc.perform(signIn(newStaff("צוות")).on(post("/api/admin/templates/dad_coach_update_he/approve")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }
}
