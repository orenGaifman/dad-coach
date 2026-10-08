package com.dadcoach.whatsapp.inbound;

import com.dadcoach.api.error.PlatformUnavailableException;
import com.dadcoach.api.tools.DashboardTools;
import com.dadcoach.auth.LoginLinkService;
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
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.replies.ClaimGuard;
import com.dadcoach.replies.CoachMentions;
import com.dadcoach.replies.TurnLedger;
import com.dadcoach.whatsapp.ReplyLanguageGuard;
import com.dadcoach.whatsapp.ReplyStyleGuard;
import java.util.List;
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
    private final TurnLedger ledger;
    private final CoachMentions mentions;
    private final ChildRepository children;
    private com.dadcoach.auth.LoginLinkRepository loginLinks;
    private LoginLinkService loginLinkService;

    /** B-4: the reply was not Hebrew - the coach writes it again once, told why (as Tair's HebrewOnly). */
    static final String HEBREW_RETRY_NOTE = "[הודעת מערכת, לא מהאבא: התשובה הקודמת שלך לא נשלחה כי לא הייתה בעברית. "
            + "כתוב אותה שוב - אותו תוכן, בעברית בלבד, בלי מילה באנגלית. אל תקרא שוב לכלי שכבר הצליח.]";
    /** B-4: still not Hebrew - one short line, never silence. */
    static final String NOT_HEBREW_FALLBACK = IDENTITY + "סליחה, התבלבלתי בניסוח.\nאפשר לכתוב לי שוב מה צריך?";
    /** D-036: a reply said a button was sent and none could be - said honestly. */
    static final String BUTTON_FAILED_LINE = "לא הצלחתי לשלוח עכשיו כפתור לדף שלך. אפשר לבקש שוב עוד כמה דקות.";

    public InboundMessageHandler(FatherRepository fathers, WhatsAppEndpoints endpoints, DeletedSenders deletedSenders,
                                 WhatsAppDeletionRequests deletionRequests, WorkflowPlatformClient platform,
                                 WorkflowPlatformProperties platformProperties, WhatsAppAdapter whatsapp,
                                 WhatsAppInboundRateLimiter rateLimiter, SessionButtonTaps buttonTaps,
                                 SentMessageRecorder recorder, VoiceNotes voiceNotes, Clock clock, TurnLedger ledger,
                                 CoachMentions mentions, ChildRepository children) {
        this.ledger = ledger;
        this.mentions = mentions;
        this.children = children;
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
        TurnLedger.Snapshot before = ledger.before(father);
        Instant platformStart = clock.instant();
        Instant deliveryStart = platformStart;
        String outcome = "REPLIED";
        try {
            WorkerExecuteResponse response = platform.execute(turn(in, phone, in.idempotencyKey(),
                    heard == null ? text : VoiceNoteReplies.TURN_NOTE + text, metadata, father));
            deliveryStart = clock.instant();
            String reply = response.responseContent();
            if (response.suppressed() || response.isDuplicate() || reply == null || reply.isBlank()) {
                outcome = response.suppressed() ? "SUPPRESSED" : response.isDuplicate() ? "DUPLICATE" : "BLANK";
                return;
            }
            Optional<String> hebrew = ReplyLanguageGuard.clean(reply.strip());
            if (hebrew.isEmpty()) {
                log.atWarn().setMessage("whatsapp.reply.blocked_not_hebrew").addKeyValue("correlationId", in.idempotencyKey())
                        .addKeyValue("chars", reply.length()).log();
                // B-4: never silence - the coach writes it again once, then a short Hebrew line
                WorkerExecuteResponse again = platform.execute(turn(in, phone, in.idempotencyKey() + ":he",
                        HEBREW_RETRY_NOTE, metadata, father));
                hebrew = again.suppressed() || again.responseContent() == null || again.responseContent().isBlank()
                        ? Optional.empty() : ReplyLanguageGuard.clean(again.responseContent().strip());
                if (hebrew.isEmpty()) {
                    outcome = "BLOCKED_NOT_HEBREW";
                    send(phone, VoiceNoteReplies.withHeard(NOT_HEBREW_FALLBACK, heard));
                    return;
                }
                outcome = "REWRITTEN_IN_HEBREW";
            } else if (!hebrew.get().equals(reply.strip())) {
                log.atWarn().setMessage("whatsapp.reply.english_note_removed").addKeyValue("correlationId", in.idempotencyKey()).log();
            }
            Optional<Father> now = father.isPresent() ? father : fathers.findByPhone(phone);
            if (now.isPresent() && isOnlyTheSentLine(hebrew.get())) {
                Optional<String> instead = theCardInsteadOfTheLine(now.get(), platformStart);
                outcome = "DASHBOARD_CARD";
                instead.ifPresent(line -> send(phone, VoiceNoteReplies.withHeard(line, heard)));
                return;
            }
            String checked = checkClaims(hebrew.get(), before, now, phone, platformStart, in.idempotencyKey());
            String out = ReplyStyleGuard.clean(VoiceNoteReplies.withHeard(checked, heard));
            send(phone, out);
            now.ifPresent(f -> mentions.sent(f, out));
            if (!checked.equals(hebrew.get())) {
                outcome = "CLAIM_CORRECTED";
                // the father saw the corrected text: his AI conversation gets it too, so the next turn builds on it
                now.ifPresent(f -> recorder.recordSent(f, out, in.idempotencyKey() + ":corrected"));
            }
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

    private WorkerExecuteRequest turn(InboundMessageDto in, String phone, String correlationId, String content,
                                      Map<String, Object> metadata, Optional<Father> father) {
        return new WorkerExecuteRequest(platformProperties.getWorkerKey(), PersonRefs.whatsappId(phone), "whatsapp",
                correlationId, in.messageType() == MessageType.INTERACTIVE ? "button_reply" : "text", content, metadata,
                platformProperties.getTenantId(), father.map(f -> PersonRefs.of(f.getId())).orElse(null),
                father.map(Father::getDisplayName).orElse(null), platformProperties.getWorkflowKey());
    }

    /**
     * D-034 (D-2): the reply may confirm only what this turn really did ({@link ClaimGuard}); a button it says was sent
     * is sent now, so the sentence is true - or the reply says it could not be.
     */
    private String checkClaims(String reply, TurnLedger.Snapshot before, Optional<Father> father, String phone,
                               Instant turnStarted, String correlationId) {
        try {
            String identity = reply.startsWith(IDENTITY) ? IDENTITY : "";
            String body = reply.substring(identity.length());
            TurnLedger.Changes changes = ledger.after(before, father);
            boolean buttonSent = loginLinks != null && father.isPresent()
                    && loginLinks.sentToFatherSince(father.get().getId(), turnStarted);
            List<String> names = father.map(f -> children.findByFatherIdAndStatus(f.getId(), "ACTIVE").stream()
                    .map(com.dadcoach.domain.child.Child::getName).toList()).orElse(List.of());
            ClaimGuard.Result result = ClaimGuard.check(body, changes, buttonSent, names);
            String checked = result.body();
            if (result.needsButton() && father.isPresent() && loginLinkService != null) {
                // D-035 in a longer reply: the card it speaks of is sent now (or is still on his screen)
                var sent = loginLinkService.sendTo(phone);
                log.atInfo().setMessage("whatsapp.reply.button_sent_for_claim").addKeyValue("outcome", sent).log();
                if (sent == LoginLinkService.SendOutcome.NOT_ALLOWED || sent == LoginLinkService.SendOutcome.FAILED) {
                    checked = ClaimGuard.withoutButtonClaims(checked) + "\n" + BUTTON_FAILED_LINE;
                }
            } else if (result.needsButton()) {
                checked = ClaimGuard.withoutButtonClaims(checked) + "\n" + BUTTON_FAILED_LINE;
            }
            if (result.changed()) {
                log.atWarn().setMessage("whatsapp.reply.claim_corrected").addKeyValue("correlationId", correlationId).log();
            }
            return identity + checked.strip();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("whatsapp.reply.claim_check_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
            return reply;
        }
    }


    /**
     * The coach's reply only says "I sent you the button". The button message is the whole answer (owner, 2026-10-08),
     * so the line never goes. When the coach said it without calling dad_dashboard_link (prod simulate: 7 in 10 - he
     * copies his earlier replies), the button goes out from here, so what he said is true. Returns the line to send
     * instead of the card, or empty when the card is the answer.
     */
    private Optional<String> theCardInsteadOfTheLine(Father father, Instant turnStarted) {
        if (loginLinks == null || loginLinkService == null) {
            return Optional.of(IDENTITY + DashboardTools.ON_SCREEN_REPLY);
        }
        if (loginLinks.sentToFatherSince(father.getId(), turnStarted)) {
            return Optional.empty();
        }
        LoginLinkService.SendOutcome sent = loginLinkService.sendTo(father.getPhone());
        log.atInfo().setMessage("whatsapp.reply.dashboard_card_from_line").addKeyValue("delivery", sent.name()).log();
        return switch (sent) {
            case SENT -> Optional.empty();
            case ALREADY_SENT, RATE_LIMITED -> Optional.of(IDENTITY + DashboardTools.ON_SCREEN_REPLY);
            case NOT_ALLOWED -> Optional.of(IDENTITY + "הדף שלך לא זמין כרגע.");
            case FAILED -> Optional.of(IDENTITY + "לא הצלחתי לשלוח את הכפתור כרגע. נסה שוב עוד כמה דקות.");
        };
    }

    /**
     * Only "I sent you the button", in whatever words: every word is one of {@link #SENT_LINE_WORDS}. The coach words it
     * his own way ("שלחתי לך כפתור לדף שלך 😊" - prod 2026-10-08 - slipped past an exact match), so the words count,
     * not the sentence. A reply that says anything else (a child to fix under ילדים, the calendar) still goes.
     */
    static boolean isOnlyTheSentLine(String reply) {
        String r = reply == null ? "" : reply.strip();
        r = r.startsWith(IDENTITY) ? r.substring(IDENTITY.length()).strip() : r;  // the platform puts the identity line on top
        String[] words = r.replaceAll("[^\\p{L}\\p{N}]+", " ").strip().split(" ");
        if (words.length == 0 || words[0].isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (!SENT_LINE_WORDS.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static final java.util.Set<String> SENT_LINE_WORDS = java.util.Set.of(
            "שלחתי", "שלחנו", "נשלח", "נשלחה", "שולח", "הנה", "לך", "אליך", "את", "זה", "הוא", "והוא", "כבר", "עכשיו", "פה",
            "כאן", "עוד", "רגע", "בדרך", "כפתור", "הכפתור", "קישור", "הקישור", "לינק", "הלינק", "לדף", "הדף", "דף", "לעמוד",
            "העמוד", "שלך", "האישי", "האישית", "בהודעה", "הודעה", "נפרדת", "בנפרד", "למעלה", "מעל", "ללוח", "לוח", "הבקרה",
            "לדשבורד", "דשבורד", "הדשבורד", "בדאד", "דאד", "קואץ", "קואץ׳", "ממשיך", "ימשיך", "לעבוד", "עובד", "תמיד",
            "בכל", "פעם", "מתי", "שתרצה", "שוב", "לחזור", "אפשר", "תוכל", "להיכנס", "להכנס", "כניסה", "דרכו", "בו", "עליו",
            "לחץ", "תלחץ", "לחיצה", "אחת", "ותגיע", "תגיע", "תיהנה", "בהנאה", "וגם");

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLoginLinks(com.dadcoach.auth.LoginLinkRepository loginLinks) {
        this.loginLinks = loginLinks;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLoginLinkService(LoginLinkService loginLinkService) {
        this.loginLinkService = loginLinkService;
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
