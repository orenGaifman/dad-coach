package com.dadcoach.integration.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.idempotency.IdempotencyService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

/**
 * D-042 unit: the switch and the request checks come before anything is read or written (no database here - a test
 * that reached the database or the idempotency store would fail on the null collaborators).
 */
class HeldOutcomeControllerTest {

    private final HeldOutcomes outcomes = new HeldOutcomes(null, null, Clock.systemUTC(), false, true);
    private final HeldOutcomeController controller = new HeldOutcomeController(outcomes, new IdempotencyService(null, Clock.systemUTC()));

    private static HeldOutcomeController.Report report(Long heldId, String outcome) {
        return new HeldOutcomeController.Report(heldId, "dad_3", "dad-coach", outcome, "wamid.x", null, null, null, null,
                null, null, null);
    }

    @Test
    void switchedOffItIs404WhateverTheRequest() {
        assertThat(controller.report("held-outcome:5:SENT:-", report(5L, "SENT")).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.report(null, null).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.report("held-outcome:5:SENT:-", report(5L, "SENT")).getBody())
                .containsEntry("applied", false).containsEntry("reason", "DISABLED");
    }

    @Test
    void aRequestWrongInItselfIs400() {
        outcomes.setEnabled(true);
        assertThat(controller.report("held-outcome:5:SENT:-", null).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.report("held-outcome:0:SENT:-", report(0L, "SENT")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.report("held-outcome:5:SENDING:-", report(5L, "SENDING")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.report("held-outcome:5:x:-", report(5L, null)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.report(null, report(5L, "SENT")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.report("held-outcome:55:SENT:-", report(5L, "SENT")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.report("other:5:SENT:-", report(5L, "SENT")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void theFourOutcomesAreReadInAnyCase() {
        assertThat(HeldOutcomes.outcome("sent")).contains(HeldOutcomes.Outcome.SENT);
        assertThat(HeldOutcomes.outcome(" FAILED ")).contains(HeldOutcomes.Outcome.FAILED);
        assertThat(HeldOutcomes.outcome("Unknown")).contains(HeldOutcomes.Outcome.UNKNOWN);
        assertThat(HeldOutcomes.outcome("EXPIRED")).contains(HeldOutcomes.Outcome.EXPIRED);
        assertThat(HeldOutcomes.outcome("SENDING")).isEmpty();
        assertThat(HeldOutcomes.outcome(null)).isEmpty();
    }
}
