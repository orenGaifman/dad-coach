package com.dadcoach.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.auth.LoginLinkRateLimiter;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
 * Delivery regression baseline (Task 1.2): the dashboard link (dad_dashboard_link, D-027) as a delivery - its
 * login_link row says SENT once the button was accepted, keeps the wamid and follows what became of it. D-053: the
 * platform's Send API sends it and its delivery reports (never Meta's receipts on Dad Coach's webhook) move the row.
 *
 * <p>PASS tests pin the behaviour that was already correct. KNOWN-BUG tests stated the correct behaviour while the bug
 * was open (they were {@code @Disabled}); D-038 (Phase 2, docs/architecture/PHASE2_DADCOACH_SPEC.md) fixed them and
 * they run like every other test. Their names keep the bug ids for traceability.
 */
class LoginLinkDeliveryBaselineTest extends AbstractIntegrationTest {

    private static final String WAMID = "wamid.HBgMOTcyNTAwMDAwMDAwFQIAERgSQkFTRUxJTkUtTElOSy0xAA==";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LoginLinkRateLimiter rateLimiter;
    @Autowired com.dadcoach.integration.platform.messaging.MessagingSettings messaging;

    @BeforeEach
    void freshBudget() {
        rateLimiter.reset();
    }

    private JsonNode linkTool(Father f) throws Exception {
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("execution_id", "exec-" + UUID.randomUUID());
        body.put("idempotency_key", key);
        body.put("user_id", "whatsapp:" + f.getPhone());
        body.put("execution_context", Map.of("currentStateKey", "ACTIVE_COACHING"));
        body.put("parameters", Map.of());
        MvcResult r = mvc.perform(post("/api/tools/dad_dashboard_link").header("X-API-Key", TOOL_KEY)
                .header("X-Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        int status = mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus();
        assertThat(status).as("Meta always gets its 200").isEqualTo(200);
    }

    private String linkStatus(Father f) {
        return jdbc.queryForObject("SELECT delivery_status FROM login_link WHERE father_id = ?", String.class, f.getId());
    }

    /** The whole login_link row as text (every column), so a wamid stored under any column name is found. */
    private String linkRowText(Father f) {
        return jdbc.queryForObject("SELECT l::text FROM login_link l WHERE father_id = ?", String.class, f.getId());
    }

    /** The dashboard button sent inside the window, the platform answering with {@link #WAMID}. */
    private Father sentLink(String phone) throws Exception {
        Father f = data.activeFather(phone);
        data.endpoint(f, true);
        fake.onPlatformSend(c -> FakeServers.platformAnswer(200, MESSAGE_ID, "ACCEPTED", null, WAMID));
        linkTool(f);
        assertThat(fake.platformSends()).hasSize(1);
        assertThat(fake.sendRequests().get(0).path("content").path("type").asText()).isEqualTo("link_button");
        return f;
    }

    private static final String MESSAGE_ID = "6f1f0d3e-0000-4000-8000-000000000721";

    /** The platform's delivery report for the link (D-053: the only source of its fate). */
    private void report(Father f, String status, String reason) throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        UUID linkId = jdbc.queryForObject("SELECT id FROM login_link WHERE father_id = ?", UUID.class, f.getId());
        String body = json.writeValueAsString(Map.of("messageId", MESSAGE_ID, "idempotencyKey", "login-link:" + linkId,
                "kind", "LOGIN_LINK", "status", status, "providerMessageId", WAMID, "reason", reason));
        int code = mvc.perform(post("/api/integration/channel/delivery-status").contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", CALLBACK_KEY).header("X-Idempotency-Key", "delivery:" + MESSAGE_ID + ":" + status)
                .content(body)).andReturn().getResponse().getStatus();
        assertThat(code).isEqualTo(200);
    }

    @Test
    @DisplayName("PASS: the dashboard button the platform accepted is recorded SENT; one it refuses is FAILED")
    void theLinkRowFollowsMetasAnswerToTheSend() throws Exception {
        Father ok = sentLink("+19995550721");
        assertThat(linkStatus(ok)).isEqualTo("SENT");

        Father refused = data.activeFather("+19995550722");
        data.endpoint(refused, true);
        fake.onPlatformSend(c -> new FakeServers.Reply(422, "{\"error\":\"messaging.param_invalid\",\"field\":\"content\"}"));
        linkTool(refused);
        assertThat(linkStatus(refused)).isEqualTo("FAILED");
        fake.reset();
        Father after = data.activeFather("+19995550723");
        data.endpoint(after, true);
        linkTool(after);
        assertThat(linkStatus(after)).isEqualTo("SENT");
    }

    /**
     * DC-B1. LoginLinkService.issueAndSend (LoginLinkService.java:134-140) records only
     * link.recordDelivery(result.isSuccessful(), result.failureReason()); the wamid in DeliveryResult.providerMessageId
     * (WhatsAppAdapter.java:133) is dropped - login_link has no column for it (V30__dashboard_sign_in.sql,
     * V33__reusable_login_links.sql). Correct: the login_link row holds the wamid of the message that carried it.
     */
    @Test
    @DisplayName("KNOWN-BUG DC-B1: the sent dashboard link keeps Meta's wamid")
    void theLinkRowKeepsTheWamid() throws Exception {
        Father f = sentLink("+19995550724");

        assertThat(linkRowText(f))
                .as("DC-B1: login_link row should hold the Meta message id %s", WAMID)
                .contains(WAMID);
    }

    /**
     * DC-B2. A dashboard button Meta accepted and then failed to deliver (e.g. 131047) stayed delivery_status SENT:
     * WhatsAppWebhookController.receive dropped every receipt (WhatsAppWebhookController.java:92-94). D-053: the
     * platform reports it (FAILED with Meta's code); a receipt on Dad Coach's webhook changes nothing.
     * The coach then tells him his button "is right above" (LoginLinkService ALREADY_SENT, LoginLinkService.java:99)
     * although it never arrived, and the admin failed-links view (idx_login_link_failed) never shows it.
     * Correct: the link is marked FAILED with Meta's reason.
     */
    @Test
    @DisplayName("KNOWN-BUG DC-B2: the platform's FAILED report for the dashboard link marks it FAILED")
    void aFailedReceiptMarksTheLinkFailed() throws Exception {
        Father f = sentLink("+19995550725");
        assertThat(linkStatus(f)).isEqualTo("SENT");

        webhook(Receipts.failed(WAMID, f.getPhone(), 131047, "Re-engagement message"));
        assertThat(linkStatus(f)).as("D-053: Meta's receipts are the platform's").isEqualTo("SENT");
        report(f, "FAILED", "META_131047");

        assertThat(linkStatus(f))
                .as("DC-B2: Meta said the dashboard-link message failed (131047) - login_link must not stay SENT")
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT delivery_error FROM login_link WHERE father_id = ?", String.class, f.getId()))
                .contains("131047");
    }
}
