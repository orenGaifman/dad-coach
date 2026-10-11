package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.auth.LoginLinkRateLimiter;
import com.dadcoach.auth.LoginLinkRepository;
import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-038 (DC-B1/DC-B2) beyond the baseline: Meta's receipts only move a recorded message forward, a message the shared
 * number's gateway held is kept apart from Meta's ids, and a dashboard link's receipts never change the "already on
 * his screen" status the coach reads.
 */
class DeliveryReceiptsTest extends AbstractIntegrationTest {

    private static final String WAMID = "wamid.HBgMRDM4LVJFQ0VJUFRTLTEA";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WhatsAppAdapter whatsapp;
    @Autowired LoginLinkService links;
    @Autowired LoginLinkRepository linkRows;
    @Autowired LoginLinkRateLimiter rateLimiter;
    @Autowired DeliveryReceipts receipts;

    @BeforeEach
    void freshBudget() {
        rateLimiter.reset();
    }

    private MvcResult callback(Father f, String triggerId) throws Exception {
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w-d038",
                "userId", "whatsapp:" + f.getPhone(), "channel", "whatsapp", "targetStateKey", "ACTIVE_COACHING",
                "responseContent", "❤️ דאד קואץ׳:\nעוד שעה זמן איכות עם נועה 💪"));
        return mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private void receipt(String wamid, String status, Father f) throws Exception {
        webhook(Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"15550000000\","
                + "\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid + "\",\"status\":\"" + status
                + "\",\"timestamp\":\"1793700100\",\"recipient_id\":\"" + f.getPhone().substring(1) + "\"}]}"));
    }

    private void failedReceipt(String wamid, Father f) throws Exception {
        webhook(Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"15550000000\","
                + "\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid + "\",\"status\":\"failed\","
                + "\"timestamp\":\"1793700100\",\"recipient_id\":\"" + f.getPhone().substring(1) + "\",\"errors\":[{\"code\":131047,"
                + "\"title\":\"Re-engagement message\"}]}]}"));
    }

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    private Map<String, Object> row(Father f, String triggerId) {
        return jdbc.queryForMap("SELECT status, failure_reason, provider_message_id, gateway_hold_id FROM "
                + "scheduled_response_delivery WHERE father_id = ? AND trigger_id = ?", f.getId(), triggerId);
    }

    private Father accepted(String phone, String triggerId) throws Exception {
        Father f = data.activeFather(phone);
        data.endpoint(f, true);
        fake.onMetaSend(c -> FakeServers.Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"" + WAMID + "\"}]}"));
        callback(f, triggerId);
        assertThat(row(f, triggerId).get("status")).isEqualTo("ACCEPTED");
        return f;
    }

    @Test
    void receiptsOnlyMoveForwardAndFailedNeverOverridesARead() throws Exception {
        Father f = accepted("+19995550801", "d038-fwd");

        receipt(WAMID, "read", f);
        receipt(WAMID, "delivered", f);  // arrives late: no step back
        receipt(WAMID, "sent", f);
        failedReceipt(WAMID, f);          // after READ: ignored

        assertThat(row(f, "d038-fwd").get("status")).isEqualTo("READ");
        assertThat(row(f, "d038-fwd").get("failure_reason")).isNull();
        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.READ);
        // a replayed callback still tells the platform it was delivered, and sends nothing
        fake.reset();
        assertThat(json.readTree(callback(f, "d038-fwd").getResponse().getContentAsString()).path("status").asText())
                .isEqualTo("DELIVERED");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void sentThenFailedIsFailedAndTheReplayedCallbackSaysSo() throws Exception {
        Father f = accepted("+19995550802", "d038-sent-failed");

        receipt(WAMID, "sent", f);
        assertThat(row(f, "d038-sent-failed").get("status")).isEqualTo("SENT");
        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.SENT);
        failedReceipt(WAMID, f);
        receipt(WAMID, "delivered", f); // FAILED is final

        assertThat(row(f, "d038-sent-failed").get("status")).isEqualTo("FAILED");
        assertThat((String) row(f, "d038-sent-failed").get("failure_reason")).isEqualTo("META_131047: Re-engagement message");
        fake.reset();
        assertThat(json.readTree(callback(f, "d038-sent-failed").getResponse().getContentAsString()).path("status").asText())
                .isEqualTo("FAILED");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void aMessageTheGatewayHeldIsHeldNotDeliveredAndItsIdIsNeverAWamid() throws Exception {
        Father f = data.activeFather("+19995550803");
        data.endpoint(f, true);
        fake.onGate(c -> FakeServers.Reply.json(
                "{\"send\":false,\"heldId\":77,\"route\":\"dad-coach\",\"activeRoute\":\"tair\",\"why\":\"ON_ANOTHER_PRODUCT\"}"));

        MvcResult r = callback(f, "d038-held");

        assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asText()).isEqualTo("DELIVERED");
        Map<String, Object> row = row(f, "d038-held");
        assertThat(row.get("status")).isEqualTo("HELD");
        assertThat(row.get("gateway_hold_id")).isEqualTo("held:77");
        assertThat(row.get("provider_message_id")).isNull();
        assertThat(fake.metaSends()).isEmpty();
        receipt("held:77", "failed", f);
        assertThat(row(f, "d038-held").get("status")).isEqualTo("HELD");
        assertThat(whatsapp.getDeliveryStatus("held:77")).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void aLinksReceiptsKeepItSentForTheCoachUntilMetaSaysItFailed() throws Exception {
        Father f = data.activeFather("+19995550804");
        data.endpoint(f, true);
        fake.onMetaSend(c -> FakeServers.Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"" + WAMID + "\"}]}"));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.SENT);

        receipt(WAMID, "delivered", f);
        failedReceipt(WAMID, f); // after a delivery receipt: ignored

        assertThat(jdbc.queryForMap("SELECT delivery_status, receipt_status, delivery_error FROM login_link WHERE father_id = ?",
                f.getId())).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "DELIVERED")
                .containsEntry("delivery_error", null);
        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.DELIVERED);
        // the coach still sees his button on the screen: no second card
        assertThat(linkRows.sentToFatherSince(f.getId(), clock.instant().minusSeconds(60))).isTrue();
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.ALREADY_SENT);
    }

    @Test
    void aLinkMetaFailedIsSentAgainWhenAsked() throws Exception {
        Father f = data.activeFather("+19995550805");
        data.endpoint(f, true);
        fake.onMetaSend(c -> FakeServers.Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"" + WAMID + "\"}]}"));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);

        failedReceipt(WAMID, f);

        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.FAILED);
        fake.onMetaSend(c -> FakeServers.Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.d038.again\"}]}"));
        assertThat(links.sendTo(f.getPhone())).as("the failed button is not 'on his screen'").isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.metaSends()).hasSize(2);
    }

    private void acceptedRow(Father f, String triggerId, String wamid) {
        jdbc.update("INSERT INTO scheduled_response_delivery (idempotency_key, trigger_id, father_id, status, delivery_mode, "
                + "provider_message_id) VALUES (?, ?, ?, 'ACCEPTED', 'FREE_FORM', ?)", "scheduled-response:" + triggerId, triggerId,
                f.getId(), wamid);
    }

    /** Meta's "failed" can come before the row with the wamid is committed: it is applied once the row is there. */
    @Test
    void aFailedReceiptThatCameBeforeItsRowIsAppliedWhenTheRowAppears() throws Exception {
        Father f = data.activeFather("+19995550806");
        failedReceipt("wamid.d038.early", f);
        acceptedRow(f, "d038-early", "wamid.d038.early");

        receipts.retryPending();

        assertThat(row(f, "d038-early").get("status")).isEqualTo("FAILED");
        assertThat((String) row(f, "d038-early").get("failure_reason")).contains("131047");
    }

    @Test
    void anEarlyFailedReceiptIsKeptOnlyForAWhileAndOnlyForAnUnknownWamid() throws Exception {
        Father f = accepted("+19995550807", "d038-kept");
        receipt(WAMID, "delivered", f);
        failedReceipt(WAMID, f);               // its row exists (DELIVERED): ignored, not kept for later
        failedReceipt("wamid.d038.late", f);    // no row yet: kept
        clock.advance(DeliveryReceipts.PENDING_FOR.plusSeconds(1));
        acceptedRow(f, "d038-late", "wamid.d038.late");

        receipts.retryPending();

        assertThat(row(f, "d038-kept").get("status")).isEqualTo("DELIVERED");
        assertThat(row(f, "d038-late").get("status")).as("too late: dropped").isEqualTo("ACCEPTED");
    }

    @Test
    void aWamidDadCoachNeverRecordedIsPending() {
        assertThat(whatsapp.getDeliveryStatus("wamid.unknown")).isEqualTo(DeliveryStatus.PENDING);
        assertThat(whatsapp.getDeliveryStatus(null)).isEqualTo(DeliveryStatus.PENDING);
    }
}
