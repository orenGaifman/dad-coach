package com.dadcoach.auth;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.dto.OutboundMessageDto.LinkButton;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.SharedNumberGate;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Answer;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Settled;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Snapshot;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Update;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.whatsapp.DeliveryReceipts;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 * <p>D-046: the mechanics (bytes first, the answer table, the re-POST, reconcile, reports, SHADOW) live in
 * {@link PlatformSendFlow}, shared with the scheduled messages; this class is the {@code login_link} ledger - how a
 * platform status reads on a link, how a link is sent directly - and the request. Behaviour unchanged.</p>
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
public class LoginLinkPlatform implements PlatformSendFlow.Ledger {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkPlatform.class);

    public static final String KEY_PREFIX = "login-link:";
    /** Receipt status of a link the platform has (or may have) and is still sending. */
    static final String QUEUED = "QUEUED";

    /** D-045's own names for the answer table (its tests use them); the same values as {@link PlatformSendFlow.Verdict}. */
    enum Verdict { OWNED, ADOPT, DIRECT, REFUSED, AMBIGUOUS }

    private final MessagingSettings settings;
    private final PlatformSendFlow flow;
    private final LoginLinkRepository links;
    private final LoginLinkDelivery delivery;
    private final FatherRepository fathers;
    private final ObjectProvider<TimelineReports> timeline;
    private final DeliveryReceipts receipts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final boolean receiptsEnabled;

    public LoginLinkPlatform(MessagingSettings settings, PlatformSendFlow flow, LoginLinkRepository links,
                             LoginLinkDelivery delivery, FatherRepository fathers, ObjectProvider<TimelineReports> timeline,
                             DeliveryReceipts receipts, JdbcTemplate jdbc, PlatformTransactionManager transactions,
                             @Value("${dad-coach.whatsapp.receipts.enabled:true}") boolean receiptsEnabled) {
        this.settings = settings;
        this.flow = flow;
        this.links = links;
        this.delivery = delivery;
        this.fathers = fathers;
        this.timeline = timeline;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.receiptsEnabled = receiptsEnabled;
    }

    /** PLATFORM mode, and a father's link (a staff user's always goes directly, spec §17.1). */
    public boolean sends(SignInSubject subject) {
        return settings.loginLink() == Mode.PLATFORM && subject.fatherId() != null;
    }

    public static String key(UUID linkId) {
        return KEY_PREFIX + linkId;
    }

    static Verdict verdict(Answer a, boolean earlierMayExist) {
        return Verdict.valueOf(PlatformSendFlow.verdict(a, earlierMayExist).name());
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
        Settled<DeliveryResult> s = flow.send(this, link.getId(), body, () -> links.saveAndFlush(link),
                () -> sendDirectly(link.getId(), subject, phone, url, turnCorrelationId));
        return switch (s.verdict()) {
            case OWNED, ADOPT -> result(s.snapshot());
            case DIRECT -> s.direct() != null ? s.direct() : DeliveryResult.sent(null);
            case REFUSED -> DeliveryResult.failed(s.reason());
            case AMBIGUOUS -> DeliveryResult.sent(null);
            case RATE_LIMITED -> DeliveryResult.failed(s.reason());   // only from sendOnce (D-048): never here
        };
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
     * The platform provably created nothing and the row is taken (state DIRECT): Dad Coach sends it itself (same row,
     * same link), today's way, and reports it to the timeline as today.
     */
    private DeliveryResult sendDirectly(UUID linkId, SignInSubject subject, String phone, String url,
                                        String turnCorrelationId) {
        LoginLinkDelivery.Sent sent = delivery.deliver(subject, phone, url);
        DeliveryResult r = sent.result();
        String error = r.failureReason();
        tx.executeWithoutResult(s -> jdbc.update("UPDATE login_link SET delivery_status = ?, delivery_error = ?, "
                        + "gateway_hold_id = ?, receipt_status = ?, provider_message_id = ? WHERE id = ?",
                r.isSuccessful() ? LoginLink.SENT : LoginLink.FAILED,
                error == null ? null : PlatformSendFlow.cut(error, 200), r.isHeld() ? r.providerMessageId() : null,
                r.isHeld() ? LoginLink.HELD : null, r.metaMessageId(), linkId));
        if (r.metaMessageId() != null && receiptsEnabled) {
            // committed on its own, as the row above (D-052): this runs inside the caller's transaction (LoginLinkService)
            try {
                tx.executeWithoutResult(s -> receipts.applyPending(r.metaMessageId()));
            } catch (RuntimeException e) {
                // the send and its row are done: never failed for this - the receipts are kept again (next pass)
                log.atWarn().setMessage("messaging.login_link.pending_receipts_failed").addKeyValue("linkId", linkId)
                        .addKeyValue("error", e.getClass().getSimpleName()).log();
            }
        }
        LoginLinkService.reportCard(timeline.getIfAvailable(), fathers, subject, linkId, sent, turnCorrelationId);
        log.atInfo().setMessage("messaging.login_link.sent_directly").addKeyValue("linkId", linkId)
                .addKeyValue("delivered", r.isSuccessful()).addKeyValue("held", r.isHeld()).log();
        return r;
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
     * Forward only, read off the row as it stands (Meta's receipts may have moved it past the platform):
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
                    snap.heldId() == null ? null : SharedNumberGate.HELD_PREFIX + snap.heldId(), linkId);
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

    /** On its way (SENT / QUEUED): no second card is stacked under it while the platform is asked. */
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
    public Update ended(Timestamp at) {
        return new Update("delivery_status = 'FAILED', receipt_status = NULL, delivery_error = 'PLATFORM_NOT_CREATED_LINK_ENDED'");
    }

    @Override
    public String blocker(UUID id, String body) {
        Map<String, Object> row = jdbc.queryForMap("SELECT father_id FROM login_link WHERE id = ?", id);
        if (row.get("father_id") == null) {
            return "NO_REQUEST";
        }
        Optional<Father> father = fathers.findById(((Number) row.get("father_id")).longValue());
        return father.isEmpty() || urlOf(body) == null ? "NO_FATHER" : null;
    }

    /** The link is still unrevoked and unexpired. */
    @Override
    public boolean stillSendable(UUID id, String body, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT revoked_at IS NULL AND expires_at > ? FROM login_link WHERE id = ?",
                Boolean.class, Timestamp.from(now), id));
    }

    @Override
    public void sendLate(UUID id, String body) {
        Long fatherId = jdbc.queryForObject("SELECT father_id FROM login_link WHERE id = ?", Long.class, id);
        Father father = fathers.findById(fatherId).orElseThrow();
        sendDirectly(id, new SignInSubject(fatherId, null), father.getPhone(), urlOf(body), null);
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

    // ------------------------------------------------------------------------------------------------- SHADOW

    /** SHADOW mode: after today's send, the platform's dry run of the same request, off this thread. */
    public void shadow(SignInSubject subject, String phone, UUID linkId, String url, LoginLinkDelivery.Sent sent,
                       String turnCorrelationId) {
        if (settings.loginLink() != Mode.SHADOW || subject.fatherId() == null) {
            return;
        }
        try {
            DeliveryResult r = sent.result();
            // the request is built on the shadow lane, never on the sending thread (review N3)
            flow.shadow(MessagingSettings.LOGIN_LINK, log, "linkId", linkId,
                    () -> request(linkId, fathers.findById(subject.fatherId()), phone, url, turnCorrelationId, true),
                    new PlatformSendFlow.ShadowFacts(r.isSuccessful(), r.isHeld(), r.failureReason(), sent.template()));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", MessagingSettings.LOGIN_LINK)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    /** Tests: whether the shadow lane has nothing left to do. */
    public boolean shadowIdle() {
        return flow.shadowIdle();
    }

    // ------------------------------------------------------------------------------------------------- the request

    /**
     * The {@code /send} body for a father's link (spec §3.1, §17.1 DC1): the same button, text, footer and template as
     * the direct path ({@link LoginLinkDelivery}); the text with the link on its own line when Meta refuses the button;
     * the timeline row {@code dashboard-link:<id>} with the card's text, never the link.
     */
    String request(UUID linkId, Optional<Father> father, String phone, String url, String turnCorrelationId, boolean dryRun) {
        Map<String, Object> r = flow.newRequest(father, phone);
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
        flow.lastInbound(father).ifPresent(at -> r.put("windowHint", Map.of("lastInboundAt", at.toString())));
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
        return flow.json(r);
    }
}
