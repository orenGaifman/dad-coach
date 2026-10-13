package com.dadcoach.auth;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.dto.OutboundMessageDto.LinkButton;
import com.dadcoach.channel.template.TemplateText;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.platform.client.PlatformMessagingClient.Answer;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Settled;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Snapshot;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Update;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-045 (Unified Workflow Phase 6.3-DC, step DC1; D-053 6.4): a dashboard link sent by the platform's Send API - the
 * only sender since D-053 - for a father and (D-053) a staff user alike. Platform spec {@code PHASE6_2_SEND_API_SPEC.md}
 * §3, §7.4, §8, §16, §17.1 with "Spec v2" v2.1-v2.6; {@code PHASE6_4_CLEANUP_SPEC.md} §4.1.
 *
 * <p>The mechanics (bytes first, the answer table, the re-POST, reconcile, reports) live in {@link PlatformSendFlow};
 * this class is the {@code login_link} ledger - how a platform status reads on a link - and the request.</p>
 *
 * <p>The link row and the exact request bytes are committed first (own transaction), then {@code POST /send} with
 * idempotency key {@code login-link:<linkId>}. The answer:
 * <ul>
 *   <li>2xx with a status - the platform owns it: mapped onto the row (§7.4: QUEUED = SENT/QUEUED, HELD = SENT/HELD +
 *       {@code held:<n>}, ACCEPTED = SENT + wamid, FAILED = FAILED {@code PLATFORM_<reason>}, UNKNOWN = SENT/UNKNOWN),
 *       forward only; the bytes are cleared. The platform records a father's timeline row (Dad Coach reports nothing).</li>
 *   <li>Provably nothing created (503 {@code messaging.disabled}, 401/403, never written) - NOT_CREATED: the row reads
 *       SENT / QUEUED (on its way, no second card stacked under it) and the reconcile job re-POSTs the same bytes while the
 *       link is unrevoked, unexpired and at most {@link #NOT_CREATED_FOR} old, then FAILED {@code PLATFORM_NOT_CREATED}.</li>
 *   <li>400/422 - FAILED {@code PLATFORM_<error>} and an ERROR log. 409 conflict on the first attempt: FAILED
 *       {@code PLATFORM_KEY_CONFLICT}; after an ambiguous attempt: adopted (v2.6).</li>
 *   <li>Anything ambiguous: one immediate re-POST (not after a timeout), then the reconcile job (re-POST, then GET by
 *       key: 200 adopt, 404 re-POST while still valid, else UNKNOWN after 1 h). Meanwhile the row reads SENT / QUEUED.</li>
 * </ul>
 * The platform's delivery reports ({@link #applyReport}) and the reconcile job move the row forward only.
 *
 * <p>D-053: a staff user's link (before: Meta directly, no window check) goes the same way - the platform decides
 * free-form inside his window or the general template outside it ({@code dad_coach_update_he}, which a free-form message
 * Meta would refuse could not); it is not recorded on any timeline (a staff user has no conversation).</p>
 */
@Service
@org.springframework.context.annotation.Lazy(false) // production starts lazily: the reconcile job must run after a restart
public class LoginLinkPlatform implements PlatformSendFlow.Ledger {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkPlatform.class);

    public static final String KEY_PREFIX = "login-link:";
    /** Receipt status of a link the platform has (or may have, or will be asked again) and is still sending. */
    static final String QUEUED = "QUEUED";
    /** A link nothing was created for is POSTed again for at most this long (spec 6.4 §4.1: the default 1 h). */
    static final Duration NOT_CREATED_FOR = Duration.ofHours(1);

    /** D-045's own names for the answer table (its tests use them); the same values as {@link PlatformSendFlow.Verdict}. */
    enum Verdict { OWNED, ADOPT, NOT_CREATED, REFUSED, AMBIGUOUS }

    private final PlatformSendFlow flow;
    private final LoginLinkRepository links;
    private final FatherRepository fathers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public LoginLinkPlatform(PlatformSendFlow flow, LoginLinkRepository links, FatherRepository fathers, JdbcTemplate jdbc,
                             PlatformTransactionManager transactions) {
        this.flow = flow;
        this.links = links;
        this.fathers = fathers;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Tests only: the next requests fail to build (a Dad Coach bug, D-053: FAILED, never sent another way). */
    private volatile RuntimeException requestFault;

    void setRequestFaultForTests(RuntimeException fault) {
        this.requestFault = fault;
    }

    public static String key(UUID linkId) {
        return KEY_PREFIX + linkId;
    }

    static Verdict verdict(Answer a, boolean earlierMayExist) {
        return Verdict.valueOf(PlatformSendFlow.verdict(a, earlierMayExist).name());
    }

    // ------------------------------------------------------------------------------------------------- send

    /**
     * Issues {@code link} and has the platform send it. Returns what the caller reports to the coach: success while the
     * platform has it (or may have it, or will be asked again), a failure only when it is known not to go out.
     */
    public DeliveryResult issueAndSend(LoginLink link, SignInSubject subject, String phone, String url,
                                       String turnCorrelationId) {
        Optional<Father> father = subject.fatherId() == null ? Optional.empty() : fathers.findById(subject.fatherId());
        String body;
        try {
            RuntimeException fault = requestFault;
            if (fault != null) {
                throw fault;
            }
            body = request(link.getId(), subject, father, phone, url, turnCorrelationId);
        } catch (RuntimeException e) {
            // a Dad Coach bug (D-053): nothing was posted - the link is kept FAILED, never sent another way
            log.atError().setMessage("messaging.login_link.request_failed").addKeyValue("error", e.getClass().getSimpleName())
                    .setCause(e).log();
            link.recordDelivery(DeliveryResult.failed(PlatformSendFlow.REQUEST_BUILD_FAILED));
            links.saveAndFlush(link);
            return DeliveryResult.failed(PlatformSendFlow.REQUEST_BUILD_FAILED);
        }
        Settled s = flow.send(this, link.getId(), body, () -> links.saveAndFlush(link));
        return switch (s.verdict()) {
            case OWNED, ADOPT -> result(s.snapshot());
            case REFUSED -> DeliveryResult.failed(s.reason());
            case AMBIGUOUS, NOT_CREATED -> DeliveryResult.sent(null);   // on its way: asked again by the job
            case RATE_LIMITED -> DeliveryResult.failed(s.reason());     // only from sendOnce (D-048): never here
        };
    }

    /** The caller's view: held = {@code held:<n>}, FAILED = its reason, else sent (maybe no wamid yet). */
    private static DeliveryResult result(Snapshot s) {
        return switch (s.status()) {
            case "FAILED" -> DeliveryResult.failed("PLATFORM_" + (s.reason() == null ? "FAILED" : s.reason()));
            case "HELD" -> DeliveryResult.sent(s.heldId() == null ? null : DeliveryResult.HELD_PREFIX + s.heldId());
            default -> DeliveryResult.sent(s.providerMessageId());
        };
    }

    /** One platform statement about a link (forward only, under the row's lock). Returns whether the row moved. */
    boolean apply(UUID linkId, Snapshot snap, String source) {
        return flow.apply(this, linkId, snap, source);
    }

    /**
     * A delivery report from the platform (spec §3.5), matched by the idempotency key, else by the platform's message
     * id; a key no link holds is unknown (never retried).
     */
    public PlatformSendFlow.Applied applyReport(String idempotencyKey, String messageId, JsonNode body) {
        return flow.applyReport(this, idempotencyKey, messageId, body);
    }

    // ------------------------------------------------------------------------------------------------- the ledger

    @Override
    public String kind() {
        return MessagingSettings.LOGIN_LINK;
    }

    @Override
    public String table() {
        return "login_link";
    }

    @Override
    public Logger log() {
        return log;
    }

    @Override
    public String event() {
        return "login_link";
    }

    @Override
    public String idKey() {
        return "linkId";
    }

    @Override
    public String idempotencyKey(UUID id) {
        return key(id);
    }

    @Override
    public Optional<UUID> rowOf(String idempotencyKey, String messageId) {
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
            return Optional.empty();
        }
        return Optional.of(linkId);
    }

    @Override
    public String statusColumns() {
        return "delivery_status, receipt_status, provider_message_id";
    }

    /**
     * Forward only, read off the row as it stands (before D-053 Meta's receipts could move it past the platform):
     * ISSUED 0 &lt; QUEUED 1 &lt; HELD / UNKNOWN 2 &lt; accepted 3 &lt; SENT 4 &lt; DELIVERED 5 &lt; READ 6; FAILED is final
     * and comes only before DELIVERED; UNKNOWN only from ISSUED / QUEUED / HELD; after UNKNOWN only accepted or later.
     */
    @Override
    public boolean forward(Map<String, Object> row, String to) {
        return forwardOf(row, to);
    }

    static boolean forwardOf(Map<String, Object> row, String to) {
        String delivery = (String) row.get("delivery_status");
        String receipt = (String) row.get("receipt_status");
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
        return PlatformSendFlow.forward(LoginLink.FAILED.equals(delivery), at, "HELD".equals(receipt),
                "UNKNOWN".equals(receipt), to);
    }

    @Override
    public void applyStatus(UUID linkId, Snapshot snap, Timestamp at) {
        String wamid = snap.providerMessageId();
        switch (snap.status()) {
            case "QUEUED" -> jdbc.update("UPDATE login_link SET delivery_status = 'SENT', receipt_status = 'QUEUED' WHERE id = ?",
                    linkId);
            case "HELD" -> jdbc.update("UPDATE login_link SET delivery_status = 'SENT', receipt_status = 'HELD', "
                    + "gateway_hold_id = ? WHERE id = ?",
                    snap.heldId() == null ? null : DeliveryResult.HELD_PREFIX + snap.heldId(), linkId);
            case "ACCEPTED" -> jdbc.update("UPDATE login_link SET delivery_status = 'SENT', receipt_status = NULL, "
                    + "delivery_error = NULL, provider_message_id = COALESCE(?, provider_message_id) WHERE id = ?", wamid, linkId);
            case "DELIVERED", "READ" -> jdbc.update("UPDATE login_link SET delivery_status = 'SENT', receipt_status = ?, "
                    + "receipt_at = ?, delivery_error = NULL, provider_message_id = COALESCE(?, provider_message_id) "
                    + "WHERE id = ?", snap.status(), at, wamid, linkId);
            case "FAILED" -> jdbc.update("UPDATE login_link SET delivery_status = 'FAILED', delivery_error = ?, "
                    + "receipt_status = 'FAILED', receipt_at = ?, provider_message_id = COALESCE(?, provider_message_id) "
                    + "WHERE id = ?", PlatformSendFlow.cut("PLATFORM_" + (snap.reason() == null ? "FAILED" : snap.reason()), 200),
                    at, wamid, linkId);
            case "UNKNOWN" -> jdbc.update("UPDATE login_link SET delivery_status = 'SENT', receipt_status = 'UNKNOWN', "
                    + "receipt_at = ?, delivery_error = ? WHERE id = ?", at,
                    PlatformSendFlow.cut("PLATFORM_" + (snap.reason() == null ? "UNKNOWN" : snap.reason()), 200), linkId);
            default -> throw new IllegalStateException("unhandled status " + snap.status());
        }
    }

    /** On its way (SENT / QUEUED): no second card is stacked under it while the platform is asked (again). */
    @Override
    public String ambiguousSet() {
        return "delivery_status = CASE WHEN delivery_status = 'ISSUED' THEN 'SENT' ELSE delivery_status END, "
                + "receipt_status = CASE WHEN platform_status IS NULL AND receipt_status IS NULL THEN 'QUEUED' "
                + "ELSE receipt_status END";
    }

    @Override
    public Update refused(String reason, Timestamp at) {
        return new Update("delivery_status = 'FAILED', delivery_error = ?", reason);
    }

    @Override
    public Update gaveUp(String reason, Timestamp at) {
        return new Update("receipt_status = 'UNKNOWN', receipt_at = ?, delivery_error = ?", at, reason);
    }

    @Override
    public Update ended(String reason, Timestamp at) {
        return new Update("delivery_status = 'FAILED', receipt_status = NULL, delivery_error = ?", reason);
    }

    /** A father who is still there, or a staff user (D-053), and the request's link. */
    @Override
    public String blocker(UUID id, String body) {
        Map<String, Object> row = jdbc.queryForMap("SELECT father_id, staff_user_id FROM login_link WHERE id = ?", id);
        if (row.get("father_id") == null && row.get("staff_user_id") == null) {
            return "NO_REQUEST";
        }
        if (row.get("father_id") != null && fathers.findById(((Number) row.get("father_id")).longValue()).isEmpty()) {
            return "NO_FATHER";
        }
        return urlOf(body) == null ? "NO_REQUEST" : null;
    }

    /** The link is still unrevoked and unexpired, and (nothing created yet) at most {@link #NOT_CREATED_FOR} old. */
    @Override
    public boolean stillSendable(UUID id, String body, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT revoked_at IS NULL AND expires_at > ? AND created_at > ? "
                + "FROM login_link WHERE id = ?", Boolean.class, Timestamp.from(now), Timestamp.from(now.minus(NOT_CREATED_FOR)), id));
    }

    private String urlOf(String body) {
        JsonNode node = flow.read(body);
        JsonNode url = node == null ? null : node.path("content").path("link").path("url");
        return url != null && url.isTextual() ? url.asText() : null;
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
        return flow.reconcileDue(this);
    }

    // ------------------------------------------------------------------------------------------------- the request

    /**
     * The {@code /send} body (spec §3.1, §17.1 DC1; D-053): the button, text and footer of the link card; the text with
     * the link on its own line when Meta refuses the button; {@code templateData} = the kind's default message type with
     * {@code named.text} = the one line the general template carries (never a Meta template name); a father's timeline
     * row {@code dashboard-link:<id>} with the card's text, never the link (a staff user's is not recorded).
     */
    String request(UUID linkId, SignInSubject subject, Optional<Father> father, String phone, String url,
                   String turnCorrelationId) {
        boolean isFather = subject.fatherId() != null;
        String text = isFather ? LoginLinkDelivery.FATHER_TEXT : LoginLinkDelivery.STAFF_TEXT;
        String label = isFather ? LoginLinkDelivery.FATHER_LABEL : LoginLinkDelivery.STAFF_LABEL;
        Map<String, Object> r = flow.newRequest(father, phone);
        r.put("idempotencyKey", PlatformSendFlow.platformKey(key(linkId)));
        r.put("kind", MessagingSettings.LOGIN_LINK);
        r.put("messageClass", "UTILITY");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "link_button");
        content.put("text", text);
        content.put("footer", LoginLinkDelivery.FOOTER);
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("url", url);
        link.put("title", label);
        content.put("link", link);
        r.put("content", content);
        r.put("fallbackText", new LinkButton(label, url, LoginLinkDelivery.FOOTER).asText(text));
        String line = isFather ? LoginLinkDelivery.templateLine(url) : LoginLinkDelivery.staffTemplateLine(url);
        r.put("templateData", PlatformSendFlow.templateData("", null,
                Map.of("text", TemplateText.asTemplateParameter(line)), null));
        r.put("windowPolicy", "AUTO");
        r.put("outsideWindowWithoutTemplate", "FAIL");
        flow.lastInbound(father).ifPresent(at -> r.put("windowHint", Map.of("lastInboundAt", at.toString())));
        r.put("holdPolicy", "GATEWAY");
        Map<String, Object> timelineRow = new LinkedHashMap<>();
        timelineRow.put("record", isFather);
        if (isFather) {
            timelineRow.put("correlationId", "dashboard-link:" + linkId);
            timelineRow.put("mode", "NEW_ROW");
            if (turnCorrelationId != null) {
                timelineRow.put("turnCorrelationId", turnCorrelationId);
            }
            timelineRow.put("content", LoginLinkDelivery.reportedText(false));
        }
        r.put("timeline", timelineRow);
        r.put("wait", "ACCEPTED");
        return flow.json(r);
    }
}
