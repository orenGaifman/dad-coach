package com.dadcoach.channel.capability;

import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

/**
 * Handles automatic message type downgrade when a channel does not support
 * the requested message type. Implements the downgrade rules defined in
 * Requirement 1, criteria 9-10.
 *
 * Downgrade rules:
 * - INTERACTIVE → TEXT: Render button/list options as numbered text list
 * - IMAGE → TEXT: Deliver text_content only; log that image was dropped
 * - AUDIO → TEXT: Reject delivery (no reasonable text equivalent for voice)
 * - TEMPLATE → TEXT: If session is open, send as free-form text with variables substituted.
 *                     If session closed, reject delivery.
 */
@Component
public class MessageDowngrader {

    private static final Logger log = LoggerFactory.getLogger(MessageDowngrader.class);

    /**
     * Result of a downgrade attempt.
     */
    public sealed interface DowngradeResult permits DowngradeResult.Success, DowngradeResult.Rejected {

        record Success(OutboundMessageDto downgradedMessage) implements DowngradeResult {}

        record Rejected(String reason) implements DowngradeResult {}
    }

    /**
     * Evaluates whether the message needs downgrading based on channel capabilities,
     * and performs the downgrade if needed.
     *
     * @param message      the outbound message to evaluate
     * @param capabilities the target channel's capabilities
     * @param sessionOpen  whether the session window is currently open (relevant for TEMPLATE downgrade)
     * @return the original message if no downgrade needed, or a downgraded version
     */
    public DowngradeResult downgradeIfNeeded(OutboundMessageDto message, ChannelCapabilities capabilities, boolean sessionOpen) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("Capabilities must not be null");
        }

        if (isSupported(message, capabilities)) {
            return new DowngradeResult.Success(message);
        }

        return downgrade(message, capabilities, sessionOpen);
    }

    private boolean isSupported(OutboundMessageDto message, ChannelCapabilities capabilities) {
        if (message.isTemplate()) {
            return capabilities.template();
        }

        return switch (message.messageType()) {
            case TEXT -> capabilities.text();
            case IMAGE -> capabilities.image();
            case AUDIO -> capabilities.audio();
            case VIDEO -> capabilities.video();
            case DOCUMENT -> capabilities.document();
            case INTERACTIVE -> capabilities.interactive();
            default -> false;
        };
    }

    private DowngradeResult downgrade(OutboundMessageDto message, ChannelCapabilities capabilities, boolean sessionOpen) {
        // Template downgrade
        if (message.isTemplate()) {
            return downgradeTemplate(message, sessionOpen);
        }

        return switch (message.messageType()) {
            case INTERACTIVE -> downgradeInteractive(message);
            case IMAGE -> downgradeImage(message);
            case AUDIO -> downgradeAudio(message);
            case VIDEO -> downgradeVideo(message);
            case DOCUMENT -> downgradeDocument(message);
            default -> new DowngradeResult.Rejected(
                "No downgrade path available for message type: " + message.messageType());
        };
    }

    /**
     * INTERACTIVE → TEXT: Render button/list options as numbered text list.
     * The text_content of the interactive message is used as the base text.
     */
    private DowngradeResult downgradeInteractive(OutboundMessageDto message) {
        log.info("Downgrading INTERACTIVE message {} to TEXT (numbered list)",
            message.messageId());

        String textContent = message.textContent() != null ? message.textContent() : "";

        OutboundMessageDto downgraded = new OutboundMessageDto(
            message.messageId(),
            message.fatherId(),
            message.channel(),
            MessageType.TEXT,
            textContent,
            null, // no media reference for text
            false,
            null,
            null,
            message.priority(),
            message.requestedAt()
        );
        return new DowngradeResult.Success(downgraded);
    }

    /**
     * IMAGE → TEXT: Deliver text_content only; log that image was dropped.
     */
    private DowngradeResult downgradeImage(OutboundMessageDto message) {
        log.warn("Downgrading IMAGE message {} to TEXT — image media dropped",
            message.messageId());

        String textContent = message.textContent();
        if (textContent == null || textContent.isBlank()) {
            return new DowngradeResult.Rejected(
                "Cannot downgrade IMAGE to TEXT: no text_content available and no reasonable text equivalent for image");
        }

        OutboundMessageDto downgraded = new OutboundMessageDto(
            message.messageId(),
            message.fatherId(),
            message.channel(),
            MessageType.TEXT,
            textContent,
            null, // media dropped
            false,
            null,
            null,
            message.priority(),
            message.requestedAt()
        );
        return new DowngradeResult.Success(downgraded);
    }

    /**
     * AUDIO → TEXT: Reject delivery (no reasonable text equivalent for voice).
     */
    private DowngradeResult downgradeAudio(OutboundMessageDto message) {
        log.warn("Rejecting AUDIO message {} — no reasonable text equivalent for voice",
            message.messageId());
        return new DowngradeResult.Rejected(
            "Cannot downgrade AUDIO to TEXT: no reasonable text equivalent for voice content");
    }

    /**
     * VIDEO → TEXT: Similar to IMAGE, deliver text_content only if available.
     */
    private DowngradeResult downgradeVideo(OutboundMessageDto message) {
        log.warn("Downgrading VIDEO message {} to TEXT — video media dropped",
            message.messageId());

        String textContent = message.textContent();
        if (textContent == null || textContent.isBlank()) {
            return new DowngradeResult.Rejected(
                "Cannot downgrade VIDEO to TEXT: no text_content available");
        }

        OutboundMessageDto downgraded = new OutboundMessageDto(
            message.messageId(),
            message.fatherId(),
            message.channel(),
            MessageType.TEXT,
            textContent,
            null,
            false,
            null,
            null,
            message.priority(),
            message.requestedAt()
        );
        return new DowngradeResult.Success(downgraded);
    }

    /**
     * DOCUMENT → TEXT: Deliver text_content only if available.
     */
    private DowngradeResult downgradeDocument(OutboundMessageDto message) {
        log.warn("Downgrading DOCUMENT message {} to TEXT — document media dropped",
            message.messageId());

        String textContent = message.textContent();
        if (textContent == null || textContent.isBlank()) {
            return new DowngradeResult.Rejected(
                "Cannot downgrade DOCUMENT to TEXT: no text_content available");
        }

        OutboundMessageDto downgraded = new OutboundMessageDto(
            message.messageId(),
            message.fatherId(),
            message.channel(),
            MessageType.TEXT,
            textContent,
            null,
            false,
            null,
            null,
            message.priority(),
            message.requestedAt()
        );
        return new DowngradeResult.Success(downgraded);
    }

    /**
     * TEMPLATE → TEXT: If session is open, send as free-form text using template body
     * with variables substituted. If session closed, reject delivery.
     */
    private DowngradeResult downgradeTemplate(OutboundMessageDto message, boolean sessionOpen) {
        if (!sessionOpen) {
            log.warn("Rejecting TEMPLATE message {} — session closed and template capability not supported",
                message.messageId());
            return new DowngradeResult.Rejected(
                "Cannot downgrade TEMPLATE to TEXT: session is closed and channel does not support templates");
        }

        log.info("Downgrading TEMPLATE message {} to TEXT — substituting variables into template body",
            message.messageId());

        // Build text from template name and parameters
        String textContent = substituteTemplateVariables(message);

        OutboundMessageDto downgraded = new OutboundMessageDto(
            message.messageId(),
            message.fatherId(),
            message.channel(),
            MessageType.TEXT,
            textContent,
            null,
            false, // no longer a template
            null,
            null,
            message.priority(),
            message.requestedAt()
        );
        return new DowngradeResult.Success(downgraded);
    }

    /**
     * Substitutes template variables ({{1}}, {{2}}, etc.) with their values.
     * If the message has text_content, uses it as the template body.
     * Otherwise, concatenates parameter values in order.
     */
    private String substituteTemplateVariables(OutboundMessageDto message) {
        String body = message.textContent();
        Map<String, String> params = message.templateParameters();

        if (body != null && !body.isBlank() && params != null && !params.isEmpty()) {
            // Replace placeholders {{1}}, {{2}}, etc. with parameter values
            String result = body;
            var sortedParams = new TreeMap<>(params);
            for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        }

        if (body != null && !body.isBlank()) {
            return body;
        }

        // Fallback: concatenate parameter values
        if (params != null && !params.isEmpty()) {
            var sortedParams = new TreeMap<>(params);
            return String.join(" ", sortedParams.values());
        }

        return "";
    }
}
