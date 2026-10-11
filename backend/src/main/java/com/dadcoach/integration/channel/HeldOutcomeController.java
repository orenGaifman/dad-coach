package com.dadcoach.integration.channel;

import com.dadcoach.idempotency.IdempotencyService;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * D-042 (Unified Workflow Phase 6.1): the platform's shared-number gateway reports what became of a message it held
 * (platform {@code HeldOutcomes}, spec v2 §v2.7). Auth: the route's claim key, which is Dad Coach's callback key - the
 * {@code /api/integration/channel/**} chain opens with it always (SecurityConfig, order 2).
 *
 * <p>Contract: body {@code {heldId, workerKey, route, outcome SENT|FAILED|UNKNOWN|EXPIRED, providerMessageId,
 * errorCode, closedReason, latestStatus, latestStatusAt, latestErrorCode, heldAt, closedAt}} (no phone, no text),
 * header {@code X-Idempotency-Key: held-outcome:<heldId>:<outcome>:<latestStatus|->}. One report per held message;
 * a second one (new key) only when a newer status reached the platform while the first was in flight.
 * Answers: 200 applied or not (an id no row holds is {@code {"applied":false,"known":false}} - never retried); the same
 * key again is 200 {@code duplicate}; 400 for a request that is wrong in itself (the platform gives up); 404 while
 * {@code GATEWAY_HELD_REPORTS} is off and 409 while the same key is being applied (the platform retries both, so a
 * report sent before the switch went on is applied once it is on, within the platform's 24 h).</p>
 */
@RestController
public class HeldOutcomeController {

    private static final Logger log = LoggerFactory.getLogger(HeldOutcomeController.class);

    public static final String PATH = "/api/integration/channel/held-outcome";
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    static final String KEY_PREFIX = "held-outcome:";
    static final String SCOPE = "GATEWAY_HELD_OUTCOME";
    /** A claim older than this belongs to a request that died; the platform's retry takes it over. */
    static final Duration LEASE = Duration.ofMinutes(2);

    /** The platform's report; unknown fields are ignored (additive contract). */
    public record Report(Long heldId, String workerKey, String route, String outcome, String providerMessageId,
                         String errorCode, String closedReason, String latestStatus, String latestStatusAt,
                         String latestErrorCode, String heldAt, String closedAt) {}

    private final HeldOutcomes outcomes;
    private final IdempotencyService idempotency;

    public HeldOutcomeController(HeldOutcomes outcomes, IdempotencyService idempotency) {
        this.outcomes = outcomes;
        this.idempotency = idempotency;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, Object>> report(@RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key,
                                                      @RequestBody(required = false) Report report) {
        if (!outcomes.isEnabled()) {
            return answer(HttpStatus.NOT_FOUND, false, "DISABLED");
        }
        if (report == null || report.heldId() == null || report.heldId() <= 0) {
            return answer(HttpStatus.BAD_REQUEST, false, "heldId is required");
        }
        Optional<HeldOutcomes.Outcome> outcome = HeldOutcomes.outcome(report.outcome());
        if (outcome.isEmpty()) {
            return answer(HttpStatus.BAD_REQUEST, false, "outcome must be SENT, FAILED, UNKNOWN or EXPIRED");
        }
        if (key == null || !key.startsWith(KEY_PREFIX + report.heldId() + ":") || key.length() > 200) {
            return answer(HttpStatus.BAD_REQUEST, false, IDEMPOTENCY_HEADER + " must be '" + KEY_PREFIX + "<heldId>:...'");
        }
        IdempotencyService.Claim claim = idempotency.claim(SCOPE, key, LEASE);
        if (claim == IdempotencyService.Claim.DONE) {
            log.atInfo().setMessage("whatsapp.held.outcome_duplicate").addKeyValue("heldId", report.heldId()).log();
            Map<String, Object> body = body(false, null);
            body.put("duplicate", true);
            return ResponseEntity.ok(body);
        }
        if (claim == IdempotencyService.Claim.BUSY) {
            return answer(HttpStatus.CONFLICT, false, "IN_PROGRESS");
        }
        HeldOutcomes.Applied applied;
        try {
            applied = outcomes.apply(report.heldId(), outcome.get(), report.providerMessageId(), report.errorCode(),
                    report.closedReason(), report.latestStatus(), instant(report.latestStatusAt()),
                    report.latestErrorCode(), instant(report.closedAt()));
        } catch (RuntimeException e) {
            idempotency.release(SCOPE, key); // not applied: the platform's retry (same key) runs it again
            throw e;
        }
        Map<String, Object> body = body(applied.applied(), null);
        body.put("known", applied.known());
        idempotency.complete(SCOPE, key, true, 200, body.toString());
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> answer(HttpStatus status, boolean applied, String reason) {
        return ResponseEntity.status(status).body(body(applied, reason));
    }

    private static Map<String, Object> body(boolean applied, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", applied);
        if (reason != null) {
            body.put("reason", reason);
        }
        return body;
    }

    private static Instant instant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
