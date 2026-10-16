package com.dadcoach.integration.platform.messaging;

import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.platform.client.PlatformMessagingClient.Answer;
import com.platform.client.PlatformMessagingClient.Kind;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mechanics every kind Dad Coach hands to the platform's Send API shares - platform spec
 * {@code PHASE6_2_SEND_API_SPEC.md} §3, §7.4, §8, §16 with "Spec v2" v2.1-v2.6 (D-045 / D-046 / D-048). Since D-053
 * (Unified Workflow 6.4, {@code PHASE6_4_CLEANUP_SPEC.md} §4.1) the platform is the only sender: no answer leads to Dad
 * Coach sending a message itself. Each kind supplies its ledger ({@link Ledger}) and its own logger, so its log lines
 * keep their names ({@code messaging.<event>.*}).
 *
 * <p>The answer table (§4.1 with v2 R5/R6/R12/R13/R14):
 * <ul>
 *   <li>2xx with a status: OWNED - the platform has it ({@link #apply}, forward only, bytes cleared).</li>
 *   <li>Provably nothing created on an attempt after which no earlier one may exist - 503 {@code messaging.disabled}
 *       (ERROR {@code messaging.platform_disabled}), 401/403 (ERROR {@code messaging.platform_auth_failed}) or a request
 *       never written: <b>NOT_CREATED</b>. A ledger row keeps its stored request bytes and stays pending (state
 *       {@code NOT_CREATED}); {@link #reconcileDue} re-POSTs the same bytes while the kind's relevance rule holds
 *       ({@link Ledger#stillSendable}: {@code notAfter}, the link's validity), then FAILED {@code PLATFORM_NOT_CREATED}.
 *       A line without a ledger ({@link #sendOnce}): one re-POST after at most 2 s, then dropped with WARN
 *       {@code messaging.platform_not_created}.</li>
 *   <li>400/422 (any attempt) and a first-attempt 409: REFUSED (FAILED with the platform's reason).</li>
 *   <li>A 409 after an ambiguous attempt: ADOPT.</li>
 *   <li>Anything ambiguous (timeout, 5xx, an edge page, 429, a connection lost after write): one immediate re-POST of
 *       the same bytes (not after a timeout or a 429), then the reconcile job re-POSTs once more and, after
 *       {@code MESSAGING_RECONCILE_AFTER}, asks by key: 200 adopt; 404 {@code messaging.message_not_found} (nothing
 *       created) - re-POST the stored bytes while still relevant, else FAILED; anything else again for 1 h, then
 *       UNKNOWN - never sent again.</li>
 * </ul>
 * A request that cannot be built is a Dad Coach bug: the caller records FAILED {@code REQUEST_BUILD_FAILED} and logs an
 * ERROR. An idempotency key the platform's key shape refuses is replaced by {@link #platformKey} (deterministic).
 */
@Component
public class PlatformSendFlow {

    public static final String POSTING = "POSTING";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String NOT_CREATED = "NOT_CREATED";
    public static final String OWNED = "OWNED";
    public static final String REFUSED = "REFUSED";
    public static final String GAVE_UP = "GAVE_UP";
    /** A request Dad Coach could not build (a bug): FAILED with this reason, nothing posted. */
    public static final String REQUEST_BUILD_FAILED = "REQUEST_BUILD_FAILED";
    /** Nothing was created and it is no longer worth sending (or no longer can be): FAILED with this reason. */
    public static final String PLATFORM_NOT_CREATED = "PLATFORM_NOT_CREATED";

    /** A POST in flight is the sending thread's for this long; after it the job takes the row as ambiguous. */
    public static final Duration LEASE = Duration.ofMinutes(2);
    /** The first POST, one immediate re-POST and one by the job (spec v2.5: re-POST up to 2 times). */
    public static final int MAX_POSTS = 3;
    /** Reconcile by GET for this long after the first POST, then UNKNOWN (spec §16: every 5 min for 1 h). */
    public static final Duration RECONCILE_FOR = Duration.ofHours(1);
    /** A row the platform keeps QUEUED/HELD is looked up by GET while it is this young (held: 24 h at most). */
    public static final Duration OPEN_FOR = Duration.ofHours(26);
    public static final Duration HELD_POLL = Duration.ofHours(1);
    /** A NOT_CREATED row is re-POSTed after 1 min, then doubling, at most every 10 min (while still relevant). */
    static final Duration NOT_CREATED_FIRST = Duration.ofMinutes(1);
    static final Duration NOT_CREATED_MAX = Duration.ofMinutes(10);
    static final int BATCH = 20;

    /** The platform's idempotency key shape ({@code SendRequestValidator.KEY}). */
    static final Pattern KEY_SHAPE = Pattern.compile("[A-Za-z0-9:_.~@/+=-]{1,200}");

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

    /** How one answer is acted on (spec 6.4 §4.1). */
    public enum Verdict {
        OWNED, ADOPT, REFUSED, AMBIGUOUS,
        /** Provably nothing was created (503 messaging.disabled, 401/403, never written): pending, re-POSTed later. */
        NOT_CREATED,
        /**
         * D-048 review: only from {@link #sendOnce} with a {@link Options#rateLimitWait}: every answer was a 429 - the
         * platform answers an existing key before its rate limit (v2.1), so nothing was created; the caller may try the
         * same key again later.
         */
        RATE_LIMITED
    }

    /** How a send ended for its caller. {@code snapshot}: OWNED / ADOPT; {@code reason}: REFUSED / RATE_LIMITED. */
    public record Settled(Verdict verdict, Snapshot snapshot, String reason, Answer answer) {}

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

        /** The row's idempotency key as the platform knows it ({@link PlatformSendFlow#platformKey}). */
        String idempotencyKey(UUID id);

        /** The row a delivery report is about, by its idempotency key or else the platform's message id; empty if none. */
        Optional<UUID> rowOf(String idempotencyKey, String messageId);

        /** The row's own status columns {@link #forward} reads (besides the platform columns). */
        String statusColumns();

        /** Whether the platform's {@code to} moves the row as it stands (forward only). */
        boolean forward(Map<String, Object> row, String to);

        /** The platform's status onto the row's own columns; runs in the apply transaction, the row locked. */
        void applyStatus(UUID id, Snapshot snap, Timestamp at);

        /** Extra SET while the message is on its way but not owned (ambiguous or not created yet; "" for none). */
        String ambiguousSet();

        /** The row's own columns for a refused request (400/422/409). */
        Update refused(String reason, Timestamp at);

        /** The row's own columns when no answer came for an hour: UNKNOWN, never sent again. */
        Update gaveUp(String reason, Timestamp at);

        /** The row's own columns when the platform never created it and it is no longer sent: FAILED {@code reason}. */
        Update ended(String reason, Timestamp at);

        /** Before a reconcile re-POST: null to go on, else why it cannot be sent any more ({@code NO_FATHER}, ...). */
        String blocker(UUID id, String body);

        /** Nothing created (yet): whether it is still worth sending - the kind's relevance rule. */
        boolean stillSendable(UUID id, String body, Instant now);

        /**
         * D-067: the bytes a reconcile re-POST sends, from the stored ones - the stored bytes themselves unless the kind
         * keeps a secret out of them (a morning's sign-in token: a new one is minted for each re-POST); null when they can
         * no longer be built (then it is never posted again).
         */
        default String toPost(UUID id, String stored) {
            return stored;
        }
    }

    private final MessagingSettings settings;
    private final PlatformMessagingClient client;
    private final WorkflowPlatformProperties platform;
    private final CommunicationEndpointRepository endpoints;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;

    public PlatformSendFlow(MessagingSettings settings, PlatformMessagingClient client, WorkflowPlatformProperties platform,
                            CommunicationEndpointRepository endpoints, JdbcTemplate jdbc,
                            PlatformTransactionManager transactions, ObjectMapper json, Clock clock) {
        this.settings = settings;
        this.client = client;
        this.platform = platform;
        this.endpoints = endpoints;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------- the key

    /**
     * The idempotency key as the platform accepts it (spec v2 R5): the key itself when it fits the platform's shape
     * ({@code [A-Za-z0-9:_.~@/+=-]{1,200}}), else {@code h:} + the first 32 hex digits of its SHA-256 - deterministic, so
     * every re-POST, lookup and redelivery uses the same key.
     */
    public static String platformKey(String key) {
        if (key != null && KEY_SHAPE.matcher(key).matches()) {
            return key;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(String.valueOf(key).getBytes(StandardCharsets.UTF_8));
            return "h:" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------------------------------------- the answer

    /** @param earlierMayExist an earlier attempt with these bytes may have created the message */
    public static Verdict verdict(Answer a, boolean earlierMayExist) {
        if (a.kind() == Kind.NEVER_WRITTEN) {
            return earlierMayExist ? Verdict.AMBIGUOUS : Verdict.NOT_CREATED;
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
            return earlierMayExist ? Verdict.AMBIGUOUS : Verdict.NOT_CREATED;
        }
        if (s == 401 || s == 403) {
            return earlierMayExist ? Verdict.AMBIGUOUS : Verdict.NOT_CREATED;   // refused before any handler (R13)
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
     * answer is acted on as the class comment says.
     */
    public Settled send(Ledger l, UUID id, String body, Runnable reserve) {
        return send(l, id, body, body, reserve);
    }

    /**
     * D-067: {@code stored} is committed, {@code posted} is POSTed (and re-POSTed at once) - the same bytes but for a
     * secret the stored ones never hold; a reconcile re-POST sends {@link Ledger#toPost} of the stored bytes.
     */
    public Settled send(Ledger l, UUID id, String stored, String posted, Runnable reserve) {
        Instant now = clock.instant();
        String body = posted;
        // the bytes are durable before the first POST (v2.6): every re-POST and reconcile sends exactly these
        tx.executeWithoutResult(s -> {
            reserve.run();
            jdbc.update("UPDATE " + l.table() + " SET platform_request = ?, platform_state = 'POSTING', platform_posts = 1, "
                    + "platform_first_post_at = ?, platform_next_at = ? WHERE id = ?", stored, Timestamp.from(now),
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
        return settle(l, id, answer, verdict, 1);
    }

    /** @param notCreatedPosts how many POSTs in a row created nothing (the next re-POST's backoff) */
    private Settled settle(Ledger l, UUID id, Answer answer, Verdict verdict, int notCreatedPosts) {
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
                return new Settled(verdict, snap, null, answer);
            }
            case NOT_CREATED -> {
                logNotCreated(log, l.event(), l.idKey(), id, answer, true);
                notCreated(l, id, notCreatedPosts);
                return new Settled(verdict, null, null, answer);
            }
            case REFUSED -> {
                String reason = refusal(answer);
                logRefused(log, l.event(), l.idKey(), id, answer, reason);
                Update u = l.refused(reason, Timestamp.from(clock.instant()));
                tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'REFUSED', "
                        + "platform_request = NULL, platform_next_at = NULL, " + u.set()
                        + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS','NOT_CREATED')", append(u.params(), id)));
                return new Settled(verdict, null, reason, answer);
            }
            default -> {
                ambiguous(l, id, answer);
                return new Settled(Verdict.AMBIGUOUS, null, null, answer);
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
                + (extra.isEmpty() ? "" : ", " + extra)
                + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS','NOT_CREATED')", Timestamp.from(next), id));
    }

    /**
     * Provably nothing was created: the row keeps its bytes, reads "on its way" (it is pending, never stuck or failed)
     * and the job re-POSTs the same bytes later (1 min, doubling, at most 10 min) while it is still relevant.
     */
    private void notCreated(Ledger l, UUID id, int posts) {
        Duration wait = NOT_CREATED_FIRST.multipliedBy(1L << Math.min(Math.max(posts - 1, 0), 4));
        Instant next = clock.instant().plus(wait.compareTo(NOT_CREATED_MAX) > 0 ? NOT_CREATED_MAX : wait);
        String extra = l.ambiguousSet();
        tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'NOT_CREATED', platform_next_at = ?"
                + (extra.isEmpty() ? "" : ", " + extra)
                + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS','NOT_CREATED')", Timestamp.from(next), id));
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
            String known = (String) row.get("platform_message_id");
            if (known != null && !known.equals(snap.messageId())) {
                log.atError().setMessage("messaging." + l.event() + ".other_message").addKeyValue(l.idKey(), id)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("known", known).log();
                return false;
            }
            if (!POSTING.equals(state) && !AMBIGUOUS.equals(state) && !NOT_CREATED.equals(state) && !GAVE_UP.equals(state)
                    && !OWNED.equals(state)) {
                // a row the platform was never asked to send (before D-053: a direct send) or refused: not this message's
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
     * scheduler thread for a batch of timeouts; rows left are still due and are taken by the next pass.
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
        if (!POSTING.equals(state) && !AMBIGUOUS.equals(state) && !NOT_CREATED.equals(state)) {
            clearNext(l, id);
            return;
        }
        String body = (String) row.get("platform_request");
        if (body == null) {
            giveUp(l, id, "NO_REQUEST");
            return;
        }
        int posts = ((Number) row.get("platform_posts")).intValue();
        if (NOT_CREATED.equals(state)) {
            repostNotCreated(l, id, body, posts);
            return;
        }
        String blocker = l.blocker(id, body);
        if (blocker != null) {
            giveUp(l, id, blocker);
            return;
        }
        Instant firstPost = row.get("platform_first_post_at") == null ? now
                : ((Timestamp) row.get("platform_first_post_at")).toInstant();
        if (posts < MAX_POSTS) {
            String posted = l.toPost(id, body);
            if (posted == null) {
                giveUp(l, id, "NO_REQUEST");      // it may have gone out with the first bytes: UNKNOWN, never sent again
                return;
            }
            jdbc.update("UPDATE " + l.table() + " SET platform_posts = platform_posts + 1 WHERE id = ?", id);
            Answer a = client.send(posted);
            Verdict v = verdict(a, true);
            if (v == Verdict.AMBIGUOUS) {
                ambiguous(l, id, a);
                Instant reconcileAt = firstPost.plus(settings.reconcileAfter());
                next(l, id, posts + 1 < MAX_POSTS ? now.plus(Duration.ofMinutes(1))
                        : reconcileAt.isAfter(now) ? reconcileAt : now.plus(Duration.ofMinutes(1)));
                return;
            }
            settle(l, id, a, v, 1);
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
            // the platform commits a message before any answer; minutes later, "not found" means it never existed (v2.5):
            // the platform holds nothing under this key, so the stored bytes are POSTed again (never a second message)
            log.atWarn().setMessage("messaging." + l.event() + ".not_found_repost").addKeyValue(l.idKey(), id).log();
            Integer taken = tx.execute(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'NOT_CREATED' "
                    + "WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", id));
            if (taken != null && taken > 0) {
                repostNotCreated(l, id, body, 1);
            }
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

    /**
     * A row nothing was created for: re-POSTs the same bytes while it is still relevant, else FAILED
     * {@code PLATFORM_NOT_CREATED} (spec 6.4 §4.1, R14).
     */
    private void repostNotCreated(Ledger l, UUID id, String body, int posts) {
        Instant now = clock.instant();
        String blocker = l.blocker(id, body);
        boolean relevant = blocker == null && l.stillSendable(id, body, now);
        String posted = relevant ? l.toPost(id, body) : null;
        if (posted == null) {
            l.log().atWarn().setMessage("messaging." + l.event() + ".not_created_ended").addKeyValue(l.idKey(), id)
                    .addKeyValue("why", blocker != null ? blocker : !relevant ? "NO_LONGER_RELEVANT" : "NO_REQUEST").log();
            end(l, id, PLATFORM_NOT_CREATED);
            return;
        }
        jdbc.update("UPDATE " + l.table() + " SET platform_posts = platform_posts + 1 WHERE id = ?", id);
        Answer a = client.send(posted);
        Verdict v = verdict(a, false);   // every earlier attempt provably created nothing
        if (v == Verdict.AMBIGUOUS) {
            // it may exist now: the ambiguous path (re-POST, then the lookup by key) takes it from here
            ambiguous(l, id, a);
            jdbc.update("UPDATE " + l.table() + " SET platform_first_post_at = ?, platform_posts = ? WHERE id = ?",
                    Timestamp.from(now), MAX_POSTS - 1, id);
            return;
        }
        settle(l, id, a, v, posts + 1);
    }

    private void end(Ledger l, UUID id, String reason) {
        Update u = l.ended(reason, Timestamp.from(clock.instant()));
        tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'GAVE_UP', "
                + "platform_request = NULL, platform_next_at = NULL, " + u.set()
                + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS','NOT_CREATED')", append(u.params(), id)));
    }

    /** No answer for an hour: it may have gone out - UNKNOWN, never sent again (spec §16, v2.5). */
    private void giveUp(Ledger l, UUID id, String why) {
        l.log().atWarn().setMessage("messaging." + l.event() + ".gave_up").addKeyValue(l.idKey(), id).addKeyValue("why", why).log();
        Update u = l.gaveUp("PLATFORM_" + why, Timestamp.from(clock.instant()));
        tx.executeWithoutResult(s -> jdbc.update("UPDATE " + l.table() + " SET platform_state = 'GAVE_UP', platform_request = NULL, "
                + "platform_next_at = NULL, " + u.set() + " WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS','NOT_CREATED')",
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
     * {@code notCreatedWait}: a "nothing created" answer is re-POSTed once after this wait (D-053, ≤ 2 s).
     */
    public record Options(Integer readTimeoutMs, boolean conflictAdopts, Duration rateLimitWait, Duration notCreatedWait) {
        public static final Options DEFAULT = new Options(null, false, Duration.ZERO, Duration.ofSeconds(2));
    }

    /**
     * A kind without a ledger row (spec v2.6: the bytes stay in memory): POST, one immediate re-POST of the same bytes
     * when ambiguous (not after a timeout or a 429), one re-POST after a short wait when nothing was created, then:
     * OWNED / ADOPT - the platform has it; NOT_CREATED - dropped (WARN {@code messaging.platform_not_created}); REFUSED -
     * logged; AMBIGUOUS - logged (it may have gone out: never sent a second time).
     */
    public Settled sendOnce(String event, Logger log, String idKey, Object id, String body, Options options) {
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
                if (sleep(wait)) {
                    answer = post(body, options);
                    verdict = verdict(answer, false);
                }
            }
        } else if (verdict == Verdict.NOT_CREATED) {
            logNotCreated(log, event, idKey, id, answer, false);
            if (sleep(options.notCreatedWait())) {
                log.atInfo().setMessage("messaging." + event + ".not_created_repost").addKeyValue(idKey, id).log();
                answer = post(body, options);
                verdict = verdict(answer, false);
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
                return new Settled(verdict, snap, null, answer);
            }
            case NOT_CREATED -> {
                log.atWarn().setMessage("messaging.platform_not_created").addKeyValue("event", event).addKeyValue(idKey, id)
                        .addKeyValue("answer", answer.summary()).log();
                return new Settled(verdict, null, PLATFORM_NOT_CREATED, answer);
            }
            case REFUSED -> {
                String reason = refusal(answer);
                logRefused(log, event, idKey, id, answer, reason);
                return new Settled(verdict, null, reason, answer);
            }
            case RATE_LIMITED -> {
                log.atWarn().setMessage("messaging." + event + ".rate_limited").addKeyValue(idKey, id)
                        .addKeyValue("retryAfter", answer.retryAfterSeconds()).log();
                return new Settled(verdict, null, "PLATFORM_RATE_LIMITED", answer);
            }
            default -> {
                log.atWarn().setMessage("messaging." + event + ".ambiguous").addKeyValue(idKey, id)
                        .addKeyValue("answer", answer.summary()).addKeyValue("ledger", false).log();
                return new Settled(Verdict.AMBIGUOUS, null, null, answer);
            }
        }
    }

    private static boolean sleep(Duration wait) {
        try {
            if (!wait.isZero() && !wait.isNegative()) {
                Thread.sleep(wait.toMillis());
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Answer post(String body, Options options) {
        return options.readTimeoutMs() == null ? client.send(body) : client.send(body, options.readTimeoutMs());
    }

    /** ERROR for 401/403 and the kill switch (alertable misconfiguration, R2/R13); WARN for a request never written. */
    private static void logNotCreated(Logger log, String event, String idKey, Object id, Answer answer, boolean ledger) {
        if (answer.status() == 401 || answer.status() == 403) {
            log.atError().setMessage("messaging.platform_auth_failed").addKeyValue("event", event).addKeyValue(idKey, id)
                    .addKeyValue("status", answer.status()).addKeyValue("error", answer.errorCode()).log();
        } else if (answer.status() == 503) {
            log.atError().setMessage("messaging.platform_disabled").addKeyValue("event", event).addKeyValue(idKey, id)
                    .addKeyValue("reason", answer.text("reason")).log();
        } else {
            log.atWarn().setMessage("messaging." + event + ".not_created").addKeyValue(idKey, id)
                    .addKeyValue("why", answer.summary()).addKeyValue("ledger", ledger).log();
        }
    }

    private static void logRefused(Logger log, String event, String idKey, Object id, Answer answer, String reason) {
        log.atError().setMessage("messaging." + event + ".refused").addKeyValue(idKey, id)
                .addKeyValue("status", answer.status()).addKeyValue("error", answer.errorCode())
                .addKeyValue("reason", reason).addKeyValue("field", answer.text("field")).log();
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
        // D-050 (Unified Workflow 5.4 R-4): the father's timezone seeds a conversation the platform creates (or fills in one
        // that has none), as the inbound turn's metadata does - so his 09:00 daily check runs in it, not at 09:00 UTC
        r.put("timezone", com.dadcoach.integration.platform.FatherTimezones.of(father.orElse(null)).getId());
        return r;
    }

    /**
     * D-053 (spec v2 R7): a request about nobody - no {@code userId}, {@code personRef} or {@code personName}, only
     * {@code to} - so the platform opens no conversation for it (the deletion answer: he is being deleted).
     */
    public Map<String, Object> newAnonymousRequest(String phone) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("workerKey", platform.getWorkerKey());
        r.put("workflowKey", platform.getWorkflowKey());
        r.put("channelId", "whatsapp");
        r.put("to", phone);
        r.put("tenantId", platform.getTenantId());
        return r;
    }

    /** Spec v2.13: Dad Coach keeps no inbound time - its window closes 24 h after the last one. */
    public Optional<Instant> lastInbound(Optional<Father> father) {
        return father.flatMap(f -> endpoints.findPrimaryByFatherId(new UUID(0L, f.getId())))
                .map(e -> e.getSessionClosesAt()).map(closes -> closes.minus(Duration.ofHours(24)));
    }

    /**
     * D-053 (spec 6.4 §2): what the platform's kind→template mapping needs - the message type ({@code variant}, "" =
     * the kind's default), its positional values, named values and quick-reply payloads. Never a Meta template name.
     */
    public static Map<String, Object> templateData(String variant, List<String> values, Map<String, String> named,
                                                   List<String> payloads) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("variant", variant == null ? "" : variant);
        if (values != null && !values.isEmpty()) {
            d.put("values", values);
        }
        if (named != null && !named.isEmpty()) {
            d.put("named", named);
        }
        if (payloads != null && !payloads.isEmpty()) {
            d.put("payloads", payloads);
        }
        return d;
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
