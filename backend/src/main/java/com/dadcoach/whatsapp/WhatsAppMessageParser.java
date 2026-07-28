package com.dadcoach.whatsapp;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.StatusUpdateDto;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Parses raw WhatsApp Cloud API webhook JSON payloads (deserialized into
 * {@link WhatsAppWebhookPayload}) into normalized {@link InboundMessageDto}
 * objects and {@link StatusUpdateDto} objects.
 *
 * <p>Handles all supported WhatsApp message types: text, image, audio, video,
 * document, location, reaction, and interactive. Status updates (sent, delivered,
 * read, failed) are extracted separately for delivery tracking.
 *
 * <p>Invalid or unparseable payloads are logged and discarded without propagating
 * exceptions — this ensures webhook processing never fails due to unexpected payload
 * formats.
 */
@Component
public class WhatsAppMessageParser {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageParser.class);

    /**
     * Result of parsing a WhatsApp webhook payload.
     * Contains both inbound messages and status updates extracted from the payload.
     */
    public record ParseResult(
        List<InboundMessageDto> messages,
        List<StatusUpdateDto> statusUpdates
    ) {
        public static ParseResult empty() {
            return new ParseResult(Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * Parses a deserialized WhatsApp webhook payload into normalized messages
     * and status updates.
     *
     * @param payload the deserialized webhook payload (may be null)
     * @return a ParseResult containing extracted messages and status updates (never null)
     */
    public ParseResult parse(WhatsAppWebhookPayload payload) {
        if (payload == null) {
            log.warn("Received null webhook payload, discarding");
            return ParseResult.empty();
        }

        if (!"whatsapp_business_account".equals(payload.object())) {
            log.warn("Unexpected webhook object type: '{}', discarding", payload.object());
            return ParseResult.empty();
        }

        if (payload.entry() == null || payload.entry().isEmpty()) {
            log.warn("Webhook payload has no entries, discarding");
            return ParseResult.empty();
        }

        List<InboundMessageDto> messages = new ArrayList<>();
        List<StatusUpdateDto> statusUpdates = new ArrayList<>();

        for (Entry entry : payload.entry()) {
            if (entry.changes() == null) {
                continue;
            }
            for (Change change : entry.changes()) {
                if (change.value() == null) {
                    continue;
                }
                parseMessages(change.value(), messages);
                parseStatuses(change.value(), statusUpdates);
            }
        }

        return new ParseResult(
            Collections.unmodifiableList(messages),
            Collections.unmodifiableList(statusUpdates)
        );
    }

    private void parseMessages(Value value, List<InboundMessageDto> results) {
        if (value.messages() == null) {
            return;
        }

        for (Message msg : value.messages()) {
            try {
                InboundMessageDto dto = parseMessage(msg);
                if (dto != null) {
                    results.add(dto);
                }
            } catch (Exception e) {
                log.warn("Failed to parse WhatsApp message (id={}, type={}): {}",
                    msg != null ? msg.id() : "null",
                    msg != null ? msg.type() : "null",
                    e.getMessage());
            }
        }
    }

    private InboundMessageDto parseMessage(Message msg) {
        if (msg == null || msg.id() == null || msg.from() == null || msg.type() == null) {
            log.warn("Incomplete message payload (missing id, from, or type), discarding");
            return null;
        }

        MessageType messageType = mapMessageType(msg.type());
        if (messageType == null) {
            log.warn("Unsupported WhatsApp message type: '{}', discarding message id={}",
                msg.type(), msg.id());
            return null;
        }

        String textContent = extractTextContent(msg, messageType);
        // Media reference would be populated by the media service after download;
        // at parse time we don't yet have the stored UUID, so it's null here.
        // The caller (adapter) is responsible for media download and reference assignment.

        Instant receivedAt = parseTimestamp(msg.timestamp());
        Instant ingestedAt = Instant.now();

        return new InboundMessageDto(
            UUID.randomUUID(),
            msg.id(),           // idempotencyKey derived from provider message ID
            msg.from(),         // fatherChannelIdentity (phone number)
            "WHATSAPP",
            messageType,
            textContent,
            null,               // mediaReference — populated later by media service
            receivedAt,
            ingestedAt
        );
    }

    private void parseStatuses(Value value, List<StatusUpdateDto> results) {
        if (value.statuses() == null) {
            return;
        }

        for (Status status : value.statuses()) {
            try {
                StatusUpdateDto dto = parseStatus(status);
                if (dto != null) {
                    results.add(dto);
                }
            } catch (Exception e) {
                log.warn("Failed to parse WhatsApp status update (id={}): {}",
                    status != null ? status.id() : "null",
                    e.getMessage());
            }
        }
    }

    private StatusUpdateDto parseStatus(Status status) {
        if (status == null || status.id() == null || status.status() == null) {
            log.warn("Incomplete status payload (missing id or status), discarding");
            return null;
        }

        Integer errorCode = null;
        String errorMessage = null;

        if (status.errors() != null && !status.errors().isEmpty()) {
            var firstError = status.errors().get(0);
            errorCode = firstError.code();
            errorMessage = firstError.title();
        }

        return new StatusUpdateDto(
            status.id(),
            status.status(),
            status.recipientId(),
            parseTimestamp(status.timestamp()),
            errorCode,
            errorMessage
        );
    }

    /**
     * Maps a WhatsApp message type string to the internal MessageType enum.
     *
     * @param whatsAppType the type string from the webhook payload
     * @return the corresponding MessageType, or null if unsupported
     */
    MessageType mapMessageType(String whatsAppType) {
        if (whatsAppType == null) {
            return null;
        }
        return switch (whatsAppType.toLowerCase()) {
            case "text" -> MessageType.TEXT;
            case "image" -> MessageType.IMAGE;
            case "audio" -> MessageType.AUDIO;
            case "video" -> MessageType.VIDEO;
            case "document" -> MessageType.DOCUMENT;
            case "location" -> MessageType.LOCATION;
            case "reaction" -> MessageType.REACTION;
            case "interactive" -> MessageType.INTERACTIVE;
            default -> null;
        };
    }

    /**
     * Extracts the text content from a message based on its type.
     * For media messages, the caption is used as text content.
     * For location, a text description is generated.
     * For reactions, the emoji is the text content.
     * For interactive replies, the selected option title is the text content.
     */
    String extractTextContent(Message msg, MessageType messageType) {
        return switch (messageType) {
            case TEXT -> msg.text() != null ? msg.text().body() : null;
            case IMAGE -> msg.image() != null ? msg.image().caption() : null;
            case VIDEO -> msg.video() != null ? msg.video().caption() : null;
            case DOCUMENT -> msg.document() != null ? msg.document().caption() : null;
            case AUDIO -> null; // audio messages don't have text content
            case LOCATION -> formatLocation(msg.location());
            case REACTION -> msg.reaction() != null ? msg.reaction().emoji() : null;
            case INTERACTIVE -> extractInteractiveText(msg.interactive());
        };
    }

    private String formatLocation(LocationBody location) {
        if (location == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (location.name() != null && !location.name().isBlank()) {
            sb.append(location.name());
        }
        if (location.address() != null && !location.address().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" - ");
            }
            sb.append(location.address());
        }
        if (sb.isEmpty()) {
            sb.append(String.format("%.6f, %.6f", location.latitude(), location.longitude()));
        }
        return sb.toString();
    }

    private String extractInteractiveText(InteractiveReply interactive) {
        if (interactive == null) {
            return null;
        }
        if (interactive.buttonReply() != null) {
            return interactive.buttonReply().title();
        }
        if (interactive.listReply() != null) {
            return interactive.listReply().title();
        }
        return null;
    }

    /**
     * Parses a Unix timestamp string (seconds since epoch) into an Instant.
     * Returns Instant.now() as fallback if the timestamp is null or unparseable.
     */
    Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            long epochSeconds = Long.parseLong(timestamp);
            return Instant.ofEpochSecond(epochSeconds);
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp '{}', using current time", timestamp);
            return Instant.now();
        }
    }
}
