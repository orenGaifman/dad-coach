package com.dadcoach.integration.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.auth.LoginLinkPlatform;
import com.dadcoach.auth.LoginLinkRateLimiter;
import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.scheduled.ScheduledPlatformSend;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * D-052 (lost-write audit of delivery-status handling) after D-053 (Unified Workflow 6.4): the platform's delivery
 * reports and reconcile lookups are the only writers of what became of a message Dad Coach recorded - Meta's receipts
 * on the webhook are ignored (the races D-052 forced between a receipt and a report's commit cannot happen any more;
 * those tests went with the receipt handler). What stays pinned: reports that race each other are applied forward only
 * under the row's lock (both ledgers), a receipt still forwarded during the rollout changes nothing even when it races
 * a report, the reconcile lookup commits on the scheduler lane, kinds with no ledger write nothing, the session-timer
 * notes are committed.
 */
class DeliveryLostWritesTest extends AbstractIntegrationTest {

    static final String IDENTITY = "❤️ דאד קואץ׳:\n";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessagingSettings messaging;
    @Autowired LoginLinkService links;
    @Autowired LoginLinkRateLimiter rateLimiter;
    @Autowired LoginLinkPlatform linkPlatform;
    @Autowired ScheduledPlatformSend scheduledPlatform;
    @Autowired TimelineReports timelineReports;
    @Autowired QualityTimeRepository sessions;

