package com.dadcoach.whatsapp.inbound;

import com.dadcoach.api.error.PlatformUnavailableException;
import com.dadcoach.channel.WhatsAppEndpoints;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.common.MaskingUtils;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.FatherTimezones;
import com.dadcoach.integration.platform.SentMessageRecorder;
import com.dadcoach.integration.platform.WorkerExecuteRequest;
import com.dadcoach.integration.platform.WorkerExecuteResponse;
import com.dadcoach.integration.platform.WorkflowPlatformClient;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.lifecycle.DeletedSenders;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.dadcoach.integration.platform.lifecycle.WhatsAppDeletionRequests;
import com.dadcoach.whatsapp.ReplyLanguageGuard;
import com.dadcoach.whatsapp.WhatsAppAdapter;
import com.dadcoach.whatsapp.buttons.SessionButtonTaps;
import com.dadcoach.whatsapp.voice.VoiceNoteReplies;
import com.dadcoach.whatsapp.voice.VoiceNotes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One inbound WhatsApp message, in order (playbook §34):
 * <ol>
 *   <li>a DELETED father, or a number whose platform deletion is not confirmed yet: dropped, nothing reaches the AI;</li>
 *   <li>a voice note, when voice notes are on (D-029): heard (ElevenLabs) and from here on read like the same words
 *       typed - the reply opens with "🎙️ שמעתי: ..."; a note too long, silent or not heard gets its own line, no AI
 *       turn;</li>
 *   <li>"DELETE MY DATA": the deletion request, handled here, never by the AI;</li>
 *   <li>a known father: his WhatsApp endpoint is ensured and his 24-hour window opened (F1);</li>
 *   <li>a voice note (voice notes off) or a file without words: one fixed line (the coach reads text only);</li>
 *   <li>a tapped session button ("dc:done:&lt;id&gt;"...): handled by {@link SessionButtonTaps} - its fixed reply is
 *       sent and recorded in the conversation with no AI turn, or the turn runs with the text it hands over;</li>
 *   <li>the turn: worker + workflow named by the caller, correlation id = Meta's message id, the father's timezone,
 *       person ref and name in the request; a new number is onboarded by the workflow itself (D-002);</li>
 *   <li>the reply goes out as written; a SUPPRESSED, duplicate or blank reply sends nothing; the platform down or
 *       refusing: one short Hebrew line, never English, never silence.</li>
 * </ol>
 */
