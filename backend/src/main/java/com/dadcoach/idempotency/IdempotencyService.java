package com.dadcoach.idempotency;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.config.SchedulingLanes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
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
    /** Inbound dedup and reply guards live 7 days (Meta retries for days). */
    static final Duration DEDUP_RETENTION = RETENTION.multipliedBy(7);

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

    /** For pure dedup: true the first time a key is seen in this scope (the row is stored already done). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean firstTime(String scope, String key) {
        Instant now = clock.instant();
        return rows.insertIfAbsent(UUID.randomUUID(), scope, key, "SUCCEEDED", now, now.plus(DEDUP_RETENTION)) == 1;
    }

    /** What {@link #claim} found: CLAIMED - go ahead; DONE - already handled; BUSY - being handled right now. */
    public enum Claim { CLAIMED, DONE, BUSY }

    /**
     * D-038 (DC-B3): claims a key for processing without marking it done. The row is IN_PROGRESS until the caller
     * {@link #complete}s it (handled: any later arrival is DONE) or {@link #release}s it (not handled: the next arrival
     * claims it again). A claim older than {@code lease} belongs to a worker that died and is taken over.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(String scope, String key, Duration lease) {
        Instant now = clock.instant();
        for (int attempt = 0; attempt < 2; attempt++) {
            if (rows.insertIfAbsent(UUID.randomUUID(), scope, key, "IN_PROGRESS", now, now.plus(DEDUP_RETENTION)) == 1) {
                return Claim.CLAIMED;
            }
            var existing = rows.findByScopeAndIdempotencyKey(scope, key);
            if (existing.isEmpty()) {
                continue; // released between the insert and the read: try the insert once more
            }
            if (!"IN_PROGRESS".equals(existing.get().getStatus())) {
                return Claim.DONE;
            }
            return rows.takeOverStale(scope, key, now.minus(lease), now) == 1 ? Claim.CLAIMED : Claim.BUSY;
        }
        return Claim.BUSY;
    }

    /** Removes a key whatever its state (a guard taken for a send that then failed). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forget(String scope, String key) {
        rows.deleteKey(scope, key);
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

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M", scheduler = SchedulingLanes.HOUSEKEEPING)
    @Transactional
    public void purgeExpired() {
        rows.deleteExpired(clock.instant());
    }
}
