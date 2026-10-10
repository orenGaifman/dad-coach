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
import com.dadcoach.integration.platform.SessionTimers;
import com.dadcoach.integration.platform.WorkerExecuteRequest;
import com.dadcoach.integration.platform.WorkerExecuteResponse;
import com.dadcoach.integration.platform.WorkflowPlatformClient;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.lifecycle.DeletedSenders;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.dadcoach.integration.platform.lifecycle.WhatsAppDeletionRequests;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.integration.platform.timeline.TimelineText;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.replies.ClaimGuard;
import com.dadcoach.replies.CoachReplies;
import com.dadcoach.replies.ReadyQuestions;
import com.dadcoach.replies.TimerClaims;
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
 *   <li>the reply goes out as written; a SUPPRESSED or blank reply sends nothing; the platform down or refusing: one
 *       short Hebrew line, never English, never silence;</li>
 *   <li>D-038 (DC-B4): a duplicate answer (the platform's answer to a retried correlation id) carries the reply the
 *       first attempt wrote: it goes out once - the durable guard {@link #REPLY_SCOPE}/&lt;Meta's message id&gt; is taken
 *       before any reply to this message is sent, and released only when that send failed. A duplicate with no content
 *       sends nothing.</li>
 * </ol>
 *
 * <p>D-038 (DC-B3): {@link #handle} says whether the message was answered. {@link Outcome#UNANSWERED} - the platform
 * was unavailable (after the client's retries) or Meta refused a reply - lets the webhook release its claim, so Meta's
 * redelivery of the message is processed again instead of being dropped as a duplicate.
 *
 * <p>D-039 (Phase 3.4, {@code workflow.platform.delivery-reports} on): what reached him is reported to the platform's
 * conversation ({@link TimelineReports}, after the send, never blocking it) - the turn's reply AS_IS, or what was sent
 * instead of it (replacing the draft), or DROPPED; a message answered without a turn as his message plus the answer.
 * Path by path: docs/architecture/PHASE3_DADCOACH_SPEC.md.</p>
 */
@Component
public class InboundMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageHandler.class);
    /** D-038: one reply per inbound message, durably (tool_idempotency; the key is Meta's message id). */
    static final String REPLY_SCOPE = "WHATSAPP_REPLY";

    /** Whether the message was answered: HANDLED - done, a redelivery is dropped; UNANSWERED - process it again. */
    public enum Outcome { HANDLED, UNANSWERED }

    /** One message's processing: set when it could not be answered (a refused send, the platform unavailable). */
    static final class Attempt {
        boolean unanswered;
        /** Sends Meta accepted so far. */
        int sent;

        Outcome outcome() {
            return unanswered ? Outcome.UNANSWERED : Outcome.HANDLED;
        }

        /**
         * The processing threw. Something already reached him (a fixed line, a ready answer, a button reply, the AI
         * reply) and nothing failed: HANDLED - a redelivery must not send that line again. Nothing reached him, or a
         * send failed: UNANSWERED - the redelivery is processed again.
         */
        Outcome afterError() {
            return sent > 0 && !unanswered ? Outcome.HANDLED : Outcome.UNANSWERED;
        }
    }
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
    private final com.dadcoach.idempotency.IdempotencyService idempotency;
    private com.dadcoach.auth.LoginLinkRepository loginLinks;
    private LoginLinkService loginLinkService;
    private com.dadcoach.weeklyplan.WeeklyPlanContextBuilder weeklyPlan;
    private SessionTimers sessionTimers;
    private TimelineReports timeline;

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
                                 CoachMentions mentions, ChildRepository children,
                                 com.dadcoach.idempotency.IdempotencyService idempotency) {
        this.idempotency = idempotency;
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

    public Outcome handle(InboundMessageDto in, Instant receivedAt) {
        Attempt attempt = new Attempt();
        try {
            process(in, receivedAt, attempt);
        } catch (RuntimeException e) {
            Outcome outcome = attempt.afterError();
            log.atError().setMessage("whatsapp.turn.failed").addKeyValue("messageId", in.idempotencyKey())
                    .addKeyValue("sent", attempt.sent).addKeyValue("outcome", outcome).setCause(e).log();
            return outcome;
        }
        return attempt.outcome();
    }

    private void process(InboundMessageDto in, Instant receivedAt, Attempt attempt) {
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
                Optional<Father> known = fathers.findByPhone(phone);
                known.ifPresent(endpoints::recordInbound);
                String line = VoiceNoteReplies.notHeard(outcome);
                DeliveryResult sent = send(attempt, phone, line);
                reportAnswered(in, known, TimelineText.marker(MessageType.AUDIO), in.idempotencyKey() + ":reply", line, sent,
                        TimelineReports.KIND_FIXED_LINE);
                return;
            }
        }
        if (WhatsAppDeletionRequests.isRequest(text)) {
            if (heard != null) {
                // deleting everything cannot be undone - it never rests on a machine transcription; he types it
                String line = VoiceNoteReplies.withHeard(SPOKEN_DELETION_REPLY, heard);
                DeliveryResult sent = send(attempt, phone, line);
                reportAnswered(in, fathers.findByPhone(phone), VoiceNoteReplies.TURN_NOTE + heard,
                        in.idempotencyKey() + ":reply", line, sent, TimelineReports.KIND_FIXED_LINE);
                return;
            }
            // D-039: never reported - he is deleted now and the platform deletes his conversations; a report could
            // open a new one
            send(attempt, phone, deletionRequests.handle(phone));
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
            String line = in.messageType() != MessageType.AUDIO && voiceNotes.active() ? FILE_REPLY : MEDIA_REPLY;
            DeliveryResult sent = send(attempt, phone, line);
            reportAnswered(in, father, TimelineText.marker(in.messageType()), in.idempotencyKey() + ":reply", line, sent,
                    TimelineReports.KIND_FIXED_LINE);
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
                DeliveryResult sent = send(attempt, phone, PLATFORM_DOWN_REPLY);
                reportAnswered(in, father, in.textContent(), in.idempotencyKey(), PLATFORM_DOWN_REPLY, sent,
                        TimelineReports.KIND_FIXED_LINE);
                return;
            }
            if (tap.reply() != null) {
                DeliveryResult sent = send(attempt, phone, tap.reply());
                if (reporting()) {
                    reportAnswered(in, father, in.textContent(), in.idempotencyKey(), tap.reply(), sent,
                            TimelineReports.KIND_BUTTON_REPLY);
                } else {
                    recorder.recordSent(father.get(), tap.reply(), in.idempotencyKey());
                }
                return;
            }
            if (tap.coachText() != null) {
                text = tap.coachText();
            }
        }
        if (father.isPresent() && in.buttonId() == null && answeredFromData(in, father.get(), heard != null ? heard : text, heard, attempt)) {
            return;
        }
        runTurn(in, text, heard, father, receivedAt, started, attempt);
    }

    /** @param heard the words of the voice note this message was (D-029), or null for typed text */
    private void runTurn(InboundMessageDto in, String text, String heard, Optional<Father> father, Instant receivedAt,
                         Instant started, Attempt attempt) {
        String phone = in.fatherChannelIdentity();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timezone", FatherTimezones.of(father.orElse(null)).getId());
        father.ifPresent(f -> metadata.put("productUserId", String.valueOf(f.getId())));
        TurnLedger.Snapshot before = ledger.before(father);
        Instant platformStart = clock.instant();
        Instant deliveryStart = platformStart;
        String outcome = "REPLIED";
        boolean replyGuarded = false;
        int sentAtGuard = 0;
        String cid = in.idempotencyKey();
        // D-039: the turns whose rows the delivered text supersedes (the Hebrew rewrite), and whether that turn ran
        List<String> superseded = new java.util.ArrayList<>();
        boolean rewriting = false;
        try {
            WorkerExecuteResponse response = platform.execute(turn(in, phone, cid,
                    heard == null ? text : VoiceNoteReplies.TURN_NOTE + text, metadata, father, null));
            deliveryStart = clock.instant();
            String reply = response.responseContent();
            if (response.suppressed() || reply == null || reply.isBlank()) {
                outcome = response.suppressed() ? "SUPPRESSED" : response.isDuplicate() ? "DUPLICATE" : "BLANK";
                return;
            }
            // D-038 (DC-B4): a duplicate answer carries the reply of the attempt whose answer was lost - it goes out,
            // but only once per inbound message, whatever retries or redeliveries follow
            if (!idempotency.firstTime(REPLY_SCOPE, cid)) {
                outcome = response.isDuplicate() ? "DUPLICATE" : "ALREADY_REPLIED";
                return;
            }
            replyGuarded = true;
            sentAtGuard = attempt.sent;
            if (response.isDuplicate()) {
                outcome = "DUPLICATE_REPLAYED";
                log.atInfo().setMessage("whatsapp.reply.duplicate_replayed").addKeyValue("correlationId", cid).log();
            }
            Optional<String> hebrew = ReplyLanguageGuard.clean(reply.strip());
            if (hebrew.isEmpty()) {
                log.atWarn().setMessage("whatsapp.reply.blocked_not_hebrew").addKeyValue("correlationId", cid)
                        .addKeyValue("chars", reply.length()).log();
                // B-4: never silence - the coach writes it again once, then a short Hebrew line
                rewriting = true;
                if (reporting()) {
                    // D-039: the rewrite note is Dad Coach's instruction, never his words; that turn's rows give way to
                    // what is delivered on this one
                    superseded.add(cid + ":he");
                }
                WorkerExecuteResponse again = platform.execute(turn(in, phone, cid + ":he",
                        HEBREW_RETRY_NOTE, metadata, father, reporting() ? Boolean.TRUE : null));
                hebrew = again.suppressed() || again.responseContent() == null || again.responseContent().isBlank()
                        ? Optional.empty() : ReplyLanguageGuard.clean(again.responseContent().strip());
                if (hebrew.isEmpty()) {
                    outcome = "BLOCKED_NOT_HEBREW";
                    String line = VoiceNoteReplies.withHeard(NOT_HEBREW_FALLBACK, heard);
                    DeliveryResult sent = send(attempt, phone, line);
                    reportReplaced(phone, father, cid, line, sent, TimelineReports.KIND_FIXED_LINE, superseded);
                    return;
                }
                outcome = outcome.equals("DUPLICATE_REPLAYED") ? outcome : "REWRITTEN_IN_HEBREW";
            } else if (!hebrew.get().equals(reply.strip())) {
                log.atWarn().setMessage("whatsapp.reply.english_note_removed").addKeyValue("correlationId", cid).log();
            }
            Optional<Father> now = father.isPresent() ? father : fathers.findByPhone(phone);
            if (now.isPresent() && isOnlyTheSentLine(hebrew.get())) {
                Optional<String> instead = theCardInsteadOfTheLine(now.get(), platformStart, cid);
                outcome = "DASHBOARD_CARD";
                if (instead.isPresent()) {
                    String line = VoiceNoteReplies.withHeard(instead.get(), heard);
                    DeliveryResult sent = send(attempt, phone, line);
                    reportReplaced(phone, now, cid, line, sent, TimelineReports.KIND_FIXED_LINE, superseded);
                } else if (reporting()) {
                    // the card (reported where it is sent) is the answer: nothing of the reply went out
                    timeline.turnOutcome(TimelineReports.Person.of(phone, now), cid, TimelineReports.DROPPED, null,
                            "SENT_LINE_CARD", superseded);
                }
                return;
            }
            String checked = checkClaims(hebrew.get(), before, now, phone, platformStart, cid);
            String out = ReplyStyleGuard.clean(VoiceNoteReplies.withHeard(checked, heard));
            DeliveryResult sent = send(attempt, phone, out);
            now.ifPresent(f -> mentions.sent(f, out));
            if (!checked.equals(hebrew.get())) {
                outcome = "CLAIM_CORRECTED";
            }
            if (reporting()) {
                // D-039: as the platform wrote it -> AS_IS; anything else replaces the draft (no ":corrected" row)
                if (superseded.isEmpty() && !rewriting && TimelineText.sameAsDraft(out, reply)) {
                    if (sent.isSuccessful()) {
                        timeline.turnOutcome(TimelineReports.Person.of(phone, now), cid, TimelineReports.AS_IS, sent, null,
                                null);
                    }
                } else {
                    reportReplaced(phone, now, cid, out, sent, TimelineReports.KIND_REPLY, superseded);
                }
            } else if (!checked.equals(hebrew.get())) {
                // the father saw the corrected text: his AI conversation gets it too, so the next turn builds on it
                now.ifPresent(f -> recorder.recordSent(f, out, cid + ":corrected"));
            }
        } catch (PlatformUnavailableException | WorkflowPlatformClient.PlatformRejectedException e) {
            outcome = "PLATFORM_FAILED";
            log.atWarn().setMessage("whatsapp.turn.platform_failed").addKeyValue("error", e.getMessage()).log();
            deliveryStart = clock.instant();
            // D-038 (DC-B3): unavailable (5xx after retries, timeout, circuit open) - the message was not processed and
            // a redelivery runs it again; a refusal (4xx) is the platform's answer and would be the same again
            attempt.unanswered |= e instanceof PlatformUnavailableException;
            String line = VoiceNoteReplies.withHeard(PLATFORM_DOWN_REPLY, heard);
            DeliveryResult sent = send(attempt, phone, line);
            if (rewriting && !(e instanceof PlatformUnavailableException)) {
                // D-039: the turn's reply exists (English, not sent) and the platform refused the rewrite for good: the
                // line went instead of it (when unavailable, the redelivery answers and reports)
                reportReplaced(phone, father.isPresent() ? father : fathers.findByPhone(phone), cid, line, sent,
                        TimelineReports.KIND_FIXED_LINE, superseded);
            }
        } catch (RuntimeException e) {
            if (replyGuarded && attempt.sent == sentAtGuard) {
                // failed before anything reached him: the redelivery (the webhook releases its claim) may send it
                idempotency.forget(REPLY_SCOPE, in.idempotencyKey());
            }
            replyGuarded = false;
            throw e;
        } finally {
            if (replyGuarded && attempt.unanswered) {
                // the reply never reached him: the redelivery DC-B3 lets through may send it
                idempotency.forget(REPLY_SCOPE, in.idempotencyKey());
            }
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
     * D-036: "מה יש לי השבוע?", "איך אני עומד?", "מתי התזכורת?" as the whole message - answered from the weekly plan of
     * this moment, before any AI turn (the model listed a session cancelled on his page from its own history), and
     * recorded in his conversation so the next turn knows it.
     */
    private boolean answeredFromData(InboundMessageDto in, Father father, String words, String heard, Attempt attempt) {
        Optional<ReadyQuestions.Kind> kind = ReadyQuestions.of(words);
        if (kind.isEmpty() || weeklyPlan == null) {
            return false;
        }
        int sentBefore = attempt.sent;
        try {
            // D-037: "מתי התזכורת?" is answered from the timers the platform holds (one read), never from the policy
            com.dadcoach.weeklyplan.ArmedTimers armed = kind.get() == ReadyQuestions.Kind.REMINDER && sessionTimers != null
                    ? sessionTimers.armed(father) : null;
            Map<String, Object> plan = weeklyPlan.build(father, armed);
            if (!(plan.get("ready_replies") instanceof Map<?, ?> ready) || !(ready.get(kind.get().key) instanceof List<?> lines)
                    || children.findByFatherIdAndStatus(father.getId(), "ACTIVE").isEmpty()) {
                return false;
            }
            StringBuilder answer = new StringBuilder(IDENTITY);
            answer.append(String.join("\n", lines.stream().map(String::valueOf).toList()));
            CoachReplies.Week week = CoachReplies.Week.of(plan.get("coverage"));
            if (kind.get() != ReadyQuestions.Kind.REMINDER && week != null && week.hasGoal() && !week.isCovered()) {
                answer.append("\n\nרוצה שנמצא עוד זמן השבוע?");
            }
            String out = ReplyStyleGuard.clean(VoiceNoteReplies.withHeard(answer.toString(), heard));
            DeliveryResult sent = send(attempt, in.fatherChannelIdentity(), out);
            if (reporting()) {
                // D-039: his question (as a turn would have read it), then the answer
                reportAnswered(in, Optional.of(father), heard != null ? VoiceNoteReplies.TURN_NOTE + heard : words,
                        in.idempotencyKey(), out, sent, TimelineReports.KIND_READY_ANSWER);
            } else {
                recorder.recordSent(father, out, in.idempotencyKey());
            }
            mentions.sent(father, out);
            log.atInfo().setMessage("whatsapp.reply.ready_answer").addKeyValue("kind", kind.get().name()).log();
            return true;
        } catch (RuntimeException e) {
            log.atWarn().setMessage("whatsapp.reply.ready_answer_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
            // D-038: once the ready answer reached him it is the answer - never a second (AI) reply after it
            return attempt.sent > sentBefore;
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setWeeklyPlan(com.dadcoach.weeklyplan.WeeklyPlanContextBuilder weeklyPlan) {
        this.weeklyPlan = weeklyPlan;
    }

    /** @param internal D-039: true for Dad Coach's own instruction (the Hebrew rewrite); null - never on the wire */
    private WorkerExecuteRequest turn(InboundMessageDto in, String phone, String correlationId, String content,
                                      Map<String, Object> metadata, Optional<Father> father, Boolean internal) {
        return new WorkerExecuteRequest(platformProperties.getWorkerKey(), PersonRefs.whatsappId(phone), "whatsapp",
                correlationId, in.messageType() == MessageType.INTERACTIVE ? "button_reply" : "text", content, metadata,
                platformProperties.getTenantId(), father.map(f -> PersonRefs.of(f.getId())).orElse(null),
                father.map(Father::getDisplayName).orElse(null), platformProperties.getWorkflowKey(), internal);
    }

    // ---- D-039: delivery reports to the platform's conversation (TimelineReports) ------------------------------------

    private boolean reporting() {
        return timeline != null && timeline.enabled();
    }

    /**
     * A message answered without a turn: his message, then what was sent - for a known father (a stranger's number never
     * gets a conversation from a report) and only once it reached him (a refused send is redelivered and reported then).
     */
    private void reportAnswered(InboundMessageDto in, Optional<Father> father, String inboundContent,
                                String outCorrelationId, String sentText, DeliveryResult sent, String kind) {
        if (!reporting() || father.isEmpty() || sent == null || !sent.isSuccessful()) {
            return;
        }
        TimelineReports.Person person = TimelineReports.Person.of(father.get());
        String type = in.messageType() == MessageType.AUDIO || inboundContent != null
                && inboundContent.startsWith(VoiceNoteReplies.TURN_NOTE) ? "audio" : TimelineText.messageType(in.messageType());
        timeline.inbound(person, in.idempotencyKey(), inboundContent, type, in.buttonId(),
                in.buttonId() == null ? null : in.textContent());
        timeline.outbound(person, TimelineReports.Part.sent(outCorrelationId, TimelineText.withoutIdentity(sentText), sent,
                kind));
    }

    /** What was sent instead of the turn's reply: it replaces the draft (and the superseded turns' rows). */
    private void reportReplaced(String phone, Optional<Father> father, String cid, String sentText, DeliveryResult sent,
                                String kind, List<String> superseded) {
        if (!reporting() || sent == null || !sent.isSuccessful()) {
            return;
        }
        timeline.outbound(TimelineReports.Person.of(phone, father), TimelineReports.Part.replacing(cid + ":delivered", cid,
                TimelineText.withoutIdentity(sentText), sent, kind, superseded));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeline(TimelineReports timeline) {
        this.timeline = timeline;
    }

    /**
     * D-036 (D-2): the reply may confirm only what this turn really did ({@link ClaimGuard}); a button it says was sent
     * is sent now, so the sentence is true - or the reply says it could not be.
     */
    private String checkClaims(String reply, TurnLedger.Snapshot before, Optional<Father> father, String phone,
                               Instant turnStarted, String correlationId) {
        try {
            String identity = reply.startsWith(IDENTITY) ? IDENTITY : "";
            String body = reply.substring(identity.length());
            TurnLedger.Changes changes = ledger.after(before, father);
            // D-037: the turn is over (the platform released the conversation) - its sessions' timers are confirmed with
            // the platform now, missing ones armed; the reply promises only what the platform holds
            TimerCheck timers = confirmTimers(father, changes);
            if (timers.single() != null) {
                changes = changes.withBookingReply(TimerClaims.confirmation(changes.bookingReply(),
                        timers.single().planned(), timers.single().confirmed()));
            }
            boolean buttonSent = loginLinks != null && father.isPresent()
                    && loginLinks.sentToFatherSince(father.get().getId(), turnStarted);
            List<String> names = father.map(f -> children.findByFatherIdAndStatus(f.getId(), "ACTIVE").stream()
                    .map(com.dadcoach.domain.child.Child::getName).toList()).orElse(List.of());
            ClaimGuard.Result result = ClaimGuard.check(body, changes, buttonSent, names);
            String checked = result.body();
            if (result.needsButton() && father.isPresent() && loginLinkService != null) {
                // D-035 in a longer reply: the card it speaks of is sent now (or is still on his screen)
                var sent = loginLinkService.sendTo(phone, correlationId);
                log.atInfo().setMessage("whatsapp.reply.button_sent_for_claim").addKeyValue("outcome", sent).log();
                if (sent == LoginLinkService.SendOutcome.NOT_ALLOWED || sent == LoginLinkService.SendOutcome.FAILED) {
                    checked = ClaimGuard.withoutButtonClaims(checked) + "\n" + BUTTON_FAILED_LINE;
                }
            } else if (result.needsButton()) {
                checked = ClaimGuard.withoutButtonClaims(checked) + "\n" + BUTTON_FAILED_LINE;
            }
            if (timers.applies()) {
                String aligned = TimerClaims.align(checked, timers.expectedLine(),
                        changes.bookingReply() == null || changes.bookingReply().isEmpty() ? null : changes.bookingReply().get(0));
                if (!ClaimGuard.words(aligned).equals(ClaimGuard.words(checked))) {
                    log.atWarn().setMessage("whatsapp.reply.timer_claims_corrected").addKeyValue("correlationId", correlationId)
                            .addKeyValue("allConfirmed", timers.single() != null && timers.single().complete()).log();
                }
                checked = aligned;
            }
            if (result.changed()) {
                log.atWarn().setMessage("whatsapp.reply.claim_corrected").addKeyValue("correlationId", correlationId).log();
            }
            return identity + checked.strip();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("whatsapp.reply.claim_check_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
            // D-037: unchecked, it may not promise a reminder the platform never confirmed
            String identity = reply.startsWith(IDENTITY) ? IDENTITY : "";
            return identity + TimerClaims.withoutTimerClaims(reply.substring(identity.length()));
        }
    }

    /**
     * D-037: what this turn's booked, moved or joined sessions really got. {@code single} is the confirmation of the one
     * session of a ready booking reply; {@code expectedLine} the only reminder sentence the reply may carry (null: none,
     * or - with several sessions all confirmed - the reply's own words stay); {@code applies} false when no session was
     * booked or moved.
     */
    record TimerCheck(boolean applies, SessionTimers.Confirmation single, String expectedLine) {
        static final TimerCheck NONE = new TimerCheck(false, null, null);
    }

    private TimerCheck confirmTimers(Optional<Father> father, TurnLedger.Changes changes) {
        if (father.isEmpty() || sessionTimers == null) {
            return TimerCheck.NONE;
        }
        for (java.util.UUID closed : changes.closed()) {
            sessionTimers.cancel(father.get(), closed);
        }
        if (!changes.sessionBookedOrMoved()) {
            return TimerCheck.NONE;
        }
        List<com.dadcoach.qualitytime.QualityTime> sessions = new java.util.ArrayList<>(changes.booked());
        sessions.addAll(changes.joined());
        List<SessionTimers.Confirmation> confirmations = sessionTimers.ensure(father.get(), sessions);
        if (changes.bookingReply() != null && confirmations.size() == 1) {
            SessionTimers.Confirmation one = confirmations.get(0);
            return new TimerCheck(true, one, TimerClaims.line(one.planned(), one.confirmed()));
        }
        if (confirmations.isEmpty()) {
            return TimerCheck.NONE;
        }
        String honest = TimerClaims.lineForMany(confirmations);
        // all confirmed: the reply's own reminder words are true and stay
        return honest == null ? TimerCheck.NONE : new TimerCheck(true, null, honest);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSessionTimers(SessionTimers sessionTimers) {
        this.sessionTimers = sessionTimers;
    }


    /**
     * The coach's reply only says "I sent you the button". The button message is the whole answer (owner, 2026-10-08),
     * so the line never goes. When the coach said it without calling dad_dashboard_link (prod simulate: 7 in 10 - he
     * copies his earlier replies), the button goes out from here, so what he said is true. Returns the line to send
     * instead of the card, or empty when the card is the answer.
     */
    private Optional<String> theCardInsteadOfTheLine(Father father, Instant turnStarted, String turnCorrelationId) {
        if (loginLinks == null || loginLinkService == null) {
            return Optional.of(IDENTITY + DashboardTools.ON_SCREEN_REPLY);
        }
        if (loginLinks.sentToFatherSince(father.getId(), turnStarted)) {
            return Optional.empty();
        }
        LoginLinkService.SendOutcome sent = loginLinkService.sendTo(father.getPhone(), turnCorrelationId);
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

    /**
     * A reply inside the conversation the father just opened (the 24-hour window is open by definition). A send Meta
     * (or the gateway's circuit) refused leaves the message unanswered (DC-B3).
     */
    private DeliveryResult send(Attempt attempt, String phone, String text) {
        DeliveryResult result = whatsapp.sendMessage(new OutboundMessageDto(UUID.randomUUID(), null, WhatsAppEndpoints.CHANNEL,
                MessageType.TEXT, text, null, false, null, Map.of(), MessagePriority.IMMEDIATE, clock.instant()), phone);
        log.atInfo().setMessage("whatsapp.delivery.result")
                .addKeyValue("kind", "CONVERSATIONAL_REPLY")
                .addKeyValue("status", result.status())
                .addKeyValue("failure", result.failureReason())
                .log();
        if (result.isSuccessful()) {
            attempt.sent++;
        } else {
            attempt.unanswered = true;
        }
        return result;
    }
}
