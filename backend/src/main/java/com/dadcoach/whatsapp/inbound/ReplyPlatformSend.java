package com.dadcoach.whatsapp.inbound;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Settled;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Snapshot;
import com.dadcoach.integration.platform.timeline.TimelineText;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * D-048 (Unified Workflow Phase 6.3-DC, step DC4; D-053 6.4): the lines Dad Coach writes in answer to a father's
 * WhatsApp message - sent by the platform's Send API, the only sender since D-053:
 * <ul>
 *   <li>{@code REPLY} - the coach's turn reply (as written, or as Dad Coach's guards corrected it);</li>
 *   <li>{@code FIXED_LINE} - Dad Coach's fixed lines: a picture / file / voice note it cannot read, a voice note asking
 *       to delete, the not-Hebrew line, the line sent instead of the card and (D-053) the "something went wrong" line and
 *       the answer to a deletion request;</li>
 *   <li>{@code BUTTON_REPLY} - a session button's fixed answer;</li>
 *   <li>{@code READY_ANSWER} - an answer written from data with no turn (D-036).</li>
 * </ul>
 *
 * <p><b>The request</b>: key {@code reply:<inbound wamid>:<n>} (n = the line's place among the lines sent for that
 * message, so Meta's redelivery of the same message posts the same keys and the platform replays - never a second
 * message; {@link PlatformSendFlow#platformKey} when the platform's shape refuses it); {@code content.text} = the text
 * (cut to WhatsApp's 4096); {@code windowPolicy SESSION_ONLY} (a reply is free-form), {@code windowHint} = his message's
 * time; {@code holdPolicy GATEWAY}; {@code wait ACCEPTED}. Timeline (spec §9): the reply as written - {@code TURN_AS_IS}
 * on the turn; anything sent in place of the turn's reply - {@code REPLACES_DRAFT} {@code <wamid>:delivered}; a line with
 * no turn - {@code NEW_ROW} with D-039's correlation id; a stranger's number - not recorded. The deletion answer (spec v2
 * R7, {@link Line#anonymous}) names nobody - no {@code userId}/{@code personRef}/{@code personName}, only {@code to} -
 * is {@code sensitive}, never recorded, and is sent before the deletion is requested.</p>
 *
 * <p><b>The answer</b> ({@link PlatformSendFlow#sendOnce}: the bytes stay in memory, spec v2.6): the read timeout is
 * short ({@code MESSAGING_REPLY_READ_TIMEOUT_MS}, 5 s), never a re-POST after a timeout, one immediate re-POST of an
 * ambiguous answer, a 429 re-POSTed once after ≤ 2 s; a 409 carrying the existing message is adopted. Provably nothing
 * created (503 {@code messaging.disabled}, 401/403, never written): one re-POST after ≤ 2 s, then nothing is sent (WARN
 * {@code messaging.platform_not_created}) and the message stays unanswered (Meta's redelivery runs it again). Refused
 * (400/422), or only 429s - nothing sent, unanswered. FAILED - unanswered; on Meta's redelivery the platform replays it,
 * and when its reason is resendable ({@link #RESENDABLE}) the line is sent again ONCE as {@code <key>~r1} with
 * {@code retryOf}; any other FAILED is final. ACCEPTED / HELD / QUEUED / UNKNOWN or an ambiguous answer - it reached him
 * or may have: answered, never sent a second time.</p>
 */
@Component
public class ReplyPlatformSend {

    private static final Logger log = LoggerFactory.getLogger(ReplyPlatformSend.class);
    static final String EVENT = "reply";
    public static final String KEY_PREFIX = "reply:";
    /** Spec §16: a turn reply re-POSTs a 429 once, after at most 2 s. */
    static final Duration RATE_LIMIT_WAIT = Duration.ofSeconds(2);
    /** His message before the answer on the timeline: one synchronous attempt at most this long, else in the background. */
    public static final Duration INBOUND_BEFORE = Duration.ofSeconds(2);
    /** The resend's key: the failed message's key plus this (spec §10.1 convention). */
    public static final String RESEND_SUFFIX = "~r1";
    /**
     * The platform's resendable reasons ({@code MetaErrorPolicy.RESENDABLE}, spec v2.3): not delivered and Meta took no
     * decision about the person. Any other FAILED (131026, 131049, 131050, ...) is final.
     */
    static final java.util.Set<String> RESENDABLE = java.util.Set.of("META_131047", "META_131056", "META_130429",
            "META_80007", "META_4", "HTTP_429", "NEVER_SENT_CONNECT", "NEVER_SENT_DNS", "NEVER_SENT_TLS", "HELD_EXPIRED",
            "HELD_NOT_IN_LATEST", "HELD_INTERRUPTED", "NOT_AFTER", "NO_TEMPLATE", "WINDOW_CLOSED", "SENDER_UNAVAILABLE",
            "META_131048", "META_131057", "MESSAGING_DISABLED");

    /** How the platform records the line on the conversation's timeline (spec §9); {@code mode} null = not recorded. */
    public record Timeline(String mode, String correlationId, String turnCorrelationId, List<String> supersededTurns,
                           String content) {

        public static Timeline none() {
            return new Timeline(null, null, null, List.of(), null);
        }

        /** The turn's reply went out as the platform wrote it. */
        public static Timeline asIs(String turnCorrelationId) {
            return new Timeline("TURN_AS_IS", null, turnCorrelationId, List.of(), null);
        }

        /** What was sent in place of the turn's reply (D-039's {@code <cid>:delivered} row). */
        public static Timeline replacing(String turnCorrelationId, List<String> supersededTurns, String sentText) {
            return new Timeline("REPLACES_DRAFT", turnCorrelationId + ":delivered", turnCorrelationId,
                    List.copyOf(supersededTurns), TimelineText.withoutIdentity(sentText));
        }

        /** A line with no turn (D-039's correlation id). */
        public static Timeline newRow(String correlationId, String sentText) {
            return new Timeline("NEW_ROW", correlationId, null, List.of(), TimelineText.withoutIdentity(sentText));
        }

        public boolean records() {
            return mode != null;
        }
    }

    /**
     * One line: its kind, to whom, for which of his messages (wamid) and its place among that message's lines.
     * {@code anonymous} (D-053, spec v2 R7): the request names nobody, is {@code sensitive} and is never recorded.
     */
    public record Line(String kind, String phone, Optional<Father> father, String inboundId, int part, Instant inboundAt,
                       String text, Timeline timeline, boolean anonymous) {

        public Line(String kind, String phone, Optional<Father> father, String inboundId, int part, Instant inboundAt,
                    String text, Timeline timeline) {
            this(kind, phone, father, inboundId, part, inboundAt, text, timeline, false);
        }

        /** The key as the platform knows it ({@link PlatformSendFlow#platformKey}). */
        public String key() {
            return PlatformSendFlow.platformKey(KEY_PREFIX + inboundId + ":" + part);
        }
    }

    /**
     * What happened: {@code result} for the handler (successful = it reached him or may have); {@code platformRecords}
     * the platform records it on the timeline (or may) - Dad Coach reports nothing of it; {@code platformStatus} the
     * platform's word (or AMBIGUOUS / REFUSED / NOT_CREATED / REQUEST_BUILD_FAILED).
     */
    public record Sent(DeliveryResult result, boolean platformRecords, String platformStatus) {}

    private final MessagingSettings settings;
    private final PlatformSendFlow flow;
    private final WhatsAppMessageFormatter formatter;
    private final Clock clock;

    public ReplyPlatformSend(MessagingSettings settings, PlatformSendFlow flow, WhatsAppMessageFormatter formatter, Clock clock) {
        this.settings = settings;
        this.flow = flow;
        this.formatter = formatter;
        this.clock = clock;
    }

    PlatformSendFlow.Options options() {
        return new PlatformSendFlow.Options(settings.replyReadTimeoutMs(), true, RATE_LIMIT_WAIT,
                settings.notCreatedRepostWait());
    }

    /** The platform sends it (the only sender, D-053). */
    public Sent send(Line line) {
        String body;
        try {
            body = request(line, line.key(), line.timeline(), null);
        } catch (RuntimeException e) {
            // a Dad Coach bug (D-053): nothing was posted, nothing is sent another way
            log.atError().setMessage("messaging.reply.request_failed").addKeyValue("kind", line.kind())
                    .addKeyValue("error", e.getClass().getSimpleName()).setCause(e).log();
            return new Sent(DeliveryResult.failed(PlatformSendFlow.REQUEST_BUILD_FAILED), false,
                    PlatformSendFlow.REQUEST_BUILD_FAILED);
        }
        Settled s = flow.sendOnce(EVENT, log, "key", line.key(), body, options());
        Sent sent = settled(line.timeline(), s);
        Snapshot snap = s.snapshot();
        if (snap != null && "FAILED".equals(snap.status()) && snap.reason() != null && RESENDABLE.contains(snap.reason())
                && "true".equals(s.answer().text("replayed"))) {
            return resend(line, snap);
        }
        return sent;
    }

    /**
     * D-048 review: Meta's redelivery of a message whose line the platform FAILED without Meta deciding about him
     * ({@link #RESENDABLE}: nothing reached him) - the line is sent again once, as a new message {@code <key>~r1} with
     * {@code retryOf} the failed one; the platform enforces G1-G5 (not delivered, once, same person, window, durable key).
     * A later redelivery posts the same {@code ~r1} bytes (the platform replays it); a resend that fails is final. On
     * the timeline it is a row of its own ({@code <row>~r1}, NEW_ROW): the failed message keeps its row.
     */
    private Sent resend(Line line, Snapshot failed) {
        String key = PlatformSendFlow.platformKey(line.key() + RESEND_SUFFIX);
        Timeline row = resendRow(line);
        log.atInfo().setMessage("messaging.reply.resend").addKeyValue("key", key).addKeyValue("retryOf", failed.messageId())
                .addKeyValue("reason", failed.reason()).log();
        String body;
        try {
            body = request(line, key, row, failed.messageId());
        } catch (RuntimeException e) {
            log.atError().setMessage("messaging.reply.request_failed").addKeyValue("kind", line.kind())
                    .addKeyValue("error", e.getClass().getSimpleName()).setCause(e).log();
            return new Sent(DeliveryResult.failed("PLATFORM_" + failed.reason()), false, "FAILED");
        }
        return settled(row, flow.sendOnce(EVENT, log, "key", key, body, options()));
    }

    static Timeline resendRow(Line line) {
        Timeline t = line.timeline();
        if (!t.records()) {
            return t;
        }
        String row = t.correlationId() != null ? t.correlationId() : t.turnCorrelationId() + ":delivered";
        return Timeline.newRow(row + RESEND_SUFFIX, line.text());
    }

    private Sent settled(Timeline row, Settled s) {
        return switch (s.verdict()) {
            // nothing was created (D-053: no other sender): unanswered - a redelivery posts the same key again
            case NOT_CREATED -> new Sent(DeliveryResult.failed(s.reason()), false, "NOT_CREATED");
            case REFUSED -> new Sent(DeliveryResult.failed(s.reason()), false, "REFUSED");
            // nothing was created (every answer a 429): unanswered - a redelivery posts the same key again
            case RATE_LIMITED -> new Sent(DeliveryResult.failed(s.reason()), false, "RATE_LIMITED");
            case AMBIGUOUS -> new Sent(onItsWay(null), row.records(), "AMBIGUOUS");
            case OWNED, ADOPT -> answered(row, s.snapshot());
        };
    }

    private Sent answered(Timeline row, Snapshot snap) {
        DeliveryResult r = switch (snap.status()) {
            case "FAILED" -> DeliveryResult.failed(PlatformSendFlow.cut("PLATFORM_"
                    + (snap.reason() == null ? "FAILED" : snap.reason()), 200));
            case "HELD" -> onItsWay(snap.heldId() == null ? null : DeliveryResult.HELD_PREFIX + snap.heldId());
            case "ACCEPTED", "DELIVERED", "READ" -> DeliveryResult.sent(snap.providerMessageId());
            default -> onItsWay(null);  // QUEUED (the platform sends it), UNKNOWN (it may have gone out; never again)
        };
        return new Sent(r, row.records(), snap.status());
    }

    /** It reached him or may have, and Meta's id is not known (yet): answered, never sent a second time. */
    private static DeliveryResult onItsWay(String providerMessageId) {
        return new DeliveryResult(DeliveryStatus.SENT, providerMessageId, null);
    }

    /** The {@code /send} body (class comment). */
    String request(Line line, String key, Timeline t, String retryOf) {
        Map<String, Object> r = line.anonymous() ? flow.newAnonymousRequest(line.phone())
                : flow.newRequest(line.father(), line.phone());
        r.put("idempotencyKey", key);
        r.put("kind", line.kind());
        r.put("messageClass", "SERVICE");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", formatter.enforceCharacterLimit(formatter.applyWhatsAppMarkdown(line.text())));
        r.put("content", content);
        r.put("windowPolicy", "SESSION_ONLY");
        r.put("outsideWindowWithoutTemplate", "FAIL");
        Instant now = clock.instant();
        Instant inbound = line.inboundAt() == null || line.inboundAt().isAfter(now) ? now : line.inboundAt();
        r.put("windowHint", Map.of("lastInboundAt", inbound.toString()));
        r.put("holdPolicy", "GATEWAY");
        Map<String, Object> timeline = new LinkedHashMap<>();
        boolean records = t.records() && !line.anonymous();
        timeline.put("record", records);
        if (records) {
            if (t.correlationId() != null) {
                timeline.put("correlationId", t.correlationId());
            }
            timeline.put("mode", t.mode());
            if (t.turnCorrelationId() != null) {
                timeline.put("turnCorrelationId", t.turnCorrelationId());
            }
            if (!t.supersededTurns().isEmpty()) {
                timeline.put("supersededTurns", t.supersededTurns());
            }
            if (t.content() != null) {
                timeline.put("content", t.content());
            }
        }
        r.put("timeline", timeline);
        if (retryOf != null) {
            r.put("retryOf", retryOf);
        }
        r.put("wait", "ACCEPTED");
        if (line.anonymous()) {
            r.put("sensitive", true);
        }
        return flow.json(r);
    }
}
