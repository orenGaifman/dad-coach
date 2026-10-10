package com.dadcoach.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Delivery regression baseline (Task 1.2): an inbound WhatsApp message is answered exactly once - never twice for a
 * Meta redelivery, and never zero times because a first attempt failed or because the platform answered a retried
 * turn as a duplicate. Turns run inline in tests (dad-coach.whatsapp.inbound-sync=true).
 *
 * <p>PASS tests pin today's correct behaviour. KNOWN-BUG tests state the correct behaviour and are {@link Disabled};
 * run them with {@code -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition} to see them fail.
 */
class InboundDedupBaselineTest extends AbstractIntegrationTest {

    private static final String REPLY = "❤️ דאד קואץ׳:\nאיזה כיף לשמוע! מה תרצו לעשות ביחד?";
    private static final String DOWN = "❤️ דאד קואץ׳:\nמשהו השתבש אצלי. נסה שוב עוד רגע.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        int status = mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus();
        assertThat(status).as("Meta always gets its 200").isEqualTo(200);
    }

    /** The texts of every send that reached Meta, in order. */
    private List<String> sentTexts() throws Exception {
        List<String> out = new java.util.ArrayList<>();
        for (FakeServers.Call c : fake.metaSends()) {
            out.add(json.readTree(c.body()).path("text").path("body").asText());
        }
        return out;
    }

    private static String reply(String content, boolean duplicate) {
        return FakeServers.turnReply(content, "GENERATED").replace("\"isDuplicate\":false", "\"isDuplicate\":" + duplicate);
    }

    private long turnsFor(String wamid) throws Exception {
        long n = 0;
        for (FakeServers.Call c : fake.turns()) {
            if (wamid.equals(json.readTree(c.body()).path("correlationId").asText())) {
                n++;
            }
        }
        return n;
    }

    // ---- PASS ---------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("PASS (c): Meta redelivering an already-answered message runs no second turn and sends no second reply")
    void aRedeliveryOfAnAnsweredMessageIsNotAnsweredAgain() throws Exception {
        Father f = data.activeFather("+19995550711");
        data.endpoint(f, true);
        fake.onTurn(c -> FakeServers.Reply.json(reply(REPLY.replace("\n", "\\n"), false)));

        webhook(Webhooks.text(f.getPhone(), "wamid.bl.answered", "היי, היה לנו סופש מעולה"));
        webhook(Webhooks.text(f.getPhone(), "wamid.bl.answered", "היי, היה לנו סופש מעולה"));
        webhook(Webhooks.text(f.getPhone(), "wamid.bl.answered", "היי, היה לנו סופש מעולה"));

        assertThat(turnsFor("wamid.bl.answered")).isEqualTo(1);
        assertThat(sentTexts()).containsExactly(REPLY);
    }

    @Test
    @DisplayName("PASS: a duplicate answer with no content (nothing cached to give) sends nothing")
    void aDuplicateWithNoContentSendsNothing() throws Exception {
        Father f = data.activeFather("+19995550712");
        data.endpoint(f, true);
        fake.onTurn(c -> FakeServers.Reply.json(reply(null, true)));

        webhook(Webhooks.text(f.getPhone(), "wamid.bl.dupnull", "היי"));

        assertThat(turnsFor("wamid.bl.dupnull")).isEqualTo(1);
        assertThat(fake.metaSends()).isEmpty();
    }

    // ---- KNOWN-BUG DC-B3: the dedupe key is spent before the turn succeeded --------------------------------------------

    /**
     * DC-B3 (platform failed). WhatsAppWebhookController.receive marks Meta's message id as seen
     * (idempotency.firstTime, WhatsAppWebhookController.java:86; IdempotencyService.firstTime stores the row already
     * COMPLETED, IdempotencyService.java:84-97) BEFORE the turn runs on InboundTurnExecutor
     * (WhatsAppWebhookController.java:90). Here the platform fails the turn (500 on every attempt), the father only gets
     * "משהו השתבש אצלי", and his message was never processed; when the same message arrives again (Meta redelivery)
     * it is dropped as "whatsapp.inbound.duplicate" (WhatsAppWebhookController.java:86-88) although it was never
     * answered. Nothing ever retries it. Correct: a message whose processing failed is processed again when it comes
     * again, and the father gets the coach's real reply.
     */
    @Test
    @Disabled("KNOWN-BUG DC-B3: a redelivery of a message whose turn failed (platform 500) is dropped as a duplicate")
    @DisplayName("KNOWN-BUG DC-B3: after the platform failed the turn, a redelivery is processed and gets the real reply")
    void aRedeliveryAfterAFailedTurnIsProcessedAgain() throws Exception {
        Father f = data.activeFather("+19995550713");
        data.endpoint(f, true);
        AtomicBoolean platformUp = new AtomicBoolean(false);
        fake.onTurn(c -> platformUp.get() ? FakeServers.Reply.json(reply(REPLY.replace("\n", "\\n"), false))
                : new FakeServers.Reply(500, "{\"error\":\"boom\"}"));

        webhook(Webhooks.text(f.getPhone(), "wamid.bl.failedturn", "היי, היה לנו סופש מעולה"));
        assertThat(sentTexts()).as("first attempt: only the apology line").containsExactly(DOWN);
        long firstAttempts = turnsFor("wamid.bl.failedturn");
        assertThat(firstAttempts).as("the platform was tried (with its retries)").isPositive();

        platformUp.set(true);
        webhook(Webhooks.text(f.getPhone(), "wamid.bl.failedturn", "היי, היה לנו סופש מעולה"));

        assertThat(turnsFor("wamid.bl.failedturn"))
                .as("DC-B3: the redelivered message (first turn failed) must run a turn again")
                .isGreaterThan(firstAttempts);
        assertThat(sentTexts())
                .as("DC-B3: the father gets the coach's real reply after the redelivery")
                .containsExactly(DOWN, REPLY);
    }

    /**
     * DC-B3 (reply never reached the father). Same root cause (WhatsAppWebhookController.java:86 before :90): the
     * turn ran but Meta refused the reply (HTTP 500), InboundMessageHandler.send only logs the FAILED DeliveryResult
     * (InboundMessageHandler.java:509-516) and the father got nothing. Meta's id is already spent, so when the message
     * comes again it is dropped. Correct: a message the father got no answer to is processed again on redelivery and
     * the reply reaches him.
     */
    @Test
    @Disabled("KNOWN-BUG DC-B3: a redelivery of a message whose reply Meta refused is dropped - the father never gets an answer")
    @DisplayName("KNOWN-BUG DC-B3: after the reply failed to send, a redelivery is processed and the reply reaches him")
    void aRedeliveryAfterTheReplyFailedToSendIsProcessedAgain() throws Exception {
        Father f = data.activeFather("+19995550714");
        data.endpoint(f, true);
        fake.onTurn(c -> FakeServers.Reply.json(reply(REPLY.replace("\n", "\\n"), false)));
        AtomicBoolean metaUp = new AtomicBoolean(false);
        AtomicInteger accepted = new AtomicInteger();
        fake.onMetaSend(c -> metaUp.get()
                ? FakeServers.Reply.json(Receipts.accepted("wamid.bl.reply." + accepted.incrementAndGet()))
                : new FakeServers.Reply(500, "{\"error\":{\"message\":\"Service temporarily unavailable\",\"code\":2}}"));

        webhook(Webhooks.text(f.getPhone(), "wamid.bl.lostreply", "היי, היה לנו סופש מעולה"));
        assertThat(accepted.get()).as("first attempt: Meta accepted nothing").isZero();

        metaUp.set(true);
        webhook(Webhooks.text(f.getPhone(), "wamid.bl.lostreply", "היי, היה לנו סופש מעולה"));

        assertThat(accepted.get())
                .as("DC-B3: the redelivered message (reply never sent) must be answered - Meta accepted sends")
                .isEqualTo(1);
    }

    // ---- KNOWN-BUG DC-B4: a duplicate answer with the cached reply sends nothing ----------------------------------------

    /**
     * DC-B4. WorkflowPlatformClient retries a turn on a 5xx (MAX_RETRIES=2, WorkflowPlatformClient.java:84-90 with
     * isRetryable :254-257). When the first attempt ran the turn but its answer was lost (here: the platform answered
     * 502), the retry with the same correlationId is answered by the platform as a duplicate WITH the cached reply
     * (platform WorkflowEngine ~418-436: isDuplicate=true, responseContent = the stored reply).
     * InboundMessageHandler.runTurn returns on any isDuplicate (InboundMessageHandler.java:229-231, outcome
     * "DUPLICATE") and sends nothing, so the father never gets the reply the coach wrote.
     * Correct: the cached reply reaches the father exactly once.
     */
    @Test
    @Disabled("KNOWN-BUG DC-B4: isDuplicate=true with responseContent (a retried turn) sends nothing - the reply is lost")
    @DisplayName("KNOWN-BUG DC-B4: a retried turn answered as a duplicate with the cached reply still reaches the father once")
    void aDuplicateAnswerWithTheCachedReplyIsSentOnce() throws Exception {
        Father f = data.activeFather("+19995550715");
        data.endpoint(f, true);
        AtomicInteger attempt = new AtomicInteger();
        fake.onTurn(c -> attempt.incrementAndGet() == 1
                ? new FakeServers.Reply(502, "{\"error\":\"bad gateway\"}")
                : FakeServers.Reply.json(reply(REPLY.replace("\n", "\\n"), true)));

        webhook(Webhooks.text(f.getPhone(), "wamid.bl.retried", "היי, היה לנו סופש מעולה"));

        assertThat(turnsFor("wamid.bl.retried")).as("the client retried the turn once").isEqualTo(2);
        assertThat(sentTexts())
                .as("DC-B4: the platform's cached reply (isDuplicate=true) must reach the father exactly once")
                .containsExactly(REPLY);
    }
}
