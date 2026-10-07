package com.dadcoach.idempotency;

import com.dadcoach.api.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * reserve → execute → persist for every side-effecting tool and every durable dedup (WhatsApp inbound), copied
 * from Tair/Big Boss (playbook §11.2). Same key + same payload + finished = replay; same key + different payload =
 * 409; still running = 409. A stored response is replayed only to the SAME actor — never to someone else who sends
 * the same key. Tool rows live 24 hours, inbound dedup rows 7 days.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    static final Duration RETENTION = Duration.ofHours(24);

    public sealed interface Outcome {
        record Proceed() implements Outcome {}

        record Replay(int status, String body) implements Outcome {}
    }

    public static class KeyReusedException extends ApiException {
        public KeyReusedException() {
            super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This idempotency key was used for a different request");
        }
    }

    public static class InProgressException extends ApiException {
        public InProgressException() {
            super(HttpStatus.CONFLICT, "IN_PROGRESS", "This request is still being executed");
        }
    }

    private final ToolIdempotencyRepository rows;
    private final Clock clock;

    public IdempotencyService(ToolIdempotencyRepository rows, Clock clock) {
        this.rows = rows;
        this.clock = clock;
    }

    public static String hashOf(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome reserve(String scope, String key, String actorRef, String payloadHash) {
        var existing = rows.findByScopeAndIdempotencyKey(scope, key);
        if (existing.isPresent()) {
            return resolve(existing.get(), actorRef, payloadHash);
        }
        Instant now = clock.instant();
        try {
            rows.saveAndFlush(new ToolIdempotency(scope, key, payloadHash, actorRef, now, now.plus(RETENTION)));
            return new Outcome.Proceed();
        } catch (DataIntegrityViolationException raceLost) {
            return resolve(rows.findByScopeAndIdempotencyKey(scope, key).orElseThrow(() -> raceLost), actorRef, payloadHash);
        }
    }

    /** For pure dedup (inbound webhooks): true the first time a key is seen in this scope. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean firstTime(String scope, String key) {
        if (rows.findByScopeAndIdempotencyKey(scope, key).isPresent()) {
            return false;
        }
        Instant now = clock.instant();
        try {
            ToolIdempotency row = new ToolIdempotency(scope, key, null, null, now, now.plus(RETENTION.multipliedBy(7)));
            row.complete(true, 200, null, now);
            rows.saveAndFlush(row);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private Outcome resolve(ToolIdempotency existing, String actorRef, String payloadHash) {
        if (!Objects.equals(existing.getActorRef(), actorRef)) {
            log.atWarn().setMessage("idempotency.foreign_key").addKeyValue("scope", existing.getScope()).log();
            throw new KeyReusedException();
        }
        if (!Objects.equals(existing.getPayloadHash(), payloadHash)) {
            throw new KeyReusedException();
        }
        if ("IN_PROGRESS".equals(existing.getStatus())) {
            throw new InProgressException();
        }
        return new Outcome.Replay(existing.getResponseStatus(), existing.getResponseBody());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String scope, String key, boolean succeeded, int status, String body) {
        rows.findByScopeAndIdempotencyKey(scope, key).orElseThrow().complete(succeeded, status, body, clock.instant());
    }

    /** A reservation whose execution threw: removed, so the platform's retry with the same key runs again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String scope, String key) {
        rows.findByScopeAndIdempotencyKey(scope, key).filter(r -> "IN_PROGRESS".equals(r.getStatus())).ifPresent(rows::delete);
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    @Transactional
    public void purgeExpired() {
        rows.deleteExpired(clock.instant());
    }
}
