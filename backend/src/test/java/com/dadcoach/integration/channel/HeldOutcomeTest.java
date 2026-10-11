package com.dadcoach.integration.channel;

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
import com.dadcoach.whatsapp.DeliveryReceipts;
import com.dadcoach.whatsapp.WhatsAppAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-042 (Unified Workflow Phase 6.1): the platform gateway's report of what became of a message it held, against the
 * real rows a held scheduled message / dashboard link leave behind (the gate is FakeServers, Meta too). Nothing here
 * may ever send a second message.
 */
class HeldOutcomeTest extends AbstractIntegrationTest {

    private static final String WAMID = "wamid.HBgMRDQyLUhFTEQtT1VUQ09NRQA";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired HeldOutcomes outcomes;
    @Autowired WhatsAppAdapter whatsapp;
    @Autowired LoginLinkService links;
    @Autowired LoginLinkRepository linkRows;
    @Autowired LoginLinkRateLimiter rateLimiter;
    @Autowired DeliveryReceipts receipts;

    @BeforeEach
    void freshBudget() {
        rateLimiter.reset();
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private void gateHolds(long heldId) {
        fake.onGate(c -> FakeServers.Reply.json("{\"send\":false,\"heldId\":" + heldId
                + ",\"route\":\"dad-coach\",\"activeRoute\":\"big-boss\",\"why\":\"ON_ANOTHER_PRODUCT\"}"));
    }

    private MvcResult callback(Father f, String triggerId) throws Exception {
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w-d042",
                "userId", "whatsapp:" + f.getPhone(), "channel", "whatsapp", "targetStateKey", "ACTIVE_COACHING",
                "responseContent", "❤️ דאד קואץ׳:\nעוד שעה זמן איכות עם נועה 💪"));
        return mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    /** A scheduled coach message the shared number's gateway held as {@code held:<heldId>}. */
    private Father heldScheduled(String phone, String triggerId, long heldId) throws Exception {
        Father f = data.activeFather(phone);
        data.endpoint(f, true);
        gateHolds(heldId);
        callback(f, triggerId);
        assertThat(row(f, triggerId)).containsEntry("status", "HELD").containsEntry("gateway_hold_id", "held:" + heldId);
        fake.reset();
        return f;
    }

