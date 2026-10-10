package com.dadcoach.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.dadcoach.whatsapp.WhatsAppAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Delivery regression baseline (Task 1.2): the platform's scheduled-response callback through ProactiveSender /
 * DeliveryService / WhatsAppAdapter to a controlled fake Meta (FakeServers), and what Dad Coach keeps about that
 * delivery afterwards - the wamid Meta answered with and Meta's later status receipts for it.
 *
 * <p>PASS tests pin the behaviour that was already correct. KNOWN-BUG tests stated the correct behaviour while the bug
 * was open (they were {@code @Disabled}); D-038 (Phase 2, docs/architecture/PHASE2_DADCOACH_SPEC.md) fixed them and
 * they run like every other test. Their names keep the bug ids for traceability.
 * The free-form and general-template happy paths are also in ScheduledResponseCallbackTest (status in the HTTP
 * answer, payload type) and the own-template path in OwnTemplatesTest; here the delivery ROW is pinned too.
 */
class ScheduledDeliveryBaselineTest extends AbstractIntegrationTest {

    private static final String WAMID = "wamid.HBgMOTcyNTAwMDAwMDAwFQIAERgSQkFTRUxJTkUtU0NIRUQtMQA=";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WhatsAppAdapter whatsapp;

    private MvcResult callback(Father f, String triggerId, String content) throws Exception {
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w-baseline",
                "userId", "whatsapp:" + f.getPhone(), "channel", "whatsapp", "targetStateKey", "ACTIVE_COACHING",
                "responseContent", content));
        return mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        int status = mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus();
        assertThat(status).as("Meta always gets its 200").isEqualTo(200);
    }

    private Map<String, Object> row(Father f, String triggerId) {
        return jdbc.queryForMap("SELECT status, delivery_mode, failure_reason FROM scheduled_response_delivery "
                + "WHERE father_id = ? AND trigger_id = ?", f.getId(), triggerId);
    }

    /** The whole delivery row as text (every column), so a wamid stored under any column name is found. */
    private String rowText(Father f, String triggerId) {
        return jdbc.queryForObject("SELECT d::text FROM scheduled_response_delivery d WHERE father_id = ? AND trigger_id = ?",
                String.class, f.getId(), triggerId);
    }

    /** A scheduled message sent free-form inside the window, Meta answering with {@link #WAMID}. */
    private Father deliveredFreeForm(String phone, String triggerId) throws Exception {
        Father f = data.activeFather(phone);
        data.endpoint(f, true);
        fake.onMetaSend(c -> FakeServers.Reply.json(Receipts.accepted(WAMID)));
        MvcResult r = callback(f, triggerId, "❤️ דאד קואץ׳:\nעוד שעה זמן איכות עם נועה 💪");
        assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(fake.metaSends()).hasSize(1);
        return f;
    }

    // ---- PASS: the successful delivery path ------------------------------------------------------------------------

    @Test
    @DisplayName("PASS: inside the 24h window the message goes out free-form once; the platform hears DELIVERED, the row is ACCEPTED / FREE_FORM")
    void insideTheWindowFreeFormAndTheRowIsAccepted() throws Exception {
        Father f = data.activeFather("+19995550701");
        data.endpoint(f, true);

        MvcResult r = callback(f, "bl-in-1", "❤️ דאד קואץ׳:\nעוד שעה זמן איכות עם נועה 💪");

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(fake.metaSends()).hasSize(1);
        JsonNode sent = json.readTree(fake.metaSends().get(0).body());
        assertThat(sent.path("to").asText()).isEqualTo("19995550701");
        assertThat(sent.path("type").asText()).isIn("text", "interactive");
        assertThat(sent.toString()).contains("עוד שעה זמן איכות עם נועה");
        Map<String, Object> row = row(f, "bl-in-1");
        // D-038 (DC-B2): Meta's API accepting the send is ACCEPTED; DELIVERED only comes from Meta's delivery receipt
        assertThat(row.get("status")).isEqualTo("ACCEPTED");
        assertThat(row.get("delivery_mode")).isEqualTo("FREE_FORM");
        assertThat(row.get("failure_reason")).isNull();
    }

    @Test
    @DisplayName("PASS: outside the window the approved general template goes out with the message as {{1}}; row ACCEPTED / TEMPLATE")
    void outsideTheWindowTheApprovedTemplateCarriesIt() throws Exception {
        Father f = data.activeFather("+19995550702");
        data.endpoint(f, false);
        String content = "❤️ דאד קואץ׳:\nבוקר טוב!\nהיום ב-18:00 עם נועה";

        MvcResult r = callback(f, "bl-out-1", content);

        assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asText()).isEqualTo("DELIVERED");
        // exactly one send reached Meta: the template (the refused free-form attempt never left Dad Coach)
        assertThat(fake.metaSends()).hasSize(1);
        JsonNode sent = json.readTree(fake.metaSends().get(0).body());
        assertThat(sent.path("type").asText()).isEqualTo("template");
        assertThat(sent.path("to").asText()).isEqualTo("19995550702");
        assertThat(sent.path("template").path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(sent.path("template").path("language").path("code").asText()).isEqualTo("he");
        JsonNode body = sent.path("template").path("components").get(0);
        assertThat(body.path("type").asText()).isEqualTo("body");
        assertThat(body.path("parameters")).hasSize(1);
        assertThat(body.path("parameters").get(0).path("text").asText())
                .isEqualTo(ProactiveSender.asTemplateParameter(content))
                .isEqualTo("בוקר טוב! היום ב-18:00 עם נועה");
        Map<String, Object> row = row(f, "bl-out-1");
        assertThat(row.get("status")).isEqualTo("ACCEPTED"); // D-038: accepted by Meta's API, no delivery receipt yet
        assertThat(row.get("delivery_mode")).isEqualTo("TEMPLATE");
    }

    @Test
    @DisplayName("PASS: Meta refusing the send leaves the row FAILED with Meta's reason, and a replay never re-sends")
    void metaRefusingTheSendIsRecordedAsFailed() throws Exception {
        Father f = data.activeFather("+19995550703");
        data.endpoint(f, true);
        fake.onMetaSend(c -> new FakeServers.Reply(400,
                "{\"error\":{\"message\":\"(#131026) Message undeliverable\",\"code\":131026}}"));

        MvcResult r = callback(f, "bl-refused-1", "❤️ דאד קואץ׳:\nעוד שעה זמן איכות עם נועה 💪");
        fake.reset(); // Meta is fine again: the replay must still not send
        MvcResult again = callback(f, "bl-refused-1", "❤️ דאד קואץ׳:\nעוד שעה זמן איכות עם נועה 💪");

        assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asText()).isEqualTo("FAILED");
        Map<String, Object> row = row(f, "bl-refused-1");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat((String) row.get("failure_reason")).contains("131026");
        assertThat(json.readTree(again.getResponse().getContentAsString()).path("status").asText()).isEqualTo("FAILED");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PASS: a status receipt for a wamid Dad Coach never sent is acknowledged and changes nothing")
    void aReceiptForAnUnknownWamidChangesNothing() throws Exception {
        Father f = deliveredFreeForm("+19995550704", "bl-unknown-1");
        String before = rowText(f, "bl-unknown-1");

        webhook(Receipts.failed("wamid.someone-else", f.getPhone(), 131047, "Re-engagement message"));
        webhook(Receipts.of("wamid.someone-else", "read", f.getPhone()));

        assertThat(rowText(f, "bl-unknown-1")).isEqualTo(before);
        assertThat(row(f, "bl-unknown-1").get("status")).isEqualTo("ACCEPTED"); // D-038: as Meta's API left it
        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).hasSize(1);
    }

    // ---- KNOWN-BUG DC-B1: the wamid is not kept ---------------------------------------------------------------------

    /**
     * DC-B1. Meta answers an accepted send with the message's wamid; WhatsAppApiClient reads it
     * (WhatsAppApiClient.java:135, extractMessageId) and WhatsAppAdapter returns it in DeliveryResult.sent(wamid)
     * (WhatsAppAdapter.java:133). ScheduledResponseDeliveryService.deliver (ScheduledResponseDeliveryService.java:95-96)
     * only calls markDelivered(mode): scheduled_response_delivery has no column for it
     * (V21__create_scheduled_response_delivery_table.sql) and ScheduledResponseDelivery has no field. So no later
     * receipt from Meta can ever be matched to the delivery, and support cannot look a message up at Meta.
     * Correct: the delivery row holds the wamid (any column - the row is read as text).
     */
    @Test
    @DisplayName("KNOWN-BUG DC-B1: after a successful scheduled send the Meta wamid is stored on the delivery row")
    void theWamidIsPersistedOnTheDeliveryRow() throws Exception {
        Father f = deliveredFreeForm("+19995550705", "bl-wamid-1");

        assertThat(rowText(f, "bl-wamid-1"))
                .as("DC-B1: scheduled_response_delivery row should hold the Meta message id %s", WAMID)
                .contains(WAMID);
    }

    // ---- KNOWN-BUG DC-B2: status receipts are dropped ---------------------------------------------------------------

    /**
     * DC-B2. Meta reports a message it accepted but could not deliver with a "failed" status receipt (131047: more
     * than 24 hours since the father last wrote). WhatsAppMessageParser parses it into a StatusUpdateDto, and
     * WhatsAppWebhookController.receive (WhatsAppWebhookController.java:92-94) only logs "whatsapp.receipts.ignored".
     * The delivery stays DELIVERED forever although the father never got the message.
     * Correct: the delivery is marked FAILED, with Meta's error code as the reason.
     */
    @Test
    @DisplayName("KNOWN-BUG DC-B2: a 'failed' receipt from Meta marks the scheduled delivery FAILED")
    void aFailedReceiptMarksTheDeliveryFailed() throws Exception {
        Father f = deliveredFreeForm("+19995550706", "bl-failed-1");

        webhook(Receipts.failed(WAMID, f.getPhone(), 131047, "Re-engagement message"));

        Map<String, Object> row = row(f, "bl-failed-1");
        assertThat(row.get("status"))
                .as("DC-B2: Meta said the message failed (131047) - the delivery must not stay DELIVERED")
                .isEqualTo("FAILED");
        assertThat((String) row.get("failure_reason")).contains("131047");
    }

    /**
     * DC-B2 (status lookup). ChannelAdapter.getDeliveryStatus promises "the current delivery status" of a sent
     * message; WhatsAppAdapter.getDeliveryStatus (WhatsAppAdapter.java:165-171) always answers PENDING, and the
     * receipts that would feed it are dropped (WhatsAppWebhookController.java:92-94).
     * Correct: after a "failed" receipt the wamid's status is FAILED.
     */
    @Test
    @DisplayName("KNOWN-BUG DC-B2: after a 'failed' receipt the adapter reports the message FAILED")
    void theAdapterReportsFailedAfterAFailedReceipt() throws Exception {
        Father f = deliveredFreeForm("+19995550707", "bl-failed-2");

        webhook(Receipts.failed(WAMID, f.getPhone(), 131047, "Re-engagement message"));

        assertThat(whatsapp.getDeliveryStatus(WAMID))
                .as("DC-B2: getDeliveryStatus(%s) after Meta's 'failed' receipt", WAMID)
                .isEqualTo(DeliveryStatus.FAILED);
    }

    /**
     * DC-B2 (delivered / read). The same dropped receipts (WhatsAppWebhookController.java:92-94): "delivered" and
     * "read" never reach the delivery, and WhatsAppAdapter.getDeliveryStatus (WhatsAppAdapter.java:165-171) is
     * hard-coded PENDING. Correct: the delivery follows Meta - DELIVERED after "delivered", READ after "read" - and
     * the delivery row records it (it changes).
     */
    @Test
    @DisplayName("KNOWN-BUG DC-B2: 'delivered' then 'read' receipts update the delivery's status")
    void deliveredAndReadReceiptsUpdateTheDelivery() throws Exception {
        Father f = deliveredFreeForm("+19995550708", "bl-read-1");
        String sentRow = rowText(f, "bl-read-1");

        webhook(Receipts.of(WAMID, "delivered", f.getPhone()));
        assertThat(whatsapp.getDeliveryStatus(WAMID))
                .as("DC-B2: getDeliveryStatus(%s) after Meta's 'delivered' receipt", WAMID)
                .isEqualTo(DeliveryStatus.DELIVERED);
        String deliveredRow = rowText(f, "bl-read-1");

        webhook(Receipts.of(WAMID, "read", f.getPhone()));
        assertThat(whatsapp.getDeliveryStatus(WAMID))
                .as("DC-B2: getDeliveryStatus(%s) after Meta's 'read' receipt", WAMID)
                .isEqualTo(DeliveryStatus.READ);
        assertThat(rowText(f, "bl-read-1"))
                .as("DC-B2: the delivery row records the 'read' receipt")
                .isNotEqualTo(deliveredRow).isNotEqualTo(sentRow);
    }
}
