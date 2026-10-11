package com.dadcoach.integration.platform.messaging;

import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind;
import com.dadcoach.whatsapp.DeliveryReceipts;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-045 / D-046 / D-048 (Unified Workflow Phase 6.3-DC): the mechanics every kind Dad Coach hands to the platform's Send API
 * shares - platform spec {@code PHASE6_2_SEND_API_SPEC.md} §3, §7.4, §8, §16 with "Spec v2" v2.1-v2.6. Extracted from
 * D-045's {@code LoginLinkPlatform} without a change in behaviour; each kind supplies its ledger ({@link Ledger}: its
 * table, how a platform status reads on its row, how it is sent directly) and its own logger, so its log lines keep
 * their names ({@code messaging.<event>.*}).
 *
 * <ul>
 *   <li><b>Ledger kinds</b> ({@link #send}): the row and the exact request bytes are committed first (own transaction),
 *       then {@code POST /send}. 2xx with a status: the platform owns it ({@link #apply}, forward only, bytes cleared).
 *       Provably nothing created - 503 {@code messaging.disabled}, 401/403 or never written, each on the FIRST attempt
 *       only: sent directly, today's way, same row. 400/422 (any attempt) and a first-attempt 409: refused, FAILED, never
 *       sent directly. Anything ambiguous: one immediate re-POST of the same bytes (not after a timeout or a 429), then
 *       {@link #reconcileDue} re-POSTs once more and, {@code MESSAGING_RECONCILE_AFTER} after the first POST, asks by
 *       key: 200 adopt, 404 {@code messaging.message_not_found} = never created (sent directly if the kind says it is
 *       still worth sending), anything else again for 1 h, then UNKNOWN - never sent.</li>
 *   <li><b>Kinds without a ledger</b> ({@link #sendOnce}, spec v2.6: the bytes stay in memory): the same answer rules
 *       and the one immediate re-POST; an ambiguous answer is logged and never sent directly (no reconcile). D-048: a
 *       kind may set {@link Options} - its own read timeout, a 409 with state adopted on the first POST (a key a
 *       redelivery reuses), one re-POST of a 429 after a short Retry-After.</li>
 *   <li><b>SHADOW</b> ({@link #shadow}): the platform's dry run of the same request on a thread of its own
 *       ({@code messaging-shadow}, queue 100, dropped when full); differences logged, never a second message.</li>
 * </ul>
 */
@Component
public class PlatformSendFlow {

    public static final String POSTING = "POSTING";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String OWNED = "OWNED";
    public static final String DIRECT = "DIRECT";
    public static final String REFUSED = "REFUSED";
    public static final String GAVE_UP = "GAVE_UP";

    /** A POST in flight is the sending thread's for this long; after it the job takes the row as ambiguous. */
    public static final Duration LEASE = Duration.ofMinutes(2);
    /** The first POST, one immediate re-POST and one by the job (spec v2.5: re-POST up to 2 times). */
    public static final int MAX_POSTS = 3;
    /** Reconcile by GET for this long after the first POST, then UNKNOWN (spec §16: every 5 min for 1 h). */
    public static final Duration RECONCILE_FOR = Duration.ofHours(1);
    /** A row the platform keeps QUEUED/HELD is looked up by GET while it is this young (held: 24 h at most). */
    public static final Duration OPEN_FOR = Duration.ofHours(26);
    public static final Duration HELD_POLL = Duration.ofHours(1);
    static final int BATCH = 20;

    /** The platform's statuses, by rank (forward only); FAILED and UNKNOWN are decided apart. */
    private static final Map<String, Integer> RANK = Map.of("QUEUED", 1, "HELD", 2, "ACCEPTED", 3, "DELIVERED", 5,
            "READ", 6);

    /** What the platform said about one message: the sync answer, a 409's state, a GET, or a delivery report. */
    public record Snapshot(String messageId, String status, String providerMessageId, String reason, String errorCode,
                           Long heldId, String channelWindow) {
        public static Optional<Snapshot> of(JsonNode body) {
            if (body == null || !body.hasNonNull("messageId") || !body.hasNonNull("status")) {
                return Optional.empty();
            }
            String status = body.get("status").asText().toUpperCase(Locale.ROOT);
            if (!RANK.containsKey(status) && !"FAILED".equals(status) && !"UNKNOWN".equals(status)) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(body.get("messageId").asText(), status, text(body, "providerMessageId"),
                    text(body, "reason"), text(body, "errorCode"),
                    body.hasNonNull("heldId") && body.get("heldId").canConvertToLong() ? body.get("heldId").asLong() : null,
                    text(body, "channelWindow")));
        }
    }

    /** What applying a report did: whether a row holds the key, and whether the row moved. */
    public record Applied(boolean known, boolean moved) {}

    /** How one answer is acted on (spec §16 table with v2.5 / v2.6). */
    public enum Verdict {
        OWNED, ADOPT, DIRECT, REFUSED, AMBIGUOUS,
        /**
         * D-048 review: only from {@link #sendOnce} with a {@link Options#rateLimitWait}: every answer was a 429 - the
         * platform answers an existing key before its rate limit (v2.1), so nothing was created; the caller may try the
         * same key again later (never sends it directly: spec §16).
         */
        RATE_LIMITED
    }

    /**
     * How a send ended for its caller. {@code snapshot}: OWNED / ADOPT; {@code reason}: REFUSED; {@code direct}: what the
     * direct send returned (DIRECT), null when another path had taken the row meanwhile.
     */
    public record Settled<R>(Verdict verdict, Snapshot snapshot, String reason, R direct, Answer answer) {}

    /** One SET fragment of this class's own constants, with its parameters (never outside input in the SQL). */
    public record Update(String set, Object... params) {}

    /**
     * A kind with a ledger table. The table has an {@code id UUID} key, {@code created_at} and the platform columns
     * ({@code platform_message_id, platform_request, platform_state, platform_status, platform_posts,
     * platform_first_post_at, platform_next_at}).
     */
    public interface Ledger {
        /** The Send API's {@code kind}. */
        String kind();

        String table();

        /** Its log lines ({@code messaging.<event>.*}) go to this logger. */
        Logger log();

        String event();

        /** The row id's name in its log lines ({@code linkId}, ...). */
        String idKey();

        /** The row's idempotency key. */
        String idempotencyKey(UUID id);

        /** The row a delivery report is about, by its idempotency key or else the platform's message id; empty if none. */
        Optional<UUID> rowOf(String idempotencyKey, String messageId);

        /** The row's own status columns {@link #forward} reads (besides the platform columns). */
        String statusColumns();

        /** Whether the platform's {@code to} moves the row as it stands (forward only). */
        boolean forward(Map<String, Object> row, String to);

        /** The platform's status onto the row's own columns; runs in the apply transaction, the row locked. */
        void applyStatus(UUID id, Snapshot snap, Timestamp at);

        /** Extra SET for an ambiguous answer ("" for none). */
        String ambiguousSet();

        /** The row's own columns for a refused request (400/422/409). */
        Update refused(String reason, Timestamp at);

        /** The row's own columns when no answer came for an hour: UNKNOWN, never sent again. */
        Update gaveUp(String reason, Timestamp at);

        /** The row's own columns when the platform never created it and it is too late to send it directly. */
        Update ended(Timestamp at);

        /** Before a reconcile re-POST: null to go on, else why it is given up ({@code NO_FATHER}, ...). */
        String blocker(UUID id, String body);

        /** After "never created" (reconcile 404): whether it is still worth sending directly. */
        boolean stillSendable(UUID id, String body, Instant now);

        /** Sends it directly after the flow took the row (state DIRECT) in the reconcile job: today's way. */
        void sendLate(UUID id, String body);
    }

    /** What Dad Coach itself did, for the SHADOW comparison. {@code template} null = free-form. */
    public record ShadowFacts(boolean successful, boolean held, String failureReason, String template) {}

    private final MessagingSettings settings;
    private final PlatformMessagingClient client;
    private final WorkflowPlatformProperties platform;
    private final CommunicationEndpointRepository endpoints;
    private final DeliveryReceipts receipts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean receiptsEnabled;
    /** One SHADOW lane per kind (review N3): a burst of one kind never drops another kind's comparisons. */
    private final Map<String, ThreadPoolExecutor> shadowLanes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicLong> shadowDropped = new java.util.concurrent.ConcurrentHashMap<>();

    public PlatformSendFlow(MessagingSettings settings, PlatformMessagingClient client, WorkflowPlatformProperties platform,
                            CommunicationEndpointRepository endpoints, DeliveryReceipts receipts, JdbcTemplate jdbc,
                            PlatformTransactionManager transactions, ObjectMapper json, Clock clock,
                            @Value("${dad-coach.whatsapp.receipts.enabled:true}") boolean receiptsEnabled) {
        this.settings = settings;
        this.client = client;
        this.platform = platform;
        this.endpoints = endpoints;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
        this.clock = clock;
        this.receiptsEnabled = receiptsEnabled;
    }

    // ------------------------------------------------------------------------------------------------- the answer

    /** @param earlierMayExist an earlier attempt with these bytes may have created the message */
    public static Verdict verdict(Answer a, boolean earlierMayExist) {
        if (a.kind() == Kind.NEVER_WRITTEN) {
            return earlierMayExist ? Verdict.AMBIGUOUS : Verdict.DIRECT;
        }
        if (a.kind() == Kind.AMBIGUOUS) {
            return Verdict.AMBIGUOUS;
        }
        int s = a.status();
        if (s == 200 || s == 202) {
            return Snapshot.of(a.body()).isPresent() ? Verdict.OWNED : Verdict.AMBIGUOUS;
        }
        if (s == 503 && PlatformMessagingClient.DISABLED.equals(a.errorCode())) {
            // nothing created by THIS request (an existing key is answered before the kill switch, v2.1) - but after an
            // ambiguous attempt that one may still be in flight on another platform instance (a restart flipping the
            // switch): only the lookup by key, minutes later, may say "never created" (review M1)
            return earlierMayExist ? Verdict.AMBIGUOUS : Verdict.DIRECT;
        }
        if (s == 401 || s == 403) {
            return earlierMayExist ? Verdict.AMBIGUOUS : Verdict.DIRECT;   // refused before any handler
        }
        if (s == 409 && PlatformMessagingClient.IDEMPOTENCY_CONFLICT.equals(a.errorCode())) {
            return earlierMayExist && Snapshot.of(a.body()).isPresent() ? Verdict.ADOPT : Verdict.REFUSED;
        }
        if (s == 400 || s == 409 || s == 422) {
            return Verdict.REFUSED;
        }
        return Verdict.AMBIGUOUS;                     // 404, 408, 429, 5xx, an edge page, anything else
    }

    /** {@code PLATFORM_<error>[_<reason>]} for a refused request. */
    public static String refusal(Answer a) {
        String error = a.errorCode();
        if (a.status() == 409 && PlatformMessagingClient.IDEMPOTENCY_CONFLICT.equals(error)) {
            return "PLATFORM_KEY_CONFLICT";
        }
        if (a.status() == 409) {
            return "RESEND_NOT_PERMITTED";
        }
        String code = error == null ? "HTTP_" + a.status() : error.replace("messaging.", "").toUpperCase(Locale.ROOT);
        String reason = a.text("reason");
        return cut("PLATFORM_" + code + (reason == null ? "" : "_" + reason), 200);
    }

    /**
     * Forward only, by rank: ISSUED/SENDING 0 &lt; QUEUED 1 &lt; HELD / UNKNOWN 2 &lt; accepted 3 &lt; SENT 4 &lt;
     * DELIVERED 5 &lt; READ 6; FAILED is final and comes only before DELIVERED; UNKNOWN only from rank &lt; 2 or HELD;
     * after UNKNOWN only accepted or later.
     */
    public static boolean forward(boolean failed, int at, boolean held, boolean unknown, String to) {
        if (failed) {
            return false;
        }
        return switch (to) {
            case "FAILED" -> at < 5;
            case "UNKNOWN" -> at < 2 || held;
            default -> RANK.getOrDefault(to, 0) > at || unknown && RANK.getOrDefault(to, 0) >= 3;
        };
    }

    // ------------------------------------------------------------------------------------------------- ledger kinds

    /**
     * Commits the row ({@code reserve}, same transaction) and the exact request bytes, then {@code POST /send}; the
     * answer is acted on as the class comment says. {@code direct} sends it Dad Coach's own way after the row was
     * taken for it (state DIRECT).
     */
    public <R> Settled<R> send(Ledger l, UUID id, String body, Runnable reserve, Supplier<R> direct) {
        Instant now = clock.instant();
        // the bytes are durable before the first POST (v2.6): every re-POST and reconcile sends exactly these
        tx.executeWithoutResult(s -> {
            reserve.run();
            jdbc.update("UPDATE " + l.table() + " SET platform_request = ?, platform_state = 'POSTING', platform_posts = 1, "
                    + "platform_first_post_at = ?, platform_next_at = ? WHERE id = ?", body, Timestamp.from(now),
                    Timestamp.from(now.plus(LEASE)), id);
        });
        Answer answer = client.send(body);
        Verdict verdict = verdict(answer, false);
        if (verdict == Verdict.AMBIGUOUS && !"TIMEOUT".equals(answer.error()) && answer.status() != 429) {
            l.log().atInfo().setMessage("messaging." + l.event() + ".repost").addKeyValue(l.idKey(), id)
                    .addKeyValue("first", answer.summary()).log();
            tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_posts = platform_posts + 1 "
                    + "WHERE id = ?", id));
            answer = client.send(body);
            verdict = verdict(answer, true);
        }
        return settle(l, id, answer, verdict, direct);
    }

    private <R> Settled<R> settle(Ledger l, UUID id, Answer answer, Verdict verdict, Supplier<R> direct) {
        Logger log = l.log();
        switch (verdict) {
            case OWNED, ADOPT -> {
                Snapshot snap = Snapshot.of(answer.body()).orElseThrow();
                if (verdict == Verdict.ADOPT) {
                    log.atWarn().setMessage("messaging.platform_conflict_adopted").addKeyValue(l.idKey(), id)
                            .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status()).log();
                }
                apply(l, id, snap, verdict == Verdict.ADOPT ? "adopted" : "answer");
                log.atInfo().setMessage("messaging." + l.event() + ".platform").addKeyValue(l.idKey(), id)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                        .addKeyValue("reason", snap.reason()).addKeyValue("replayed", answer.text("replayed")).log();
                return new Settled<>(verdict, snap, null, null, answer);
            }
            case DIRECT -> {
                logDirect(log, l.event(), l.idKey(), id, answer);
                return new Settled<>(verdict, null, null, takeAndSend(l, id, direct).orElse(null), answer);
            }
            case REFUSED -> {
                String reason = refusal(answer);
                logRefused(log, l.event(), l.idKey(), id, answer, reason);
                Update u = l.refused(reason, Timestamp.from(clock.instant()));
                tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'REFUSED', "
                        + "platform_request = NULL, platform_next_at = NULL, " + u.set()
                        + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", append(u.params(), id)));
                return new Settled<>(verdict, null, reason, null, answer);
            }
            default -> {
                ambiguous(l, id, answer);
                return new Settled<>(Verdict.AMBIGUOUS, null, null, null, answer);
            }
        }
    }

    /** The row reads "on its way" (the kind's {@link Ledger#ambiguousSet}) and the job takes it from here. */
    private void ambiguous(Ledger l, UUID id, Answer answer) {
        Instant now = clock.instant();
        Instant next = now.plus(answer.retryAfterSeconds() != null ? Duration.ofSeconds(answer.retryAfterSeconds())
                : Duration.ofMinutes(1));
        l.log().atWarn().setMessage("messaging." + l.event() + ".ambiguous").addKeyValue(l.idKey(), id)
                .addKeyValue("answer", answer.summary()).log();
        String extra = l.ambiguousSet();
        tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'AMBIGUOUS', platform_next_at = ?"
                + (extra.isEmpty() ? "" : ", " + extra) + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')",
                Timestamp.from(next), id));
    }

    /**
     * The platform provably created nothing: the row is taken (state DIRECT, bytes cleared) and sent Dad Coach's own way.
     * Empty when another path took the row meanwhile (a report adopted it).
     */
    private <R> Optional<R> takeAndSend(Ledger l, UUID id, Supplier<R> direct) {
        Integer taken = tx.execute(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'DIRECT', "
                + "platform_request = NULL, platform_next_at = NULL WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", id));
        if (taken == null || taken == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(direct.get());
    }

    // ------------------------------------------------------------------------------------------------- apply

    /**
     * One platform statement about a row, forward only, under the row's lock: the sync answer, an adopted 409, a GET or
     * a delivery report. Returns whether the row moved.
     */
    public boolean apply(Ledger l, UUID id, Snapshot snap, String source) {
        Instant now = clock.instant();
        Logger log = l.log();
        Boolean moved = tx.execute(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + l.statusColumns() + ", platform_state, "
                    + "platform_status, platform_message_id FROM " + l.table() + " WHERE id = ? FOR UPDATE", id);
            if (rows.isEmpty()) {
                return false;
            }
            Map<String, Object> row = rows.get(0);
            String state = (String) row.get("platform_state");
            if (DIRECT.equals(state)) {
                // nothing was created, by the platform's own word - yet it speaks of this key: a second message is possible
                log.atError().setMessage("messaging." + l.event() + ".platform_after_direct").addKeyValue(l.idKey(), id)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                        .addKeyValue("source", source).log();
                return false;
            }
            String known = (String) row.get("platform_message_id");
            if (known != null && !known.equals(snap.messageId())) {
                log.atError().setMessage("messaging." + l.event() + ".other_message").addKeyValue(l.idKey(), id)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("known", known).log();
                return false;
            }
            if (!POSTING.equals(state) && !AMBIGUOUS.equals(state) && !GAVE_UP.equals(state) && !OWNED.equals(state)) {
                // a row the platform was never asked to send (direct mode) or refused: not this message's
                log.atWarn().setMessage("messaging." + l.event() + ".not_platform").addKeyValue(l.idKey(), id)
                        .addKeyValue("state", state).addKeyValue("status", snap.status()).addKeyValue("source", source).log();
                return false;
            }
            boolean move = l.forward(row, snap.status());
            // not moved: an adopted ambiguous row (no platform status yet) still takes the platform's word as its
            // status, so a QUEUED / HELD one keeps its follow-up lookup (review L1); a stale one never replaces a newer
            String status = move ? snap.status()
                    : row.get("platform_status") != null ? (String) row.get("platform_status") : snap.status();
            if (!move && row.get("platform_status") == null) {
                jdbc.update("UPDATE " + l.table() + " SET platform_status = ? WHERE id = ?", status, id);
            }
            Instant next = "QUEUED".equals(status) ? now.plus(settings.reconcileAfter())
                    : "HELD".equals(status) ? now.plus(HELD_POLL) : null;
            // the platform has it: the bytes are no longer needed
            jdbc.update("UPDATE " + l.table() + " SET platform_message_id = ?, platform_state = 'OWNED', platform_request = NULL, "
                    + "platform_next_at = ? WHERE id = ?", snap.messageId(), next == null ? null : Timestamp.from(next), id);
            if (!move) {
                return false;
            }
            jdbc.update("UPDATE " + l.table() + " SET platform_status = ? WHERE id = ?", snap.status(), id);
            l.applyStatus(id, snap, Timestamp.from(now));
            return true;
        });
        if (Boolean.TRUE.equals(moved)) {
            log.atInfo().setMessage("messaging." + l.event() + ".status").addKeyValue(l.idKey(), id)
                    .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                    .addKeyValue("reason", snap.reason()).addKeyValue("source", source).log();
            if (snap.providerMessageId() != null && receiptsEnabled) {
                receipts.applyPending(snap.providerMessageId());   // Meta's receipts that beat the platform's word
            }
        }
        return Boolean.TRUE.equals(moved);
    }

    /** A delivery report from the platform (spec §3.5) for this ledger's rows. */
    public Applied applyReport(Ledger l, String idempotencyKey, String messageId, JsonNode body) {
        Optional<Snapshot> snap = Snapshot.of(body);
        if (snap.isEmpty()) {
            return new Applied(false, false);
        }
        Optional<UUID> id = l.rowOf(idempotencyKey, messageId);
        if (id.isEmpty()) {
            return new Applied(false, false);
        }
        return new Applied(true, apply(l, id.get(), snap.get(), "report"));
    }

    // ------------------------------------------------------------------------------------------------- reconcile

    /**
     * One reconcile pass over the ledger's due rows; returns how many rows it looked at. D-047: the pass stops taking rows
     * once it has run {@code MESSAGING_RECONCILE_PASS_BUDGET} (real time, 15 s) - a hanging platform must not hold the
     * scheduler thread (shared by every job while {@code APP_SCHEDULING_LANES} is off) for a batch of timeouts; rows left
     * are still due and are taken by the next pass. Worst case per pass: the budget plus one call (a lookup 5 s, a
     * re-POST 12 s).
     */
    public int reconcileDue(Ledger l) {
        long started = System.nanoTime();
        long budgetNanos = settings.reconcilePassBudget().toNanos();
        List<UUID> due = jdbc.queryForList("SELECT id FROM " + l.table() + " WHERE platform_next_at IS NOT NULL "
                + "AND platform_next_at <= ? ORDER BY platform_next_at LIMIT " + BATCH, UUID.class, Timestamp.from(clock.instant()));
        int seen = 0;
        int position = 0;
        for (UUID id : due) {
            if (System.nanoTime() - started >= budgetNanos) {
                l.log().atWarn().setMessage("messaging." + l.event() + ".reconcile_budget_spent")
                        .addKeyValue("seen", seen).addKeyValue("left", due.size() - position)
                        .addKeyValue("budgetMs", settings.reconcilePassBudget().toMillis()).log();
                break;
            }
            position++;
            Instant now = clock.instant();      // per row: a slow row before it must not shorten this one's lease
            // claim: another instance (or the next pass) leaves it alone for the lease
            Integer claimed = tx.execute(s -> jdbc.update("UPDATE " + l.table() + " SET platform_next_at = ? WHERE id = ? "
                    + "AND platform_next_at <= ?", Timestamp.from(now.plus(LEASE)), id, Timestamp.from(now)));
            if (claimed == null || claimed == 0) {
                continue;
            }
            seen++;
            try {
                reconcile(l, id, now);
            } catch (RuntimeException e) {
                l.log().atWarn().setMessage("messaging." + l.event() + ".reconcile_failed").addKeyValue(l.idKey(), id)
                        .addKeyValue("error", e.getClass().getSimpleName()).log();
            }
        }
        return seen;
    }

    private void reconcile(Ledger l, UUID id, Instant now) {
        Logger log = l.log();
        Map<String, Object> row = jdbc.queryForMap("SELECT platform_state, platform_status, platform_request, "
                + "platform_posts, platform_first_post_at, created_at FROM " + l.table() + " WHERE id = ?", id);
        String state = (String) row.get("platform_state");
        if (OWNED.equals(state)) {
            Instant created = ((Timestamp) row.get("created_at")).toInstant();
            if (created.plus(OPEN_FOR).isBefore(now)) {
                clearNext(l, id);
                return;
            }
            Answer a = client.byKey(l.idempotencyKey(id));
            Optional<Snapshot> snap = a.status() == 200 ? Snapshot.of(a.body()) : Optional.empty();
            if (snap.isPresent()) {
                next(l, id, now.plus(settings.reconcileAfter()));
                apply(l, id, snap.get(), "reconcile");      // sets the next look (still QUEUED / HELD) or none
                return;
            }
            log.atWarn().setMessage("messaging." + l.event() + ".reconcile_unanswered").addKeyValue(l.idKey(), id)
                    .addKeyValue("answer", a.summary()).log();
            next(l, id, now.plus(settings.reconcileAfter()));
            return;
        }
        if (!POSTING.equals(state) && !AMBIGUOUS.equals(state)) {
            clearNext(l, id);
            return;
        }
        String body = (String) row.get("platform_request");
        if (body == null) {
            giveUp(l, id, "NO_REQUEST");
            return;
        }
        String blocker = l.blocker(id, body);
        if (blocker != null) {
            giveUp(l, id, blocker);
            return;
        }
        int posts = ((Number) row.get("platform_posts")).intValue();
        Instant firstPost = row.get("platform_first_post_at") == null ? now
                : ((Timestamp) row.get("platform_first_post_at")).toInstant();
        if (posts < MAX_POSTS) {
            jdbc.update("UPDATE " + l.table() + " SET platform_posts = platform_posts + 1 WHERE id = ?", id);
            Answer a = client.send(body);
            Verdict v = verdict(a, true);
            if (v == Verdict.AMBIGUOUS) {
                ambiguous(l, id, a);
                Instant reconcileAt = firstPost.plus(settings.reconcileAfter());
                next(l, id, posts + 1 < MAX_POSTS ? now.plus(Duration.ofMinutes(1))
                        : reconcileAt.isAfter(now) ? reconcileAt : now.plus(Duration.ofMinutes(1)));
                return;
            }
            settle(l, id, a, v, () -> {
                l.sendLate(id, body);
                return Boolean.TRUE;
            });
            return;
        }
        if (firstPost.plus(settings.reconcileAfter()).isAfter(now)) {
            next(l, id, firstPost.plus(settings.reconcileAfter()));
            return;
        }
        Answer a = client.byKey(l.idempotencyKey(id));
        Optional<Snapshot> snap = a.status() == 200 ? Snapshot.of(a.body()) : Optional.empty();
        if (snap.isPresent()) {
            log.atWarn().setMessage("messaging." + l.event() + ".reconciled").addKeyValue(l.idKey(), id)
                    .addKeyValue("messageId", snap.get().messageId()).addKeyValue("status", snap.get().status()).log();
            apply(l, id, snap.get(), "reconcile");
            return;
        }
        if (a.status() == 404 && PlatformMessagingClient.MESSAGE_NOT_FOUND.equals(a.errorCode())) {
            // the platform commits a message before any answer; minutes later, "not found" means it never existed (v2.5)
            if (!l.stillSendable(id, body, clock.instant())) {
                Update u = l.ended(Timestamp.from(clock.instant()));
                tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'GAVE_UP', "
                        + "platform_request = NULL, platform_next_at = NULL, " + u.set()
                        + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", append(u.params(), id)));
                return;
            }
            log.atWarn().setMessage("messaging." + l.event() + ".direct_fallback").addKeyValue(l.idKey(), id)
                    .addKeyValue("why", "RECONCILE_NOT_FOUND").log();
            takeAndSend(l, id, () -> {
                l.sendLate(id, body);
                return Boolean.TRUE;
            });
            return;
        }
        if (firstPost.plus(RECONCILE_FOR).isBefore(now)) {
            giveUp(l, id, "RECONCILE_GAVE_UP");
            return;
        }
        log.atWarn().setMessage("messaging." + l.event() + ".reconcile_unanswered").addKeyValue(l.idKey(), id)
                .addKeyValue("answer", a.summary()).log();
        next(l, id, now.plus(settings.reconcileAfter()));
    }

    /** No answer for an hour: it may have gone out - UNKNOWN, never sent again (spec §16, v2.5). */
    private void giveUp(Ledger l, UUID id, String why) {
        l.log().atWarn().setMessage("messaging." + l.event() + ".gave_up").addKeyValue(l.idKey(), id).addKeyValue("why", why).log();
        Update u = l.gaveUp("PLATFORM_" + why, Timestamp.from(clock.instant()));
        tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'GAVE_UP', platform_request = NULL, "
                + "platform_next_at = NULL, " + u.set() + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')",
                append(u.params(), id)));
    }

    private void next(Ledger l, UUID id, Instant at) {
        jdbc.update("UPDATE " + l.table() + " SET platform_next_at = ? WHERE id = ?", Timestamp.from(at), id);
    }

    private void clearNext(Ledger l, UUID id) {
        jdbc.update("UPDATE " + l.table() + " SET platform_next_at = NULL WHERE id = ?", id);
    }

    // ------------------------------------------------------------------------------------------------- no ledger

    /**
     * How a kind without a ledger is sent ({@link #sendOnce}). {@code readTimeoutMs}: the POST's read timeout (null = the
     * Send API's, 12 s). {@code conflictAdopts}: a 409 {@code idempotency_conflict} that carries the existing message is
     * adopted even on the first POST - for a key an earlier processing of the same business event may have used (D-048:
     * a reply keyed by the father's inbound message, whose redelivery runs again). {@code rateLimitWait}: a 429 whose
     * Retry-After (none = 1 s) is at most this is re-POSTed once after that wait (spec §16: turn replies once, ≤ 2 s).
     */
    public record Options(Integer readTimeoutMs, boolean conflictAdopts, Duration rateLimitWait) {
        public static final Options DEFAULT = new Options(null, false, Duration.ZERO);
    }

    /**
     * A kind without a ledger row (spec v2.6: the bytes stay in memory): POST, one immediate re-POST of the same bytes
     * when ambiguous (not after a timeout or a 429), then: OWNED / ADOPT - the platform has it; DIRECT (provably nothing
     * created, first attempt only) - {@code direct} sends it today's way; REFUSED - logged, never sent directly;
     * AMBIGUOUS - logged, never sent directly (nothing to reconcile against).
     */
    public <R> Settled<R> sendOnce(String event, Logger log, String idKey, Object id, String body, Supplier<R> direct) {
        return sendOnce(event, log, idKey, id, body, direct, Options.DEFAULT);
    }

    /** {@link #sendOnce(String, Logger, String, Object, String, Supplier)} with a kind's {@link Options}. */
    public <R> Settled<R> sendOnce(String event, Logger log, String idKey, Object id, String body, Supplier<R> direct,
                                   Options options) {
        Answer answer = post(body, options);
        Verdict verdict = verdict(answer, false);
        boolean mayExist = false;
        boolean rateLimits = options.rateLimitWait().compareTo(Duration.ZERO) > 0;
        if (answer.status() == 429 && rateLimits) {
            // the platform answers an existing key before its rate limit (v2.1): a 429 created nothing - one more try
            Duration wait = Duration.ofSeconds(answer.retryAfterSeconds() == null ? 1 : answer.retryAfterSeconds());
            if (wait.compareTo(options.rateLimitWait()) <= 0) {
                log.atInfo().setMessage("messaging." + event + ".rate_limited_repost").addKeyValue(idKey, id)
                        .addKeyValue("waitMs", wait.toMillis()).log();
                try {
                    Thread.sleep(wait.toMillis());
                    answer = post(body, options);
                    verdict = verdict(answer, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else if (verdict == Verdict.AMBIGUOUS && !"TIMEOUT".equals(answer.error()) && answer.status() != 429) {
            log.atInfo().setMessage("messaging." + event + ".repost").addKeyValue(idKey, id)
                    .addKeyValue("first", answer.summary()).log();
            answer = post(body, options);
            verdict = verdict(answer, true);
            mayExist = true;
        }
        if (verdict == Verdict.AMBIGUOUS && rateLimits && answer.status() == 429 && !mayExist) {
            verdict = Verdict.RATE_LIMITED;   // every answer a 429: nothing was created
        }
        if (verdict == Verdict.REFUSED && options.conflictAdopts() && answer.status() == 409
                && PlatformMessagingClient.IDEMPOTENCY_CONFLICT.equals(answer.errorCode())
                && Snapshot.of(answer.body()).isPresent()) {
            verdict = Verdict.ADOPT;
        }
        switch (verdict) {
            case OWNED, ADOPT -> {
                Snapshot snap = Snapshot.of(answer.body()).orElseThrow();
                if (verdict == Verdict.ADOPT) {
                    log.atWarn().setMessage("messaging.platform_conflict_adopted").addKeyValue(idKey, id)
                            .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status()).log();
                }
                log.atInfo().setMessage("messaging." + event + ".platform").addKeyValue(idKey, id)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                        .addKeyValue("reason", snap.reason()).addKeyValue("replayed", answer.text("replayed")).log();
                return new Settled<>(verdict, snap, null, null, answer);
            }
            case DIRECT -> {
                logDirect(log, event, idKey, id, answer);
                return new Settled<>(verdict, null, null, direct.get(), answer);
            }
            case REFUSED -> {
                String reason = refusal(answer);
                logRefused(log, event, idKey, id, answer, reason);
                return new Settled<>(verdict, null, reason, null, answer);
            }
            case RATE_LIMITED -> {
                log.atWarn().setMessage("messaging." + event + ".rate_limited").addKeyValue(idKey, id)
                        .addKeyValue("retryAfter", answer.retryAfterSeconds()).log();
                return new Settled<>(verdict, null, "PLATFORM_RATE_LIMITED", null, answer);
            }
            default -> {
                log.atWarn().setMessage("messaging." + event + ".ambiguous").addKeyValue(idKey, id)
                        .addKeyValue("answer", answer.summary()).addKeyValue("ledger", false).log();
                return new Settled<>(Verdict.AMBIGUOUS, null, null, null, answer);
            }
        }
    }

    private Answer post(String body, Options options) {
        return options.readTimeoutMs() == null ? client.send(body) : client.send(body, options.readTimeoutMs());
    }

    private static void logDirect(Logger log, String event, String idKey, Object id, Answer answer) {
        if (answer.status() == 401 || answer.status() == 403) {
            log.atError().setMessage("messaging.platform_auth_failed").addKeyValue(idKey, id)
                    .addKeyValue("status", answer.status()).addKeyValue("error", answer.errorCode()).log();
        } else {
            log.atInfo().setMessage("messaging." + event + ".direct_fallback").addKeyValue(idKey, id)
                    .addKeyValue("why", answer.summary()).addKeyValue("reason", answer.text("reason")).log();
        }
    }

    private static void logRefused(Logger log, String event, String idKey, Object id, Answer answer, String reason) {
        log.atError().setMessage("messaging." + event + ".refused").addKeyValue(idKey, id)
                .addKeyValue("status", answer.status()).addKeyValue("error", answer.errorCode())
                .addKeyValue("reason", reason).addKeyValue("field", answer.text("field")).log();
    }

    // ------------------------------------------------------------------------------------------------- SHADOW

    /**
     * SHADOW: the platform's dry run of the request {@code body} builds ({@code dryRun: true}), built and compared on the
     * kind's own lane ({@code messaging-shadow-<kind>}, queue {@value #SHADOW_QUEUE}), never on the caller's thread. A
     * full lane drops the comparison and logs {@code messaging.shadow.dropped} with the kind's running count.
     */
    public void shadow(String kind, Logger log, String idKey, Object id, Supplier<String> body, ShadowFacts dc) {
        lane(kind).execute(() -> {
            String request;
            try {
                request = body.get();
            } catch (RuntimeException e) {
                log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", kind)
                        .addKeyValue("error", e.getClass().getSimpleName()).log();
                return;
            }
            compare(kind, log, idKey, id, request, dc);
        });
    }

    static final int SHADOW_QUEUE = 100;

    private ThreadPoolExecutor lane(String kind) {
        return shadowLanes.computeIfAbsent(kind, k -> new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(SHADOW_QUEUE), r -> {
                    Thread t = new Thread(r, "messaging-shadow-" + k.toLowerCase(Locale.ROOT));
                    t.setDaemon(true);
                    return t;
                }, (r, executor) -> {
                    long dropped = shadowDropped.computeIfAbsent(k, x -> new java.util.concurrent.atomic.AtomicLong())
                            .incrementAndGet();
                    org.slf4j.LoggerFactory.getLogger(PlatformSendFlow.class).atWarn()
                            .setMessage("messaging.shadow.dropped").addKeyValue("kind", k).addKeyValue("dropped", dropped).log();
                }));
    }

    /** Comparisons dropped so far because the kind's lane was full. */
    public long shadowDropped(String kind) {
        java.util.concurrent.atomic.AtomicLong n = shadowDropped.get(kind);
        return n == null ? 0 : n.get();
    }

    @jakarta.annotation.PreDestroy
    void stopShadowLane() {
        shadowLanes.values().forEach(ThreadPoolExecutor::shutdownNow);
    }

    /** Tests: whether every shadow lane has nothing left to do. */
    public boolean shadowIdle() {
        return shadowLanes.values().stream().allMatch(l -> l.getActiveCount() == 0 && l.getQueue().isEmpty());
    }

    private void compare(String kind, Logger log, String idKey, Object id, String body, ShadowFacts dc) {
        try {
            Answer a = client.send(body);
            String dcWindow = dc.template() != null ? "TEMPLATE" : "SESSION";
            String dcOutcome = !dc.successful() ? "FAIL" : dc.held() ? "HOLD" : "SEND";
            if (a.status() == 400 || a.status() == 409 || a.status() == 422) {
                // the platform would refuse the request outright (review M2): a mismatch whenever Dad Coach's own send
                // went out - in PLATFORM mode this message would have FAILED, never been sent directly
                String code = refusal(a);
                if (dc.successful()) {
                    log.atWarn().setMessage("messaging.shadow.mismatch").addKeyValue("kind", kind)
                            .addKeyValue(idKey, id).addKeyValue("field", "refused").addKeyValue("code", code)
                            .addKeyValue("product", dcOutcome + (dc.template() == null ? "" : "/" + dc.template()))
                            .addKeyValue("platform", "HTTP_" + a.status()).addKeyValue("fieldRefused", a.text("field")).log();
                } else {
                    log.atInfo().setMessage("messaging.shadow.match").addKeyValue("kind", kind)
                            .addKeyValue(idKey, id).addKeyValue("platform", code).log();
                }
                return;
            }
            if (a.status() != 200 || a.body() == null) {
                log.atInfo().setMessage("messaging.shadow.unavailable").addKeyValue("kind", kind)
                        .addKeyValue(idKey, id).addKeyValue("answer", a.summary()).log();
                return;
            }
            String wouldBe = a.text("wouldBe");
            String window = a.text("channelWindow");
            String template = a.body().path("template").hasNonNull("name") ? a.body().path("template").path("name").asText() : null;
            String platformOutcome = "HOLD".equals(wouldBe) ? "HOLD" : "SEND".equals(wouldBe) ? "SEND" : "FAIL";
            int mismatches = 0;
            if ("HOLD".equals(dcOutcome) != "HOLD".equals(platformOutcome)) {
                mismatches += mismatch(kind, log, idKey, id, "hold", dcOutcome, platformOutcome + "/" + a.text("holdWhy"));
            } else if ("FAIL".equals(dcOutcome) != "FAIL".equals(platformOutcome)) {
                mismatches += mismatch(kind, log, idKey, id, "outcome", dcOutcome + "/" + dc.failureReason(),
                        platformOutcome + "/" + a.text("reason"));
            }
            if (window != null && !"FAIL".equals(dcOutcome) && !"FAIL".equals(platformOutcome)) {
                if (!dcWindow.equals(window)) {
                    mismatches += mismatch(kind, log, idKey, id, "window", dcWindow, window);
                } else if ("TEMPLATE".equals(window) && template != null && !template.equals(dc.template())) {
                    mismatches += mismatch(kind, log, idKey, id, "template", dc.template(), template);
                }
            }
            if (mismatches == 0) {
                log.atInfo().setMessage("messaging.shadow.match").addKeyValue("kind", kind)
                        .addKeyValue(idKey, id).addKeyValue("outcome", dcOutcome).addKeyValue("window", dcWindow).log();
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", kind)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    private static int mismatch(String kind, Logger log, String idKey, Object id, String field, String product,
                                String platformValue) {
        log.atWarn().setMessage("messaging.shadow.mismatch").addKeyValue("kind", kind)
                .addKeyValue(idKey, id).addKeyValue("field", field).addKeyValue("product", product)
                .addKeyValue("platform", platformValue).log();
        return 1;
    }

    // ------------------------------------------------------------------------------------------------- the request

    /** The request's "who" fields (spec §3.1), in D-045's order: worker, conversation, recipient, tenant, person. */
    public Map<String, Object> newRequest(Optional<Father> father, String phone) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("workerKey", platform.getWorkerKey());
        r.put("workflowKey", platform.getWorkflowKey());
        r.put("userId", PersonRefs.whatsappId(phone));
        r.put("channelId", "whatsapp");
        r.put("to", phone);
        r.put("tenantId", platform.getTenantId());
        father.ifPresent(f -> {
            r.put("personRef", PersonRefs.of(f.getId()));
            if (f.getDisplayName() != null) {
                r.put("personName", f.getDisplayName());
            }
        });
        return r;
    }

    /** Spec v2.13: Dad Coach keeps no inbound time - its window closes 24 h after the last one. */
    public Optional<Instant> lastInbound(Optional<Father> father) {
        return father.flatMap(f -> endpoints.findPrimaryByFatherId(new UUID(0L, f.getId())))
                .map(e -> e.getSessionClosesAt()).map(closes -> closes.minus(Duration.ofHours(24)));
    }

    public String json(Map<String, Object> request) {
        try {
            return json.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Object[] append(Object[] params, Object last) {
        Object[] all = java.util.Arrays.copyOf(params, params.length + 1);
        all[params.length] = last;
        return all;
    }

    private static String text(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    public static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
