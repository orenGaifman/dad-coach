package com.dadcoach.api.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.father.FatherStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** family_context and weekly_plan_context as the platform's Dad Coach context client calls them (playbook §12). */
class ContextProvidersTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode load(String provider, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/context/" + provider).header("X-API-Key", TOOL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void theFatherIsFoundByHisWhatsAppNumberWithOrWithoutTheChannel() throws Exception {
        Father f = data.activeFather("+19995550300");
        data.child(f, "נועה", 6);
        for (String phone : new String[]{f.getPhone(), "whatsapp:" + f.getPhone()}) {
            JsonNode family = load("family_context", "{\"config\":{\"phone\":\"" + phone + "\"}}");
            assertThat(family.path("success").asBoolean()).isTrue();
            assertThat(family.path("data").path("father_profile").path("display_name").asText()).isEqualTo("דני");
            assertThat(family.path("data").path("children").get(0).path("name").asText()).isEqualTo("נועה");
            assertThat(family.path("data").path("children_count").asInt()).isEqualTo(1);
        }
    }

    @Test
    void aNumericUserIdIsNeverTrusted() throws Exception {
        Father f = data.activeFather("+19995550301");
        JsonNode family = load("family_context", "{\"user_id\":" + f.getId() + ",\"config\":{}}");
        assertThat(family.path("data").path("father_profile").isNull()).isTrue();
    }

    @Test
    void anUnknownOrDeletedFatherIsALegitimateEmptyAnswer() throws Exception {
        Father deleted = data.father("+19995550302", "x", FatherStatus.DELETED);
        JsonNode plan = load("weekly_plan_context", "{\"config\":{\"phone\":\"" + deleted.getPhone() + "\"}}");
        assertThat(plan.path("success").asBoolean()).isTrue();
        assertThat(plan.path("data").path("father_found").asBoolean()).isFalse();
        assertThat(load("weekly_plan_context", "{\"config\":{\"phone\":\"+19995550399\"}}").path("data").path("father_found").asBoolean())
                .isFalse();
        assertThat(load("other_context", "{}").path("error_code").asText()).isEqualTo("PROVIDER_NOT_FOUND");
    }

    @Test
    void theWeeklyPlanLinksTheDashboardLoginNeverAFatherId() throws Exception {
        Father f = data.activeFather("+19995550303");
        JsonNode plan = load("weekly_plan_context", "{\"config\":{\"phone\":\"" + f.getPhone() + "\"}}");
        assertThat(plan.path("data").path("father_found").asBoolean()).isTrue();
        assertThat(plan.path("data").path("dashboard_url").asText()).isEqualTo("https://app.dadcoach.test/login");
        assertThat(plan.path("data").path("calendar_connected").asBoolean()).isFalse();
        assertThat(plan.toString()).doesNotContain("dadcoach.app").doesNotContain("fatherId");
    }
}
