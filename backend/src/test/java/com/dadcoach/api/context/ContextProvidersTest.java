package com.dadcoach.api.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
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
    @Autowired com.dadcoach.domain.father.FatherRepository fathers;
    @Autowired com.dadcoach.domain.child.ChildRepository children;

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
        assertThat(plan.path("data").has("dashboard_url")).isFalse();
        assertThat(plan.path("data").path("calendar_connected").asBoolean()).isFalse();
        assertThat(plan.toString()).doesNotContain("dadcoach.app").doesNotContain("fatherId");
    }

    /** D-059: the agent's view of family_context is what the platform's FamilyContextProvider built from family_context. */
    @Test
    void theAgentFamilyViewIsWhatThePlatformMappingBuilt() throws Exception {
        Father f = data.activeFather("+19995550304");
        f.setGoogleRefreshToken("connected");
        f.setGoogleCalendarEnabled(true);
        fathers.saveAndFlush(f);
        Child noa = data.child(f, "נועה", 6);
        noa.setGender("FEMALE");
        noa.setInterests(java.util.List.of("ציור", "כדורגל"));
        children.saveAndFlush(noa);
        Child ido = data.child(f, "עידו", 3);

        JsonNode raw = load("family_context", "{\"config\":{\"phone\":\"whatsapp:" + f.getPhone() + "\"}}");
        JsonNode agent = load("agent_family_context", "{\"user_id\":null,\"config\":{\"phone\":\"whatsapp:" + f.getPhone() + "\"}}");
        assertThat(agent.path("success").asBoolean()).isTrue();
        assertThat(agent.path("data")).isEqualTo(json.readTree("""
                {"isNewUser":false,"hasProfile":true,
                 "father":{"displayName":"דני","locale":"he","timezone":"Asia/Jerusalem","preferredCoachingTime":null,
                           "googleCalendarConnected":false,"welcomeStep":null},
                 "children":[%s,%s],
                 "hasMultipleChildren":true,"childCount":2,"hasGoogleCalendarConnected":false}
                """.formatted(agentChild(raw.path("data").path("children").get(0)), agentChild(raw.path("data").path("children").get(1)))));
        assertThat(agent.path("data").path("children").findValuesAsText("name")).containsExactlyInAnyOrder("נועה", "עידו");
        assertThat(agent.path("data").path("children").get(0).path("childId").asLong())
                .isIn(noa.getId(), ido.getId());

        JsonNode noChildren = load("agent_family_context", "{\"config\":{\"phone\":\"" + f.getPhone() + "\",\"includeChildren\":false}}");
        assertThat(noChildren.path("data").path("children")).isEmpty();
        assertThat(noChildren.path("data").path("childCount").asInt()).isZero();
        assertThat(noChildren.path("data").path("hasMultipleChildren").asBoolean()).isFalse();
    }

    @Test
    void anUnknownFatherIsANewUserInTheAgentFamilyView() throws Exception {
        Father deleted = data.father("+19995550305", "x", FatherStatus.DELETED);
        for (String phone : new String[]{"+19995550398", "whatsapp:" + deleted.getPhone()}) {
            JsonNode agent = load("agent_family_context", "{\"user_id\":null,\"config\":{\"phone\":\"" + phone + "\"}}");
            assertThat(agent.path("data")).isEqualTo(json.readTree("""
                    {"isNewUser":true,"hasProfile":false,"father":{},"children":[],
                     "hasMultipleChildren":false,"childCount":0,"hasGoogleCalendarConnected":false}
                    """));
        }
    }

    /** The platform mapping of one raw family_context child. */
    private String agentChild(JsonNode raw) throws Exception {
        java.util.Map<String, Object> c = new java.util.LinkedHashMap<>();
        c.put("childId", raw.path("child_id").asLong());
        c.put("name", raw.path("name").asText());
        c.put("age", raw.path("age").asInt());
        c.put("gender", raw.path("gender").isNull() ? null : raw.path("gender").asText());
        c.put("interests", json.convertValue(raw.path("interests"), java.util.List.class));
        return json.writeValueAsString(c);
    }
}
