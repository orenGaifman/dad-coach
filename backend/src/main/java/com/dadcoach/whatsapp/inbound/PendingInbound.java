package com.dadcoach.whatsapp.inbound;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.config.SchedulingLanes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-063 (Tair D-124's port): his message survives a crash between the webhook's 200 and its turn. D-038's claim made a
 * redelivery processable, but after a 200 none comes - a crash, an OOM or a redeploy's queued turns lost the message.
 * {@link #record} writes it in the claim's transaction; the row is deleted when the turn settled (answered or released, as
 * D-038). While this instance holds a message (queued or running) it renews the row's lease; a row whose lease ran out
 * belongs to an instance that died and is replayed through the controller (its claim taken over once older than the lease,
 * same per-sender executor; a claim settled meanwhile only clears the row), claimed here with SKIP LOCKED so two instances never replay it both. After {@link #MAX_REPLAYS} replays it is given up;
 * a row older than {@link #KEPT_AT_MOST} is purged (logged).
 */
@Component
public class PendingInbound {

    private static final Logger log = LoggerFactory.getLogger(PendingInbound.class);
    static final Duration LEASE = Duration.ofMinutes(2);
    static final int MAX_REPLAYS = 3;
    static final Duration KEPT_AT_MOST = Duration.ofHours(1);
    private static final int BATCH = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ObjectMapper json;
    private final boolean inline;
    /** Meta ids this instance holds: from acceptance until the turn settled. Their leases are renewed. */
    private final Set<String> held = ConcurrentHashMap.newKeySet();
    private volatile Consumer<InboundMessageDto> replayer;

    public PendingInbound(JdbcTemplate jdbc, TransactionTemplate tx, Clock clock, ObjectMapper json,
                          @Value("${dad-coach.whatsapp.inbound-sync:false}") boolean inline) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.json = json;
        this.inline = inline;
    }

    /** The webhook controller: runs a replayed message as it runs a new one. */
    void replayWith(Consumer<InboundMessageDto> replayer) {
        this.replayer = replayer;
    }

    /** Inside the claim's transaction: his message, until its turn settled (the caller holds it once committed). */
    void record(InboundMessageDto in) {
        Instant now = clock.instant();
        String message;
        try {
            message = json.writeValueAsString(in);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.update("INSERT INTO pending_inbound (message_id, sender, message, received_at, lease_until) VALUES (?, ?, ?, ?, ?)"
                        + " ON CONFLICT (message_id) DO UPDATE SET lease_until = excluded.lease_until",
                in.idempotencyKey(), in.fatherChannelIdentity(), message, Timestamp.from(now),
                Timestamp.from(now.plus(LEASE)));
    }

    /** A replayed message this instance runs now: its lease is renewed until its turn settled. */
    void hold(String messageId) {
        held.add(messageId);
    }

    /** Its turn settled: the row goes. */
    void done(String messageId) {
        try {
            jdbc.update("DELETE FROM pending_inbound WHERE message_id = ?", messageId);
        } catch (RuntimeException e) {
            // left behind, the replay finds its claim settled and clears it
            log.atWarn().setMessage("whatsapp.inbound.pending_not_cleared").addKeyValue("messageId", messageId)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        } finally {
            held.remove(messageId);
        }
    }

    @Scheduled(fixedDelayString = "${dad-coach.whatsapp.pending-replay-interval:PT30S}",
            initialDelayString = "${dad-coach.whatsapp.pending-replay-interval:PT30S}", scheduler = SchedulingLanes.HOUSEKEEPING)
    public void tick() {
        if (inline) {
            return; // tests run turns inline and drive the replay themselves
        }
        try {
            renewHeld();
            purgeStale();
            replayDue();
        } catch (RuntimeException e) {
            log.atError().setMessage("whatsapp.inbound.replay_failed").setCause(e).log();
        }
    }

    void renewHeld() {
        if (held.isEmpty()) {
            return;
        }
        Timestamp until = Timestamp.from(clock.instant().plus(LEASE));
        jdbc.batchUpdate("UPDATE pending_inbound SET lease_until = ? WHERE message_id = ?",
                held.stream().map(id -> new Object[] {until, id}).toList());
    }

    /** A row this old is never replayed: a turn that hung, or a message nobody could take (logged). */
    void purgeStale() {
        List<String> stale = jdbc.queryForList("DELETE FROM pending_inbound WHERE received_at < ? RETURNING message_id", String.class,
                Timestamp.from(clock.instant().minus(KEPT_AT_MOST)));
        stale.forEach(id -> log.atError().setMessage("whatsapp.inbound.pending_purged").addKeyValue("messageId", id).log());
    }

    private record Row(String id, String message, int attempts) {}

    /** Claims the rows whose lease ran out, oldest first, and replays them; returns how many were replayed. */
    public int replayDue() {
        Instant now = clock.instant();
        List<Row> claimed = tx.execute(s -> {
            List<Row> rows = jdbc.query("SELECT message_id, message, attempts FROM pending_inbound WHERE lease_until < ?"
                            + " ORDER BY received_at LIMIT " + BATCH + " FOR UPDATE SKIP LOCKED",
                    (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getInt(3)), Timestamp.from(now));
            jdbc.batchUpdate("UPDATE pending_inbound SET lease_until = ?, attempts = attempts + 1 WHERE message_id = ?",
                    rows.stream().map(r -> new Object[] {Timestamp.from(now.plus(LEASE)), r.id()}).toList());
            return rows;
        });
        int count = 0;
        for (Row r : claimed) {
            if (r.attempts() >= MAX_REPLAYS || replayer == null) {
                log.atError().setMessage("whatsapp.inbound.replay_gave_up").addKeyValue("messageId", r.id())
                        .addKeyValue("replays", r.attempts()).log();
                done(r.id());
                continue;
            }
            InboundMessageDto in;
            try {
                in = json.readValue(r.message(), InboundMessageDto.class);
            } catch (JsonProcessingException e) {
                log.atError().setMessage("whatsapp.inbound.replay_gave_up").addKeyValue("messageId", r.id())
                        .addKeyValue("error", "unreadable").log();
                done(r.id());
                continue;
            }
            log.atWarn().setMessage("whatsapp.inbound.replayed").addKeyValue("messageId", r.id())
                    .addKeyValue("replay", r.attempts() + 1).log();
            try {
                replayer.accept(in);
                count++;
            } catch (RuntimeException e) {
                // the rest of the batch still runs; this one is tried again after its lease
                log.atError().setMessage("whatsapp.inbound.replay_failed").addKeyValue("messageId", r.id()).setCause(e).log();
            }
        }
        return count;
    }
}
