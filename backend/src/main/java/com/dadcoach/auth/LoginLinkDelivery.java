package com.dadcoach.auth;

import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto.LinkButton;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends a sign-in link over WhatsApp as a button (D-027: "📊 הדף שלך בדאד קואץ׳" + "כניסה לדף שלי", a WhatsApp
 * cta_url message - the reusable link sits behind the button, never in the text). A father's goes through the
 * channel delivery layer ({@link DeliveryService}): the button inside his 24-hour window (he usually just wrote to
 * the coach); a button Meta refuses for good goes out as text with the link on its own line (WhatsAppAdapter);
 * outside the window, the approved one-parameter template the scheduled messages use, if configured - without one it
 * fails as SESSION_CLOSED. A staff user who is not a father has no tracked window: the WhatsApp channel is asked
 * directly. The outcome is recorded on the link (the admin's "undelivered" list shows failures); the link itself is
 * never logged.
 */
@Component
public class LoginLinkDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkDelivery.class);
    static final String WHATSAPP = "WHATSAPP";
    /** The identity line every message Dad Coach sends by itself opens with (the number is shared). */
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final String FATHER_TEXT = IDENTITY + "📊 *הדף שלך בדאד קואץ׳*\nהשבוע, הילדים וההתקדמות, הכול בדף אחד.";
    static final String FATHER_LABEL = "כניסה לדף שלי";
    static final String STAFF_TEXT = IDENTITY + "📊 *ניהול דאד קואץ׳*\nהכניסה שלך לניהול.";
    static final String STAFF_LABEL = "כניסה לניהול";
    /** "הקישור", not "הכפתור": the same footer closes the text fallback, where there is no button (D-032). */
    static final String FOOTER = "הקישור אישי, לא להעביר הלאה";

    private final DeliveryService deliveryService;
    private final ChannelRouter channelRouter;
    private final Clock clock;
    private final String templateName;

    public LoginLinkDelivery(DeliveryService deliveryService, ChannelRouter channelRouter, Clock clock,
                             @Value("${workflow.platform.scheduled-response.template-name:}") String templateName) {
        this.deliveryService = deliveryService;
        this.channelRouter = channelRouter;
        this.clock = clock;
        // the general template, as the scheduled messages use it (sending still needs Meta's approval, via the registry)
        this.templateName = templateName == null || templateName.isBlank()
                ? com.dadcoach.channel.template.WhatsAppTemplateCatalog.UPDATE_HE : templateName.strip();
    }

    public DeliveryResult send(SignInSubject subject, String phone, String url) {
        boolean father = subject.fatherId() != null;
        LinkButton button = new LinkButton(father ? FATHER_LABEL : STAFF_LABEL, url, FOOTER);
        String text = father ? FATHER_TEXT : STAFF_TEXT;
        try {
            if (father) {
                UUID fatherUuid = new UUID(0L, subject.fatherId());
                DeliveryResult result = deliveryService.deliver(OutboundMessageDto.withLinkButton(fatherUuid, text, button, clock.instant()));
                if (!result.isSuccessful() && DeliveryService.SESSION_CLOSED.equals(result.failureReason()) && !templateName.isEmpty()) {
                    result = deliveryService.deliver(template(fatherUuid, url));
                }
                return result;
            }
            if (!channelRouter.supportsChannel(WHATSAPP)) {
                return DeliveryResult.failed("WHATSAPP_NOT_CONFIGURED");
            }
            return channelRouter.getAdapter(WHATSAPP).sendMessage(OutboundMessageDto.withLinkButton(null, text, button, clock.instant()), phone);
        } catch (RuntimeException e) {
            log.warn("auth.login_link.delivery_error type={}", e.getClass().getSimpleName());
            return DeliveryResult.failed("DELIVERY_ERROR: " + e.getClass().getSimpleName());
        }
    }

    /** Outside the window: the general template takes one line of text (no line breaks), the link inside it. */
    private OutboundMessageDto template(UUID fatherUuid, String url) {
        return new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null, MessageType.TEXT, templateLine(url), null, true,
                templateName, Map.of("1", ProactiveSender.asTemplateParameter(templateLine(url))), MessagePriority.IMMEDIATE,
                clock.instant());
    }

    static String templateLine(String url) {
        return "הקישור לדף שלך, אישי ולא להעביר הלאה: " + url;
    }
}
