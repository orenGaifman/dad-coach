package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-038 (DC-B3/DC-B4) beyond the baseline: the inbound claim (IN_PROGRESS until answered, a stale claim taken over)
 * and the one-reply guard (WHATSAPP_REPLY) that keeps a cached duplicate reply from going out twice.
 */
class InboundClaimTest extends AbstractIntegrationTest {

    private static final String REPLY = "❤️ דאד קואץ׳:\\nאיזה כיף לשמוע!";

    @Autowired MockMvc mvc;

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    private void row(String scope, String key, String status, Duration age) {
        jdbc.update("INSERT INTO tool_idempotency (id, scope, idempotency_key, status, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), scope, key, status, Timestamp.from(clock.instant().minus(age)),
                Timestamp.from(clock.instant().plus(Duration.ofDays(7))));
    }

    private String inboundStatus(String wamid) {
        return jdbc.queryForList("SELECT status FROM tool_idempotency WHERE scope = 'WHATSAPP_INBOUND' AND idempotency_key = ?",
                String.class, wamid).stream().findFirst().orElse(null);
    }

    private static String duplicateWith(String content) {
        return FakeServers.turnReply(content, "GENERATED").replace("\"isDuplicate\":false", "\"isDuplicate\":true");
    }

    @Test
    void aMessageBeingProcessedIsNotStartedTwice() throws Exception {
        Father f = data.activeFather("+19995550811");
        row("WHATSAPP_INBOUND", "wamid.busy", "IN_PROGRESS", Duration.ofMinutes(1));

        webhook(Webhooks.text(f.getPhone(), "wamid.busy", "היי"));

        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).isEmpty();
        assertThat(inboundStatus("wamid.busy")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aClaimWhoseWorkerDiedIsTakenOverAfterTheLease() throws Exception {
        Father f = data.activeFather("+19995550812");
        data.endpoint(f, true);
        row("WHATSAPP_INBOUND", "wamid.stale", "IN_PROGRESS", Duration.ofMinutes(11));

        webhook(Webhooks.text(f.getPhone(), "wamid.stale", "היי"));

        assertThat(fake.turns()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(inboundStatus("wamid.stale")).isEqualTo("SUCCEEDED");
    }

    @Test
    void anAnsweredMessageIsDoneAndAFailedOneLeavesNoTrace() throws Exception {
        Father f = data.activeFather("+19995550813");
        data.endpoint(f, true);
        webhook(Webhooks.text(f.getPhone(), "wamid.ok", "היי"));
        assertThat(inboundStatus("wamid.ok")).isEqualTo("SUCCEEDED");

        fake.onTurn(c -> new FakeServers.Reply(503, "{\"error\":\"down\"}"));
        webhook(Webhooks.text(f.getPhone(), "wamid.down", "היי"));
        assertThat(inboundStatus("wamid.down")).as("released for the redelivery").isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency WHERE scope = 'WHATSAPP_REPLY' "
                + "AND idempotency_key = 'wamid.down'", Integer.class)).isZero();
    }

    @Test
    void aRefusalFromThePlatformIsAnAnswerNotARetry() throws Exception {
        Father f = data.activeFather("+19995550814");
        data.endpoint(f, true);
        fake.onTurn(c -> new FakeServers.Reply(409, "{\"code\":\"person.inactive\"}"));

        webhook(Webhooks.text(f.getPhone(), "wamid.refused", "היי"));

        assertThat(inboundStatus("wamid.refused")).isEqualTo("SUCCEEDED");
    }

    @Test
    void aCachedReplyThatAlreadyWentOutIsNeverSentAgain() throws Exception {
        Father f = data.activeFather("+19995550815");
        data.endpoint(f, true);
        // the reply to this message went out (its guard is there), then its claim was lost - a later arrival runs a
        // turn and the platform answers with the cached reply
        row("WHATSAPP_REPLY", "wamid.third", "SUCCEEDED", Duration.ofMinutes(30));
        fake.onTurn(c -> FakeServers.Reply.json(duplicateWith(REPLY)));

        webhook(Webhooks.text(f.getPhone(), "wamid.third", "היי"));

        assertThat(fake.turns()).hasSize(1);
        assertThat(fake.metaSends()).isEmpty();
        assertThat(inboundStatus("wamid.third")).isEqualTo("SUCCEEDED");
    }

    /** D-038: an error before anything reached him leaves the message unanswered - its redelivery is answered. */
    @Test
    void anErrorBeforeAnyReplyReleasesTheMessageForItsRedelivery() throws Exception {
        Father f = data.activeFather("+19995550817");
        data.endpoint(f, true);
        jdbc.execute("CREATE OR REPLACE FUNCTION d038_fail() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'd038 test'; END $$ "
                + "LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER d038_reply_guard BEFORE INSERT ON tool_idempotency FOR EACH ROW "
                + "WHEN (NEW.scope = 'WHATSAPP_REPLY') EXECUTE FUNCTION d038_fail()");
        try {
            webhook(Webhooks.text(f.getPhone(), "wamid.boom", "היי"));
            assertThat(fake.metaSends()).isEmpty();
            assertThat(inboundStatus("wamid.boom")).as("released").isNull();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS d038_reply_guard ON tool_idempotency");
            jdbc.execute("DROP FUNCTION IF EXISTS d038_fail()");
        }

        webhook(Webhooks.text(f.getPhone(), "wamid.boom", "היי"));

        assertThat(fake.metaSends()).hasSize(1);
        assertThat(inboundStatus("wamid.boom")).isEqualTo("SUCCEEDED");
    }

    @Test
    void aCachedReplyIsSentOnceAndItsGuardStays() throws Exception {
        Father f = data.activeFather("+19995550816");
        data.endpoint(f, true);
        fake.onTurn(c -> FakeServers.Reply.json(duplicateWith(REPLY)));

        webhook(Webhooks.text(f.getPhone(), "wamid.cached", "היי"));
        // Meta delivers it again: answered, dropped before any turn
        webhook(Webhooks.text(f.getPhone(), "wamid.cached", "היי"));

        assertThat(fake.turns()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency WHERE scope = 'WHATSAPP_REPLY' "
                + "AND idempotency_key = 'wamid.cached'", Integer.class)).isEqualTo(1);
    }
}
