package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.auth.LoginLinkDelivery;
import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.TemplateText;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Settled;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Snapshot;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Update;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.integration.platform.timeline.TimelineText;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.whatsapp.ButtonIds;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import com.dadcoach.whatsapp.buttons.SessionButtonOffers;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * D-046 (Unified Workflow Phase 6.3-DC, step DC2; D-053 6.4): a scheduled coach message - whatever reaches Dad Coach
 * through the platform's scheduled-response callback (session morning / hour-before / follow-up timers, the daily check,
 * the weekly plan) - sent by the platform's Send API, the only sender since D-053. The mechanics are
 * {@link PlatformSendFlow}'s; this class is the {@code scheduled_response_delivery} ledger and the request. Platform spec
 * {@code PHASE6_2_SEND_API_SPEC.md} §17.1 DC2, {@code PHASE6_4_CLEANUP_SPEC.md} §2.
 *
 * <p><b>The request</b> ({@link #request}): key {@code scheduled-response:<triggerId>} (the row's own UNIQUE key - a
 * replayed trigger never reaches here; {@link PlatformSendFlow#platformKey} if the platform's shape refuses it), kind
 * {@code SCHEDULED}; {@code content} = the free-form message (the text with the session buttons when it fits an
 * interactive body, else the text); {@code templateData} - never a Meta template name: a session timer with its own
 * template ({@link ScheduledMessageTemplates}) names it as {@code variant} ({@code session_morning_he} /
 * {@code session_hour_before_he} / {@code session_follow_up_he}) with its values and quick-reply payloads, and every
 * message carries {@code named.text} = the one-line text ({@link TemplateText#asTemplateParameter}) for the general
 * template; without its own template the variant is "" (the kind's default). The platform's mapping picks the approved
 * template outside the window. {@code windowHint = session_closes_at - 24 h}; {@code notAfter} ({@link #notAfter});
 * hold by the gateway; timeline {@code REPLACES_DRAFT} on {@code scheduled-transition:<triggerId>} with correlation
 * {@code callback:scheduled-transition:<triggerId>}.</p>
 *
 * <p><b>The morning's dashboard button</b> (D-067): the session-day morning goes as {@code link_button} - its text exactly
 * as without it, under it the father's dashboard button ({@link LoginLinkDelivery#fatherLabel}) behind a sign-in link
 * valid {@link LoginLinkService#MORNING_LINK_TTL}, minted for this send ({@link LoginLinkService#issueMorning}); the
 * {@code fallbackText} is the text with the link on its own line; {@code templateData.named.link} is the token, the URL
 * suffix of {@code dad_coach_session_morning_dash_he} (mapped before {@code dad_coach_session_morning_he}). The request is
 * {@code sensitive}. The stored bytes ({@code platform_request}) hold {@link #LINK_TOKEN_MARK} in place of the token,
 * never the token: the first POST and its immediate re-POST send the minted one, a reconcile re-POST mints a new one
 * ({@link #toPost}). A morning the platform refused ends its link. No link when he may not sign in, nor for a message
 * with reply buttons or too long for an interactive body (the morning has neither).</p>
 *
 * <p><b>The callback answer</b> ({@link ScheduledResponseDeliveryService.CallbackAnswer}): when the platform has (or may
 * have, or will be asked again) the message it records the timeline itself, so the answer is {@code {}} plus
 * {@code platformMessageId} when known. A refused request (400/422/409) or one Dad Coach could not build answers FAILED
 * with the reason.</p>
 *
 * <p><b>Mapping</b> (spec §7.4, forward only): QUEUED, NOT_CREATED and an ambiguous answer leave the row SENDING
 * (pending); HELD = HELD + {@code held:<n>}; ACCEPTED = ACCEPTED + wamid (+ delivery mode by the platform's channel
 * window); DELIVERED / READ as themselves; FAILED = FAILED {@code PLATFORM_<reason>}; UNKNOWN = UNKNOWN (D-042: never
 * failed, never re-sent). Nothing created and no longer true ({@code notAfter} passed): FAILED
 * {@code PLATFORM_NOT_CREATED}.</p>
 */
@Service
@org.springframework.context.annotation.Lazy(false) // production starts lazily: the reconcile job must run after a restart
public class ScheduledPlatformSend implements PlatformSendFlow.Ledger {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPlatformSend.class);

    public static final String KEY_PREFIX = "scheduled-response:";
    public static final String TURN_PREFIX = "scheduled-transition:";
    public static final String CALLBACK_PREFIX = "callback:";
    /** A message is never given a deadline closer than this (a send now must never fail on it). */
    static final Duration MIN_NOT_AFTER = Duration.ofMinutes(15);
    static final Duration FOLLOW_UP_VALID = Duration.ofHours(12);
    /** D-067: the morning link's token in the stored bytes - the token itself never is (class comment). */
    static final String LINK_TOKEN_MARK = "{{dashboard-token}}";
    /** D-067: the named value the URL-button template's mapping row puts on its button. */
    static final String LINK_REF = "link";

    private final PlatformSendFlow flow;
    private final ScheduledResponseDeliveryRepository repository;
    private final ScheduledMessageTemplates templates;
    private final FatherRepository fathers;
    private final QualityTimeRepository sessions;
    private final SessionButtonOffers offers;
    private final WeeklyGoalService goals;
    private final WorkflowPlatformProperties platform;
    private final LoginLinkService links;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ScheduledPlatformSend(PlatformSendFlow flow, ScheduledResponseDeliveryRepository repository,
                                 ScheduledMessageTemplates templates, FatherRepository fathers,
                                 QualityTimeRepository sessions, SessionButtonOffers offers, WeeklyGoalService goals,
                                 WorkflowPlatformProperties platform, LoginLinkService links, JdbcTemplate jdbc,
                                 Clock clock) {
        this.flow = flow;
        this.repository = repository;
        this.templates = templates;
        this.fathers = fathers;
        this.sessions = sessions;
        this.offers = offers;
        this.goals = goals;
        this.platform = platform;
        this.links = links;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private com.dadcoach.integration.platform.messaging.PendingSweep pendingSweep;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPendingSweep(com.dadcoach.integration.platform.messaging.PendingSweep pendingSweep) {
        this.pendingSweep = pendingSweep;
    }

    /** Tests only: the next requests fail to build (review N1). */
    private volatile RuntimeException requestFault;

    void setRequestFaultForTests(RuntimeException fault) {
        this.requestFault = fault;
    }

    // ------------------------------------------------------------------------------------------------- send

    /** The row is claimed (SENDING) and the text decided; the platform sends it. */
    ScheduledResponseDeliveryService.CallbackAnswer deliver(Father father, ScheduledResponseDelivery delivery,
                                                            ScheduledResponseRequest request, String content,
                                                            List<OutboundMessageDto.ReplyButton> offered) {
        String body;
        Optional<LoginLinkService.MorningLink> link = Optional.empty();
        try {
            RuntimeException fault = requestFault;
            if (fault != null) {
                throw fault;
            }
            link = dashboardButton(request.targetStateKey(), content, offered) ? morningLink(father) : Optional.empty();
            body = request(father, delivery.getIdempotencyKey(), request.triggerId(), request.targetStateKey(), content,
                    offered, ownTemplate(father, request.targetStateKey()), link.isPresent());
        } catch (RuntimeException e) {
            link.ifPresent(this::endLink);
            // a Dad Coach bug (D-053): nothing was posted - FAILED, never sent another way
            log.atError().setMessage("messaging.scheduled.request_failed").addKeyValue("deliveryId", delivery.getId())
                    .addKeyValue("error", e.getClass().getSimpleName()).setCause(e).log();
            jdbc.update("UPDATE scheduled_response_delivery SET status = 'FAILED', failure_reason = ?, completed_at = ? "
                    + "WHERE id = ? AND status = 'SENDING'", PlatformSendFlow.REQUEST_BUILD_FAILED,
                    java.sql.Timestamp.from(clock.instant()), delivery.getId());
            ScheduledResponseResult failed = ScheduledResponseResult.of(repository.findById(delivery.getId()).orElse(delivery),
                    false);
            ScheduledResponseResult answer = platform.isDeliveryReports()
                    ? failed.withOutcome(TimelineReports.FAILED, PlatformSendFlow.REQUEST_BUILD_FAILED, null) : failed;
            return new ScheduledResponseDeliveryService.CallbackAnswer(answer, answer, false);
        }
        String posted = link.map(l -> withToken(body, l.token())).orElse(body);
        Settled s = flow.send(this, delivery.getId(), body, posted, () -> { });
        ScheduledResponseDelivery row = repository.findById(delivery.getId()).orElse(delivery);
        ScheduledResponseResult internal = ScheduledResponseResult.of(row, false);
        if (s.verdict() == PlatformSendFlow.Verdict.REFUSED) {
            link.ifPresent(this::endLink);   // nothing was created: nobody holds its token
            // nothing was created: the platform records nothing, so the answer says it failed, as a failed send does
            ScheduledResponseResult failed = platform.isDeliveryReports()
                    ? internal.withOutcome(TimelineReports.FAILED, s.reason(), null) : internal;
            return new ScheduledResponseDeliveryService.CallbackAnswer(failed, failed, false);
        }
        // the platform has it (or may have it, or is asked again): it records the timeline row itself - no report here
        Map<String, Object> answer = new LinkedHashMap<>();
        String messageId = s.snapshot() != null ? s.snapshot().messageId()
                : jdbc.queryForObject("SELECT platform_message_id FROM scheduled_response_delivery WHERE id = ?", String.class,
                        delivery.getId());
        if (messageId != null) {
            answer.put("platformMessageId", messageId);
        }
        boolean onItsWay = s.snapshot() == null || !"FAILED".equals(s.snapshot().status());
        return new ScheduledResponseDeliveryService.CallbackAnswer(internal, answer, onItsWay);
    }

    /**
     * D-067: the session-day morning carries his dashboard button - unless it has reply buttons (WhatsApp cannot mix them
     * with a URL button; the morning has none) or is too long for an interactive body.
     */
    static boolean dashboardButton(String targetStateKey, String content, List<OutboundMessageDto.ReplyButton> offered) {
        return ScheduledMessageTemplates.MORNING_STATE.equals(targetStateKey) && offered.isEmpty()
                && content.length() <= WhatsAppMessageFormatter.INTERACTIVE_BODY_LIMIT;
    }

    /** D-067: a new morning link for him, or none (he may not sign in, or it could not be issued: the morning still goes). */
    private Optional<LoginLinkService.MorningLink> morningLink(Father father) {
        try {
            return father.getPhone() == null ? Optional.empty() : links.issueMorning(father.getPhone());
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.scheduled.morning_link_failed").addKeyValue("fatherId", father.getId())
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
            return Optional.empty();
        }
    }

    /** D-067: a morning link whose message never went out is ended; best effort (it is unused and expires in 7 days). */
    private void endLink(LoginLinkService.MorningLink link) {
        try {
            links.revokeUnsent(link.id());
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.scheduled.morning_link_not_ended").addKeyValue("linkId", link.id())
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    /** The bytes to POST: the stored ones with the token in place of {@link #LINK_TOKEN_MARK}. */
    static String withToken(String stored, String token) {
        return stored.replace(LINK_TOKEN_MARK, token);
    }

    private Optional<TemplateCall> ownTemplate(Father father, String targetStateKey) {
        try {
            return templates.forScheduledMessage(father, targetStateKey);
        } catch (RuntimeException e) {
            log.warn("Own template not built, the general template is the platform's only one: {}", e.toString());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------------------------------------- the request

    /** The {@code /send} body (class comment); with {@code dashboardButton} its token is {@link #LINK_TOKEN_MARK}. */
    String request(Father father, String key, String triggerId, String targetStateKey, String content,
                   List<OutboundMessageDto.ReplyButton> offered, Optional<TemplateCall> own, boolean dashboardButton) {
        Optional<Father> who = Optional.of(father);
        Map<String, Object> r = flow.newRequest(who, father.getPhone());
        r.put("idempotencyKey", PlatformSendFlow.platformKey(key));
        r.put("kind", MessagingSettings.SCHEDULED);
        r.put("messageClass", "UTILITY");
        Map<String, Object> body = new LinkedHashMap<>();
        boolean withButtons = !offered.isEmpty() && content.length() <= WhatsAppMessageFormatter.INTERACTIVE_BODY_LIMIT;
        boolean withLink = !withButtons && dashboardButton;
        body.put("type", withButtons ? "buttons" : withLink ? "link_button" : "text");
        body.put("text", content);
        if (withButtons) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (OutboundMessageDto.ReplyButton b : offered) {
                Map<String, Object> button = new LinkedHashMap<>();
                button.put("id", ButtonIds.require(b.id(), "Button"));   // the shared-number routing contract
                button.put("title", b.title());
                buttons.add(button);
            }
            body.put("buttons", buttons);
        }
        String url = withLink ? links.buttonUrl(LINK_TOKEN_MARK) : null;
        if (withLink) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("url", url);
            link.put("title", LoginLinkDelivery.fatherLabel());
            body.put("link", link);   // no footer: the morning's text is exactly as without the button
        }
        r.put("content", body);
        if (withLink) {
            r.put("fallbackText", new OutboundMessageDto.LinkButton(LoginLinkDelivery.fatherLabel(), url, null).asText(content));
        }
        r.put("templateData", templateData(content, own, withLink ? LINK_TOKEN_MARK : null));
        r.put("windowPolicy", "AUTO");
        r.put("outsideWindowWithoutTemplate", "FAIL");
        flow.lastInbound(who).ifPresent(at -> r.put("windowHint", Map.of("lastInboundAt", at.toString())));
        r.put("holdPolicy", "GATEWAY");
        r.put("notAfter", notAfter(father, targetStateKey).toString());
        Map<String, Object> timelineRow = new LinkedHashMap<>();
        timelineRow.put("record", true);
        timelineRow.put("correlationId", CALLBACK_PREFIX + TURN_PREFIX + triggerId);
        timelineRow.put("mode", "REPLACES_DRAFT");
        timelineRow.put("turnCorrelationId", TURN_PREFIX + triggerId);
        timelineRow.put("content", TimelineText.withoutIdentity(content));
        r.put("timeline", timelineRow);
        r.put("wait", "ACCEPTED");
        if (withLink) {
            r.put("sensitive", true);   // a sign-in link: the platform keeps only the link's host
        }
        return flow.json(r);
    }

    /**
     * D-053: the message type and its values for the platform's kind→template mapping (spec 6.4 §2.1, seed rows
     * {@code dad_coach}/SCHEDULED): the session template's variant, values and payloads when the message has one, and
     * always {@code named.text} for the general {@code dad_coach_update_he} ({{1}} = the message as one line).
     */
    static Map<String, Object> templateData(String content, Optional<TemplateCall> own) {
        return templateData(content, own, null);
    }

    /**
     * D-067: with {@code linkToken}, a message with its own template also carries {@code named.link} - the URL suffix of
     * the button template mapped first ({@code dad_coach_session_morning_dash_he}); the rows after it read the same values.
     */
    static Map<String, Object> templateData(String content, Optional<TemplateCall> own, String linkToken) {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("text", TemplateText.asTemplateParameter(content));
        if (linkToken != null && own.isPresent()) {
            named.put(LINK_REF, linkToken);
        }
        if (own.isEmpty()) {
            return PlatformSendFlow.templateData("", null, named, null);
        }
        TemplateCall t = own.get();
        return PlatformSendFlow.templateData(t.variant(), t.values().stream().map(TemplateText::oneLine).toList(), named,
                t.buttonPayloads().stream().map(p -> ButtonIds.require(p, "Template quick-reply")).toList());
    }

    /**
     * Until when the message still says something true - the platform never sends it later (it may hold it while he is
     * on another product, up to 24 h): the morning summary until the start of the day's last session it names; the
     * hour-before until its session starts; the follow-up until 12 h after its session ended (the timers' own window,
     * ScheduledReplies); anything else (daily check, weekly plan) until the end of his day. Never closer than
     * {@link #MIN_NOT_AFTER}, so a message sent now is never refused for it.
     */
    Instant notAfter(Father father, String targetStateKey) {
        Instant now = clock.instant();
        ZoneId zone = goals.zoneFor(father);
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant until;
        if (ScheduledMessageTemplates.MORNING_STATE.equals(targetStateKey)) {
            until = sessions.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED).stream()
                    .filter(qt -> qt.getScheduledStart() != null && qt.getScheduledEnd() != null)
                    .filter(qt -> qt.getScheduledEnd().isAfter(now) && qt.getScheduledStart().atZone(zone).toLocalDate().equals(today))
                    .map(QualityTime::getScheduledStart).max(Instant::compareTo).orElse(endOfDay);
        } else if (SessionButtonOffers.REMINDER_STATE.equals(targetStateKey)) {
            until = offers.sessionFor(father, targetStateKey).map(QualityTime::getScheduledStart).orElse(endOfDay);
        } else if (SessionButtonOffers.FOLLOW_UP_STATE.equals(targetStateKey)) {
            until = offers.sessionFor(father, targetStateKey).map(qt -> qt.getScheduledEnd().plus(FOLLOW_UP_VALID))
                    .orElse(endOfDay);
        } else {
            until = endOfDay;
        }
        Instant floor = now.plus(MIN_NOT_AFTER);
        return until.isAfter(floor) ? until : floor;
    }

    // ------------------------------------------------------------------------------------------------- the ledger

    @Override
    public String kind() {
        return MessagingSettings.SCHEDULED;
    }

    @Override
    public String table() {
        return "scheduled_response_delivery";
    }

    @Override
    public Logger log() {
        return log;
    }

    @Override
    public String event() {
        return "scheduled";
    }

    @Override
    public String idKey() {
        return "deliveryId";
    }

    @Override
    public String idempotencyKey(UUID id) {
        return PlatformSendFlow.platformKey(jdbc.queryForObject("SELECT idempotency_key FROM scheduled_response_delivery "
                + "WHERE id = ?", String.class, id));
    }

    @Override
    public Optional<UUID> rowOf(String idempotencyKey, String messageId) {
        if (idempotencyKey != null && idempotencyKey.startsWith(KEY_PREFIX)) {
            Optional<UUID> byKey = jdbc.queryForList("SELECT id FROM scheduled_response_delivery WHERE idempotency_key = ?",
                    UUID.class, idempotencyKey).stream().findFirst();
            if (byKey.isPresent()) {
                return byKey;
            }
        }
        if (idempotencyKey != null && idempotencyKey.startsWith("h:")) {
            // D-053 (R5): a key the platform's shape refused travels as h:<sha256-32hex> of the row's own key
            Optional<UUID> byHash = jdbc.queryForList("SELECT id FROM scheduled_response_delivery WHERE 'h:' || "
                    + "left(encode(sha256(convert_to(idempotency_key, 'UTF8')), 'hex'), 32) = ?", UUID.class, idempotencyKey)
                    .stream().findFirst();
            if (byHash.isPresent()) {
                return byHash;
            }
        }
        if (messageId != null) {
            return jdbc.queryForList("SELECT id FROM scheduled_response_delivery WHERE platform_message_id = ?", UUID.class,
                    messageId).stream().findFirst();
        }
        return Optional.empty();
    }

    @Override
    public String statusColumns() {
        return "status, provider_message_id";
    }

    /** SENDING 0 (1 once the platform said QUEUED) &lt; HELD / UNKNOWN 2 &lt; ACCEPTED 3 &lt; SENT 4 &lt; DELIVERED 5 &lt; READ 6. */
    @Override
    public boolean forward(Map<String, Object> row, String to) {
        String status = (String) row.get("status");
        int at = switch (status == null ? "SENDING" : status) {
            case "SENDING" -> "QUEUED".equals(row.get("platform_status")) ? 1 : 0;
            case "HELD", "UNKNOWN" -> 2;
            case "ACCEPTED" -> 3;
            case "SENT" -> 4;
            case "DELIVERED" -> 5;
            case "READ" -> 6;
            default -> 0;
        };
        return PlatformSendFlow.forward("FAILED".equals(status), at, "HELD".equals(status), "UNKNOWN".equals(status), to);
    }

    @Override
    public void applyStatus(UUID id, Snapshot snap, Timestamp at) {
        String wamid = snap.providerMessageId();
        String mode = "SESSION".equals(snap.channelWindow()) ? "FREE_FORM"
                : "TEMPLATE".equals(snap.channelWindow()) ? "TEMPLATE" : null;
        switch (snap.status()) {
            case "QUEUED" -> { } // stays SENDING (spec 7.4); the flow keeps its lookup
            case "HELD" -> jdbc.update("UPDATE scheduled_response_delivery SET status = 'HELD', gateway_hold_id = ?, "
                    + "completed_at = COALESCE(completed_at, ?) WHERE id = ?",
                    snap.heldId() == null ? null : DeliveryResult.HELD_PREFIX + snap.heldId(), at, id);
            case "ACCEPTED" -> jdbc.update("UPDATE scheduled_response_delivery SET status = 'ACCEPTED', failure_reason = NULL, "
                    + "provider_message_id = COALESCE(?, provider_message_id), delivery_mode = COALESCE(?, delivery_mode), "
                    + "completed_at = COALESCE(completed_at, ?) WHERE id = ?", wamid, mode, at, id);
            case "DELIVERED", "READ" -> jdbc.update("UPDATE scheduled_response_delivery SET status = ?, status_at = ?, "
                    + "failure_reason = NULL, provider_message_id = COALESCE(?, provider_message_id), "
                    + "delivery_mode = COALESCE(?, delivery_mode), completed_at = COALESCE(completed_at, ?) WHERE id = ?",
                    snap.status(), at, wamid, mode, at, id);
            case "FAILED" -> jdbc.update("UPDATE scheduled_response_delivery SET status = 'FAILED', failure_reason = ?, "
                    + "status_at = ?, provider_message_id = COALESCE(?, provider_message_id), "
                    + "completed_at = COALESCE(completed_at, ?) WHERE id = ?",
                    PlatformSendFlow.cut("PLATFORM_" + (snap.reason() == null ? "FAILED" : snap.reason()), 200), at, wamid, at, id);
            case "UNKNOWN" -> jdbc.update("UPDATE scheduled_response_delivery SET status = 'UNKNOWN', failure_reason = ?, "
                    + "status_at = ?, completed_at = COALESCE(completed_at, ?) WHERE id = ?",
                    PlatformSendFlow.cut("PLATFORM_" + (snap.reason() == null ? "UNKNOWN" : snap.reason()), 200), at, at, id);
            default -> throw new IllegalStateException("unhandled status " + snap.status());
        }
    }

    /** Stays SENDING: the platform may have it, or will be asked again (spec 7.4: as QUEUED; D-053 NOT_CREATED). */
    @Override
    public String ambiguousSet() {
        return "";
    }

    @Override
    public Update refused(String reason, Timestamp at) {
        return new Update("status = 'FAILED', failure_reason = ?, completed_at = ?", reason, at);
    }

    @Override
    public Update gaveUp(String reason, Timestamp at) {
        return new Update("status = 'UNKNOWN', failure_reason = ?, status_at = ?, completed_at = COALESCE(completed_at, ?)",
                reason, at, at);
    }

    @Override
    public Update ended(String reason, Timestamp at) {
        return new Update("status = 'FAILED', failure_reason = ?, completed_at = ?", reason, at);
    }

    @Override
    public String blocker(UUID id, String body) {
        Long fatherId = jdbc.queryForObject("SELECT father_id FROM scheduled_response_delivery WHERE id = ?", Long.class, id);
        Optional<Father> father = fathers.findById(fatherId);
        if (father.isEmpty() || father.get().getStatus() == FatherStatus.DELETED || father.get().getPhone() == null) {
            return "NO_FATHER";
        }
        JsonNode request = flow.read(body);
        return request == null || !request.path("content").path("text").isTextual() ? "NO_REQUEST" : null;
    }

    /**
     * D-067: a morning's stored bytes hold {@link #LINK_TOKEN_MARK}: each re-POST carries a newly minted link (the earlier
     * token is not stored anywhere; if the earlier POST did create the message the platform answers with it - 409 adopted
     * - and its link keeps working). Null when no link can be issued for him any more: never posted again.
     */
    @Override
    public String toPost(UUID id, String stored) {
        if (!stored.contains(LINK_TOKEN_MARK)) {
            return stored;
        }
        Long fatherId = jdbc.queryForObject("SELECT father_id FROM scheduled_response_delivery WHERE id = ?", Long.class, id);
        return fathers.findById(fatherId).flatMap(this::morningLink).map(l -> withToken(stored, l.token())).orElse(null);
    }

    /** Nothing created (yet): POSTed again only while it still says something true ({@code notAfter}). */
    @Override
    public boolean stillSendable(UUID id, String body, Instant now) {
        JsonNode request = flow.read(body);
        if (request == null || !request.path("notAfter").isTextual()) {
            return false;
        }
        try {
            return now.isBefore(Instant.parse(request.path("notAfter").asText()));
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /** A delivery report for a scheduled message (spec §3.5). */
    public PlatformSendFlow.Applied applyReport(String idempotencyKey, String messageId, JsonNode body) {
        return flow.applyReport(this, idempotencyKey, messageId, body);
    }

    // ------------------------------------------------------------------------------------------------- reconcile

    /** As D-045's job, for the scheduled messages. Runs on the messaging lane: it re-POSTs. */
    @Scheduled(fixedDelayString = "${dad-coach.messaging.reconcile-interval:PT1M}",
            initialDelayString = "${dad-coach.messaging.reconcile-initial-delay:PT1M}",
            scheduler = com.dadcoach.config.SchedulingLanes.MESSAGING)
    public void scheduledReconcile() {
        if (pendingSweep != null) {
            pendingSweep.sweepIfDue();   // D-053 (R12): abandoned pending rows to UNKNOWN, at most hourly
        }
        try {
            reconcileDue();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.scheduled.reconcile_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    /** One pass; returns how many rows it looked at. */
    public int reconcileDue() {
        return flow.reconcileDue(this);
    }
}
