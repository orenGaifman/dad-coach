package com.dadcoach.whatsapp.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.domain.father.Father;
import com.dadcoach.idempotency.IdempotencyService;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-063 (Tair D-124's port): his message is kept from its claim until its turn settled - an instance that dies after the
 * webhook's 200 has it replayed once by the next instance, with exactly one reply; D-038's claim and reply guard still
 * decide (a settled claim is only cleared, a reply that went out is not sent again); two instances never replay it both.
 */
class PendingInboundTest extends AbstractIntegrationTest {

    private static final Duration PAST_LEASE = Duration.ofMinutes(2).plusSeconds(1);

    @Autowired MockMvc mvc;
    @Autowired IdempotencyService idempotency;
    @Autowired PendingInbound pending;
    @Autowired TransactionTemplate tx;

    private InboundMessageDto text(Father f, String wamid, String body) {
        return new InboundMessageDto(UUID.randomUUID(), wamid, f.getPhone(), "WHATSAPP", MessageType.TEXT, body, null,
                clock.instant(), clock.instant(), null);
    }

    /** What the webhook does before its 200 - and then the instance dies: the turn never runs. */
    private void acceptedThenCrashed(InboundMessageDto in) {
        assertThat(idempotency.claim("WHATSAPP_INBOUND", in.idempotencyKey(), Duration.ofMinutes(10), () -> recordVia(in)))
                .isEqualTo(IdempotencyService.Claim.CLAIMED);
    }

    private void recordVia(InboundMessageDto in) {
        pending.record(in);
    }

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    private int pendingRows() {
        return jdbc.queryForObject("SELECT count(*) FROM pending_inbound", Integer.class);
    }

    private String inboundStatus(String wamid) {
        return jdbc.queryForList("SELECT status FROM tool_idempotency WHERE scope = 'WHATSAPP_INBOUND' AND idempotency_key = ?",
                String.class, wamid).stream().findFirst().orElse(null);
    }

    @Test
    void aMessageWhoseInstanceDiedAfterThe200IsAnsweredOnceAfterTheLease() {
        Father f = data.activeFather("+19995550831");
        data.endpoint(f, true);
        acceptedThenCrashed(text(f, "wamid.d063-crash", "היי, איך מתחילים?"));
        assertThat(pendingRows()).isEqualTo(1);

        assertThat(pending.replayDue()).as("still leased by the instance that took it").isZero();
        clock.advance(PAST_LEASE);
        assertThat(pending.replayDue()).isEqualTo(1);

        assertThat(fake.turns()).hasSize(1);
        assertThat(fake.sentMessages()).hasSize(1);
        assertThat(inboundStatus("wamid.d063-crash")).isEqualTo("SUCCEEDED");
        assertThat(pendingRows()).isZero();

        clock.advance(PAST_LEASE);
        assertThat(pending.replayDue()).isZero();
        assertThat(fake.sentMessages()).hasSize(1);
    }

    @Test
    void aReplyThatWentOutBeforeTheCrashIsNotSentAgain() {
        Father f = data.activeFather("+19995550832");
        data.endpoint(f, true);
        acceptedThenCrashed(text(f, "wamid.d063-sent", "היי"));
        // the instance sent the reply (its guard is there) and died before the claim settled
        jdbc.update("INSERT INTO tool_idempotency (id, scope, idempotency_key, status, created_at, expires_at)"
                + " VALUES (?, 'WHATSAPP_REPLY', 'wamid.d063-sent', 'SUCCEEDED', now(), now() + interval '7 days')", UUID.randomUUID());
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("❤️ דאד קואץ׳:\\nאיזה כיף!", "GENERATED")
                .replace("\"isDuplicate\":false", "\"isDuplicate\":true")));
        clock.advance(PAST_LEASE);

        assertThat(pending.replayDue()).isEqualTo(1);

        assertThat(fake.sentMessages()).isEmpty();
        assertThat(inboundStatus("wamid.d063-sent")).isEqualTo("SUCCEEDED");
        assertThat(pendingRows()).isZero();
    }

    @Test
    void aClaimSettledMeanwhileOnlyClearsTheRow() {
        Father f = data.activeFather("+19995550833");
        InboundMessageDto in = text(f, "wamid.d063-settled", "היי");
        acceptedThenCrashed(in);
        idempotency.complete("WHATSAPP_INBOUND", in.idempotencyKey(), true, 200, null);
        clock.advance(PAST_LEASE);

        assertThat(pending.replayDue()).isEqualTo(1);

        assertThat(fake.turns()).isEmpty();
        assertThat(pendingRows()).isZero();
    }

    @Test
    void aWebhookTurnClearsItsRowAnsweredOrReleasedAndADuplicateStaysDeduplicated() throws Exception {
        Father f = data.activeFather("+19995550834");
        data.endpoint(f, true);
        webhook(Webhooks.text(f.getPhone(), "wamid.d063-ok", "היי"));
        webhook(Webhooks.text(f.getPhone(), "wamid.d063-ok", "היי"));
        assertThat(fake.turns()).hasSize(1);
        assertThat(pendingRows()).isZero();

        fake.onTurn(c -> new FakeServers.Reply(503, "{\"error\":\"down\"}"));
        webhook(Webhooks.text(f.getPhone(), "wamid.d063-down", "היי"));
        assertThat(inboundStatus("wamid.d063-down")).as("released, as D-038").isNull();
        assertThat(pendingRows()).isZero();
    }

    @Test
    void aMessageThatKeepsKillingItsInstanceIsGivenUpAfterThreeReplays() {
        Father f = data.activeFather("+19995550835");
        acceptedThenCrashed(text(f, "wamid.d063-poison", "שלום"));
        jdbc.update("UPDATE pending_inbound SET attempts = 3");
        clock.advance(PAST_LEASE);
        assertThat(pending.replayDue()).isZero();
        assertThat(fake.turns()).isEmpty();
        assertThat(pendingRows()).isZero();
    }

    @Test
    void aLiveInstanceRenewsItsLeaseSoNoOtherInstanceReplaysIt() {
        Father f = data.activeFather("+19995550836");
        acceptedThenCrashed(text(f, "wamid.d063-live", "היי"));
        clock.advance(Duration.ofMinutes(2).minusSeconds(10));
        pending.renewHeld();
        clock.advance(Duration.ofSeconds(30));
        assertThat(pending.replayDue()).isZero();
        assertThat(pendingRows()).isEqualTo(1);
    }

    @Test
    void aFailedPendingRowLeavesTheKeyFree() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> idempotency.claim("WHATSAPP_INBOUND", "wamid.d063-fail",
                Duration.ofMinutes(10), () -> {
                    throw new IllegalStateException("pending row not written");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(inboundStatus("wamid.d063-fail")).isNull();
    }

    @Test
    void aStaleRowIsPurged() {
        Father f = data.activeFather("+19995550837");
        acceptedThenCrashed(text(f, "wamid.d063-stale", "היי"));
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        pending.purgeStale();
        assertThat(pendingRows()).isZero();
    }

    @Test
    void twoInstancesNeverReplayTheSameMessage() throws Exception {
        Father f = data.activeFather("+19995550838");
        data.endpoint(f, true);
        acceptedThenCrashed(text(f, "wamid.d063-x1", "שאלה ראשונה"));
        clock.advance(Duration.ofSeconds(1));
        acceptedThenCrashed(text(f, "wamid.d063-x2", "שאלה שנייה"));
        clock.advance(PAST_LEASE);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread other = new Thread(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForList("SELECT message_id FROM pending_inbound WHERE message_id = 'wamid.d063-x1' FOR UPDATE");
            locked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        other.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pending.replayDue()).as("x1 is being claimed by the other instance").isEqualTo(1);
        release.countDown();
        other.join();
        assertThat(jdbc.queryForList("SELECT message_id FROM pending_inbound", String.class)).containsExactly("wamid.d063-x1");
        assertThat(fake.sentMessages()).hasSize(1);
    }
}
