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
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.scheduled.ScheduledPlatformSend;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.support.Webhooks;
import com.dadcoach.whatsapp.DeliveryReceipts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-052 (lost-write audit of delivery-status handling): every path that writes what became of a message Dad Coach
 * recorded - Meta's webhook receipts (and the ones kept for a wamid no row holds yet), the platform's delivery reports and
 * reconcile lookups, the gateway's held outcomes - against the real endpoints and rows, with the races forced, not hoped
 * for. A race is forced by a test-only statement trigger that parks Meta's FAILED receipt between its UPDATE (which found
 * no row: the wamid was not committed yet) and its look for the row - exactly the window a racing commit falls into.
 */
class DeliveryLostWritesTest extends AbstractIntegrationTest {

    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    /** The advisory lock the parked receipt waits on (D-052). */
    static final long PAUSE_KEY = 52052L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessagingSettings messaging;
    @Autowired DeliveryReceipts receipts;
    @Autowired HeldOutcomes outcomes;
    @Autowired LoginLinkService links;
    @Autowired LoginLinkRateLimiter rateLimiter;
    @Autowired LoginLinkPlatform linkPlatform;
    @Autowired ScheduledPlatformSend scheduledPlatform;
    @Autowired PlatformSendFlow flow;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
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
        for (int i = 0; i < 100 && !flow.shadowIdle(); i++) {
            Thread.sleep(50);
        }
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(10))).isTrue();
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Father father() {
        Father f = data.activeFather("+1999555" + String.format("%04d", 5200 + (++phones)));
        data.endpoint(f, true);
        return f;
    }

    private static String wamid() {
        return "wamid.d052." + UUID.randomUUID();
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
        String body = json.writeValueAsString(Map.of("triggerId", triggerId, "workflowInstanceId", "w-d052",
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

    private JsonNode held(long heldId, String outcome, String wamid, String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("heldId", heldId);
        body.put("workerKey", "dad_3");
        body.put("route", "dad-coach");
        body.put("outcome", outcome);
        body.put("providerMessageId", wamid);
        body.put("heldAt", "2026-11-03T08:00:00Z");
        body.put("closedAt", "2026-11-03T09:59:00Z");
        MvcResult r = mvc.perform(post(HeldOutcomeController.PATH).contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", CALLBACK_KEY).header("X-Idempotency-Key", key)
                .content(json.writeValueAsString(body))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** A scheduled message in PLATFORM mode the platform answered QUEUED (no wamid yet). */
    private String queuedScheduled(Father f, String triggerId) throws Exception {
        messaging.setScheduled(Mode.PLATFORM);
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "QUEUED", null));
        callback(f, triggerId);
        assertThat(row(triggerId)).containsEntry("status", "SENDING").containsEntry("platform_state", "OWNED")
                .containsEntry("provider_message_id", null);
        return messageId;
    }

    /** A dashboard link in PLATFORM mode the platform answered QUEUED (no wamid yet). */
    private String queuedLink(Father f) {
        messaging.setLoginLink(Mode.PLATFORM);
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "QUEUED", null));
        assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
        assertThat(link(f)).containsEntry("receipt_status", "QUEUED").containsEntry("platform_state", "OWNED")
                .containsEntry("provider_message_id", null);
        return messageId;
    }

    /**
     * Test-only: Meta's FAILED receipt waits (on an advisory lock this test holds) right after its UPDATE of
     * {@code login_link} - the last statement of its move, so after it found no row - and before it looks whether a row
     * holds the wamid. A statement trigger fires also when no row matched.
     */
    final class Pause implements AutoCloseable {
        private final Connection holder;
        private boolean held;

        Pause() throws Exception {
            jdbc.execute("CREATE OR REPLACE FUNCTION d052_pause() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF current_query() LIKE '%SET receipt_status = ''FAILED''%' THEN "
                    + "PERFORM pg_advisory_lock(" + PAUSE_KEY + "); PERFORM pg_advisory_unlock(" + PAUSE_KEY + "); END IF; "
                    + "RETURN NULL; END $$");
            jdbc.execute("DROP TRIGGER IF EXISTS d052_pause ON login_link");
            jdbc.execute("CREATE TRIGGER d052_pause AFTER UPDATE ON login_link FOR EACH STATEMENT EXECUTE FUNCTION d052_pause()");
            holder = dataSource.getConnection();
            holder.setAutoCommit(true);
            try (Statement s = holder.createStatement()) {
                s.execute("SELECT pg_advisory_lock(" + PAUSE_KEY + ")");
            }
            held = true;
        }

        /** Until a receipt is parked. */
        void awaitParked() throws InterruptedException {
            for (int i = 0; i < 200; i++) {
                Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' "
                        + "AND objid = ? AND NOT granted", Integer.class, (int) PAUSE_KEY);
                if (waiting != null && waiting > 0) {
                    return;
                }
                Thread.sleep(25);
            }
            throw new AssertionError("Meta's receipt never reached the pause");
        }

        void release() throws Exception {
            if (held) {
                try (Statement s = holder.createStatement()) {
                    s.execute("SELECT pg_advisory_unlock(" + PAUSE_KEY + ")");
                }
                held = false;
            }
        }

        @Override
        public void close() throws Exception {
            try {
                release();
                holder.close();
            } finally {
                jdbc.execute("DROP TRIGGER IF EXISTS d052_pause ON login_link");
                jdbc.execute("DROP FUNCTION IF EXISTS d052_pause()");
            }
        }
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

    // ------------------------------------------------------------------------------------------------- forced races (FIXED)

    @Test
    @DisplayName("SCHEDULED: Meta's 'failed' that looked for its row just before the platform's report committed the wamid "
            + "is applied - not dropped as 'its row exists' (D-052)")
    void failedReceiptRacingTheScheduledReportIsApplied() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father();
        String messageId = queuedScheduled(f, "d052-race-s");
        String w = wamid();

        try (Pause pause = new Pause()) {
            Future<?> meta = threads.submit(() -> {
                receipt(f, w, "failed");
                return null;
            });
            pause.awaitParked();       // its UPDATE found no row: the wamid is not committed yet
            assertThat(report("SCHEDULED", "scheduled-response:d052-race-s", messageId, "ACCEPTED", w)
                    .path("applied").asBoolean()).isTrue();   // commits the wamid; nothing kept yet to apply
            pause.release();
            meta.get(30, TimeUnit.SECONDS);
        }

        assertThat(row("d052-race-s")).as("Meta's failure is the last word, forward only (FAILED after ACCEPTED)")
                .containsEntry("status", "FAILED").containsEntry("provider_message_id", w);
        assertThat((String) row("d052-race-s").get("failure_reason")).contains("131047");
    }

    @Test
    @DisplayName("LOGIN_LINK: Meta's 'failed' racing the platform's report that links the wamid is applied (D-052)")
    void failedReceiptRacingTheLinkReportIsApplied() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father();
        String messageId = queuedLink(f);
        UUID id = (UUID) link(f).get("id");
        String w = wamid();

        try (Pause pause = new Pause()) {
            Future<?> meta = threads.submit(() -> {
                receipt(f, w, "failed");
                return null;
            });
            pause.awaitParked();
            assertThat(report("LOGIN_LINK", LoginLinkPlatform.key(id), messageId, "ACCEPTED", w).path("applied").asBoolean())
                    .isTrue();
            pause.release();
            meta.get(30, TimeUnit.SECONDS);
        }

        assertThat(link(f)).containsEntry("provider_message_id", w).containsEntry("delivery_status", "FAILED")
                .containsEntry("receipt_status", "FAILED");
    }

    @Test
    @DisplayName("Held outcome: Meta's 'failed' racing the gateway's SENT report that links the wamid is applied (D-052)")
    void failedReceiptRacingTheHeldOutcomeIsApplied() throws Exception {
        outcomes.setEnabled(true);
        Father f = father();
        fake.onGate(c -> Reply.json("{\"send\":false,\"heldId\":5201,\"route\":\"dad-coach\",\"activeRoute\":\"big-boss\","
                + "\"why\":\"ON_ANOTHER_PRODUCT\"}"));
        callback(f, "d052-race-held");
        assertThat(row("d052-race-held")).containsEntry("status", "HELD").containsEntry("gateway_hold_id", "held:5201");
        String w = wamid();

        try (Pause pause = new Pause()) {
            Future<?> meta = threads.submit(() -> {
                receipt(f, w, "failed");
                return null;
            });
            pause.awaitParked();
            assertThat(held(5201, "SENT", w, "held-outcome:5201:SENT:-").path("applied").asBoolean()).isTrue();
            pause.release();
            meta.get(30, TimeUnit.SECONDS);
        }

        assertThat(row("d052-race-held")).containsEntry("status", "FAILED").containsEntry("provider_message_id", w);
    }

    @Test
    @DisplayName("DIRECT: a kept 'failed' whose re-apply pass looked just before the direct send committed its row is kept "
            + "for the next pass, not dropped (GATEWAY_HELD_REPORTS off) (D-052)")
    void keptReceiptRacingADirectSendIsAppliedByTheNextPass() throws Exception {
        Father f = father();
        String w = wamid();
        receipt(f, w, "failed");                     // no row holds it: kept
        fake.onMetaSend(c -> Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"" + w + "\"}]}"));

        try (Pause pause = new Pause()) {
            Future<?> pass = threads.submit(() -> {
                receipts.retryPending();
                return null;
            });
            pause.awaitParked();
            callback(f, "d052-race-direct");          // today's send: the row with the wamid, committed
            assertThat(row("d052-race-direct")).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", w);
            pause.release();
            pass.get(30, TimeUnit.SECONDS);
        }
        receipts.retryPending();                       // the next pass (every 15 s)

        assertThat(row("d052-race-direct")).containsEntry("status", "FAILED");
    }

    // ------------------------------------------------------------------------------------------------- the caller's transaction (FIXED)

    @Test
    @DisplayName("LOGIN_LINK sync answer: the kept receipt applied after the platform's answer commits on its own - a "
            + "caller's transaction (a tool's) that rolls back afterwards does not take it along (D-052)")
    void keptReceiptAppliedByTheSyncAnswerSurvivesTheCallersRollback() throws Exception {
        messaging.setLoginLink(Mode.PLATFORM);
        Father f = father();
        String w = wamid();
        receipt(f, w, "failed");                     // Meta's failure before the platform's answer: kept
        fake.onPlatformSend(c -> answer(UUID.randomUUID().toString(), "ACCEPTED", w));

        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            assertThat(links.sendTo(f.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
            s.setRollbackOnly();                      // e.g. the tool's transaction (ToolController) rolls back
        });

        assertThat(link(f)).containsEntry("provider_message_id", w).containsEntry("delivery_status", "FAILED")
                .containsEntry("receipt_status", "FAILED");
    }

    @Test
    @DisplayName("Held outcome rolled back after it took the kept receipts: they are kept again, and the platform's retry "
            + "applies them (D-052)")
    void keptReceiptsComeBackWhenTheHeldOutcomeRollsBack() throws Exception {
        outcomes.setEnabled(true);
        Father f = father();
        fake.onGate(c -> Reply.json("{\"send\":false,\"heldId\":5202,\"route\":\"dad-coach\",\"activeRoute\":\"big-boss\","
                + "\"why\":\"ON_ANOTHER_PRODUCT\"}"));
        callback(f, "d052-held-rb");
        String w = wamid();
        receipt(f, w, "failed");                     // the gateway sent it; Meta's failure beat the report: kept

        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            assertThat(outcomes.apply(5202, HeldOutcomes.Outcome.SENT, w, null, null, null, null, null, null).applied())
                    .isTrue();
            s.setRollbackOnly();                      // its commit fails: nothing of it happened
        });
        assertThat(row("d052-held-rb")).containsEntry("status", "HELD");

        held(5202, "SENT", w, "held-outcome:5202:SENT:-");   // the platform's retry

        assertThat(row("d052-held-rb")).containsEntry("status", "FAILED").containsEntry("provider_message_id", w);
    }

    // ------------------------------------------------------------------------------------------------- concurrency (x10)

    @Test
    @DisplayName("Concurrency x10, SCHEDULED: Meta's 'failed' and the platform's ACCEPTED report for the same wamid at the "
            + "same moment - always FAILED (FAILED is final and comes before delivery)")
    void failedReceiptAndScheduledReportTogether() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        Father f = father();
        for (int i = 0; i < 10; i++) {
            String trigger = "d052-cc-s-" + i;
            String messageId = queuedScheduled(f, trigger);
            String w = wamid();
            together(() -> {
                receipt(f, w, "failed");
                return null;
            }, () -> report("SCHEDULED", "scheduled-response:" + trigger, messageId, "ACCEPTED", w));
            assertThat(row(trigger)).as("run " + i).containsEntry("status", "FAILED").containsEntry("provider_message_id", w);
        }
    }

    @Test
    @DisplayName("Concurrency x10, LOGIN_LINK: Meta's 'failed' and the platform's ACCEPTED report for the same wamid at the "
            + "same moment - always FAILED")
    void failedReceiptAndLinkReportTogether() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        for (int i = 0; i < 10; i++) {
            rateLimiter.reset();
            Father f = father();
            String messageId = queuedLink(f);
            UUID id = (UUID) link(f).get("id");
            String w = wamid();
            together(() -> {
                receipt(f, w, "failed");
                return null;
            }, () -> report("LOGIN_LINK", LoginLinkPlatform.key(id), messageId, "ACCEPTED", w));
            assertThat(link(f)).as("run " + i).containsEntry("provider_message_id", w)
                    .containsEntry("delivery_status", "FAILED").containsEntry("receipt_status", "FAILED");
        }
    }

    @Test
    @DisplayName("Concurrency x10, both ledgers: Meta's 'read' and the platform's DELIVERED report for an accepted wamid at "
            + "the same moment - always READ (the report's read-modify-write holds the row's lock; never regressed)")
    void readReceiptAndDeliveredReportTogether() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        for (int i = 0; i < 10; i++) {
            // a scheduled message
            Father f = father();
            String trigger = "d052-cc-r-" + i;
            messaging.setScheduled(Mode.PLATFORM);
            String w = wamid();
            String messageId = UUID.randomUUID().toString();
            fake.onPlatformSend(c -> answer(messageId, "ACCEPTED", w));
            callback(f, trigger);
            assertThat(row(trigger)).containsEntry("status", "ACCEPTED").containsEntry("provider_message_id", w);
            together(() -> {
                receipt(f, w, "read");
                return null;
            }, () -> report("SCHEDULED", "scheduled-response:" + trigger, messageId, "DELIVERED", w));
            assertThat(row(trigger)).as("scheduled run " + i).containsEntry("status", "READ");

            // a dashboard link
            rateLimiter.reset();
            Father g = father();
            messaging.setLoginLink(Mode.PLATFORM);
            String lw = wamid();
            String linkMessageId = UUID.randomUUID().toString();
            fake.onPlatformSend(c -> answer(linkMessageId, "ACCEPTED", lw));
            assertThat(links.sendTo(g.getPhone())).isEqualTo(LoginLinkService.SendOutcome.SENT);
            UUID id = (UUID) link(g).get("id");
            together(() -> {
                receipt(g, lw, "read");
                return null;
            }, () -> report("LOGIN_LINK", LoginLinkPlatform.key(id), linkMessageId, "DELIVERED", lw));
            assertThat(link(g)).as("link run " + i).containsEntry("receipt_status", "READ")
                    .containsEntry("delivery_status", "SENT");
        }
    }

    // ------------------------------------------------------------------------------------------------- SAFE paths

    @Test
    @DisplayName("Reconcile lookup (GET by key, scheduler lane, no caller transaction): the platform's ACCEPTED links the "
            + "wamid and Meta's kept 'failed' is applied - both ledgers, committed")
    void reconcileLookupAppliesTheKeptReceipt() throws Exception {
        Father f = father();
        String scheduledMessage = queuedScheduled(f, "d052-rec-s");
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

        assertThat(row("d052-rec-s")).containsEntry("status", "FAILED").containsEntry("provider_message_id", sw);
        assertThat(link(g)).containsEntry("delivery_status", "FAILED").containsEntry("receipt_status", "FAILED")
                .containsEntry("provider_message_id", lw);
    }

    @Test
    @DisplayName("Reports for the kinds with no ledger (REPLY, FIXED_LINE, BUTTON_REPLY, READY_ANSWER, BELT_PROMOTION): "
            + "known:false, no ledger row touched even when the wamid is a recorded one, the report's key committed DONE")
    void reportsWithoutALedgerWriteNothing() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        messaging.setScheduled(Mode.PLATFORM);
        Father f = father();
        String w = wamid();
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "ACCEPTED", w));
        callback(f, "d052-noledger");
        Map<String, Object> before = row("d052-noledger");

        for (String kind : List.of("REPLY", "FIXED_LINE", "BUTTON_REPLY", "READY_ANSWER", "BELT_PROMOTION")) {
            String id = UUID.randomUUID().toString();
            JsonNode a = report(kind, "reply:wamid.in:0", id, "FAILED", w);
            assertThat(a.path("known").asBoolean()).as(kind).isFalse();
            assertThat(a.path("applied").asBoolean()).as(kind).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM tool_idempotency WHERE scope = 'PLATFORM_DELIVERY_STATUS' "
                    + "AND idempotency_key = ?", String.class, "delivery:" + id + ":FAILED")).as(kind).isNotEqualTo("IN_PROGRESS");
        }
        assertThat(row("d052-noledger")).isEqualTo(before);
    }

    @Test
    @DisplayName("Session timer (PLATFORM): the morning callback the platform took records the sessions it named - "
            + "committed, and a later delivery report never undoes it")
    void sessionTimerRecordIsCommitted() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        messaging.setScheduled(Mode.PLATFORM);
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        clock.set(Instant.parse("2026-11-03T06:00:00Z"));   // 08:00 in Israel
        Instant start = clock.instant().plus(Duration.ofHours(7));
        QualityTime qt = sessions.saveAndFlush(new QualityTime(f, noa, start, start.plus(Duration.ofMinutes(60))));
        String messageId = UUID.randomUUID().toString();
        fake.onPlatformSend(c -> answer(messageId, "QUEUED", null));

        callback(f, "d052-morning", "SESSION_MORNING_REMINDER", IDENTITY + "בוקר טוב!");

        assertThat(jdbc.queryForObject("SELECT morning_reminded_on FROM quality_time WHERE id = ?", LocalDate.class,
                qt.getId())).isEqualTo(LocalDate.of(2026, 11, 3));
        report("SCHEDULED", "scheduled-response:d052-morning", messageId, "ACCEPTED", wamid());
        assertThat(row("d052-morning")).containsEntry("status", "ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT morning_reminded_on FROM quality_time WHERE id = ?", LocalDate.class,
                qt.getId())).isEqualTo(LocalDate.of(2026, 11, 3));
    }
}
