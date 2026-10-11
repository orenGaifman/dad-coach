package com.dadcoach.api.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.auth.LoginLinkRateLimiter;
import com.dadcoach.domain.father.Father;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.support.FakeServers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * dad_dashboard_link (D-027): "תן לי דשבורד" -> a WhatsApp URL button to his page, as its own message. The token
 * never reaches the tool result; the link behind the button keeps working. D-053: the platform's Send API sends it -
 * the request carries the button, the text form for a refused button (the link on its own line) and, for outside the
 * window, the one line the platform's template mapping carries.
 */
class DashboardLinkToolTest extends AbstractIntegrationTest {

    private static final String PREFIX = "https://app.dadcoach.test/auth/consume#token=";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LoginLinkRateLimiter rateLimiter;

    @BeforeEach
    void freshBudget() {
        rateLimiter.reset();
    }

    private JsonNode tool(String userId, String idempotencyKey) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-" + UUID.randomUUID());
        body.put("idempotency_key", idempotencyKey);
        body.put("user_id", userId);
        body.put("execution_context", Map.of("currentStateKey", "ACTIVE_COACHING"));
        body.put("parameters", Map.of());
        MvcResult r = mvc.perform(post("/api/tools/dad_dashboard_link").header("X-API-Key", TOOL_KEY)
                .header("X-Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private JsonNode tool(String userId) throws Exception {
        return tool(userId, UUID.randomUUID().toString());
    }

    private JsonNode lastSend() {
        List<JsonNode> sends = fake.sendRequests("LOGIN_LINK");
        return sends.get(sends.size() - 1);
    }

    private int links() {
        return fake.sentMessages().size();
    }

    private Cookie signIn(String url) throws Exception {
        String token = url.substring(PREFIX.length());
        MvcResult r = mvc.perform(post("/api/auth/consume-link").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).as("the link signs in").isEqualTo(200);
        return r.getResponse().getCookie("DADCOACH_SESSION");
    }

    @Test
    @DisplayName("the button goes out as a cta_url message; the result has a note and no link; the link works again and again")
    void sendsTheButton() throws Exception {
        Father f = data.activeFather("+19995550301");
        data.endpoint(f, true);

        JsonNode result = tool("whatsapp:" + f.getPhone());

        assertThat(result.path("success").asBoolean()).isTrue();
        assertThat(result.path("data").path("sent").asBoolean()).isTrue();
        assertThat(result.path("data").path("delivery").asText()).isEqualTo("SENT");
        assertThat(result.path("data").path("note").asText()).contains("exactly the reply line").contains("Never write a link");
        assertThat(result.path("data").path("reply").asText()).isEqualTo(DashboardTools.SENT_REPLY);
        assertThat(result.toString()).doesNotContain("token").doesNotContain("http");

        assertThat(links()).isEqualTo(1);
        JsonNode sent = lastSend();
        assertThat(sent.path("to").asText()).isEqualTo("+19995550301");
        JsonNode content = sent.path("content");
        assertThat(content.path("type").asText()).isEqualTo("link_button");
        assertThat(content.path("text").asText())
                .startsWith("❤️ דאד קואץ׳:\n").contains("הדף שלך בדאד קואץ׳").doesNotContain("http");
        assertThat(content.path("footer").asText()).hasSizeLessThanOrEqualTo(60);
        assertThat(content.path("link").path("title").asText()).isEqualTo("כניסה לדף שלי").hasSizeLessThanOrEqualTo(20);
        String url = content.path("link").path("url").asText();
        assertThat(url).startsWith(PREFIX);

        assertThat(jdbc.queryForObject("SELECT delivery_status FROM login_link WHERE father_id = ?", String.class, f.getId()))
                .isEqualTo("SENT");
        Cookie first = signIn(url);
        Cookie second = signIn(url);
        assertThat(mvc.perform(get("/api/me").cookie(first)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/me").cookie(second)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a platform retry with the same idempotency key sends nothing twice")
    void idempotent() throws Exception {
        Father f = data.activeFather("+19995550302");
        data.endpoint(f, true);
        String key = UUID.randomUUID().toString();
        JsonNode first = tool(f.getPhone(), key);
        JsonNode replay = tool(f.getPhone(), key);
        assertThat(replay).isEqualTo(first);
        assertThat(fake.platformSends()).hasSize(1);
    }

    @Test
    @DisplayName("for a button Meta refuses for good, the request carries the text form: the link on its own line")
    void refusedButtonFallsBackToText() throws Exception {
        Father f = data.activeFather("+19995550303");
        data.endpoint(f, true);

        JsonNode result = tool(f.getPhone());

        assertThat(result.path("data").path("delivery").asText()).isEqualTo("SENT");
        String body = lastSend().path("fallbackText").asText();
        assertThat(body).startsWith("❤️ דאד קואץ׳:\n");
        assertThat(body.lines().filter(l -> l.startsWith(PREFIX)).count()).as("the link alone on its line").isEqualTo(1);
    }

    @Test
    @DisplayName("outside the 24-hour window the platform's template carries the link: one line, never a Meta name from here")
    void closedWindowUsesTheTemplate() throws Exception {
        Father f = data.activeFather("+19995550304");
        data.endpoint(f, false);

        JsonNode result = tool(f.getPhone());

        assertThat(result.path("data").path("delivery").asText()).isEqualTo("SENT");
        JsonNode sent = lastSend();
        assertThat(sent.path("windowPolicy").asText()).isEqualTo("AUTO");
        assertThat(sent.has("template")).isFalse();
        assertThat(sent.path("templateData").path("variant").asText()).isEmpty();
        String parameter = sent.path("templateData").path("named").path("text").asText();
        assertThat(parameter).contains(PREFIX).doesNotContain("\n");
    }

    @Test
    @DisplayName("his button from minutes ago is still on his screen: no second card, a reply line that answers what it is")
    void noSecondCardUnderTheFirst() throws Exception {
        Father f = data.activeFather("+19995550309");
        data.endpoint(f, true);
        assertThat(tool(f.getPhone()).path("data").path("delivery").asText()).isEqualTo("SENT");

        clock.advance(java.time.Duration.ofMinutes(1));  // "מה זה הכפתור הזה?" (prod 2026-10-08 12:47)
        JsonNode again = tool(f.getPhone());
        assertThat(again.path("success").asBoolean()).isTrue();
        assertThat(again.path("data").path("sent").asBoolean()).isFalse();
        assertThat(again.path("data").path("delivery").asText()).isEqualTo("ALREADY_SENT");
        assertThat(again.path("data").path("reply").asText()).isEqualTo(DashboardTools.ON_SCREEN_REPLY);
        assertThat(links()).isEqualTo(1);

        clock.advance(com.dadcoach.auth.LoginLinkService.ON_SCREEN);
        assertThat(tool(f.getPhone()).path("data").path("delivery").asText()).isEqualTo("SENT");
        assertThat(links()).isEqualTo(2);
    }

    @Test
    @DisplayName("a sixth try within 15 minutes is not sent: the note points to the recent one, which keeps working")
    void gentleRateLimit() throws Exception {
        Father f = data.activeFather("+19995550305");
        data.endpoint(f, true);
        fake.onPlatformSend(c -> new FakeServers.Reply(422, "{\"error\":\"messaging.param_invalid\",\"field\":\"content\"}"));
        for (int i = 0; i < 5; i++) {
            assertThat(tool(f.getPhone()).path("data").path("delivery").asText()).isEqualTo("FAILED");
        }
        JsonNode sixth = tool(f.getPhone());
        assertThat(sixth.path("success").asBoolean()).isTrue();
        assertThat(sixth.path("data").path("sent").asBoolean()).isFalse();
        assertThat(sixth.path("data").path("delivery").asText()).isEqualTo("RATE_LIMITED");
        assertThat(sixth.path("data").path("note").asText()).contains("keeps working");
    }

    @Test
    @DisplayName("no profile yet, or deactivated: a business failure the coach can explain, and nothing is sent")
    void onlyForAFatherWhoMaySignIn() throws Exception {
        JsonNode unknown = tool("+19995550306");
        assertThat(unknown.path("success").asBoolean()).isFalse();
        assertThat(unknown.path("error_code").asText()).isEqualTo("FATHER_NOT_FOUND");

        Father paused = data.father("+19995550307", "שאול", FatherStatus.PAUSED);
        data.endpoint(paused, true);
        JsonNode result = tool(paused.getPhone());
        assertThat(result.path("success").asBoolean()).isFalse();
        assertThat(result.path("error_code").asText()).isEqualTo("DASHBOARD_NOT_AVAILABLE");
        assertThat(fake.platformSends()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link", Long.class)).isZero();
    }

    @Test
    @DisplayName("a father who is also on the team gets one link for both")
    void fatherOnTheTeam() throws Exception {
        Father f = data.activeFather("+19995550308");
        data.endpoint(f, true);
        jdbc.update("INSERT INTO staff_user (id, phone, display_name) VALUES (?, ?, 'אורן')", UUID.randomUUID(), f.getPhone());

        tool(f.getPhone());

        String url = lastSend().path("content").path("link").path("url").asText();
        MvcResult me = mvc.perform(get("/api/me").cookie(signIn(url))).andReturn();
        JsonNode caps = json.readTree(me.getResponse().getContentAsByteArray()).path("capabilities");
        assertThat(caps.toString()).contains("father").contains("admin");
    }
}
