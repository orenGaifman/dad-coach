package com.dadcoach.auth;

import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.dto.OutboundMessageDto.LinkButton;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.dadcoach.integration.platform.SharedNumberGate;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind;
import com.dadcoach.integration.platform.timeline.TimelineReports;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-045 (Unified Workflow Phase 6.3-DC, step DC1): a father's dashboard link sent by the platform's Send API instead of
 * Dad Coach calling Meta itself - switch {@code MESSAGING_MODE_LOGIN_LINK} ({@link MessagingSettings}). Platform spec
 * {@code PHASE6_2_SEND_API_SPEC.md} §3, §7.4, §8, §16, §17.1 with "Spec v2" v2.1-v2.6.
 *
 * <p><b>PLATFORM.</b> The link row and the exact request bytes are committed first (own transaction), then
 * {@code POST /send} with idempotency key {@code login-link:<linkId>}. The answer:
 * <ul>
 *   <li>2xx with a status - the platform owns it: mapped onto the row (§7.4: QUEUED = SENT/QUEUED, HELD = SENT/HELD +
 *       {@code held:<n>}, ACCEPTED = SENT + wamid, FAILED = FAILED {@code PLATFORM_<reason>}, UNKNOWN = SENT/UNKNOWN),
 *       forward only; the bytes are cleared. The platform records the timeline row (Dad Coach reports nothing).</li>
 *   <li>Provably nothing created - 503 with body {@code messaging.disabled} (the platform answers an existing key before
 *       its kill switch, v2.1), 401/403 or a request never written (connection refused, DNS, TLS) on the first attempt -
 *       Dad Coach sends it itself, today's way, same row.</li>
 *   <li>400/422 - FAILED {@code PLATFORM_<error>} and an ERROR log: the request is wrong; sending it directly would send
 *       a wrong message. 409 conflict on the first attempt: FAILED {@code PLATFORM_KEY_CONFLICT}; after an ambiguous
 *       attempt: adopted (v2.6).</li>
 *   <li>Anything ambiguous (timeout, 5xx, an edge's 502/503/504, a connection lost after write, 429): never sent
 *       directly. One immediate re-POST of the same bytes (not after a timeout - the turn thread waited long enough),
 *       then {@link #reconcileDue} re-POSTs once more and, {@code MESSAGING_RECONCILE_AFTER} after the first POST, asks
 *       {@code GET /messages?idempotencyKey}: 200 = adopt, 404 = nothing was created, so it is sent directly (if the
 *       link is still valid), anything else = ask again, for one hour, then UNKNOWN. Meanwhile the row reads SENT /
 *       QUEUED - it is on its way - so no second card is stacked under it.</li>
 * </ul>
 * The platform's delivery reports ({@link #applyReport}) and the reconcile job move the row forward only; Meta's own
 * receipts ({@link DeliveryReceipts}) keep matching the wamid as before. A staff user's link always goes directly
 * (spec §17.1: a platform send would add a window check).
 *
 * <p><b>SHADOW.</b> Today's path, then - on a thread of its own, never the send's - the platform's dry run of the same
 * request; window, template, hold and outcome differences are logged ({@code messaging.shadow.mismatch}). Never a
 * second message, never a change to the row.
 */
@Service
@org.springframework.context.annotation.Lazy(false) // production starts lazily: the reconcile job must run after a restart
public class LoginLinkPlatform {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkPlatform.class);

    public static final String KEY_PREFIX = "login-link:";
    static final String POSTING = "POSTING";
    static final String AMBIGUOUS = "AMBIGUOUS";
    static final String OWNED = "OWNED";
    static final String DIRECT = "DIRECT";
    static final String REFUSED = "REFUSED";
    static final String GAVE_UP = "GAVE_UP";
    /** Receipt status of a link the platform has (or may have) and is still sending. */
    static final String QUEUED = "QUEUED";

    /** A POST in flight is the sending thread's for this long; after it the job takes the row as ambiguous. */
    static final Duration LEASE = Duration.ofMinutes(2);
    /** The first POST, one immediate re-POST and one by the job (spec v2.5: re-POST up to 2 times). */
    static final int MAX_POSTS = 3;
    /** Reconcile by GET for this long after the first POST, then UNKNOWN (spec §16: every 5 min for 1 h). */
    static final Duration RECONCILE_FOR = Duration.ofHours(1);
    /** A link the platform keeps QUEUED/HELD is looked up by GET while it is this young (held: 24 h at most). */
    static final Duration OPEN_FOR = Duration.ofHours(26);
    static final Duration HELD_POLL = Duration.ofHours(1);
    static final int BATCH = 20;

    /** The platform's statuses, by rank (forward only); FAILED and UNKNOWN are decided apart. */
    private static final Map<String, Integer> RANK = Map.of("QUEUED", 1, "HELD", 2, "ACCEPTED", 3, "DELIVERED", 5,
            "READ", 6);

    /** What the platform said about one message: the sync answer, a 409's state, a GET, or a delivery report. */
    public record Snapshot(String messageId, String status, String providerMessageId, String reason, String errorCode,
                           Long heldId) {
        static Optional<Snapshot> of(JsonNode body) {
            if (body == null || !body.hasNonNull("messageId") || !body.hasNonNull("status")) {
                return Optional.empty();
            }
            String status = body.get("status").asText().toUpperCase(Locale.ROOT);
            if (!RANK.containsKey(status) && !"FAILED".equals(status) && !"UNKNOWN".equals(status)) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(body.get("messageId").asText(), status, text(body, "providerMessageId"),
                    text(body, "reason"), text(body, "errorCode"),
                    body.hasNonNull("heldId") && body.get("heldId").canConvertToLong() ? body.get("heldId").asLong() : null));
        }
    }

    /** What applying a report did: whether a link holds the key, and whether the row moved. */
    public record Applied(boolean known, boolean moved) {}

    private final MessagingSettings settings;
    private final PlatformMessagingClient client;
    private final LoginLinkRepository links;
    private final LoginLinkDelivery delivery;
    private final WorkflowPlatformProperties platform;
    private final FatherRepository fathers;
    private final CommunicationEndpointRepository endpoints;
    private final ObjectProvider<TimelineReports> timeline;
    private final DeliveryReceipts receipts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean receiptsEnabled;
    private final ThreadPoolExecutor shadowLane = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100), r -> {
                Thread t = new Thread(r, "messaging-shadow");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());

    public LoginLinkPlatform(MessagingSettings settings, PlatformMessagingClient client, LoginLinkRepository links,
                             LoginLinkDelivery delivery, WorkflowPlatformProperties platform, FatherRepository fathers,
                             CommunicationEndpointRepository endpoints, ObjectProvider<TimelineReports> timeline,
                             DeliveryReceipts receipts, JdbcTemplate jdbc, PlatformTransactionManager transactions,
                             ObjectMapper json, Clock clock,
                             @Value("${dad-coach.whatsapp.receipts.enabled:true}") boolean receiptsEnabled) {
        this.settings = settings;
        this.client = client;
        this.links = links;
        this.delivery = delivery;
        this.platform = platform;
        this.fathers = fathers;
        this.endpoints = endpoints;
        this.timeline = timeline;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
        this.clock = clock;
        this.receiptsEnabled = receiptsEnabled;
    }

    /** PLATFORM mode, and a father's link (a staff user's always goes directly, spec §17.1). */
    public boolean sends(SignInSubject subject) {
        return settings.loginLink() == Mode.PLATFORM && subject.fatherId() != null;
    }

    public static String key(UUID linkId) {
        return KEY_PREFIX + linkId;
    }

    // ------------------------------------------------------------------------------------------------- PLATFORM

    /**
     * Issues {@code link} and has the platform send it. Returns what the caller reports to the coach: success while the
     * platform has it (or may have it and is being asked), a failure only when it is known not to go out.
     */
    public DeliveryResult issueAndSend(LoginLink link, SignInSubject subject, String phone, String url,
                                       String turnCorrelationId) {
        Optional<Father> father = fathers.findById(subject.fatherId());
        String body = request(link.getId(), father, phone, url, turnCorrelationId, false);
        Instant now = clock.instant();
        // the bytes are durable before the first POST (v2.6): every re-POST and reconcile sends exactly these
        tx.executeWithoutResult(s -> {
            links.saveAndFlush(link);
            jdbc.update("UPDATE login_link SET platform_request = ?, platform_state = 'POSTING', platform_posts = 1, "
                    + "platform_first_post_at = ?, platform_next_at = ? WHERE id = ?", body, Timestamp.from(now),
                    Timestamp.from(now.plus(LEASE)), link.getId());
        });
        Answer answer = client.send(body);
        Verdict verdict = verdict(answer, false);
        if (verdict == Verdict.AMBIGUOUS && !"TIMEOUT".equals(answer.error()) && answer.status() != 429) {
            log.atInfo().setMessage("messaging.login_link.repost").addKeyValue("linkId", link.getId())
                    .addKeyValue("first", answer.summary()).log();
            tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET platform_posts = platform_posts + 1 WHERE id = ?",
                    link.getId()));
            answer = client.send(body);
            verdict = verdict(answer, true);
        }
        return settle(link.getId(), answer, verdict, subject, phone, url, turnCorrelationId);
    }

    /** How one answer is acted on (spec §16 table with v2.5 / v2.6). */
    enum Verdict { OWNED, ADOPT, DIRECT, REFUSED, AMBIGUOUS }

    /** @param earlierMayExist an earlier attempt with these bytes may have created the message */
    static Verdict verdict(Answer a, boolean earlierMayExist) {
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

    private DeliveryResult settle(UUID linkId, Answer answer, Verdict verdict, SignInSubject subject, String phone,
                                  String url, String turnCorrelationId) {
        switch (verdict) {
            case OWNED, ADOPT -> {
                Snapshot snap = Snapshot.of(answer.body()).orElseThrow();
                if (verdict == Verdict.ADOPT) {
                    log.atWarn().setMessage("messaging.platform_conflict_adopted").addKeyValue("linkId", linkId)
                            .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status()).log();
                }
                apply(linkId, snap, verdict == Verdict.ADOPT ? "adopted" : "answer");
                log.atInfo().setMessage("messaging.login_link.platform").addKeyValue("linkId", linkId)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                        .addKeyValue("reason", snap.reason()).addKeyValue("replayed", answer.text("replayed")).log();
                return result(snap);
            }
            case DIRECT -> {
                if (answer.status() == 401 || answer.status() == 403) {
                    log.atError().setMessage("messaging.platform_auth_failed").addKeyValue("linkId", linkId)
                            .addKeyValue("status", answer.status()).addKeyValue("error", answer.errorCode()).log();
                } else {
                    log.atInfo().setMessage("messaging.login_link.direct_fallback").addKeyValue("linkId", linkId)
                            .addKeyValue("why", answer.summary()).addKeyValue("reason", answer.text("reason")).log();
                }
                return sendDirectly(linkId, subject, phone, url, turnCorrelationId)
                        .orElseGet(() -> DeliveryResult.sent(null));
            }
            case REFUSED -> {
                String reason = refusal(answer);
                log.atError().setMessage("messaging.login_link.refused").addKeyValue("linkId", linkId)
                        .addKeyValue("status", answer.status()).addKeyValue("error", answer.errorCode())
                        .addKeyValue("reason", reason).addKeyValue("field", answer.text("field")).log();
                tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET delivery_status = 'FAILED', "
                        + "delivery_error = ?, platform_state = 'REFUSED', platform_request = NULL, platform_next_at = NULL "
                        + "WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", reason, linkId));
                return DeliveryResult.failed(reason);
            }
            default -> {
                ambiguous(linkId, answer);
                return DeliveryResult.sent(null);
            }
        }
    }

    /** The row reads "on its way" (SENT / QUEUED) and the job takes it from here. */
    private void ambiguous(UUID linkId, Answer answer) {
        Instant now = clock.instant();
        Instant next = now.plus(answer.retryAfterSeconds() != null ? Duration.ofSeconds(answer.retryAfterSeconds())
                : Duration.ofMinutes(1));
        log.atWarn().setMessage("messaging.login_link.ambiguous").addKeyValue("linkId", linkId)
                .addKeyValue("answer", answer.summary()).log();
        tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET platform_state = 'AMBIGUOUS', platform_next_at = ?, "
                + "delivery_status = CASE WHEN delivery_status = 'ISSUED' THEN 'SENT' ELSE delivery_status END, "
                + "receipt_status = CASE WHEN platform_status IS NULL AND receipt_status IS NULL THEN 'QUEUED' "
                + "ELSE receipt_status END WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')",
                Timestamp.from(next), linkId));
    }

    private static String refusal(Answer a) {
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

    /** The caller's view: held = {@code held:<n>} (as the gate's), FAILED = its reason, else sent (maybe no wamid yet). */
    private static DeliveryResult result(Snapshot s) {
        return switch (s.status()) {
            case "FAILED" -> DeliveryResult.failed("PLATFORM_" + (s.reason() == null ? "FAILED" : s.reason()));
            case "HELD" -> DeliveryResult.sent(s.heldId() == null ? null : SharedNumberGate.HELD_PREFIX + s.heldId());
            default -> DeliveryResult.sent(s.providerMessageId());
        };
    }

    /**
     * The platform provably created nothing: Dad Coach sends it itself (same row, same link), today's way, and reports
     * it to the timeline as today. Empty when another path took the row meanwhile (a report adopted it).
     */
    private Optional<DeliveryResult> sendDirectly(UUID linkId, SignInSubject subject, String phone, String url,
                                                  String turnCorrelationId) {
        Integer taken = tx.execute(s -> jdbc.update("UPDATE login_link SET platform_state = 'DIRECT', platform_request = NULL, "
                + "platform_next_at = NULL WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", linkId));
        if (taken == null || taken == 0) {
            return Optional.empty();
        }
        LoginLinkDelivery.Sent sent = delivery.deliver(subject, phone, url);
        DeliveryResult r = sent.result();
        String error = r.failureReason();
        tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET delivery_status = ?, delivery_error = ?, "
                        + "gateway_hold_id = ?, receipt_status = ?, provider_message_id = ? WHERE id = ?",
                r.isSuccessful() ? LoginLink.SENT : LoginLink.FAILED,
                error == null ? null : cut(error, 200), r.isHeld() ? r.providerMessageId() : null,
                r.isHeld() ? LoginLink.HELD : null, r.metaMessageId(), linkId));
        if (r.metaMessageId() != null && receiptsEnabled) {
            receipts.applyPending(r.metaMessageId());
        }
        LoginLinkService.reportCard(timeline.getIfAvailable(), fathers, subject, linkId, sent, turnCorrelationId);
        log.atInfo().setMessage("messaging.login_link.sent_directly").addKeyValue("linkId", linkId)
                .addKeyValue("delivered", r.isSuccessful()).addKeyValue("held", r.isHeld()).log();
        return Optional.of(r);
    }

    // ------------------------------------------------------------------------------------------------- apply

    /**
     * One platform statement about a link, forward only, under the row's lock: the sync answer, an adopted 409, a GET
     * or a delivery report. Returns whether the row moved.
     */
    boolean apply(UUID linkId, Snapshot snap, String source) {
        Instant now = clock.instant();
        Boolean moved = tx.execute(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT delivery_status, receipt_status, platform_state, "
                    + "platform_status, provider_message_id, platform_message_id FROM login_link WHERE id = ? FOR UPDATE", linkId);
            if (rows.isEmpty()) {
                return false;
            }
            Map<String, Object> row = rows.get(0);
            String state = (String) row.get("platform_state");
            if (DIRECT.equals(state)) {
                // nothing was created, by the platform's own word - yet it speaks of this key: a second message is possible
                log.atError().setMessage("messaging.login_link.platform_after_direct").addKeyValue("linkId", linkId)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                        .addKeyValue("source", source).log();
                return false;
            }
            String known = (String) row.get("platform_message_id");
            if (known != null && !known.equals(snap.messageId())) {
                log.atError().setMessage("messaging.login_link.other_message").addKeyValue("linkId", linkId)
                        .addKeyValue("messageId", snap.messageId()).addKeyValue("known", known).log();
                return false;
            }
            if (!POSTING.equals(state) && !AMBIGUOUS.equals(state) && !GAVE_UP.equals(state) && !OWNED.equals(state)) {
                // a row the platform was never asked to send (direct mode) or refused: not this message's
                log.atWarn().setMessage("messaging.login_link.not_platform").addKeyValue("linkId", linkId)
                        .addKeyValue("state", state).addKeyValue("status", snap.status()).addKeyValue("source", source).log();
                return false;
            }
            boolean move = forward(row, snap.status());
            // not moved: an adopted ambiguous link (no platform status yet) still takes the platform's word as its
            // status, so a QUEUED / HELD one keeps its follow-up lookup (review L1); a stale one never replaces a newer
            String status = move ? snap.status()
                    : row.get("platform_status") != null ? (String) row.get("platform_status") : snap.status();
            if (!move && row.get("platform_status") == null) {
                jdbc.update("UPDATE login_link SET platform_status = ? WHERE id = ?", status, linkId);
            }
            Instant next = "QUEUED".equals(status) ? now.plus(settings.reconcileAfter())
                    : "HELD".equals(status) ? now.plus(HELD_POLL) : null;
            // the platform has it: the bytes are no longer needed (they carry the link)
            jdbc.update("UPDATE login_link SET platform_message_id = ?, platform_state = 'OWNED', platform_request = NULL, "
                    + "platform_next_at = ? WHERE id = ?", snap.messageId(), next == null ? null : Timestamp.from(next), linkId);
            if (!move) {
                return false;
            }
            Timestamp at = Timestamp.from(now);
            String wamid = snap.providerMessageId();
            switch (snap.status()) {
                case "QUEUED" -> jdbc.update("UPDATE login_link SET platform_status = 'QUEUED', delivery_status = 'SENT', "
                        + "receipt_status = 'QUEUED' WHERE id = ?", linkId);
                case "HELD" -> jdbc.update("UPDATE login_link SET platform_status = 'HELD', delivery_status = 'SENT', "
                        + "receipt_status = 'HELD', gateway_hold_id = ? WHERE id = ?",
                        snap.heldId() == null ? null : SharedNumberGate.HELD_PREFIX + snap.heldId(), linkId);
                case "ACCEPTED" -> jdbc.update("UPDATE login_link SET platform_status = 'ACCEPTED', delivery_status = 'SENT', "
                        + "receipt_status = NULL, delivery_error = NULL, provider_message_id = COALESCE(?, provider_message_id) "
                        + "WHERE id = ?", wamid, linkId);
                case "DELIVERED", "READ" -> jdbc.update("UPDATE login_link SET platform_status = ?, delivery_status = 'SENT', "
                        + "receipt_status = ?, receipt_at = ?, delivery_error = NULL, "
                        + "provider_message_id = COALESCE(?, provider_message_id) WHERE id = ?",
                        snap.status(), snap.status(), at, wamid, linkId);
                case "FAILED" -> jdbc.update("UPDATE login_link SET platform_status = 'FAILED', delivery_status = 'FAILED', "
                        + "delivery_error = ?, receipt_status = 'FAILED', receipt_at = ?, "
                        + "provider_message_id = COALESCE(?, provider_message_id) WHERE id = ?",
                        cut("PLATFORM_" + (snap.reason() == null ? "FAILED" : snap.reason()), 200), at, wamid, linkId);
                case "UNKNOWN" -> jdbc.update("UPDATE login_link SET platform_status = 'UNKNOWN', delivery_status = 'SENT', "
                        + "receipt_status = 'UNKNOWN', receipt_at = ?, delivery_error = ? WHERE id = ?", at,
                        cut("PLATFORM_" + (snap.reason() == null ? "UNKNOWN" : snap.reason()), 200), linkId);
                default -> throw new IllegalStateException("unhandled status " + snap.status());
            }
            return true;
        });
        if (Boolean.TRUE.equals(moved)) {
            log.atInfo().setMessage("messaging.login_link.status").addKeyValue("linkId", linkId)
                    .addKeyValue("messageId", snap.messageId()).addKeyValue("status", snap.status())
                    .addKeyValue("reason", snap.reason()).addKeyValue("source", source).log();
            if (snap.providerMessageId() != null && receiptsEnabled) {
                receipts.applyPending(snap.providerMessageId());   // Meta's receipts that beat the platform's word
            }
        }
        return Boolean.TRUE.equals(moved);
    }

    /**
     * Forward only, read off the row as it stands (Meta's receipts may have moved it past the platform):
     * ISSUED 0 &lt; QUEUED 1 &lt; HELD / UNKNOWN 2 &lt; accepted 3 &lt; SENT 4 &lt; DELIVERED 5 &lt; READ 6; FAILED is final
     * and comes only before DELIVERED; UNKNOWN only from ISSUED / QUEUED / HELD; after UNKNOWN only accepted or later.
     */
    static boolean forward(Map<String, Object> row, String to) {
        String delivery = (String) row.get("delivery_status");
        String receipt = (String) row.get("receipt_status");
        if (LoginLink.FAILED.equals(delivery)) {
            return false;
        }
        int at = receipt == null ? (LoginLink.SENT.equals(delivery) && (row.get("platform_status") != null
                || row.get("provider_message_id") != null) ? 3 : 0)
                : switch (receipt) {
                    case "QUEUED" -> 1;
                    case "HELD", "UNKNOWN" -> 2;
                    case "SENT" -> 4;
                    case "DELIVERED" -> 5;
                    case "READ" -> 6;
                    default -> 0;
                };
        return switch (to) {
            case "FAILED" -> at < 5;
            case "UNKNOWN" -> at < 2 || "HELD".equals(receipt);
            default -> RANK.getOrDefault(to, 0) > at || "UNKNOWN".equals(receipt) && RANK.getOrDefault(to, 0) >= 3;
        };
    }

    /**
     * A delivery report from the platform (spec §3.5), matched by the idempotency key, else by the platform's message
     * id. Only {@code LOGIN_LINK} rows are Dad Coach's so far; anything else is unknown (never retried).
     */
    public Applied applyReport(String idempotencyKey, String messageId, JsonNode body) {
        Optional<Snapshot> snap = Snapshot.of(body);
        if (snap.isEmpty()) {
            return new Applied(false, false);
        }
        UUID linkId = null;
        if (idempotencyKey != null && idempotencyKey.startsWith(KEY_PREFIX)) {
            try {
                linkId = UUID.fromString(idempotencyKey.substring(KEY_PREFIX.length()));
            } catch (IllegalArgumentException e) {
                linkId = null;
            }
        }
        if (linkId == null && messageId != null) {
            linkId = jdbc.queryForList("SELECT id FROM login_link WHERE platform_message_id = ?", UUID.class, messageId)
                    .stream().findFirst().orElse(null);
        }
        if (linkId == null || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM login_link WHERE id = ?)",
                Boolean.class, linkId))) {
            return new Applied(false, false);
        }
        return new Applied(true, apply(linkId, snap.get(), "report"));
    }

    // ------------------------------------------------------------------------------------------------- reconcile

    /**
     * Rows the platform may have but never answered for (re-POST the same bytes, then GET by key, then directly or
     * UNKNOWN), and rows it keeps QUEUED / HELD without a report (GET). Runs on the messaging lane: it may send.
     */
    @Scheduled(fixedDelayString = "${dad-coach.messaging.reconcile-interval:PT1M}",
            initialDelayString = "${dad-coach.messaging.reconcile-initial-delay:PT1M}",
            scheduler = com.dadcoach.config.SchedulingLanes.MESSAGING)
    public void scheduledReconcile() {
        try {
            reconcileDue();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.login_link.reconcile_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    /** One pass; returns how many rows it looked at. */
    public int reconcileDue() {
        List<UUID> due = jdbc.queryForList("SELECT id FROM login_link WHERE platform_next_at IS NOT NULL AND platform_next_at <= ? "
                + "ORDER BY platform_next_at LIMIT " + BATCH, UUID.class, Timestamp.from(clock.instant()));
        int seen = 0;
        for (UUID id : due) {
            Instant now = clock.instant();      // per row: a slow row before it must not shorten this one's lease
            // claim: another instance (or the next pass) leaves it alone for the lease
            Integer claimed = tx.execute(s -> jdbc.update("UPDATE login_link SET platform_next_at = ? WHERE id = ? "
                    + "AND platform_next_at <= ?", Timestamp.from(now.plus(LEASE)), id, Timestamp.from(now)));
            if (claimed == null || claimed == 0) {
                continue;
            }
            seen++;
            try {
                reconcile(id, now);
            } catch (RuntimeException e) {
                log.atWarn().setMessage("messaging.login_link.reconcile_failed").addKeyValue("linkId", id)
                        .addKeyValue("error", e.getClass().getSimpleName()).log();
            }
        }
        return seen;
    }

    private void reconcile(UUID id, Instant now) {
        Map<String, Object> row = jdbc.queryForMap("SELECT father_id, platform_state, platform_status, platform_request, "
                + "platform_posts, platform_first_post_at, created_at FROM login_link WHERE id = ?", id);
        String state = (String) row.get("platform_state");
        if (OWNED.equals(state)) {
            Instant created = ((Timestamp) row.get("created_at")).toInstant();
            if (created.plus(OPEN_FOR).isBefore(now)) {
                clearNext(id);
                return;
            }
            Answer a = client.byKey(key(id));
            Optional<Snapshot> snap = a.status() == 200 ? Snapshot.of(a.body()) : Optional.empty();
            if (snap.isPresent()) {
                next(id, now.plus(settings.reconcileAfter()));
                apply(id, snap.get(), "reconcile");      // sets the next look (still QUEUED / HELD) or none
                return;
            }
            log.atWarn().setMessage("messaging.login_link.reconcile_unanswered").addKeyValue("linkId", id)
                    .addKeyValue("answer", a.summary()).log();
            next(id, now.plus(settings.reconcileAfter()));
            return;
        }
        if (!POSTING.equals(state) && !AMBIGUOUS.equals(state)) {
            clearNext(id);
            return;
        }
        String body = (String) row.get("platform_request");
        Long fatherId = row.get("father_id") == null ? null : ((Number) row.get("father_id")).longValue();
        if (body == null || fatherId == null) {
            giveUp(id, "NO_REQUEST");
            return;
        }
        int posts = ((Number) row.get("platform_posts")).intValue();
        Instant firstPost = row.get("platform_first_post_at") == null ? now
                : ((Timestamp) row.get("platform_first_post_at")).toInstant();
        Optional<Father> father = fathers.findById(fatherId);
        String url = urlOf(body);
        if (father.isEmpty() || url == null) {
            giveUp(id, "NO_FATHER");
            return;
        }
        SignInSubject subject = new SignInSubject(fatherId, null);
        if (posts < MAX_POSTS) {
            jdbc.update("UPDATE login_link SET platform_posts = platform_posts + 1 WHERE id = ?", id);
            Answer a = client.send(body);
            Verdict v = verdict(a, true);
            if (v == Verdict.AMBIGUOUS) {
                ambiguous(id, a);
                Instant reconcileAt = firstPost.plus(settings.reconcileAfter());
                next(id, posts + 1 < MAX_POSTS ? now.plus(Duration.ofMinutes(1))
                        : reconcileAt.isAfter(now) ? reconcileAt : now.plus(Duration.ofMinutes(1)));
                return;
            }
            settle(id, a, v, subject, father.get().getPhone(), url, null);
            return;
        }
        if (firstPost.plus(settings.reconcileAfter()).isAfter(now)) {
            next(id, firstPost.plus(settings.reconcileAfter()));
            return;
        }
        Answer a = client.byKey(key(id));
        Optional<Snapshot> snap = a.status() == 200 ? Snapshot.of(a.body()) : Optional.empty();
        if (snap.isPresent()) {
            log.atWarn().setMessage("messaging.login_link.reconciled").addKeyValue("linkId", id)
                    .addKeyValue("messageId", snap.get().messageId()).addKeyValue("status", snap.get().status()).log();
            apply(id, snap.get(), "reconcile");
            return;
        }
        if (a.status() == 404 && PlatformMessagingClient.MESSAGE_NOT_FOUND.equals(a.errorCode())) {
            // the platform commits a message before any answer; minutes later, "not found" means it never existed (v2.5)
            if (!stillValid(id)) {
                tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET platform_state = 'GAVE_UP', platform_request = NULL, "
                        + "platform_next_at = NULL, delivery_status = 'FAILED', receipt_status = NULL, "
                        + "delivery_error = 'PLATFORM_NOT_CREATED_LINK_ENDED' WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", id));
                return;
            }
            log.atWarn().setMessage("messaging.login_link.direct_fallback").addKeyValue("linkId", id)
                    .addKeyValue("why", "RECONCILE_NOT_FOUND").log();
            sendDirectly(id, subject, father.get().getPhone(), url, null);
            return;
        }
        if (firstPost.plus(RECONCILE_FOR).isBefore(now)) {
            giveUp(id, "RECONCILE_GAVE_UP");
            return;
        }
        log.atWarn().setMessage("messaging.login_link.reconcile_unanswered").addKeyValue("linkId", id)
                .addKeyValue("answer", a.summary()).log();
        next(id, now.plus(settings.reconcileAfter()));
    }

    private boolean stillValid(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT revoked_at IS NULL AND expires_at > ? FROM login_link WHERE id = ?",
                Boolean.class, Timestamp.from(clock.instant()), id));
    }

    /** No answer for an hour: it may have gone out - UNKNOWN, never sent again (spec §16, v2.5). */
    private void giveUp(UUID id, String why) {
        log.atWarn().setMessage("messaging.login_link.gave_up").addKeyValue("linkId", id).addKeyValue("why", why).log();
        tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET platform_state = 'GAVE_UP', platform_request = NULL, "
                + "platform_next_at = NULL, receipt_status = 'UNKNOWN', receipt_at = ?, delivery_error = ? "
                + "WHERE id = ? AND platform_state IN ('POSTING','AMBIGUOUS')", Timestamp.from(clock.instant()),
                "PLATFORM_" + why, id));
    }

    private void next(UUID id, Instant at) {
        jdbc.update("UPDATE login_link SET platform_next_at = ? WHERE id = ?", Timestamp.from(at), id);
    }

    private void clearNext(UUID id) {
        jdbc.update("UPDATE login_link SET platform_next_at = NULL WHERE id = ?", id);
    }

    private String urlOf(String body) {
        try {
            JsonNode url = json.readTree(body).path("content").path("link").path("url");
            return url.isTextual() ? url.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------- SHADOW

    /** SHADOW mode: after today's send, the platform's dry run of the same request, off this thread. */
    public void shadow(SignInSubject subject, String phone, UUID linkId, String url, LoginLinkDelivery.Sent sent,
                       String turnCorrelationId) {
        if (settings.loginLink() != Mode.SHADOW || subject.fatherId() == null) {
            return;
        }
        try {
            Optional<Father> father = fathers.findById(subject.fatherId());
            String body = request(linkId, father, phone, url, turnCorrelationId, true);
            shadowLane.execute(() -> compare(linkId, body, sent));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    @jakarta.annotation.PreDestroy
    void stopShadowLane() {
        shadowLane.shutdownNow();
    }

    /** Tests: whether the shadow lane has nothing left to do. */
    public boolean shadowIdle() {
        return shadowLane.getActiveCount() == 0 && shadowLane.getQueue().isEmpty();
    }

    private void compare(UUID linkId, String body, LoginLinkDelivery.Sent sent) {
        try {
            Answer a = client.send(body);
            DeliveryResult r = sent.result();
            String dcWindow = sent.template() != null ? "TEMPLATE" : "SESSION";
            String dcOutcome = !r.isSuccessful() ? "FAIL" : r.isHeld() ? "HOLD" : "SEND";
            if (a.status() == 422) {
                String reason = a.text("reason");
                if (sent.template() != null && r.isSuccessful()) {
                    mismatch(linkId, "template", sent.template(), "UNUSABLE_" + reason);
                } else {
                    log.atInfo().setMessage("messaging.shadow.match").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                            .addKeyValue("linkId", linkId).addKeyValue("platform", "422_" + reason).log();
                }
                return;
            }
            if (a.status() != 200 || a.body() == null) {
                log.atInfo().setMessage("messaging.shadow.unavailable").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                        .addKeyValue("linkId", linkId).addKeyValue("answer", a.summary()).log();
                return;
            }
            String wouldBe = a.text("wouldBe");
            String window = a.text("channelWindow");
            String template = a.body().path("template").hasNonNull("name") ? a.body().path("template").path("name").asText() : null;
            String platformOutcome = "HOLD".equals(wouldBe) ? "HOLD" : "SEND".equals(wouldBe) ? "SEND" : "FAIL";
            int mismatches = 0;
            if ("HOLD".equals(dcOutcome) != "HOLD".equals(platformOutcome)) {
                mismatches += mismatch(linkId, "hold", dcOutcome, platformOutcome + "/" + a.text("holdWhy"));
            } else if ("FAIL".equals(dcOutcome) != "FAIL".equals(platformOutcome)) {
                mismatches += mismatch(linkId, "outcome", dcOutcome + "/" + r.failureReason(),
                        platformOutcome + "/" + a.text("reason"));
            }
            if (window != null && !"FAIL".equals(dcOutcome) && !"FAIL".equals(platformOutcome)) {
                if (!dcWindow.equals(window)) {
                    mismatches += mismatch(linkId, "window", dcWindow, window);
                } else if ("TEMPLATE".equals(window) && template != null && !template.equals(sent.template())) {
                    mismatches += mismatch(linkId, "template", sent.template(), template);
                }
            }
            if (mismatches == 0) {
                log.atInfo().setMessage("messaging.shadow.match").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                        .addKeyValue("linkId", linkId).addKeyValue("outcome", dcOutcome).addKeyValue("window", dcWindow).log();
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    private int mismatch(UUID linkId, String field, String product, String platformValue) {
        log.atWarn().setMessage("messaging.shadow.mismatch").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                .addKeyValue("linkId", linkId).addKeyValue("field", field).addKeyValue("product", product)
                .addKeyValue("platform", platformValue).log();
        return 1;
    }

    // ------------------------------------------------------------------------------------------------- the request

    /**
     * The {@code /send} body for a father's link (spec §3.1, §17.1 DC1): the same button, text, footer and template as
     * the direct path ({@link LoginLinkDelivery}); the text with the link on its own line when Meta refuses the button;
     * the timeline row {@code dashboard-link:<id>} with the card's text, never the link.
     */
    String request(UUID linkId, Optional<Father> father, String phone, String url, String turnCorrelationId, boolean dryRun) {
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
        r.put("idempotencyKey", key(linkId));
        r.put("kind", MessagingSettings.LOGIN_LINK);
        r.put("messageClass", "UTILITY");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "link_button");
        content.put("text", LoginLinkDelivery.FATHER_TEXT);
        content.put("footer", LoginLinkDelivery.FOOTER);
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("url", url);
        link.put("title", LoginLinkDelivery.FATHER_LABEL);
        content.put("link", link);
        r.put("content", content);
        r.put("fallbackText", new LinkButton(LoginLinkDelivery.FATHER_LABEL, url, LoginLinkDelivery.FOOTER)
                .asText(LoginLinkDelivery.FATHER_TEXT));
        String templateName = delivery.templateName();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", TemplateRegistry.languageForTemplateName(templateName));
        template.put("bodyParams", List.of(ProactiveSender.asTemplateParameter(LoginLinkDelivery.templateLine(url))));
        r.put("template", template);
        r.put("windowPolicy", "AUTO");
        r.put("outsideWindowWithoutTemplate", "FAIL");
        lastInbound(father).ifPresent(at -> r.put("windowHint", Map.of("lastInboundAt", at.toString())));
        r.put("holdPolicy", "GATEWAY");
        Map<String, Object> timelineRow = new LinkedHashMap<>();
        timelineRow.put("record", true);
        timelineRow.put("correlationId", "dashboard-link:" + linkId);
        timelineRow.put("mode", "NEW_ROW");
        if (turnCorrelationId != null) {
            timelineRow.put("turnCorrelationId", turnCorrelationId);
        }
        timelineRow.put("content", LoginLinkDelivery.reportedText(false));
        r.put("timeline", timelineRow);
        r.put("wait", "ACCEPTED");
        r.put("dryRun", dryRun);
        try {
            return json.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Spec v2.13: Dad Coach keeps no inbound time - its window closes 24 h after the last one. */
    private Optional<Instant> lastInbound(Optional<Father> father) {
        return father.flatMap(f -> endpoints.findPrimaryByFatherId(new UUID(0L, f.getId())))
                .map(e -> e.getSessionClosesAt()).map(closes -> closes.minus(Duration.ofHours(24)));
    }

    private static String text(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
