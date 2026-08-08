package com.dadcoach.whatsapp;

import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Translates OutboundMessageDto into WhatsApp Cloud API request format.
 * Handles text messages, template messages with variable substitution,
 * media messages, and interactive messages (buttons and lists).
 * Applies WhatsApp markdown formatting and enforces character limits.
 */
@Component
public class WhatsAppMessageFormatter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageFormatter.class);

    /**
     * WhatsApp text message character limit.
     */
    public static final int TEXT_CHARACTER_LIMIT = 4096;

    /**
     * WhatsApp interactive button text limit.
     */
    public static final int BUTTON_TEXT_LIMIT = 20;

    /**
     * WhatsApp interactive list row title limit.
     */
    public static final int LIST_ROW_TITLE_LIMIT = 24;

    /**
     * Maximum number of buttons allowed.
     */
    public static final int MAX_BUTTONS = 3;

    /**
     * Maximum number of list sections allowed.
     */
    public static final int MAX_LIST_SECTIONS = 10;

    /**
     * Maximum number of rows per section.
     */
    public static final int MAX_ROWS_PER_SECTION = 10;

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

    // ─── Interactive Message Formatting ─────────────────────────────────────────

    /**
     * Formats a button message with quick reply buttons.
     *
     * @param recipientPhone the recipient's phone number in E.164 format
     * @param bodyText the message body text
     * @param buttons list of buttons, each with "id" and "title"
     * @return a map representing the WhatsApp API JSON payload
     */
    public Map<String, Object> formatButtonMessage(
            String recipientPhone,
            String bodyText,
            List<InteractiveButton> buttons) {
        
        if (buttons == null || buttons.isEmpty()) {
            throw new IllegalArgumentException("At least one button is required");
        }
        if (buttons.size() > MAX_BUTTONS) {
            throw new IllegalArgumentException("Maximum " + MAX_BUTTONS + " buttons allowed");
        }

        String normalizedPhone = normalizePhone(recipientPhone);
        
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizedPhone);
        payload.put("type", "interactive");

        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("type", "button");

        // Body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", enforceCharacterLimit(applyWhatsAppMarkdown(bodyText)));
        interactive.put("body", body);

        // Action with buttons
        Map<String, Object> action = new LinkedHashMap<>();
        List<Map<String, Object>> buttonsList = new ArrayList<>();
        
        for (InteractiveButton button : buttons) {
            Map<String, Object> buttonMap = new LinkedHashMap<>();
            buttonMap.put("type", "reply");
            
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", button.id());
            reply.put("title", truncateText(button.title(), BUTTON_TEXT_LIMIT));
            buttonMap.put("reply", reply);
            
            buttonsList.add(buttonMap);
        }
        action.put("buttons", buttonsList);
        interactive.put("action", action);

        payload.put("interactive", interactive);
        return payload;
    }

    /**
     * Formats a list message with selectable options.
     *
     * @param recipientPhone the recipient's phone number in E.164 format
     * @param headerText optional header text
     * @param bodyText the message body text
     * @param buttonText the text displayed on the list button
     * @param sections list of sections, each containing rows
     * @return a map representing the WhatsApp API JSON payload
     */
    public Map<String, Object> formatListMessage(
            String recipientPhone,
            String headerText,
            String bodyText,
            String buttonText,
            List<InteractiveSection> sections) {
        
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("At least one section is required");
        }
        if (sections.size() > MAX_LIST_SECTIONS) {
            throw new IllegalArgumentException("Maximum " + MAX_LIST_SECTIONS + " sections allowed");
        }

        String normalizedPhone = normalizePhone(recipientPhone);
        
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizedPhone);
        payload.put("type", "interactive");

        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("type", "list");

        // Header (optional)
        if (headerText != null && !headerText.isBlank()) {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("type", "text");
            header.put("text", truncateText(headerText, 60));
            interactive.put("header", header);
        }

        // Body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", enforceCharacterLimit(applyWhatsAppMarkdown(bodyText)));
        interactive.put("body", body);

        // Action
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("button", truncateText(buttonText, BUTTON_TEXT_LIMIT));

        List<Map<String, Object>> sectionsList = new ArrayList<>();
        for (InteractiveSection section : sections) {
            Map<String, Object> sectionMap = new LinkedHashMap<>();
            
            if (section.title() != null && !section.title().isBlank()) {
                sectionMap.put("title", truncateText(section.title(), LIST_ROW_TITLE_LIMIT));
            }

            List<Map<String, Object>> rowsList = new ArrayList<>();
            List<InteractiveRow> rows = section.rows();
            if (rows.size() > MAX_ROWS_PER_SECTION) {
                rows = rows.subList(0, MAX_ROWS_PER_SECTION);
                log.warn("Section has more than {} rows, truncating", MAX_ROWS_PER_SECTION);
            }

            for (InteractiveRow row : rows) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                rowMap.put("id", row.id());
                rowMap.put("title", truncateText(row.title(), LIST_ROW_TITLE_LIMIT));
                if (row.description() != null && !row.description().isBlank()) {
                    rowMap.put("description", truncateText(row.description(), 72));
                }
                rowsList.add(rowMap);
            }
            sectionMap.put("rows", rowsList);
            sectionsList.add(sectionMap);
        }
        action.put("sections", sectionsList);
        interactive.put("action", action);

        payload.put("interactive", interactive);
        return payload;
    }

    /**
     * Convenience method to format a simple list with a single section.
     *
     * @param recipientPhone the recipient's phone number
     * @param bodyText the message body
     * @param buttonText the list button text
     * @param rows the list rows
     * @return formatted payload
     */
    public Map<String, Object> formatSimpleList(
            String recipientPhone,
            String bodyText,
            String buttonText,
            List<InteractiveRow> rows) {
        
        InteractiveSection section = new InteractiveSection(null, rows);
        return formatListMessage(recipientPhone, null, bodyText, buttonText, List.of(section));
    }

    // ─── Image Message Formatting ───────────────────────────────────────────────

    /**
     * Formats an image message with URL.
     *
     * @param recipientPhone the recipient's phone number in E.164 format
     * @param imageUrl the URL of the image
     * @param caption optional caption for the image
     * @return a map representing the WhatsApp API JSON payload
     */
    public Map<String, Object> formatImageMessage(String recipientPhone, String imageUrl, String caption) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("Image URL must not be empty");
        }

        String normalizedPhone = normalizePhone(recipientPhone);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizedPhone);
        payload.put("type", "image");

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("link", imageUrl);
        
        if (caption != null && !caption.isBlank()) {
            image.put("caption", enforceCharacterLimit(applyWhatsAppMarkdown(caption)));
        }

        payload.put("image", image);
        return payload;
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────────

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be empty");
        }
        return phone.startsWith("+") ? phone.substring(1) : phone;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }

    // ─── DTOs for Interactive Messages ──────────────────────────────────────────

    /**
     * Represents a button in an interactive button message.
     */
    public record InteractiveButton(String id, String title) {
        public InteractiveButton {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Button id must not be empty");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Button title must not be empty");
            }
        }
    }

    /**
     * Represents a section in an interactive list message.
     */
    public record InteractiveSection(String title, List<InteractiveRow> rows) {
        public InteractiveSection {
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("Section must have at least one row");
            }
        }
    }

    /**
     * Represents a row in an interactive list section.
     */
    public record InteractiveRow(String id, String title, String description) {
        public InteractiveRow(String id, String title) {
            this(id, title, null);
        }

        public InteractiveRow {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Row id must not be empty");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Row title must not be empty");
            }
        }
    }
}
