package com.dadcoach.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The templates screen: the catalog beside its registration state, and marking a template approved. */
class AdminTemplatesTest extends AbstractWebIntegrationTest {

    @Test
    @DisplayName("staff see the catalog as submitted to Meta; a father is forbidden")
    void listing() throws Exception {
        mvc.perform(signInFather(newFather("אבא")).on(get("/api/admin/templates"))).andExpect(status().isForbidden());
        mvc.perform(signIn(newStaff("צוות")).on(get("/api/admin/templates"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(WhatsAppTemplateCatalog.ALL.size()))
                .andExpect(jsonPath("$[0].name").value("dad_coach_update_he"))
                .andExpect(jsonPath("$[0].category").value("UTILITY"))
                .andExpect(jsonPath("$[0].language").value("he"))
                .andExpect(jsonPath("$[0].body").value(WhatsAppTemplateCatalog.UPDATE_HE_BODY))
                .andExpect(jsonPath("$[0].registeredStatus").doesNotExist())
                .andExpect(jsonPath("$[0].registeredBodyMatches").value(false))
                .andExpect(jsonPath("$[0].general").value(true))
                .andExpect(jsonPath("$[2].name").value("dad_coach_session_hour_before_he"))
                .andExpect(jsonPath("$[2].quickReplies[0]").value("רוצה רעיונות"))
                .andExpect(jsonPath("$[2].examples[0]").value("מאיה"));
    }

    @Test
    @DisplayName("mark approved only from the review dialog; then the registry row gates sending with the catalog's body")
    void approve() throws Exception {
        Browser staff = signIn(newStaff("צוות"));
        mvc.perform(staff.on(post("/api/admin/templates/dad_coach_update_he/approve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_CONFIRMED"));
        mvc.perform(staff.on(post("/api/admin/templates/no_such_template/approve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(staff.on(post("/api/admin/templates/dad_coach_update_he/approve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredStatus").value("APPROVED"))
                .andExpect(jsonPath("$.registeredBodyMatches").value(true));
        assertThat(jdbc.queryForObject("SELECT body FROM template_messages WHERE template_name = 'dad_coach_update_he'",
                String.class)).isEqualTo(WhatsAppTemplateCatalog.UPDATE_HE_BODY);
        assertThat(jdbc.queryForObject("SELECT status FROM template_messages WHERE template_name = 'dad_coach_update_he'",
                String.class)).isEqualTo("APPROVED");
        mvc.perform(staff.on(get("/api/admin/templates")))
                .andExpect(jsonPath("$[0].registeredStatus").value("APPROVED"));
    }
}
