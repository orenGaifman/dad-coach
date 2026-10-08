package com.dadcoach.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Outside the 24-hour window a session timer's message goes out in its own approved template, its values taken from
 * the session and its buttons carrying the session's payloads; a tap on such a button is handled like any session tap.
 * Until its own template is approved, the general template carries the message as before.
 */
class OwnTemplatesTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QualityTimeRepository sessions;

    private Father father;
    private Child noa;

    private void setUpFather(String phone) {
        father = data.activeFather(phone);
        noa = data.child(father, "נועה", 6);
        data.endpoint(father, false);
    }

    private QualityTime session(Child child, Duration fromNow, int minutes) {
        Instant start = clock.instant().plus(fromNow);
        return sessions.saveAndFlush(new QualityTime(father, child, start, start.plus(Duration.ofMinutes(minutes))));
    }

    private void approve(String name) {
        WhatsAppTemplateCatalog.Entry e = WhatsAppTemplateCatalog.require(name);
        jdbc.update("INSERT INTO template_messages (template_name, language, category, body, status, max_variables) "
                + "VALUES (?, 'he', 'UTILITY', ?, 'APPROVED', ?)", e.name(), e.body(), e.maxVariables());
    }

    private void callback(String triggerId, String state, String content) throws Exception {
        String body = "{\"triggerId\":\"" + triggerId + "\",\"workflowInstanceId\":\"w1\",\"userId\":\"whatsapp:" + father.getPhone()
                + "\",\"channel\":\"whatsapp\",\"targetStateKey\":\"" + state + "\",\"responseContent\":\"" + content + "\"}";
        mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
    }

    private JsonNode lastSend() throws Exception {
        return json.readTree(fake.metaSends().get(fake.metaSends().size() - 1).body());
    }

    private static JsonNode component(JsonNode sent, int index) {
        return sent.path("template").path("components").get(index);
    }

    @Test
    @DisplayName("the 1-hour reminder: its own template, the child as {{1}}, the ideas button carrying the session")
    void hourBefore() throws Exception {
        setUpFather("+19995550700");
        approve(WhatsAppTemplateCatalog.SESSION_HOUR_BEFORE_HE);
        QualityTime next = session(noa, Duration.ofMinutes(60), 45);

        callback("o-1", "SESSION_REMINDER_1H", "❤️ דאד קואץ׳:\\nעוד שעה הזמן שלך ושל נועה 🙂\\nיש כבר רעיון מה תעשו?");

        JsonNode sent = lastSend();
        assertThat(sent.path("type").asText()).isEqualTo("template");
        assertThat(sent.path("template").path("name").asText()).isEqualTo("dad_coach_session_hour_before_he");
        assertThat(sent.path("template").path("language").path("code").asText()).isEqualTo("he");
        assertThat(component(sent, 0).path("type").asText()).isEqualTo("body");
        assertThat(component(sent, 0).path("parameters").get(0).path("text").asText()).isEqualTo("נועה");
        assertThat(component(sent, 1).path("type").asText()).isEqualTo("button");
        assertThat(component(sent, 1).path("sub_type").asText()).isEqualTo("quick_reply");
        assertThat(component(sent, 1).path("index").asText()).isEqualTo("0");
        assertThat(component(sent, 1).path("parameters").get(0).path("payload").asText()).isEqualTo("dc:ideas:" + next.getId());
    }

    @Test
    @DisplayName("the follow-up: its own template with both buttons; a tap on 'היה מעולה' completes the session")
    void followUpAndTap() throws Exception {
        setUpFather("+19995550701");
        approve(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE);
        QualityTime ended = session(noa, Duration.ofMinutes(-90), 60);

        callback("o-2", "SESSION_FOLLOW_UP", "❤️ דאד קואץ׳:\\nנו, איך היה לכם עם נועה?");

        JsonNode sent = lastSend();
        assertThat(sent.path("template").path("name").asText()).isEqualTo("dad_coach_session_follow_up_he");
        assertThat(component(sent, 0).path("parameters").get(0).path("text").asText()).isEqualTo("נועה");
        assertThat(component(sent, 1).path("parameters").get(0).path("payload").asText()).isEqualTo("dc:done:" + ended.getId());
        assertThat(component(sent, 2).path("index").asText()).isEqualTo("1");
        assertThat(component(sent, 2).path("parameters").get(0).path("payload").asText()).isEqualTo("dc:missed:" + ended.getId());

        String tap = Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"from\":\"" + father.getPhone().substring(1)
                + "\",\"id\":\"wamid.tpl-tap\",\"timestamp\":\"1793700000\",\"type\":\"button\",\"button\":{\"payload\":\"dc:done:"
                + ended.getId() + "\",\"text\":\"היה מעולה\"}}]}");
        byte[] raw = tap.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM quality_time WHERE id = ?", String.class, ended.getId()))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("the morning reminder: every session today, earliest time first, every child")
    void morning() throws Exception {
        setUpFather("+19995550702");
        Child yuval = data.child(father, "יובל", 8);
        approve(WhatsAppTemplateCatalog.SESSION_MORNING_HE);
        session(yuval, Duration.ofHours(5), 60); // 17:00 local (the clock is Tuesday 12:00 in Israel)
        session(noa, Duration.ofHours(3), 60);   // 15:00
        session(noa, Duration.ofDays(1), 60);    // tomorrow: not today's

        callback("o-3", "SESSION_MORNING_REMINDER", "❤️ דאד קואץ׳:\\n*היום ב-15:00 ו-17:00* זה הזמן שלך ושל נועה ויובל 🙂");

        JsonNode sent = lastSend();
        assertThat(sent.path("template").path("name").asText()).isEqualTo("dad_coach_session_morning_he");
        assertThat(component(sent, 0).path("parameters").get(0).path("text").asText()).isEqualTo("15:00 ו-17:00");
        assertThat(component(sent, 0).path("parameters").get(1).path("text").asText()).isEqualTo("נועה ויובל");
        assertThat(sent.path("template").path("components")).hasSize(1);
    }

    @Test
    @DisplayName("not approved yet, or no session to fill it from: the general template carries the message, as before")
    void fallsBackToTheGeneralTemplate() throws Exception {
        setUpFather("+19995550703");
        session(noa, Duration.ofMinutes(60), 45);
        callback("o-4", "SESSION_REMINDER_1H", "❤️ דאד קואץ׳:\\nעוד שעה הזמן שלך ושל נועה 🙂\\nיש כבר רעיון מה תעשו?");
        assertThat(lastSend().path("template").path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(lastSend().toString()).contains("עוד שעה הזמן שלך ושל נועה 🙂 יש כבר רעיון מה תעשו?");

        approve(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE);
        callback("o-5", "SESSION_FOLLOW_UP", "❤️ דאד קואץ׳:\\nנו, איך היה לכם עם נועה?"); // nothing has ended
        assertThat(lastSend().path("template").path("name").asText()).isEqualTo(TEMPLATE);
    }
}
