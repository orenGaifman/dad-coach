package com.dadcoach.channel.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Normalized internal format for messages to be delivered to a communication provider.
 * This is the sole interface between the Conversation_Engine and the Communication_Channel
 * for outbound messages.
 *
 * @param messageId          unique identifier assigned by the Conversation_Engine
 * @param fatherId           internal father identifier (endpoint resolution handled by Communication_Channel)
 * @param channel            optional target delivery channel (null = deliver to primary endpoint)
 * @param messageType        content classification of the message
 * @param textContent        the message text
 * @param mediaReference     reference to media asset (if applicable)
 * @param isTemplate         whether a template message is required
 * @param templateName       template identifier (if isTemplate = true)
 * @param templateParameters key-value pairs for template variable substitution
 * @param priority           IMMEDIATE (conversation reply) or SCHEDULED (proactive notification)
 * @param requestedAt        timestamp when the Conversation_Engine requested delivery
 * @param buttons            reply buttons of an INTERACTIVE message (up to 3, every id starts with "dc:"); empty otherwise
 * @param linkButton         the one URL button of an INTERACTIVE message (WhatsApp "cta_url"); null otherwise
 */
public record OutboundMessageDto(
    UUID messageId,
    UUID fatherId,
    String channel,
    MessageType messageType,
    String textContent,
    UUID mediaReference,
    boolean isTemplate,
    String templateName,
    Map<String, String> templateParameters,
    MessagePriority priority,
    Instant requestedAt,
    List<ReplyButton> buttons,
    LinkButton linkButton
) {

    public OutboundMessageDto {
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    public OutboundMessageDto(UUID messageId, UUID fatherId, String channel, MessageType messageType, String textContent,
                              UUID mediaReference, boolean isTemplate, String templateName,
                              Map<String, String> templateParameters, MessagePriority priority, Instant requestedAt,
                              List<ReplyButton> buttons) {
        this(messageId, fatherId, channel, messageType, textContent, mediaReference, isTemplate, templateName,
                templateParameters, priority, requestedAt, buttons, null);
    }

    public OutboundMessageDto(UUID messageId, UUID fatherId, String channel, MessageType messageType, String textContent,
                              UUID mediaReference, boolean isTemplate, String templateName,
                              Map<String, String> templateParameters, MessagePriority priority, Instant requestedAt) {
        this(messageId, fatherId, channel, messageType, textContent, mediaReference, isTemplate, templateName,
                templateParameters, priority, requestedAt, List.of(), null);
    }

    /** A message whose text sits above one URL button (D-027: the dashboard button). */
    public static OutboundMessageDto withLinkButton(UUID fatherId, String text, LinkButton button, Instant requestedAt) {
        return new OutboundMessageDto(UUID.randomUUID(), fatherId, null, MessageType.INTERACTIVE, text, null, false, null,
                null, MessagePriority.IMMEDIATE, requestedAt, List.of(), button);
    }

    /** The same message as plain text, the link on its own line (where a URL button cannot be shown). */
    public OutboundMessageDto withLinkAsText() {
        return new OutboundMessageDto(messageId, fatherId, channel, MessageType.TEXT,
                linkButton == null ? textContent : linkButton.asText(textContent), null, false, null, null, priority,
                requestedAt, List.of(), null);
    }

    /** One WhatsApp reply button: the id comes back on a tap, the title is what the father sees (up to 20 characters). */
    public record ReplyButton(String id, String title) {
    }

    /**
     * One WhatsApp URL button: {@code label} on the button (up to 20 characters), {@code url} behind it, an optional
     * small {@code footer} under the text (up to 60). It carries no id - nothing comes back on a tap.
     */
    public record LinkButton(String label, String url, String footer) {
        public LinkButton {
            if (label == null || label.isBlank() || url == null || url.isBlank()) {
                throw new IllegalArgumentException("A link button needs a label and a url");
            }
        }

        /** The text with the link on its own line, then the footer. */
        public String asText(String text) {
            StringBuilder b = new StringBuilder(text == null ? "" : text.strip()).append('\n').append(url);
            if (footer != null && !footer.isBlank()) {
                b.append("\n\n").append(footer);
            }
            return b.toString();
        }
    }
}
