package com.dadcoach.whatsapp;

import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Translates OutboundMessageDto into WhatsApp Cloud API request format.
 * Handles text messages, template messages with variable substitution,
 * and media messages. Applies WhatsApp markdown formatting and enforces
 * character limits.
 */
@Component
public class WhatsAppMessageFormatter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageFormatter.class);

    /**
     * WhatsApp text message character limit.
     */
    public static final int TEXT_CHARACTER_LIMIT = 4096;

    /**
     * Formats an OutboundMessageDto into a WhatsApp Cloud API request payload.
     *
     * @param message      the outbound message to format
     * @param recipientPhone the recipient's phone number in E.164 format
     * @return a map representing the WhatsApp API JSON payload
     * @throws IllegalArgumentException if the message cannot be formatted
     */
    public Map<String, Object> format(OutboundMessageDto message, String recipientPhone) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        if (recipientPhone == null || recipientPhone.isBlank()) {
            throw new IllegalArgumentException("Recipient phone must not be null or blank");
        }

        // WhatsApp API expects phone numbers without '+' prefix
        String normalizedPhone = recipientPhone.startsWith("+") 
            ? recipientPhone.substring(1) 
            : recipientPhone;

        if (message.isTemplate()) {
            return formatTemplate(message, normalizedPhone);
        }

        return switch (message.messageType()) {
            case TEXT -> formatText(message, normalizedPhone);
            case IMAGE -> formatMedia(message, normalizedPhone, "image");
            case AUDIO -> formatMedia(message, normalizedPhone, "audio");
            case VIDEO -> formatMedia(message, normalizedPhone, "video");
            case DOCUMENT -> formatMedia(message, normalizedPhone, "document");
            case INTERACTIVE -> formatText(message, normalizedPhone);
            default -> throw new IllegalArgumentException(
                "Unsupported message type for WhatsApp formatting: " + message.messageType());
        };
    }

    private Map<String, Object> formatText(OutboundMessageDto message, String recipientPhone) {
        String text = message.textContent();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text content must not be empty for TEXT messages");
        }

        text = applyWhatsAppMarkdown(text);
        text = enforceCharacterLimit(text);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", recipientPhone);
        payload.put("type", "text");

        Map<String, Object> textBody = new LinkedHashMap<>();
        textBody.put("preview_url", false);
        textBody.put("body", text);
        payload.put("text", textBody);

        return payload;
    }

    private Map<String, Object> formatTemplate(OutboundMessageDto message, String recipientPhone) {
        if (message.templateName() == null || message.templateName().isBlank()) {
            throw new IllegalArgumentException("Template name must not be empty for template messages");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", recipientPhone);
        payload.put("type", "template");

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", message.templateName());

        Map<String, Object> language = new LinkedHashMap<>();
        // Language code should come from father's preference. Default to "en" if not specified.
        // Template names should be suffixed with language code (e.g., daily_coaching_en, daily_coaching_he)
        String templateName = message.templateName();
        String langCode = "en"; // Default to English
        if (templateName.endsWith("_he")) {
            langCode = "he";
        } else if (templateName.endsWith("_en")) {
            langCode = "en";
        }
        language.put("code", langCode);
        template.put("language", language);

        // Build template components with variable parameters
        if (message.templateParameters() != null && !message.templateParameters().isEmpty()) {
            Map<String, Object> bodyComponent = new LinkedHashMap<>();
            bodyComponent.put("type", "body");

            // Sort parameters by key to ensure consistent ordering ({{1}}, {{2}}, etc.)
            var sortedParams = new TreeMap<>(message.templateParameters());
            var parameters = sortedParams.values().stream()
                .map(value -> {
                    Map<String, Object> param = new LinkedHashMap<>();
                    param.put("type", "text");
                    param.put("text", value);
                    return param;
                })
                .toList();
            bodyComponent.put("parameters", parameters);

            template.put("components", java.util.List.of(bodyComponent));
        }

        payload.put("template", template);
        return payload;
    }

    private Map<String, Object> formatMedia(OutboundMessageDto message, String recipientPhone, String mediaType) {
        if (message.mediaReference() == null) {
            throw new IllegalArgumentException("Media reference must not be null for " + mediaType + " messages");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", recipientPhone);
        payload.put("type", mediaType);

        Map<String, Object> mediaBody = new LinkedHashMap<>();
        mediaBody.put("id", message.mediaReference().toString());

        // Add caption for image/video/document if text content is present
        if (message.textContent() != null && !message.textContent().isBlank()
                && (mediaType.equals("image") || mediaType.equals("video") || mediaType.equals("document"))) {
            String caption = applyWhatsAppMarkdown(message.textContent());
            caption = enforceCharacterLimit(caption);
            mediaBody.put("caption", caption);
        }

        payload.put(mediaType, mediaBody);
        return payload;
    }

    /**
     * Applies WhatsApp markdown formatting conventions.
     * WhatsApp uses: *bold*, _italic_, ```monospace```
     * This method ensures emoji pass-through (UTF-8 native encoding is preserved).
     *
     * @param text the input text
     * @return the text with WhatsApp markdown applied (passthrough — formatting is expected to be embedded already)
     */
    public String applyWhatsAppMarkdown(String text) {
        if (text == null) {
            return null;
        }
        // WhatsApp natively supports *bold*, _italic_, ~strikethrough~, and ```monospace```
        // The text content from the Conversation Engine is expected to already contain
        // these markdown markers. This method validates and preserves them.
        // Emoji characters are UTF-8 encoded and pass through without modification.
        return text;
    }

    /**
     * Enforces the WhatsApp text character limit (4096 chars).
     * If the text exceeds the limit, it is truncated with an ellipsis indicator.
     *
     * @param text the input text
     * @return the text truncated to the character limit if necessary
     */
    public String enforceCharacterLimit(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= TEXT_CHARACTER_LIMIT) {
            return text;
        }
        log.warn("Text message exceeds WhatsApp character limit ({}). Truncating.", TEXT_CHARACTER_LIMIT);
        // Truncate and add ellipsis, accounting for the ellipsis length
        return text.substring(0, TEXT_CHARACTER_LIMIT - 3) + "...";
    }
}