    /** A dashboard link the gateway held as {@code held:<heldId>}. */
    private Father heldLink(String phone, long heldId) {
        Father f = data.activeFather(phone);
        data.endpoint(f, true);
        gateHolds(heldId);
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(f)).containsEntry("receipt_status", "HELD").containsEntry("delivery_status", "SENT");
        fake.reset();
        return f;
    }

    private Map<String, Object> row(Father f, String triggerId) {
        return jdbc.queryForMap("SELECT status, failure_reason, provider_message_id, gateway_hold_id FROM "
                + "scheduled_response_delivery WHERE father_id = ? AND trigger_id = ?", f.getId(), triggerId);
    }

    private Map<String, Object> link(Father f) {
        return jdbc.queryForMap("SELECT delivery_status, delivery_error, receipt_status, provider_message_id, gateway_hold_id "
                + "FROM login_link WHERE father_id = ?", f.getId());
    }

    private static Map<String, Object> report(long heldId, String outcome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("heldId", heldId);
        body.put("workerKey", "dad_3");
        body.put("route", "dad-coach");
        body.put("outcome", outcome);
        body.put("providerMessageId", null);
        body.put("errorCode", null);
        body.put("closedReason", null);
        body.put("latestStatus", null);
        body.put("latestStatusAt", null);
        body.put("latestErrorCode", null);
        body.put("heldAt", "2026-11-03T08:00:00Z");
        body.put("closedAt", "2026-11-03T09:59:00Z");
        body.put("someFieldAddedLater", "ignored");
        return body;
    }

    private static String keyOf(Map<String, Object> body) {
        Object latest = body.get("latestStatus");
        return "held-outcome:" + body.get("heldId") + ":" + body.get("outcome") + ":" + (latest == null ? "-" : latest);
    }

    private MvcResult send(Map<String, Object> body) throws Exception {
        return send(body, CALLBACK_KEY, keyOf(body));
    }

    private MvcResult send(Map<String, Object> body, String apiKey, String idempotencyKey) throws Exception {
        var request = post(HeldOutcomeController.PATH).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (apiKey != null) {
            request = request.header("X-API-Key", apiKey);
        }
        if (idempotencyKey != null) {
            request = request.header("X-Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode answer(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private void receipt(String wamid, String status, Father f) throws Exception {
        byte[] raw = Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"15550000000\","
                + "\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid + "\",\"status\":\"" + status
                + "\",\"timestamp\":\"1793700100\",\"recipient_id\":\"" + f.getPhone().substring(1) + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    private long reportKeys() {
        return jdbc.queryForObject("SELECT count(*) FROM tool_idempotency WHERE scope = ?", Long.class, HeldOutcomeController.SCOPE);
    }

    // ------------------------------------------------------------------------------------------------- the switch

    @Test
    void withTheSwitchOffTheEndpointAnswers404AndChangesNothing() throws Exception {
        Father f = heldScheduled("+19995551201", "d042-off", 4201);
        Map<String, Object> body = report(4201, "SENT");
        body.put("providerMessageId", WAMID);

        MvcResult r = send(body);

        assertThat(r.getResponse().getStatus()).as("404: the platform keeps retrying until the switch is on").isEqualTo(404);
        assertThat(row(f, "d042-off")).containsEntry("status", "HELD").containsEntry("provider_message_id", null);
        assertThat(reportKeys()).isZero();
        assertThat(fake.metaSends()).isEmpty();

        // switched on: the platform's retry (same key) is applied
        outcomes.setEnabled(true);
        assertThat(send(body).getResponse().getStatus()).isEqualTo(200);
        assertThat(row(f, "d042-off")).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", WAMID);
    }

    // ------------------------------------------------------------------------------------------------- SENT

    @Test
    void sentLinksTheWamidSoALaterReceiptMatchesAndNothingIsSentAgain() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551202", "d042-sent", 4202);
        Map<String, Object> body = report(4202, "SENT");
        body.put("providerMessageId", WAMID);

        MvcResult r = send(body);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(answer(r).path("applied").asBoolean()).isTrue();
        assertThat(answer(r).path("known").asBoolean()).isTrue();
        assertThat(row(f, "d042-sent")).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", WAMID)
                .containsEntry("gateway_hold_id", "held:4202");
        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.SENT);

        receipt(WAMID, "delivered", f);
        assertThat(row(f, "d042-sent").get("status")).isEqualTo("DELIVERED");
        receipt(WAMID, "read", f);
        assertThat(row(f, "d042-sent").get("status")).isEqualTo("READ");

        // the platform's replayed callback for the same trigger answers from the row and sends nothing
        assertThat(answer(callback(f, "d042-sent")).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).isEmpty();
    }

    @Test
    void theLatestStatusTheGatewaySawIsAppliedForwardOnlyAlsoInALaterReport() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551203", "d042-latest", 4203);
        Map<String, Object> body = report(4203, "SENT");
        body.put("providerMessageId", WAMID);
        body.put("latestStatus", "DELIVERED");
        body.put("latestStatusAt", "2026-11-03T09:59:30Z");

        assertThat(send(body).getResponse().getStatus()).isEqualTo(200);
        assertThat(row(f, "d042-latest").get("status")).isEqualTo("DELIVERED");

        // a newer status is a new report (new key) for the same held id: the row moves on
        body.put("latestStatus", "READ");
        assertThat(answer(send(body)).path("applied").asBoolean()).isTrue();
        assertThat(row(f, "d042-latest").get("status")).isEqualTo("READ");

        // an older one arriving late never takes it back
        body.put("latestStatus", "ACCEPTED");
        assertThat(answer(send(body)).path("applied").asBoolean()).isFalse();
        assertThat(row(f, "d042-latest").get("status")).isEqualTo("READ");
    }

    @Test
    void aReceiptThatCameBeforeTheReportIsAppliedOnceTheReportLinksTheWamid() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551204", "d042-early", 4204);
        receipt(WAMID, "delivered", f); // the gateway sent it; Meta's receipt reaches Dad Coach before the report
        assertThat(row(f, "d042-early").get("status")).isEqualTo("HELD");

        Map<String, Object> body = report(4204, "SENT");
        body.put("providerMessageId", WAMID);  // the report carries no latestStatus (it raced the receipt)
        assertThat(send(body).getResponse().getStatus()).isEqualTo(200);

        assertThat(row(f, "d042-early").get("status")).isEqualTo("DELIVERED");
        receipts.retryPending(); // already applied: nothing left to apply twice
        assertThat(row(f, "d042-early").get("status")).isEqualTo("DELIVERED");
    }

    @Test
    void aReceiptOfAnUnknownWamidIsKeptAWhileAndAppliedByTheRetryToo() throws Exception {
        outcomes.setEnabled(true);
        Father f = data.activeFather("+19995551205");
        receipt("wamid.d042.kept", "read", f);
        jdbc.update("INSERT INTO scheduled_response_delivery (idempotency_key, trigger_id, father_id, status, delivery_mode, "
                + "provider_message_id) VALUES ('scheduled-response:d042-kept', 'd042-kept', ?, 'ACCEPTED', 'FREE_FORM', "
                + "'wamid.d042.kept')", f.getId());

        receipts.retryPending();

        assertThat(row(f, "d042-kept").get("status")).isEqualTo("READ");
    }

    @Test
    void aHeldLinkSentByTheGatewayGetsItsWamidAndStaysOnHisScreen() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldLink("+19995551206", 4206);
        Map<String, Object> body = report(4206, "SENT");
        body.put("providerMessageId", WAMID);
        body.put("latestStatus", "READ");

        assertThat(send(body).getResponse().getStatus()).isEqualTo(200);

        assertThat(link(f)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "READ")
                .containsEntry("provider_message_id", WAMID);
        assertThat(whatsapp.getDeliveryStatus(WAMID)).isEqualTo(DeliveryStatus.READ);
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.ALREADY_SENT);
        assertThat(fake.metaSends()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- FAILED / EXPIRED / UNKNOWN

    @Test
    void failedIsFailedWithTheGatewaysCodeAndNothingIsSentAgain() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551207", "d042-failed", 4207);
        Map<String, Object> body = report(4207, "FAILED");
        body.put("errorCode", "META_131047");
        body.put("closedReason", "SEND_FAILED");

        assertThat(answer(send(body)).path("applied").asBoolean()).isTrue();

        assertThat(row(f, "d042-failed")).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "GATEWAY_META_131047").containsEntry("provider_message_id", null);
        assertThat(answer(callback(f, "d042-failed")).path("status").asText()).isEqualTo("FAILED");
        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).isEmpty();
        // a late SENT for the same held id cannot revive it: an outcome only leaves HELD
        Map<String, Object> sent = report(4207, "SENT");
        sent.put("providerMessageId", WAMID);
        assertThat(answer(send(sent)).path("applied").asBoolean()).isFalse();
        assertThat(row(f, "d042-failed")).containsEntry("status", "FAILED").containsEntry("provider_message_id", null);
    }

    @Test
    void expiredIsFailedUnsentAndNeverSentAgain() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551208", "d042-expired", 4208);
        Map<String, Object> body = report(4208, "EXPIRED");
        body.put("closedReason", "TTL");

        assertThat(answer(send(body)).path("applied").asBoolean()).isTrue();

        assertThat(row(f, "d042-expired")).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "GATEWAY_HELD_EXPIRED_TTL");
        assertThat(answer(callback(f, "d042-expired")).path("status").asText()).isEqualTo("FAILED");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void anExpiredLinkIsFailedSoHisNextRequestGetsANewButtonButNothingGoesOutByItself() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldLink("+19995551209", 4209);
        Map<String, Object> body = report(4209, "EXPIRED");
        body.put("closedReason", "NOT_IN_LATEST_10");

        assertThat(send(body).getResponse().getStatus()).isEqualTo(200);

        assertThat(link(f)).containsEntry("delivery_status", "FAILED").containsEntry("receipt_status", "FAILED")
                .containsEntry("delivery_error", "GATEWAY_HELD_EXPIRED_NOT_IN_LATEST_10");
        assertThat(fake.metaSends()).as("applying an outcome sends nothing").isEmpty();
        assertThat(linkRows.sentToFatherSince(f.getId(), clock.instant().minusSeconds(60))).isFalse();
        // only when he asks again does a (first, real) button go out
        fake.onMetaSend(c -> FakeServers.Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.d042.new\"}]}"));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(fake.metaSends()).hasSize(1);
    }

    @Test
    void unknownIsNotFailedAndNeverSentAgain() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551210", "d042-unknown", 4210);
        Map<String, Object> body = report(4210, "UNKNOWN");
        body.put("errorCode", "TIMEOUT");
        body.put("closedReason", "SEND_UNKNOWN");

        assertThat(answer(send(body)).path("applied").asBoolean()).isTrue();

        assertThat(row(f, "d042-unknown")).containsEntry("status", "UNKNOWN")
                .containsEntry("failure_reason", "GATEWAY_UNKNOWN_TIMEOUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduled_response_delivery WHERE status = 'FAILED'", Long.class))
                .isZero();
        // the replayed callback still says it was handed over, and sends nothing
        assertThat(answer(callback(f, "d042-unknown")).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void anUnknownLinkStaysSentForTheCoach() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldLink("+19995551211", 4211);
        Map<String, Object> body = report(4211, "UNKNOWN");
        body.put("errorCode", "HTTP_503");

        assertThat(send(body).getResponse().getStatus()).isEqualTo(200);

        assertThat(link(f)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "UNKNOWN")
                .containsEntry("delivery_error", "GATEWAY_UNKNOWN_HTTP_503");
        assertThat(links.sendTo(f.getPhone())).as("may be on his screen: no second card")
                .isEqualTo(LoginLinkService.SendOutcome.ALREADY_SENT);
        assertThat(fake.metaSends()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- contract

    @Test
    void theSameKeyAgainIsADuplicateAndChangesNothing() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551212", "d042-dup", 4212);
        Map<String, Object> body = report(4212, "SENT");
        body.put("providerMessageId", WAMID);
        assertThat(answer(send(body)).path("applied").asBoolean()).isTrue();
        receipt(WAMID, "read", f);

        MvcResult again = send(body);

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(answer(again).path("duplicate").asBoolean()).isTrue();
        assertThat(answer(again).path("applied").asBoolean()).isFalse();
        assertThat(row(f, "d042-dup").get("status")).isEqualTo("READ");
        assertThat(reportKeys()).isEqualTo(1);
    }

    @Test
    void anIdNoRowHoldsIsAnsweredOkAndNotApplied() throws Exception {
        outcomes.setEnabled(true);
        Map<String, Object> body = report(999_999, "SENT");
        body.put("providerMessageId", WAMID);

        MvcResult r = send(body);

        assertThat(r.getResponse().getStatus()).as("200, so the platform does not retry it").isEqualTo(200);
        assertThat(answer(r).path("applied").asBoolean()).isFalse();
        assertThat(answer(r).path("known").asBoolean()).isFalse();
    }

    @Test
    void onlyTheCallbackKeyOpensIt() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551213", "d042-auth", 4213);
        Map<String, Object> body = report(4213, "FAILED");

        assertThat(send(body, null, keyOf(body)).getResponse().getStatus()).isEqualTo(401);
        assertThat(send(body, "wrong-key", keyOf(body)).getResponse().getStatus()).isEqualTo(401);
        assertThat(send(body, TOOL_KEY, keyOf(body)).getResponse().getStatus()).isEqualTo(401);
        assertThat(send(body, ADMIN_KEY, keyOf(body)).getResponse().getStatus()).isEqualTo(401);
        assertThat(row(f, "d042-auth").get("status")).isEqualTo("HELD");
    }

    @Test
    void aRequestWrongInItselfIs400() throws Exception {
        outcomes.setEnabled(true);
        Father f = heldScheduled("+19995551214", "d042-bad", 4214);
        Map<String, Object> body = report(4214, "FAILED");

        assertThat(send(body, CALLBACK_KEY, null).getResponse().getStatus()).as("no key").isEqualTo(400);
        assertThat(send(body, CALLBACK_KEY, "held-outcome:1:FAILED:-").getResponse().getStatus()).as("another id's key")
                .isEqualTo(400);
        Map<String, Object> noId = report(4214, "FAILED");
        noId.remove("heldId");
        assertThat(send(noId, CALLBACK_KEY, "held-outcome:4214:FAILED:-").getResponse().getStatus()).isEqualTo(400);
        assertThat(send(report(4214, "DELIVERED"), CALLBACK_KEY, "held-outcome:4214:DELIVERED:-").getResponse().getStatus())
                .isEqualTo(400);
        assertThat(row(f, "d042-bad").get("status")).isEqualTo("HELD");
        assertThat(reportKeys()).isZero();
    }
}
