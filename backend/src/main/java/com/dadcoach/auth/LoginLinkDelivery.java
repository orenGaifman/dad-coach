package com.dadcoach.auth;

import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends a login link over WhatsApp. A father's goes through the channel delivery layer ({@link DeliveryService}):
 * free-form inside his 24-hour window (he usually just wrote to the coach), otherwise the approved one-parameter
 * template the scheduled messages use, if configured; without one it fails as SESSION_CLOSED. A staff user who is
 * not a father has no tracked window: the WhatsApp channel is asked directly. The outcome is recorded on the link
 * (the admin's "undelivered" list shows failures); the link itself is never logged.
 */
@Component
public class LoginLinkDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkDelivery.class);
    static final String WHATSAPP = "WHATSAPP";

    private final DeliveryService deliveryService;
    private final ChannelRouter channelRouter;
    private final String templateName;

    public LoginLinkDelivery(DeliveryService deliveryService, ChannelRouter channelRouter,
                             @Value("${workflow.platform.scheduled-response.template-name:}") String templateName) {
        this.deliveryService = deliveryService;
        this.channelRouter = channelRouter;
        this.templateName = templateName == null ? "" : templateName.strip();
    }

    public DeliveryResult send(SignInSubject subject, String phone, String url) {
        String text = subject.fatherId() != null ? fatherText(url) : staffText(url);
        try {
            if (subject.fatherId() != null) {
                UUID fatherUuid = new UUID(0L, subject.fatherId());
                DeliveryResult result = deliveryService.deliver(message(fatherUuid, text, false));
                if (!result.isSuccessful() && DeliveryService.SESSION_CLOSED.equals(result.failureReason()) && !templateName.isEmpty()) {
                    result = deliveryService.deliver(message(fatherUuid, text, true));
                }
                return result;
            }
            if (!channelRouter.supportsChannel(WHATSAPP)) {
                return DeliveryResult.failed("WHATSAPP_NOT_CONFIGURED");
            }
            return channelRouter.getAdapter(WHATSAPP).sendMessage(message(null, text, false), phone);
        } catch (RuntimeException e) {
            log.warn("auth.login_link.delivery_error type={}", e.getClass().getSimpleName());
            return DeliveryResult.failed("DELIVERY_ERROR: " + e.getClass().getSimpleName());
        }
    }

    private OutboundMessageDto message(UUID fatherUuid, String text, boolean template) {
        return new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null, MessageType.TEXT, text, null, template,
                template ? templateName : null, template ? Map.of("1", text.replaceAll("\\s*[\\r\\n\\t]+\\s*", " ").trim()) : null,
                MessagePriority.IMMEDIATE, Instant.now());
    }

    static String fatherText(String url) {
        return "הנה הקישור שלך ללוח של Dad Coach:\n" + url
                + "\n\nהקישור תקף ל-15 דקות ולכניסה אחת. לא ביקשת אותו? אפשר פשוט להתעלם.";
    }

    static String staffText(String url) {
        return "קישור כניסה לניהול Dad Coach:\n" + url + "\n\nתקף ל-15 דקות ולכניסה אחת.";
    }
}
