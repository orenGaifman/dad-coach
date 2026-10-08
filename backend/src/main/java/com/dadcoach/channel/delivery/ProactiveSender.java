package com.dadcoach.channel.delivery;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.TemplateRegistry;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.scheduled.ScheduledResponseCallbackConfig;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A message Dad Coach sends a father on its own (a scheduled coach message, a belt promotion), through the channel
 * layer ({@link DeliveryService}): free-form while his 24-hour window is open. Outside it, the message's own template
 * ({@link TemplateCall}, with its quick-reply buttons) when the caller has one and it is registered as approved;
 * otherwise the general template {@code WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME} ({{1}} = the message as one line,
 * {@link #asTemplateParameter}) — or, with none configured, nothing is sent (FAILED SESSION_CLOSED: WhatsApp would
 * drop a free-form message). The general template carries no buttons.
 */
@Component
public class ProactiveSender {

    private static final Logger log = LoggerFactory.getLogger(ProactiveSender.class);

    public enum Mode { FREE_FORM, TEMPLATE }

    /** {@code template} is the template that went out (or was tried), null for a free-form message. */
    public record Outcome(DeliveryResult result, Mode mode, String template) {

        public Outcome(DeliveryResult result, Mode mode) {
            this(result, mode, null);
        }
    }

    private final DeliveryService delivery;
    private final ScheduledResponseCallbackConfig config;
    private final TemplateRegistry templates;
    private final Clock clock;

    public ProactiveSender(DeliveryService delivery, ScheduledResponseCallbackConfig config, TemplateRegistry templates,
                           Clock clock) {
        this.delivery = delivery;
        this.config = config;
        this.templates = templates;
        this.clock = clock;
    }

    public Outcome send(Father father, String content) {
        return send(father, content, List.of(), null);
    }

    public Outcome send(Father father, String content, List<OutboundMessageDto.ReplyButton> buttons) {
        return send(father, content, buttons, null);
    }

    /**
     * {@code own}: the message's own template for outside the window, asked for only when the window is closed; null,
     * or a supplier that gives null or fails, means the general template.
     */
    public Outcome send(Father father, String content, List<OutboundMessageDto.ReplyButton> buttons,
                        Supplier<TemplateCall> ownTemplate) {
        UUID fatherUuid = new UUID(0L, father.getId());
        boolean withButtons = !buttons.isEmpty() && content.length() <= WhatsAppMessageFormatter.INTERACTIVE_BODY_LIMIT;
        DeliveryResult result;
        try {
            result = delivery.deliver(new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null,
                    withButtons ? MessageType.INTERACTIVE : MessageType.TEXT, content, null, false, null, null,
                    MessagePriority.IMMEDIATE, clock.instant(), withButtons ? buttons : List.of()));
            if (!result.isSuccessful() && DeliveryService.SESSION_CLOSED.equals(result.failureReason())) {
                TemplateCall own = ownTemplate(ownTemplate);
                if (own != null && templates.findApprovedTemplate(own.name(), own.entry().language()).isPresent()) {
                    List<String> titles = own.entry().quickReplies();
                    List<OutboundMessageDto.ReplyButton> taps = new ArrayList<>();
                    for (int i = 0; i < titles.size(); i++) {
                        taps.add(new OutboundMessageDto.ReplyButton(own.buttonPayloads().get(i), titles.get(i)));
                    }
                    return new Outcome(delivery.deliver(new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null,
                            MessageType.TEXT, own.text(), null, true, own.name(),
                            WhatsAppTemplateCatalog.parameters(own.values().stream().map(ProactiveSender::oneLine).toList()),
                            MessagePriority.IMMEDIATE, clock.instant(), taps)), Mode.TEMPLATE, own.name());
                }
                String template = config.getTemplateName();
                if (template == null || template.isBlank()) {
                    return new Outcome(DeliveryResult.failed(DeliveryService.SESSION_CLOSED
                            + ": 24h window closed and no approved template configured; not sent"), Mode.FREE_FORM);
                }
                return new Outcome(delivery.deliver(new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null, MessageType.TEXT,
                        content, null, true, template, Map.of("1", asTemplateParameter(content)), MessagePriority.IMMEDIATE,
                        clock.instant())), Mode.TEMPLATE, template);
            }
            return new Outcome(result, Mode.FREE_FORM);
        } catch (RuntimeException e) {
            return new Outcome(DeliveryResult.failed("Delivery error: " + e.getClass().getSimpleName()), Mode.FREE_FORM);
        }
    }

    private static TemplateCall ownTemplate(Supplier<TemplateCall> supplier) {
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            log.warn("Own template not built, the general template goes out: {}", e.toString());
            return null;
        }
    }

    /** A template value: WhatsApp rejects newlines, tabs and more than four spaces in a row. */
    private static String oneLine(String value) {
        return value.replaceAll("\\s*\\R\\s*", " ").replace('\t', ' ').replaceAll(" {4,}", "   ").strip();
    }

    /** The identity line a message opens with ("❤️ דאד קואץ׳:"); the template body carries its own. */
    private static final Pattern IDENTITY_LINE = Pattern.compile("\\A\\s*[^\\n]*דאד קואץ׳:[ \\t]*(\\r?\\n|\\z)");
    /** A line that already ends a sentence (punctuation, a closing quote or bracket, an emoji or other symbol). */
    private static final Pattern ENDED = Pattern.compile("[.?!:;,…)\"'\\p{So}\\p{Sk}\\x{FE0F}\\x{200D}]$");

    /**
     * The message as the one line the general template takes as its {{1}} (D-032). WhatsApp rejects template text
     * parameters containing newlines, tabs or more than four consecutive spaces. The template body is
     * "❤️ דאד קואץ׳:" / {{1}} / a fixed closing line, so the message's own identity line is dropped (it would show
     * twice); each line that does not end a sentence gets a period, so the lines still read as sentences; list
     * items keep their "•".
     */
    public static String asTemplateParameter(String content) {
        String body = IDENTITY_LINE.matcher(content).replaceFirst("");
        List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        StringBuilder flat = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace('\t', ' ');
            flat.append(line);
            if (i < lines.size() - 1) {
                boolean listFollows = lines.get(i + 1).startsWith("•");
                if (!listFollows && !ENDED.matcher(line).find()) {
                    flat.append('.');
                }
                flat.append(' ');
            }
        }
        return flat.toString().replaceAll(" {4,}", "   ").trim();
    }
}
