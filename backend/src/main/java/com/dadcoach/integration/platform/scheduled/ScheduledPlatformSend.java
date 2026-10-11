package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.SharedNumberGate;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
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
import com.dadcoach.whatsapp.DeliveryReceipts;
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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * D-046 (Unified Workflow Phase 6.3-DC, step DC2): a scheduled coach message - whatever reaches Dad Coach through the
 * platform's scheduled-response callback (session morning / hour-before / follow-up timers, the daily check, the weekly
 * plan) - sent by the platform's Send API instead of Dad Coach calling Meta itself. Switch {@code MESSAGING_MODE_SCHEDULED}
 * ({@link MessagingSettings}); the mechanics are D-045's ({@link PlatformSendFlow}); this class is the
 * {@code scheduled_response_delivery} ledger and the request. Platform spec {@code PHASE6_2_SEND_API_SPEC.md} §17.1 DC2.
 *
 * <p><b>The request</b> ({@link #request}): key {@code scheduled-response:<triggerId>} (the row's own UNIQUE key - a
 * replayed trigger never reaches here), kind {@code SCHEDULED}; {@code content} = what DIRECT would send free-form (the
 * text with the session buttons when it fits an interactive body, else the text); {@code template} = the message's own
 * template ({@link ScheduledMessageTemplates}: {@code dad_coach_session_*} with its quick-reply payloads) and
 * {@code fallbackTemplates} = the general one ({@code {{1}}} = {@link ProactiveSender#asTemplateParameter}); without an
 * own template the general one is {@code template}. {@code expectedBody} = the catalog body (DC's BODY_DIFFERS check).
 * {@code windowHint = session_closes_at - 24 h}; {@code notAfter} ({@link #notAfter}); hold by the gateway; timeline
 * {@code REPLACES_DRAFT} on {@code scheduled-transition:<triggerId>} with correlation
 * {@code callback:scheduled-transition:<triggerId>} - exactly the row D-039's callback answer creates today.</p>
 *
 * <p><b>The callback answer</b> ({@link ScheduledResponseDeliveryService.CallbackAnswer}): when the platform has (or may
 * have) the message it records the timeline itself, so the answer is {@code {}} plus {@code platformMessageId} when known
 * - no {@code outcome} / {@code deliveredContent}, no second record. When the platform provably created nothing, Dad Coach
 * sends it today's way and answers as today (D-039 report included); a refused request (400/422/409) answers FAILED with
 * the reason as a failed send does today.</p>
 *
 * <p><b>Mapping</b> (spec §7.4, forward only): QUEUED and an ambiguous answer leave the row SENDING; HELD = HELD +
 * {@code held:<n>}; ACCEPTED = ACCEPTED + wamid (+ delivery mode by the platform's channel window); DELIVERED / READ as
 * themselves; FAILED = FAILED {@code PLATFORM_<reason>}; UNKNOWN = UNKNOWN (D-042: never failed, never re-sent).</p>
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

    private final MessagingSettings settings;
    private final PlatformSendFlow flow;
    private final ScheduledResponseDeliveryRepository repository;
    private final ScheduledMessageTemplates templates;
    private final ScheduledResponseCallbackConfig config;
    private final ProactiveSender sender;
    private final FatherRepository fathers;
    private final QualityTimeRepository sessions;
    private final SessionButtonOffers offers;
    private final WeeklyGoalService goals;
    private final ObjectProvider<TimelineReports> timeline;
    private final WorkflowPlatformProperties platform;
    private final DeliveryReceipts receipts;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean receiptsEnabled;

    public ScheduledPlatformSend(MessagingSettings settings, PlatformSendFlow flow,
                                 ScheduledResponseDeliveryRepository repository, ScheduledMessageTemplates templates,
                                 ScheduledResponseCallbackConfig config, ProactiveSender sender, FatherRepository fathers,
                                 QualityTimeRepository sessions, SessionButtonOffers offers, WeeklyGoalService goals,
                                 ObjectProvider<TimelineReports> timeline, WorkflowPlatformProperties platform,
                                 DeliveryReceipts receipts, JdbcTemplate jdbc, Clock clock,
                                 @Value("${dad-coach.whatsapp.receipts.enabled:true}") boolean receiptsEnabled) {
        this.settings = settings;
        this.flow = flow;
        this.repository = repository;
        this.templates = templates;
        this.config = config;
        this.sender = sender;
        this.fathers = fathers;
        this.sessions = sessions;
        this.offers = offers;
        this.goals = goals;
        this.timeline = timeline;
        this.platform = platform;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.clock = clock;
        this.receiptsEnabled = receiptsEnabled;
    }

    /** Tests only: the next requests fail to build (review N1). */
    private volatile RuntimeException requestFault;

    void setRequestFaultForTests(RuntimeException fault) {
        this.requestFault = fault;
    }

    public boolean sends() {
        return settings.scheduled() == Mode.PLATFORM;
    }

    public boolean shadows() {
        return settings.scheduled() == Mode.SHADOW;
    }

    // ------------------------------------------------------------------------------------------------- PLATFORM

    /**
     * The row is claimed (SENDING) and the text decided; the platform sends it. {@code direct}: today's send and answer,
     * used only when the platform provably created nothing.
     */
    ScheduledResponseDeliveryService.CallbackAnswer deliver(Father father, ScheduledResponseDelivery delivery,
                                                            ScheduledResponseRequest request, String content,
                                                            List<OutboundMessageDto.ReplyButton> offered,
                                                            Supplier<ScheduledResponseResult> direct) {
        String body;
        try {
            RuntimeException fault = requestFault;
            if (fault != null) {
                throw fault;
            }
            body = request(father, delivery.getIdempotencyKey(), request.triggerId(), request.targetStateKey(), content,
                    offered, ownTemplate(father, request.targetStateKey()), false);
        } catch (RuntimeException e) {
            // nothing was posted (review N1): the platform cannot have it - today's send, today's answer
            log.atError().setMessage("messaging.scheduled.request_failed").addKeyValue("deliveryId", delivery.getId())
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
            return ScheduledResponseDeliveryService.CallbackAnswer.of(direct.get());
        }
        Settled<ScheduledResponseResult> s = flow.send(this, delivery.getId(), body, () -> { }, direct);
        if (s.verdict() == PlatformSendFlow.Verdict.DIRECT && s.direct() != null) {
            ScheduledResponseResult today = s.direct();
            return new ScheduledResponseDeliveryService.CallbackAnswer(today, today, "DELIVERED".equals(today.status()));
        }
        ScheduledResponseDelivery row = repository.findById(delivery.getId()).orElse(delivery);
        ScheduledResponseResult internal = ScheduledResponseResult.of(row, false);
        if (s.verdict() == PlatformSendFlow.Verdict.REFUSED) {
            // nothing was created: the platform records nothing, so the answer says it failed, as a failed send does today
            ScheduledResponseResult failed = platform.isDeliveryReports()
                    ? internal.withOutcome(TimelineReports.FAILED, s.reason(), null) : internal;
            return new ScheduledResponseDeliveryService.CallbackAnswer(failed, failed, false);
        }
        // the platform has it (or may have it, and is asked): it records the timeline row itself - no report here
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

    /** SHADOW: after today's send, the platform's dry run of the same request, off this thread. */
    void shadow(Father father, ScheduledResponseDelivery delivery, ScheduledResponseRequest request, String content,
                List<OutboundMessageDto.ReplyButton> offered, ProactiveSender.Outcome outcome) {
        if (!shadows()) {
            return;
        }
        try {
            DeliveryResult r = outcome.result();
            // the request (a template lookup, the window hint) is built on the shadow lane, not the callback's (review N3)
            flow.shadow(MessagingSettings.SCHEDULED, log, "deliveryId", delivery.getId(),
                    () -> request(father, delivery.getIdempotencyKey(), request.triggerId(), request.targetStateKey(),
                            content, offered, ownTemplate(father, request.targetStateKey()), true),
                    new PlatformSendFlow.ShadowFacts(r.isSuccessful(), r.isHeld(), r.failureReason(),
                            outcome.mode() == ProactiveSender.Mode.TEMPLATE ? outcome.template() : null));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", MessagingSettings.SCHEDULED)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
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

    /** The {@code /send} body (class comment). */
    String request(Father father, String key, String triggerId, String targetStateKey, String content,
                   List<OutboundMessageDto.ReplyButton> offered, Optional<TemplateCall> own, boolean dryRun) {
        Optional<Father> who = Optional.of(father);
        Map<String, Object> r = flow.newRequest(who, father.getPhone());
        r.put("idempotencyKey", key);
        r.put("kind", MessagingSettings.SCHEDULED);
        r.put("messageClass", "UTILITY");
        Map<String, Object> body = new LinkedHashMap<>();
        boolean withButtons = !offered.isEmpty() && content.length() <= WhatsAppMessageFormatter.INTERACTIVE_BODY_LIMIT;
        body.put("type", withButtons ? "buttons" : "text");
        body.put("text", content);
        if (withButtons) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (OutboundMessageDto.ReplyButton b : offered) {
                Map<String, Object> button = new LinkedHashMap<>();
                button.put("id", b.id());
                button.put("title", b.title());
                buttons.add(button);
            }
            body.put("buttons", buttons);
        }
        r.put("content", body);
        Map<String, Object> general = template(config.effectiveTemplateName(),
                TemplateRegistry.languageForTemplateName(config.effectiveTemplateName()),
                List.of(ProactiveSender.asTemplateParameter(content)), List.of());
        if (own.isPresent()) {
            TemplateCall t = own.get();
            r.put("template", template(t.name(), t.entry().language(),
                    t.values().stream().map(ProactiveSender::oneLine).toList(), t.buttonPayloads()));
            r.put("fallbackTemplates", List.of(general));
        } else {
            r.put("template", general);
        }
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
        r.put("dryRun", dryRun);
        return flow.json(r);
    }

    private static Map<String, Object> template(String name, String language, List<String> params, List<String> payloads) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("language", language);
        t.put("bodyParams", params);
        if (!payloads.isEmpty()) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (int i = 0; i < payloads.size(); i++) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("index", i);
                b.put("subType", "quick_reply");
                b.put("payload", payloads.get(i));
                buttons.add(b);
            }
            t.put("buttons", buttons);
        }
        WhatsAppTemplateCatalog.named(name).ifPresent(e -> t.put("expectedBody", e.body()));
        return t;
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
        return jdbc.queryForObject("SELECT idempotency_key FROM scheduled_response_delivery WHERE id = ?", String.class, id);
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
                    snap.heldId() == null ? null : SharedNumberGate.HELD_PREFIX + snap.heldId(), at, id);
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

    /** Stays SENDING: the platform may have it (spec 7.4: as QUEUED). */
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
    public Update ended(Timestamp at) {
        return new Update("status = 'FAILED', failure_reason = 'PLATFORM_NOT_CREATED_TOO_LATE', completed_at = ?", at);
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

    /** Never created, minutes later: sent directly only while it still says something true ({@code notAfter}). */
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

    /**
     * The reconcile job learned that the platform never created it and it is still true: Dad Coach sends exactly what
     * the request carried, today's way ({@link ProactiveSender}: free-form in the window with its buttons, else its own
     * template, else the general one), and - the callback was answered long ago - reports it to the timeline as the
     * turn's replacement (D-039's {@code callback:scheduled-transition:<triggerId>} row).
     */
    @Override
    public void sendLate(UUID id, String body) {
        Map<String, Object> row = jdbc.queryForMap("SELECT father_id, trigger_id FROM scheduled_response_delivery WHERE id = ?", id);
        Father father = fathers.findById(((Number) row.get("father_id")).longValue()).orElseThrow();
        String triggerId = (String) row.get("trigger_id");
        JsonNode request = flow.read(body);
        String text = request.path("content").path("text").asText();
        List<OutboundMessageDto.ReplyButton> buttons = new ArrayList<>();
        request.path("content").path("buttons").forEach(b -> buttons.add(
                new OutboundMessageDto.ReplyButton(b.path("id").asText(), b.path("title").asText())));
        TemplateCall own = null;
        if (request.has("fallbackTemplates")) {
            JsonNode t = request.path("template");
            List<String> values = new ArrayList<>();
            t.path("bodyParams").forEach(v -> values.add(v.asText()));
            List<String> payloads = new ArrayList<>();
            t.path("buttons").forEach(b -> payloads.add(b.path("payload").asText()));
            try {
                own = new TemplateCall(t.path("name").asText(), values, payloads);
            } catch (RuntimeException e) {
                own = null;   // the catalog changed meanwhile: the general template, as ProactiveSender does
            }
        }
        TemplateCall ownTemplate = own;
        ProactiveSender.Outcome outcome = sender.send(father, text, buttons, () -> ownTemplate);
        DeliveryResult r = outcome.result();
        Timestamp at = Timestamp.from(clock.instant());
        String mode = outcome.mode() == ProactiveSender.Mode.TEMPLATE ? "TEMPLATE" : "FREE_FORM";
        if (r.isSuccessful()) {
            jdbc.update("UPDATE scheduled_response_delivery SET status = ?, gateway_hold_id = ?, provider_message_id = ?, "
                    + "delivery_mode = ?, completed_at = ? WHERE id = ? AND status = 'SENDING'", r.isHeld() ? "HELD" : "ACCEPTED",
                    r.isHeld() ? r.providerMessageId() : null, r.metaMessageId(), mode, at, id);
            if (r.metaMessageId() != null && receiptsEnabled) {
                receipts.applyPending(r.metaMessageId());
            }
        } else {
            jdbc.update("UPDATE scheduled_response_delivery SET status = 'FAILED', failure_reason = ?, completed_at = ? "
                    + "WHERE id = ? AND status = 'SENDING'", r.failureReason(), at, id);
        }
        TimelineReports reports = timeline.getIfAvailable();
        if (reports != null && reports.enabled() && r.isSuccessful()) {
            String sent = outcome.sentText() != null ? outcome.sentText() : text;
            TimelineReports.Part part = TimelineReports.Part.replacing(CALLBACK_PREFIX + TURN_PREFIX + triggerId,
                    TURN_PREFIX + triggerId, TimelineText.withoutIdentity(sent), r, TimelineReports.KIND_SCHEDULED, null);
            if (outcome.mode() == ProactiveSender.Mode.TEMPLATE) {
                part = part.withTemplate(TimelineText.template(outcome.template(), outcome.templateParams()));
            } else if (!outcome.buttons().isEmpty()) {
                part = part.withButtons(TimelineText.buttons(outcome.buttons()));
            }
            reports.outbound(TimelineReports.Person.of(father), part);
        }
        log.atInfo().setMessage("messaging.scheduled.sent_directly").addKeyValue("deliveryId", id)
                .addKeyValue("delivered", r.isSuccessful()).addKeyValue("held", r.isHeld()).log();
    }

    /** A delivery report for a scheduled message (spec §3.5). */
    public PlatformSendFlow.Applied applyReport(String idempotencyKey, String messageId, JsonNode body) {
        return flow.applyReport(this, idempotencyKey, messageId, body);
    }

    // ------------------------------------------------------------------------------------------------- reconcile

    /** As D-045's job, for the scheduled messages. Runs on the messaging lane: it may send. */
    @Scheduled(fixedDelayString = "${dad-coach.messaging.reconcile-interval:PT1M}",
            initialDelayString = "${dad-coach.messaging.reconcile-initial-delay:PT1M}",
            scheduler = com.dadcoach.config.SchedulingLanes.MESSAGING)
    public void scheduledReconcile() {
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
