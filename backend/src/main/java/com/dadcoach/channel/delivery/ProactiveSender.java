package com.dadcoach.channel.delivery;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.scheduled.ScheduledResponseCallbackConfig;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A message Dad Coach sends a father on its own (a scheduled coach message, a belt promotion), through the channel
 * layer ({@link DeliveryService}): free-form while his 24-hour window is open; outside it the approved template
 * {@code WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME} (body "{{1}}" = the message) — or, with none configured, nothing
 * is sent (FAILED SESSION_CLOSED: WhatsApp would drop a free-form message). Reply buttons ride only on the free-form
 * message (an INTERACTIVE one, body up to 1024 characters); the template fallback carries none.
 */
@Component
public class ProactiveSender {

    public enum Mode { FREE_FORM, TEMPLATE }

    public record Outcome(DeliveryResult result, Mode mode) {
    }

    private final DeliveryService delivery;
    private final ScheduledResponseCallbackConfig config;
    private final Clock clock;

    public ProactiveSender(DeliveryService delivery, ScheduledResponseCallbackConfig config, Clock clock) {
        this.delivery = delivery;
        this.config = config;
        this.clock = clock;
    }

    public Outcome send(Father father, String content) {
        return send(father, content, List.of());
    }

    public Outcome send(Father father, String content, List<OutboundMessageDto.ReplyButton> buttons) {
        UUID fatherUuid = new UUID(0L, father.getId());
        boolean withButtons = !buttons.isEmpty() && content.length() <= WhatsAppMessageFormatter.INTERACTIVE_BODY_LIMIT;
        DeliveryResult result;
        try {
            result = delivery.deliver(new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null,
                    withButtons ? MessageType.INTERACTIVE : MessageType.TEXT, content, null, false, null, null,
                    MessagePriority.IMMEDIATE, clock.instant(), withButtons ? buttons : List.of()));
            if (!result.isSuccessful() && DeliveryService.SESSION_CLOSED.equals(result.failureReason())) {
                String template = config.getTemplateName();
                if (template == null || template.isBlank()) {
                    return new Outcome(DeliveryResult.failed(DeliveryService.SESSION_CLOSED
                            + ": 24h window closed and no approved template configured; not sent"), Mode.FREE_FORM);
                }
                return new Outcome(delivery.deliver(new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null, MessageType.TEXT,
                        content, null, true, template, Map.of("1", asTemplateParameter(content)), MessagePriority.IMMEDIATE,
                        clock.instant())), Mode.TEMPLATE);
            }
            return new Outcome(result, Mode.FREE_FORM);
        } catch (RuntimeException e) {
            return new Outcome(DeliveryResult.failed("Delivery error: " + e.getClass().getSimpleName()), Mode.FREE_FORM);
        }
    }

    /**
     * WhatsApp rejects template text parameters containing newlines, tabs or more than four consecutive spaces;
     * the message is flattened into one line.
     */
    public static String asTemplateParameter(String content) {
        return content.replaceAll("\\s*[\\r\\n\\t]+\\s*", " ").replaceAll(" {4,}", "   ").trim();
    }
}
