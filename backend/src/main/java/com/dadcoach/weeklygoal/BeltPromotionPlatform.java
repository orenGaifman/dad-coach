package com.dadcoach.weeklygoal;

import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow.Settled;
import com.dadcoach.integration.platform.scheduled.ScheduledResponseCallbackConfig;
import com.dadcoach.integration.platform.timeline.TimelineText;
import com.dadcoach.workflow.Belt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * D-046 (Unified Workflow Phase 6.3-DC, step DC3): the belt promotion - its optional image, then its text - sent by the
 * platform's Send API instead of Dad Coach calling Meta itself. Switch {@code MESSAGING_MODE_BELT_PROMOTION}
 * ({@link MessagingSettings}); the answer rules are D-045's ({@link PlatformSendFlow#sendOnce}). Platform spec
 * {@code PHASE6_2_SEND_API_SPEC.md} §17.1 DC3. (The weekly job has promoted no one since D-030; the path is kept ready.)
 *
 * <ul>
 *   <li><b>Image</b> (only when one is configured for the belt): key {@code belt-promotion:<fatherId>:<BELT>:image},
 *       {@code content.image.link} + caption, {@code windowPolicy SESSION_ONLY} - as today it goes only inside his
 *       window (images cannot go in a template); outside it the platform answers FAILED {@code WINDOW_CLOSED} and nothing
 *       is sent, as today's skip. Not recorded on the timeline (D-039 never reported the image). The link's host must be
 *       on the platform's {@code workflow.messaging.send.image-hosts} (6.2b) when that list is set, else 400 - a refused
 *       image never stops the text (D-040).</li>
 *   <li><b>Text</b>: key {@code belt-promotion:<fatherId>:<BELT>:text}, {@code content.text}, the general template
 *       ({@code {{1}}} = {@link ProactiveSender#asTemplateParameter}) outside the window; timeline {@code NEW_ROW}
 *       {@code belt-promotion:<fatherId>:<BELT>} (D-039's correlation) with the text without the identity line - the
 *       platform records it, Dad Coach reports nothing.</li>
 * </ul>
 * No ledger: the bytes stay in memory (spec v2.6), one immediate re-POST of an ambiguous answer, never a direct send
 * after one; a direct send (today's way, each part on its own) only when the platform provably created nothing. Both
 * keys are fixed per father and belt, so a re-run of the job replays on the platform instead of sending twice.
 */
@Component
public class BeltPromotionPlatform {

    private static final Logger log = LoggerFactory.getLogger(BeltPromotionPlatform.class);
    static final String EVENT = "belt_promotion";

    private final MessagingSettings settings;
    private final PlatformSendFlow flow;
    private final ScheduledResponseCallbackConfig config;

    public BeltPromotionPlatform(MessagingSettings settings, PlatformSendFlow flow, ScheduledResponseCallbackConfig config) {
        this.settings = settings;
        this.flow = flow;
        this.config = config;
    }

    public boolean sends() {
        return settings.beltPromotion() == Mode.PLATFORM;
    }

    public static String correlationId(Father father, Belt belt) {
        return "belt-promotion:" + father.getId() + ":" + belt.name();
    }

    /** The image through the platform; {@code direct} sends it today's way (only if the platform provably created nothing). */
    public Settled<Boolean> sendImage(Father father, Belt belt, String imageUrl, String caption,
                                      java.util.function.Supplier<Boolean> direct) {
        String key = correlationId(father, belt) + ":image";
        return flow.sendOnce(EVENT, log, "key", key, imageRequest(father, key, imageUrl, caption, false), direct);
    }

    /** The text through the platform; {@code direct} sends it today's way (only if the platform provably created nothing). */
    public Settled<Boolean> sendText(Father father, Belt belt, String text, java.util.function.Supplier<Boolean> direct) {
        String key = correlationId(father, belt) + ":text";
        return flow.sendOnce(EVENT, log, "key", key, textRequest(father, belt, key, text, false), direct);
    }

    /** SHADOW: the platform's dry runs of the same image (when Dad Coach tried one) and text, off the job's thread. */
    public void shadow(Father father, Belt belt, String imageUrl, String caption, Boolean imageSent, String text,
                       ProactiveSender.Outcome outcome) {
        if (settings.beltPromotion() != Mode.SHADOW) {
            return;
        }
        try {
            String base = correlationId(father, belt);
            if (imageUrl != null) {
                // not tried by Dad Coach (window closed) = FAIL; the platform's SESSION_ONLY says the same then
                boolean sent = Boolean.TRUE.equals(imageSent);
                flow.shadow(MessagingSettings.BELT_PROMOTION, log, "key", base + ":image",
                        () -> imageRequest(father, base + ":image", imageUrl, caption, true),
                        new PlatformSendFlow.ShadowFacts(sent, false, imageSent == null ? "WINDOW_CLOSED" : sent ? null
                                : "IMAGE_REFUSED", null));
            }
            var r = outcome.result();
            flow.shadow(MessagingSettings.BELT_PROMOTION, log, "key", base + ":text",
                    () -> textRequest(father, belt, base + ":text", text, true),
                    new PlatformSendFlow.ShadowFacts(r.isSuccessful(), r.isHeld(), r.failureReason(),
                            outcome.mode() == ProactiveSender.Mode.TEMPLATE ? outcome.template() : null));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.shadow.failed").addKeyValue("kind", MessagingSettings.BELT_PROMOTION)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    String imageRequest(Father father, String key, String imageUrl, String caption, boolean dryRun) {
        Optional<Father> who = Optional.of(father);
        Map<String, Object> r = flow.newRequest(who, father.getPhone());
        r.put("idempotencyKey", key);
        r.put("kind", MessagingSettings.BELT_PROMOTION);
        r.put("messageClass", "UTILITY");
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("link", imageUrl);
        image.put("caption", caption);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "image");
        content.put("image", image);
        r.put("content", content);
        r.put("windowPolicy", "SESSION_ONLY");
        r.put("outsideWindowWithoutTemplate", "FAIL");
        flow.lastInbound(who).ifPresent(at -> r.put("windowHint", Map.of("lastInboundAt", at.toString())));
        r.put("holdPolicy", "GATEWAY");
        r.put("timeline", Map.of("record", false));
        r.put("wait", "ACCEPTED");
        r.put("dryRun", dryRun);
        return flow.json(r);
    }

    String textRequest(Father father, Belt belt, String key, String text, boolean dryRun) {
        Optional<Father> who = Optional.of(father);
        Map<String, Object> r = flow.newRequest(who, father.getPhone());
        r.put("idempotencyKey", key);
        r.put("kind", MessagingSettings.BELT_PROMOTION);
        r.put("messageClass", "UTILITY");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);
        r.put("content", content);
        String name = config.effectiveTemplateName();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", name);
        template.put("language", TemplateRegistry.languageForTemplateName(name));
        template.put("bodyParams", List.of(ProactiveSender.asTemplateParameter(text)));
        WhatsAppTemplateCatalog.named(name).ifPresent(e -> template.put("expectedBody", e.body()));
        r.put("template", template);
        r.put("windowPolicy", "AUTO");
        r.put("outsideWindowWithoutTemplate", "FAIL");
        flow.lastInbound(who).ifPresent(at -> r.put("windowHint", Map.of("lastInboundAt", at.toString())));
        r.put("holdPolicy", "GATEWAY");
        Map<String, Object> timelineRow = new LinkedHashMap<>();
        timelineRow.put("record", true);
        timelineRow.put("correlationId", correlationId(father, belt));
        timelineRow.put("mode", "NEW_ROW");
        timelineRow.put("content", TimelineText.withoutIdentity(text));
        r.put("timeline", timelineRow);
        r.put("wait", "ACCEPTED");
        r.put("dryRun", dryRun);
        return flow.json(r);
    }
}