    private ExecutorService threads;
    private int phones;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        threads = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        threads.shutdownNow();
        assertThat(threads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(10))).isTrue();
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Father father() {
        Father f = data.activeFather("+1999555" + String.format("%04d", 5200 + (++phones)));
        data.endpoint(f, true);
        return f;
    }

    private static String wamid() {
        return "wamid.d053." + UUID.randomUUID();
    }

    private static Reply answer(String messageId, String status, String wamid) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("messageId", messageId);
        b.put("idempotencyKey", "ignored-by-dad-coach");
        b.put("replayed", false);
        b.put("status", status);
        b.put("providerMessageId", wamid);
        b.put("reason", null);
        b.put("errorCode", null);
        b.put("channelWindow", "SESSION");
        b.put("template", null);
        b.put("heldId", null);
        try {
            return new Reply(202, new ObjectMapper().writeValueAsString(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void callback(Father f, String triggerId, String state, String content) throws Exception {
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w-d053",
                "userId", "whatsapp:" + f.getPhone(), "channel", "whatsapp", "targetStateKey", state,
                "responseContent", content));
        MvcResult r = mvc.perform(post("/api/integration/workflow/scheduled-response").header("X-API-Key", CALLBACK_KEY)
                .header("X-Idempotency-Key", "scheduled-response:" + triggerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    private void callback(Father f, String triggerId) throws Exception {
        callback(f, triggerId, "ACTIVE_COACHING", IDENTITY + "בוקר טוב! איך הולך השבוע?");
    }

    private Map<String, Object> row(String triggerId) {
        return jdbc.queryForMap("SELECT status, failure_reason, provider_message_id, gateway_hold_id, platform_state, "
                + "platform_status FROM scheduled_response_delivery WHERE trigger_id = ?", triggerId);
    }

    private Map<String, Object> link(Father f) {
        return jdbc.queryForMap("SELECT id, delivery_status, delivery_error, receipt_status, provider_message_id, "
                + "platform_state, platform_status FROM login_link WHERE father_id = ?", f.getId());
    }

    private JsonNode report(String kind, String key, String messageId, String status, String wamid) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("messageId", messageId);
        b.put("idempotencyKey", key);
        b.put("workerKey", "dad_3");
        b.put("kind", kind);
        b.put("status", status);
        b.put("providerMessageId", wamid);
        b.put("reason", null);
        b.put("channelWindow", "SESSION");
        b.put("reportSeq", 1);
        MvcResult r = mvc.perform(post(DeliveryStatusController.PATH).contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", CALLBACK_KEY).header("X-Idempotency-Key", "delivery:" + messageId + ":" + status)
                .content(json.writeValueAsString(b))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** Meta's webhook with one receipt for {@code wamid}; a "failed" one carries 131047. */
    private void receipt(Father f, String wamid, String status) throws Exception {
        String errors = "failed".equals(status) ? ",\"errors\":[{\"code\":131047,\"title\":\"Re-engagement message\"}]" : "";
        byte[] raw = Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":"
                + "\"15550000000\",\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid
                + "\",\"status\":\"" + status + "\",\"timestamp\":\"1793700100\",\"recipient_id\":\""
                + f.getPhone().substring(1) + "\"" + errors + "}]}").getBytes(StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    /** A scheduled message the platform answered QUEUED (no wamid yet). */
    private String queuedScheduled(Father f, String triggerId) throws Exception {
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "QUEUED", null));
        callback(f, triggerId);
        assertThat(row(triggerId)).containsEntry("status", "SENDING").containsEntry("platform_state", "OWNED")
                .containsEntry("provider_message_id", null);
        return messageId;
    }

    /** A dashboard link the platform answered QUEUED (no wamid yet). */
    private String queuedLink(Father f) {
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "QUEUED", null));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(f)).containsEntry("receipt_status", "QUEUED").containsEntry("platform_state", "OWNED")
                .containsEntry("provider_message_id", null);
        return messageId;
    }

    /** Both run at once: released together by a barrier; both must finish without error. */
    private void together(Callable<?> a, Callable<?> b) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Future<?> fa = threads.submit(() -> {
            start.await(10, TimeUnit.SECONDS);
            return a.call();
        });
        Future<?> fb = threads.submit(() -> {
            start.await(10, TimeUnit.SECONDS);
            return b.call();
        });
        fa.get(30, TimeUnit.SECONDS);
        fb.get(30, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------------------------------------- concurrency (x10)

    @Test
    @DisplayName("Concurrency x10, both ledgers: the platform's READ and DELIVERED reports at the same moment - always READ "
            + "(forward only under the row's lock), and Meta's 'failed' receipt forwarded alongside changes nothing (D-053)")
    void reportsRacingEachOtherAreForwardOnly() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        for (int i = 0; i < 10; i++) {
            // a scheduled message
            Father f = father();
            String trigger = "d053-cc-s-" + i;
            String w = wamid();
            String messageId = UUID.randomUUID().toString();
            fake.onPlatformSend(c -> answer(messageId, "ACCEPTED", w));
            callback(f, trigger);
            assertThat(row(trigger)).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", w);
            together(() -> report("SCHEDULED", "scheduled-response:" + trigger, messageId, "READ", w),
                    () -> report("SCHEDULED", "scheduled-response:" + trigger, messageId, "DELIVERED", w));
            receipt(f, w, "failed");
            assertThat(row(trigger)).as("scheduled run " + i).containsEntry("status", "READ");

            // a dashboard link
            rateLimiter.reset();
            Father g = father();
            String lw = wamid();
            String linkMessageId = UUID.randomUUID().toString();
            fake.onPlatformSend(c -> answer(linkMessageId, "ACCEPTED", lw));
            assertThat(links.sendTo(g.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
            UUID id = (UUID) link(g).get("id");
            together(() -> {
                receipt(g, lw, "failed");
                return null;
            }, () -> report("LOGIN_LINK", LoginLinkPlatform.key(id), linkMessageId, "DELIVERED", lw));
            report("LOGIN_LINK", LoginLinkPlatform.key(id), linkMessageId, "READ", lw);
            assertThat(link(g)).as("link run " + i).containsEntry("receipt_status", "READ")
                    .containsEntry("delivery_status", "SENT");
        }
    }

    @Test
    @DisplayName("Concurrency x10, SCHEDULED: the platform's FAILED and ACCEPTED reports at the same moment - always FAILED "
            + "(FAILED is final and comes before delivery), the wamid kept")
    void failedAndAcceptedReportsTogether() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father();
        for (int i = 0; i < 10; i++) {
            String trigger = "d053-cc-f-" + i;
            String messageId = queuedScheduled(f, trigger);
            String w = wamid();
            together(() -> report("SCHEDULED", "scheduled-response:" + trigger, messageId, "FAILED", w),
                    () -> report("SCHEDULED", "scheduled-response:" + trigger, messageId, "ACCEPTED", w));
            assertThat(row(trigger)).as("run " + i).containsEntry("status", "FAILED").containsEntry("provider_message_id", w);
        }
    }

    // ------------------------------------------------------------------------------------------------- SAFE paths

    @Test
    @DisplayName("Reconcile lookup (GET by key, scheduler lane, no caller transaction): the platform's word links the wamid "
            + "and is committed - both ledgers; a receipt on the webhook before it changed nothing")
    void reconcileLookupIsCommitted() throws Exception {
        Father f = father();
        String scheduledMessage = queuedScheduled(f, "d053-rec-s");
        String sw = wamid();
        receipt(f, sw, "failed");
        rateLimiter.reset();
        Father g = father();
        String linkMessage = queuedLink(g);
        String lw = wamid();
        receipt(g, lw, "failed");
        clock.advance(Duration.ofMinutes(6));          // MESSAGING_RECONCILE_AFTER: the QUEUED rows are looked up

        Future<?> lane = threads.submit(() -> {         // as the messaging lane runs them: no caller transaction
            fake.onPlatformLookup(c -> new Reply(200, answer(scheduledMessage, "ACCEPTED", sw).body()));
            assertThat(scheduledPlatform.reconcileDue()).isEqualTo(1);
            fake.onPlatformLookup(c -> new Reply(200, answer(linkMessage, "ACCEPTED", lw).body()));
            assertThat(linkPlatform.reconcileDue()).isEqualTo(1);
            return null;
        });
        lane.get(30, TimeUnit.SECONDS);

        assertThat(row("d053-rec-s")).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", sw);
        assertThat(link(g)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", null)
                .containsEntry("provider_message_id", lw);
    }

    @Test
    @DisplayName("Reports for the kinds with no ledger (REPLY, FIXED_LINE, BUTTON_REPLY, READY_ANSWER, BELT_PROMOTION): "
            + "known:false, no ledger row touched even when the wamid is a recorded one, the report's key committed DONE")
    void reportsWithoutALedgerWriteNothing() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father();
        String w = wamid();
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "ACCEPTED", w));
        callback(f, "d053-noledger");
        Map<String, Object> before = row("d053-noledger");

        for (String kind : List.of("REPLY", "FIXED_LINE", "BUTTON_REPLY", "READY_ANSWER", "BELT_PROMOTION")) {
            String id = UUID.randomUUID().toString();
            JsonNode a = report(kind, "reply:wamid.in:0", id, "FAILED", w);
            assertThat(a.path("known").asBoolean()).as(kind).isFalse();
            assertThat(a.path("applied").asBoolean()).as(kind).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM tool_idempotency WHERE scope = 'PLATFORM_DELIVERY_STATUS' "
                    + "AND idempotency_key = ?", String.class, "delivery:" + id + ":FAILED")).as(kind).isNotEqualTo("IN_PROGRESS");
        }
        assertThat(row("d053-noledger")).isEqualTo(before);
    }

    @Test
    @DisplayName("Session timer (PLATFORM): the morning callback the platform took records the sessions it named - "
            + "committed, and a later delivery report never undoes it")
    void sessionTimerRecordIsCommitted() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        clock.set(Instant.parse("2026-11-03T06:00:00Z"));   // 08:00 in Israel
        Instant start = clock.instant().plus(Duration.ofHours(7));
        QualityTime qt = sessions.saveAndFlush(new QualityTime(f, noa, start, start.plus(Duration.ofMinutes(60))));
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "QUEUED", null));

        callback(f, "d053-morning", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב!");

        assertThat(jdbc.queryForObject("SELECT morning_reminded_on FROM quality_time WHERE id = ?", LocalDate.class,
                qt.getId())).isEqualTo(LocalDate.of(2026, 11, 3));
        report("SCHEDULED", "scheduled-response:d053-morning", messageId, "ACCEPTED", wamid());
        assertThat(row("d053-morning")).containsEntry("status", "ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT morning_reminded_on FROM quality_time WHERE id = ?", LocalDate.class,
                qt.getId())).isEqualTo(LocalDate.of(2026, 11, 3));
    }
}
