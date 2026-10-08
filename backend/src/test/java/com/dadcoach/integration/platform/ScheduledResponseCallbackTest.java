package com.dadcoach.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The platform's scheduled-response callback (playbook §29-30): delivered once per trigger, channel-compliant. */
class ScheduledResponseCallbackTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    MvcResult callback(String triggerId, String key, String userId, String content) throws Exception {
        return callback(triggerId, key, userId, content, "ACTIVE_COACHING");
    }

    MvcResult callback(String triggerId, String key, String userId, String content, String state) throws Exception {
        String body = "{\"triggerId\":\"" + triggerId + "\",\"workflowInstanceId\":\"w1\",\"userId\":\"" + userId
                + "\",\"channel\":\"whatsapp\",\"targetStateKey\":\"" + state + "\",\"responseContent\":\"" + content + "\"}";
        return mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    @Test
    void insideTheWindowItIsSentOnceAndARepeatedCallbackReplays() throws Exception {
        Father f = data.activeFather("+19995550400");
        data.endpoint(f, true);
        MvcResult first = callback("t-1", "scheduled-response:t-1", "whatsapp:" + f.getPhone(), "עוד שעה זמן איכות עם נועה 💪");
        MvcResult again = callback("t-1", "scheduled-response:t-1", "whatsapp:" + f.getPhone(), "עוד שעה זמן איכות עם נועה 💪");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(first.getResponse().getContentAsString()).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(json.readTree(again.getResponse().getContentAsString()).path("detail").asText()).isEqualTo("Already delivered");
        assertThat(fake.metaSends()).hasSize(1);
        JsonNode sent = json.readTree(fake.metaSends().get(0).body());
        assertThat(sent.path("type").asText()).isEqualTo("text");
        assertThat(sent.path("to").asText()).isEqualTo("19995550400");
    }

    @Test
    void aFatherWithNoEndpointGetsOneAndOutsideTheWindowTheTemplateCarriesTheMessage() throws Exception {
        Father f = data.activeFather("+19995550401"); // onboarded on WhatsApp before F1: no endpoint row
        MvcResult r = callback("t-2", "scheduled-response:t-2", "whatsapp:" + f.getPhone(),
                "❤️ דאד קואץ׳:\\nבוקר טוב!\\nהיום ב-18:00 עם נועה");
        assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_endpoints WHERE channel_identity = ?", Integer.class,
                f.getPhone())).isEqualTo(1);
        JsonNode sent = json.readTree(fake.metaSends().get(0).body());
        assertThat(sent.path("type").asText()).isEqualTo("template");
        assertThat(sent.path("template").path("name").asText()).isEqualTo(TEMPLATE);
        // D-032: one readable line; the template body carries the identity line, so it is not repeated in {{1}}
        assertThat(sent.toString()).contains("\"בוקר טוב! היום ב-18:00 עם נועה\"").doesNotContain("דאד קואץ׳:");
    }

    @Test
    void aWrongIdempotencyKeyAnUnknownRecipientOrABlankMessageIsRefusedAndSendsNothing() throws Exception {
        Father f = data.activeFather("+19995550402");
        assertThat(callback("t-3", "other", "whatsapp:" + f.getPhone(), "x").getResponse().getStatus()).isEqualTo(400);
        assertThat(callback("t-4", "scheduled-response:t-4", "whatsapp:+19995550499", "x").getResponse().getStatus()).isEqualTo(404);
        assertThat(callback("t-5", "scheduled-response:t-5", "whatsapp:" + f.getPhone(), " ").getResponse().getStatus()).isEqualTo(400);
        assertThat(fake.metaSends()).isEmpty();
    }
}
