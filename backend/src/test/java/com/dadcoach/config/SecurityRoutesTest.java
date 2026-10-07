package com.dadcoach.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every route Dad Coach serves, by who may call it. Public: the health probes, Meta's webhook (signature checked in
 * the controller) and the two calendar OAuth hops (HMAC checked in the controller). Each service surface opens
 * only with its own key. Everything else - including every route of the deleted features - is refused.
 */
class SecurityRoutesTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void theHealthProbesArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void theWebhookAndCalendarHopsAreReachableButCheckTheirOwnSignatures() throws Exception {
        mvc.perform(get("/webhook/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "nope")
                .param("hub.challenge", "1")).andExpect(status().isForbidden());
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/calendar/connect/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/calendar/connect/1").param("exp", "9999999999").param("sig", "forged")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/calendar/callback").param("code", "x").param("state", "1.2.3.4")).andExpect(status().isFound());
    }

    @Test
    void eachServiceSurfaceOpensOnlyWithItsOwnKey() throws Exception {
        String tool = "{\"execution_id\":\"e\",\"idempotency_key\":\"k\",\"user_id\":\"+19995550100\",\"parameters\":{}}";
        mvc.perform(post("/api/tools/get_activity_ideas").contentType(MediaType.APPLICATION_JSON).content(tool))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tools/get_activity_ideas").header("X-API-Key", CALLBACK_KEY).contentType(MediaType.APPLICATION_JSON).content(tool))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tools/get_activity_ideas").header("X-API-Key", ADMIN_KEY).contentType(MediaType.APPLICATION_JSON).content(tool))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tools/get_activity_ideas").header("X-API-Key", TOOL_KEY).contentType(MediaType.APPLICATION_JSON).content(tool))
                .andExpect(status().isOk());

        mvc.perform(post("/api/context/family_context").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/context/family_context").header("X-API-Key", TOOL_KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        String callback = "{\"triggerId\":\"t1\",\"userId\":\"whatsapp:+19995550100\",\"responseContent\":\"hi\"}";
        mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", TOOL_KEY)
                .header("X-Idempotency-Key", "scheduled-response:t1").contentType(MediaType.APPLICATION_JSON).content(callback))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:t1").contentType(MediaType.APPLICATION_JSON).content(callback))
                .andExpect(status().isNotFound()); // authenticated; the recipient is unknown

        mvc.perform(get("/api/v1/admin/fathers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/fathers").header("X-API-Key", TOOL_KEY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/fathers").header("X-API-Key", ADMIN_KEY)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/fathers/00000000-0000-0000-0000-000000000001")).andExpect(status().isUnauthorized());
    }

    /** Routes of the deleted features and anything unknown: refused, never served (these were open once). */
    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/test", "/api/admin/test/send", "/api/v1/dev/debug-config", "/api/v1/dev/test-send",
            "/api/v1/invitations", "/api/v1/onboarding/sessions", "/api/webhooks/trigger-notification",
            "/api/v1/fathers/me", "/api/v1/calendar/status/1", "/api/v1/calendar/events/1", "/api/profile/1",
            "/api/v1/workspace/summary", "/api/v1/activity-ideas", "/api/v1/auth/magic-link/verify", "/api/whatsapp/send",
            "/actuator/prometheus", "/actuator/env", "/actuator/metrics", "/swagger-ui.html", "/v3/api-docs", "/anything"})
    void everythingElseIsRefused(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
