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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A session timer's message names its own message type for outside the 24-hour window ({@code templateData}, D-053): the
 * values taken from the session and the quick-reply payloads carrying the session's ids; a tap on such a button is
 * handled like any session tap. The platform's kind→template mapping (the seed: the session template, then the general
 * {@code dad_coach_update_he} with {@code named.text}) picks the approved Meta template - Dad Coach never names one.
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

    private void callback(String triggerId, String state, String content) throws Exception {
        String body = "{\"triggerId\":\"" + triggerId + "\",\"workflowInstanceId\":\"w1\",\"userId\":\"whatsapp:" + father.getPhone()
                + "\",\"channel\":\"whatsapp\",\"targetStateKey\":\"" + state + "\",\"responseContent\":\"" + content + "\"}";
        mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
    }

    private JsonNode lastSend() {
        List<JsonNode> sends = fake.sendRequests("SCHEDULED");
        assertThat(sends).isNotEmpty();
        JsonNode sent = sends.get(sends.size() - 1);
        assertThat(sent.has("template")).as("D-053: never a Meta template name").isFalse();
        assertThat(sent.has("fallbackTemplates")).isFalse();
        return sent;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(v -> out.add(v.asText()));
        return out;
    }

    @Test
    @DisplayName("the 1-hour reminder: its own message type, the child as {{1}}, the ideas payload carrying the session")
    void hourBefore() throws Exception {
        setUpFather("+19995550700");
        QualityTime next = session(noa, Duration.ofMinutes(60), 45);

        callback("o-1", "SESSION_REMINDER_1H", "❤️ דאד קואץ׳:\\nעוד שעה הזמן שלך ושל נועה 🙂\\nיש כבר רעיון מה תעשו?");

        JsonNode data = lastSend().path("templateData");
        assertThat(data.path("variant").asText()).isEqualTo("session_hour_before_he");
        assertThat(texts(data.path("values"))).containsExactly("נועה");
        assertThat(texts(data.path("payloads"))).containsExactly("dc:ideas:" + next.getId());
        assertThat(data.path("named").path("text").asText()).isEqualTo("עוד שעה הזמן שלך ושל נועה 🙂 יש כבר רעיון מה תעשו?");
    }

    @Test
    @DisplayName("the follow-up: its own message type with both payloads; a tap on 'היה מעולה' completes the session")
    void followUpAndTap() throws Exception {
        setUpFather("+19995550701");
        QualityTime ended = session(noa, Duration.ofMinutes(-90), 60);

        callback("o-2", "SESSION_FOLLOW_UP", "❤️ דאד קואץ׳:\\nנו, איך היה לכם עם נועה?");

        JsonNode data = lastSend().path("templateData");
        assertThat(data.path("variant").asText()).isEqualTo("session_follow_up_he");
        assertThat(texts(data.path("values"))).containsExactly("נועה");
        assertThat(texts(data.path("payloads"))).containsExactly("dc:done:" + ended.getId(), "dc:missed:" + ended.getId());

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
        session(yuval, Duration.ofHours(5), 60); // 17:00 local (the clock is Tuesday 12:00 in Israel)
        session(noa, Duration.ofHours(3), 60);   // 15:00
        session(noa, Duration.ofDays(1), 60);    // tomorrow: not today's

        callback("o-3", "SESSION_MORNING_REMINDER", "❤️ דאד קואץ׳:\\n*היום ב-15:00 וב-17:00* זה הזמן שלך ושל נועה ויובל 🙂");

        JsonNode data = lastSend().path("templateData");
        assertThat(data.path("variant").asText()).isEqualTo("session_morning_he");
        assertThat(texts(data.path("values"))).containsExactly("15:00 וב-17:00", "נועה ויובל");
        assertThat(data.has("payloads")).isFalse();
        // D-067: the dashboard-button template (mapped first) takes the sign-in token as its URL suffix; the two after
        // it read the values and named.text as before
        String token = data.path("named").path("link").asText();
        assertThat(token).isNotBlank();
        assertThat(lastSend().path("content").path("link").path("url").asText()).endsWith("/auth/consume#token=" + token);
        assertThat(data.path("named").path("text").asText()).isNotBlank();
        assertThat(com.dadcoach.channel.template.WhatsAppTemplateCatalog.require("dad_coach_session_morning_he")
                .render(List.of("15:00 וב-17:00", "נועה ויובל")))
                .isEqualTo("❤️ דאד קואץ׳:\n*היום ב-15:00 וב-17:00* זה הזמן שלך ושל נועה ויובל 🙂");
    }

    @Test
    @DisplayName("no session to fill its own template from: the kind's default message type, the message as one line")
    void noSessionIsTheDefaultMessageType() throws Exception {
        setUpFather("+19995550703");
        callback("o-5", "SESSION_FOLLOW_UP", "❤️ דאד קואץ׳:\\nנו, איך היה לכם עם נועה?"); // nothing has ended
        assertThat(fake.platformSends()).as("no session to follow up: skipped, nothing sent").isEmpty();

        callback("o-6", "ACTIVE_COACHING", "❤️ דאד קואץ׳:\\nהיי, מה המצב השבוע?");
        JsonNode data = lastSend().path("templateData");
        assertThat(data.path("variant").asText()).isEmpty();
        assertThat(data.has("values")).isFalse();
        assertThat(data.has("payloads")).isFalse();
        assertThat(data.path("named").path("text").asText()).isEqualTo("היי, מה המצב השבוע?");
    }
}