@Component
public class InboundMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageHandler.class);
    /** D-032: every fixed line opens with the identity line, like every other message on the shared number. */
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final String PLATFORM_DOWN_REPLY = IDENTITY + "משהו השתבש אצלי. נסה שוב עוד רגע.";
    /** The phrase to type sits on its own line, so it can be copied. */
    static final String SPOKEN_DELETION_REPLY = IDENTITY + "את מחיקת המידע אי אפשר לבטל, ולכן היא לא נעשית מהקלטה.\n"
            + "אם זה מה שאתה רוצה, כתוב לי במילים:\nמחק את המידע שלי";
    /** Voice notes off: neither a recording nor a file is understood. */
    static final String MEDIA_REPLY = IDENTITY + "אני עוד לא שומע הקלטות ולא רואה קבצים.\nאפשר לכתוב לי במילים?";
    /** Voice notes on: a recording is heard, so only a picture, a file or a sticker without words lands here. */
    static final String FILE_REPLY = IDENTITY + "אני עוד לא רואה תמונות וקבצים.\nאפשר לכתוב לי במילים?";

    private final FatherRepository fathers;
    private final WhatsAppEndpoints endpoints;
    private final DeletedSenders deletedSenders;
    private final WhatsAppDeletionRequests deletionRequests;
    private final WorkflowPlatformClient platform;
    private final WorkflowPlatformProperties platformProperties;
    private final WhatsAppAdapter whatsapp;
    private final WhatsAppInboundRateLimiter rateLimiter;
    private final SessionButtonTaps buttonTaps;
    private final SentMessageRecorder recorder;
    private final VoiceNotes voiceNotes;
    private final Clock clock;
    private com.dadcoach.auth.LoginLinkRepository loginLinks;

    public InboundMessageHandler(FatherRepository fathers, WhatsAppEndpoints endpoints, DeletedSenders deletedSenders,
                                 WhatsAppDeletionRequests deletionRequests, WorkflowPlatformClient platform,
                                 WorkflowPlatformProperties platformProperties, WhatsAppAdapter whatsapp,
                                 WhatsAppInboundRateLimiter rateLimiter, SessionButtonTaps buttonTaps,
                                 SentMessageRecorder recorder, VoiceNotes voiceNotes, Clock clock) {
        this.fathers = fathers;
        this.endpoints = endpoints;
        this.deletedSenders = deletedSenders;
        this.deletionRequests = deletionRequests;
        this.platform = platform;
        this.platformProperties = platformProperties;
        this.whatsapp = whatsapp;
        this.rateLimiter = rateLimiter;
        this.buttonTaps = buttonTaps;
        this.recorder = recorder;
        this.voiceNotes = voiceNotes;
        this.clock = clock;
    }

    public void handle(InboundMessageDto in, Instant receivedAt) {
        Instant started = clock.instant();
        String phone = in.fatherChannelIdentity();
        if (deletedSenders.isDeleted(phone)) {
            log.atInfo().setMessage("whatsapp.inbound.ignored").addKeyValue("reason", "DELETED_FATHER").log();
            return;
        }
        String text = in.textContent();
        // D-029: a voice note is heard, and from here on read like the same words typed - except a deletion request.
        String heard = null;
        boolean admitted = false;
        if (in.messageType() == MessageType.AUDIO && in.mediaId() != null && voiceNotes.active()) {
            // the rate limit comes before the transcription: a flood of notes never spends ElevenLabs credit
            if (!rateLimiter.tryAcquire(phone)) {
                log.atWarn().setMessage("whatsapp.inbound.rate_limited").addKeyValue("sender", MaskingUtils.maskPhone(phone)).log();
                return;
            }
            admitted = true;
            VoiceNotes.Outcome outcome = voiceNotes.listen(in.mediaId());
            log.atInfo().setMessage("whatsapp.inbound.voice").addKeyValue("outcome", outcome.getClass().getSimpleName())
                    .addKeyValue("messageId", in.idempotencyKey()).log();
            if (outcome instanceof VoiceNotes.Outcome.Heard h) {
                heard = h.text();
                text = heard;
            } else if (!(outcome instanceof VoiceNotes.Outcome.Off)) {
                fathers.findByPhone(phone).ifPresent(endpoints::recordInbound);
                send(phone, VoiceNoteReplies.notHeard(outcome));
                return;
            }
        }
        if (WhatsAppDeletionRequests.isRequest(text)) {
            if (heard != null) {
                // deleting everything cannot be undone - it never rests on a machine transcription; he types it
                send(phone, VoiceNoteReplies.withHeard(SPOKEN_DELETION_REPLY, heard));
                return;
            }
            send(phone, deletionRequests.handle(phone));
            return;
        }
        Optional<Father> father = fathers.findByPhone(phone);
        father.ifPresent(endpoints::recordInbound);
        if (in.messageType() == MessageType.REACTION) {
            // a ❤️ on one of our messages is not something to answer ("אתה איתי?" came back for one)
            log.atInfo().setMessage("whatsapp.inbound.ignored").addKeyValue("reason", "REACTION").log();
            return;
        }
        if (text == null || text.isBlank()) {
            send(phone, in.messageType() != MessageType.AUDIO && voiceNotes.active() ? FILE_REPLY : MEDIA_REPLY);
            return;
        }
        if (!admitted && !rateLimiter.tryAcquire(phone)) {
            log.atWarn().setMessage("whatsapp.inbound.rate_limited").addKeyValue("sender", MaskingUtils.maskPhone(phone)).log();
            return;
        }
        text = mediaMarker(in.messageType()) + text;
        if (in.buttonId() != null && father.isPresent()) {
            SessionButtonTaps.Tap tap;
            try {
                tap = buttonTaps.handle(father.get(), in.buttonId());
            } catch (RuntimeException e) {
                log.atWarn().setMessage("whatsapp.button.failed").addKeyValue("error", e.getClass().getSimpleName()).log();
                send(phone, PLATFORM_DOWN_REPLY);
                return;
            }
            if (tap.reply() != null) {
                send(phone, tap.reply());
                recorder.recordSent(father.get(), tap.reply(), in.idempotencyKey());
                return;
            }
            if (tap.coachText() != null) {
                text = tap.coachText();
            }
        }
        runTurn(in, text, heard, father, receivedAt, started);
    }

    /** @param heard the words of the voice note this message was (D-029), or null for typed text */
    private void runTurn(InboundMessageDto in, String text, String heard, Optional<Father> father, Instant receivedAt,
                         Instant started) {
        String phone = in.fatherChannelIdentity();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timezone", FatherTimezones.of(father.orElse(null)).getId());
        father.ifPresent(f -> metadata.put("productUserId", String.valueOf(f.getId())));
        Instant platformStart = clock.instant();
        Instant deliveryStart = platformStart;
        String outcome = "REPLIED";
        try {
            WorkerExecuteResponse response = platform.execute(new WorkerExecuteRequest(platformProperties.getWorkerKey(),
                    PersonRefs.whatsappId(phone), "whatsapp", in.idempotencyKey(),
                    in.messageType() == MessageType.INTERACTIVE ? "button_reply" : "text",
                    heard == null ? text : VoiceNoteReplies.TURN_NOTE + text, metadata,
                    platformProperties.getTenantId(), father.map(f -> PersonRefs.of(f.getId())).orElse(null),
                    father.map(Father::getDisplayName).orElse(null), platformProperties.getWorkflowKey()));
            deliveryStart = clock.instant();
            String reply = response.responseContent();
            if (response.suppressed() || response.isDuplicate() || reply == null || reply.isBlank()) {
                outcome = response.suppressed() ? "SUPPRESSED" : response.isDuplicate() ? "DUPLICATE" : "BLANK";
                return;
            }
            Optional<String> hebrew = ReplyLanguageGuard.clean(reply.strip());
            if (hebrew.isEmpty()) {
                outcome = "BLOCKED_NOT_HEBREW";
                log.atWarn().setMessage("whatsapp.reply.blocked_not_hebrew").addKeyValue("correlationId", in.idempotencyKey())
                        .addKeyValue("chars", reply.length()).log();
                return;
            }
            if (!hebrew.get().equals(reply.strip())) {
                log.atWarn().setMessage("whatsapp.reply.english_note_removed").addKeyValue("correlationId", in.idempotencyKey()).log();
            }
            if (cardSaysItAll(father, hebrew.get(), platformStart)) {
                outcome = "DASHBOARD_CARD";
                return;
            }
            send(phone, VoiceNoteReplies.withHeard(hebrew.get(), heard));
        } catch (PlatformUnavailableException | WorkflowPlatformClient.PlatformRejectedException e) {
            outcome = "PLATFORM_FAILED";
            log.atWarn().setMessage("whatsapp.turn.platform_failed").addKeyValue("error", e.getMessage()).log();
            deliveryStart = clock.instant();
            send(phone, VoiceNoteReplies.withHeard(PLATFORM_DOWN_REPLY, heard));
        } finally {
            Instant end = clock.instant();
            log.atInfo().setMessage("whatsapp.turn.timing")
                    .addKeyValue("correlationId", in.idempotencyKey())
                    .addKeyValue("fatherId", father.map(Father::getId).orElse(null))
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("queueWaitMs", Duration.between(receivedAt, started).toMillis())
                    .addKeyValue("preTurnMs", Duration.between(started, platformStart).toMillis())
                    .addKeyValue("platformMs", Duration.between(platformStart, deliveryStart).toMillis())
                    .addKeyValue("deliveryMs", Duration.between(deliveryStart, end).toMillis())
                    .log();
        }
    }

    /**
     * The button to his page went out in this turn and the coach's reply only says so: the button message is the whole
     * answer (owner, 2026-10-08). A reply with anything else in it still goes.
     */
    private boolean cardSaysItAll(Optional<Father> father, String reply, Instant turnStarted) {
        return loginLinks != null && father.isPresent() && isOnlyTheSentLine(reply)
                && loginLinks.sentToFatherSince(father.get().getId(), turnStarted);
    }

    static boolean isOnlyTheSentLine(String reply) {
        String r = reply == null ? "" : reply.strip();
        r = r.startsWith(IDENTITY) ? r.substring(IDENTITY.length()).strip() : r;  // the platform puts the identity line on top
        return r.equals(com.dadcoach.api.tools.DashboardTools.SENT_REPLY);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLoginLinks(com.dadcoach.auth.LoginLinkRepository loginLinks) {
        this.loginLinks = loginLinks;
    }

    /**
     * A photo with a caption reaches the coach as its caption only - without a marker the coach answers the words as if
     * they stood alone ("תראה מה בנינו!" got a weekly status). The marker tells it a picture came that it cannot see.
     */
    static String mediaMarker(MessageType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case IMAGE -> "[photo] ";
            case VIDEO -> "[video] ";
            case DOCUMENT -> "[file] ";
            default -> "";
        };
    }

    /** A reply inside the conversation the father just opened (the 24-hour window is open by definition). */
    private void send(String phone, String text) {
        DeliveryResult result = whatsapp.sendMessage(new OutboundMessageDto(UUID.randomUUID(), null, WhatsAppEndpoints.CHANNEL,
                MessageType.TEXT, text, null, false, null, Map.of(), MessagePriority.IMMEDIATE, clock.instant()), phone);
        log.atInfo().setMessage("whatsapp.delivery.result")
                .addKeyValue("kind", "CONVERSATIONAL_REPLY")
                .addKeyValue("status", result.status())
                .addKeyValue("failure", result.failureReason())
                .log();
    }
}
