package com.dadcoach.whatsapp.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.whatsapp.inbound.InboundMessageHandler.Outcome;
import org.junit.jupiter.api.Test;

/**
 * D-038: what an inbound message counts as when its processing threw. Once a line reached the father (a fixed line, a
 * ready answer, a button reply, the AI reply) and nothing failed, it is HANDLED - the webhook keeps its claim, so a Meta
 * redelivery can never send that line again. Nothing sent, or a send refused: UNANSWERED - processed again.
 */
class InboundAttemptTest {

    @Test
    void aThrowAfterSomethingReachedHimIsHandled() {
        InboundMessageHandler.Attempt attempt = new InboundMessageHandler.Attempt();
        attempt.sent = 1;
        assertThat(attempt.afterError()).isEqualTo(Outcome.HANDLED);
    }

    @Test
    void aThrowBeforeAnythingWasSentIsUnanswered() {
        assertThat(new InboundMessageHandler.Attempt().afterError()).isEqualTo(Outcome.UNANSWERED);
    }

    @Test
    void aThrowAfterARefusedSendIsUnanswered() {
        InboundMessageHandler.Attempt attempt = new InboundMessageHandler.Attempt();
        attempt.sent = 1;            // e.g. the "something went wrong" line went out ...
        attempt.unanswered = true;   // ... because the platform was down
        assertThat(attempt.afterError()).isEqualTo(Outcome.UNANSWERED);
        assertThat(attempt.outcome()).isEqualTo(Outcome.UNANSWERED);
    }

    @Test
    void withoutAThrowTheOutcomeIsWhetherAnythingFailed() {
        InboundMessageHandler.Attempt attempt = new InboundMessageHandler.Attempt();
        assertThat(attempt.outcome()).as("deliberately nothing to send (reaction, suppressed)").isEqualTo(Outcome.HANDLED);
    }
}
