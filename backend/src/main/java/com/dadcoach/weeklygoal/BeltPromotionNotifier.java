package com.dadcoach.weeklygoal;

import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.integration.platform.timeline.TimelineText;

import com.dadcoach.channel.WhatsAppEndpoints;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.integration.platform.SentMessageRecorder;

import com.dadcoach.config.BeltImageConfig;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.whatsapp.WhatsAppApiClient;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import com.dadcoach.workflow.Belt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Not used since D-030 (the weekly job no longer promotes); a belt is told where it is earned - the session's
 * completion reply (B-1, D-036). Kept for the job's wiring; its texts follow the vocabulary (💪 progress).
 *
 * Tells a father who earned a new belt (the weekly completion job): the belt image while his 24-hour window is open
 * (images cannot go in a template), then the congratulation text through the channel layer ({@link ProactiveSender}:
 * free-form in the window, the approved template outside it). A sent congratulation is recorded into his platform
 * conversation (playbook §10.2), so his reply to it is understood.
 */
@Service
public class BeltPromotionNotifier {

    private static final Logger log = LoggerFactory.getLogger(BeltPromotionNotifier.class);

    private final WhatsAppApiClient whatsAppApiClient;
    private final WhatsAppMessageFormatter messageFormatter;
    private final BeltImageConfig beltImageConfig;
    private final FatherRepository fatherRepository;
    private final ProactiveSender sender;
    private final SentMessageRecorder recorder;
    private final WhatsAppEndpoints endpoints;
    private final SessionWindowService sessionWindows;
    private TimelineReports timeline;

    public BeltPromotionNotifier(WhatsAppApiClient whatsAppApiClient, WhatsAppMessageFormatter messageFormatter,
                                 BeltImageConfig beltImageConfig, FatherRepository fatherRepository, ProactiveSender sender,
                                 SentMessageRecorder recorder, WhatsAppEndpoints endpoints, SessionWindowService sessionWindows) {
        this.whatsAppApiClient = whatsAppApiClient;
        this.messageFormatter = messageFormatter;
        this.beltImageConfig = beltImageConfig;
        this.fatherRepository = fatherRepository;
        this.sender = sender;
        this.recorder = recorder;
        this.endpoints = endpoints;
        this.sessionWindows = sessionWindows;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeline(TimelineReports timeline) {
        this.timeline = timeline;
    }

    public void sendPromotionNotification(WeeklyGoalService.BeltPromotionResult result) {
        if (!result.promoted()) {
            return;
        }
        Father father = fatherRepository.findById(result.fatherId()).orElse(null);
        if (father == null || father.getPhone() == null || father.getStatus() == com.dadcoach.father.FatherStatus.DELETED) {
            return;
        }
        Belt newBelt = result.newBelt();
        try {
            boolean windowOpen = sessionWindows.isOpen(endpoints.ensure(father));
            if (windowOpen && beltImageConfig.hasImage(newBelt)) {
                String caption = String.format("עלית ל*%s* 💪", newBelt.getDisplayName("he"));
                var image = whatsAppApiClient.sendMessage(messageFormatter.formatImageMessage(
                        father.getPhone(), beltImageConfig.getImageUrl(newBelt), caption));
                if (!image.success()) {
                    log.warn("Belt image not sent (text follows): fatherId={}", father.getId());
                }
            }
            String text = buildPromotionMessage(result);
            ProactiveSender.Outcome outcome = sender.send(father, text);
            log.atInfo().setMessage("whatsapp.delivery.result")
                    .addKeyValue("kind", "BELT_PROMOTION")
                    .addKeyValue("fatherId", father.getId())
                    .addKeyValue("mode", outcome.mode())
                    .addKeyValue("status", outcome.result().status())
                    .addKeyValue("failure", outcome.result().failureReason())
                    .log();
            if (outcome.result().isSuccessful()) {
                String correlationId = "belt-promotion:" + father.getId() + ":" + newBelt.name();
                if (timeline != null && timeline.enabled()) {
                    // D-039: what he read (the template as rendered, when it went as one) and its delivery
                    TimelineReports.Part part = TimelineReports.Part.sent(correlationId,
                            TimelineText.withoutIdentity(outcome.sentText() != null ? outcome.sentText() : text),
                            outcome.result(), TimelineReports.KIND_BELT_PROMOTION);
                    if (outcome.mode() == ProactiveSender.Mode.TEMPLATE) {
                        part = part.withTemplate(TimelineText.template(outcome.template(), outcome.templateParams()));
                    }
                    timeline.outbound(TimelineReports.Person.of(father), part);
                } else {
                    recorder.recordSent(father, text, correlationId);
                }
            }
        } catch (RuntimeException e) {
            log.error("Belt promotion notification failed: fatherId={}", father.getId(), e);
        }
    }

    public void sendBatchPromotionNotifications(List<WeeklyGoalService.BeltPromotionResult> results) {
        results.forEach(this::sendPromotionNotification);
    }

    /** Calm and factual: belts count completed sessions; no streak hype, no "great dad" (the site's "מה הוא אף פעם לא יעשה"). */
    private String buildPromotionMessage(WeeklyGoalService.BeltPromotionResult result) {
        Belt newBelt = result.newBelt();
        // D-032: opens with the identity line like every message on the shared number (the template body carries it)
        StringBuilder sb = new StringBuilder("❤️ דאד קואץ׳:\n💪 *").append(newBelt.getDisplayName("he")).append("*\n");
        sb.append("כל מפגש שקרה ואישרת נספר, והם הצטברו לחגורה חדשה.");
        Belt next = newBelt.getNextBelt();
        if (next != null) {
            sb.append("\nהבאה בתור: ").append(next.getDisplayName("he")).append(".");
        }
        return sb.toString();
    }
}
