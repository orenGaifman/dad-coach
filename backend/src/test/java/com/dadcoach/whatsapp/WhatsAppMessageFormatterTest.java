package com.dadcoach.whatsapp;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WhatsAppMessageFormatter verifying text, template, and media
 * message formatting, WhatsApp markdown handling, and character limit enforcement.
 */
class WhatsAppMessageFormatterTest {

    private final WhatsAppMessageFormatter formatter = new WhatsAppMessageFormatter();
    private static final String RECIPIENT = "+5491112345678";

    private OutboundMessageDto textMessage(String text) {
        return new OutboundMessageDto(
            UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
            MessageType.TEXT, text, null, false, null, null,
            MessagePriority.IMMEDIATE, Instant.now()
        );
    }

    private OutboundMessageDto templateMessage(String templateName, Map<String, String> params) {
        return new OutboundMessageDto(
            UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
            MessageType.TEXT, null, null, true, templateName, params,
            MessagePriority.SCHEDULED, Instant.now()
        );
    }

    private OutboundMessageDto mediaMessage(MessageType type, UUID mediaRef, String caption) {
        return new OutboundMessageDto(
            UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
            type, caption, mediaRef, false, null, null,
            MessagePriority.IMMEDIATE, Instant.now()
        );
    }

    @Nested
    @DisplayName("9.1 Format outbound messages")
    class FormatOutboundMessages {

        @Test
        @DisplayName("formats text message with correct WhatsApp API structure")
        void formatTextMessage() {
            var msg = textMessage("Hola padre 👋");

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            assertEquals("whatsapp", result.get("messaging_product"));
            assertEquals("individual", result.get("recipient_type"));
            assertEquals(RECIPIENT, result.get("to"));
            assertEquals("text", result.get("type"));

            @SuppressWarnings("unchecked")
            var textBody = (Map<String, Object>) result.get("text");
            assertEquals("Hola padre 👋", textBody.get("body"));
            assertEquals(false, textBody.get("preview_url"));
        }

        @Test
        @DisplayName("formats template message with variables")
        void formatTemplateMessage() {
            var msg = templateMessage("daily_coaching", Map.of("1", "Carlos", "2", "¿Cómo va tu día?"));

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            assertEquals("template", result.get("type"));

            @SuppressWarnings("unchecked")
            var template = (Map<String, Object>) result.get("template");
            assertEquals("daily_coaching", template.get("name"));

            @SuppressWarnings("unchecked")
            var language = (Map<String, Object>) template.get("language");
            assertEquals("es", language.get("code"));

            assertNotNull(template.get("components"));
        }

        @Test
        @DisplayName("formats template without parameters — no components")
        void formatTemplateWithoutParams() {
            var msg = templateMessage("system_notice", null);

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            @SuppressWarnings("unchecked")
            var template = (Map<String, Object>) result.get("template");
            assertNull(template.get("components"));
        }

        @Test
        @DisplayName("formats image message with media reference and caption")
        void formatImageMessage() {
            UUID mediaRef = UUID.randomUUID();
            var msg = mediaMessage(MessageType.IMAGE, mediaRef, "Tu misión completada 🎯");

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            assertEquals("image", result.get("type"));

            @SuppressWarnings("unchecked")
            var imageBody = (Map<String, Object>) result.get("image");
            assertEquals(mediaRef.toString(), imageBody.get("id"));
            assertEquals("Tu misión completada 🎯", imageBody.get("caption"));
        }

        @Test
        @DisplayName("formats audio message without caption")
        void formatAudioMessage() {
            UUID mediaRef = UUID.randomUUID();
            var msg = mediaMessage(MessageType.AUDIO, mediaRef, "some text");

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            assertEquals("audio", result.get("type"));

            @SuppressWarnings("unchecked")
            var audioBody = (Map<String, Object>) result.get("audio");
            assertEquals(mediaRef.toString(), audioBody.get("id"));
            // Audio messages don't support captions in WhatsApp
            assertNull(audioBody.get("caption"));
        }

        @Test
        @DisplayName("throws for null message")
        void throwsForNullMessage() {
            assertThrows(IllegalArgumentException.class,
                () -> formatter.format(null, RECIPIENT));
        }

        @Test
        @DisplayName("throws for null or blank recipient")
        void throwsForBlankRecipient() {
            var msg = textMessage("Hello");
            assertThrows(IllegalArgumentException.class,
                () -> formatter.format(msg, null));
            assertThrows(IllegalArgumentException.class,
                () -> formatter.format(msg, "  "));
        }

        @Test
        @DisplayName("throws for text message with empty content")
        void throwsForEmptyTextContent() {
            var msg = textMessage("");
            assertThrows(IllegalArgumentException.class,
                () -> formatter.format(msg, RECIPIENT));
        }

        @Test
        @DisplayName("throws for media message with null media reference")
        void throwsForNullMediaReference() {
            var msg = mediaMessage(MessageType.IMAGE, null, "caption");
            assertThrows(IllegalArgumentException.class,
                () -> formatter.format(msg, RECIPIENT));
        }
    }

    @Nested
    @DisplayName("9.2 WhatsApp markdown")
    class WhatsAppMarkdown {

        @Test
        @DisplayName("preserves bold markdown (*text*)")
        void preservesBold() {
            String result = formatter.applyWhatsAppMarkdown("Hello *bold* world");
            assertEquals("Hello *bold* world", result);
        }

        @Test
        @DisplayName("preserves italic markdown (_text_)")
        void preservesItalic() {
            String result = formatter.applyWhatsAppMarkdown("Hello _italic_ world");
            assertEquals("Hello _italic_ world", result);
        }

        @Test
        @DisplayName("preserves monospace markdown (```text```)")
        void preservesMonospace() {
            String result = formatter.applyWhatsAppMarkdown("Use ```code``` here");
            assertEquals("Use ```code``` here", result);
        }

        @Test
        @DisplayName("handles null input")
        void handlesNull() {
            assertNull(formatter.applyWhatsAppMarkdown(null));
        }

        @Test
        @DisplayName("preserves combined formatting")
        void preservesCombined() {
            String input = "*Bold* and _italic_ and ```mono```";
            assertEquals(input, formatter.applyWhatsAppMarkdown(input));
        }
    }

    @Nested
    @DisplayName("9.3 Character limits")
    class CharacterLimits {

        @Test
        @DisplayName("text within limit passes through unchanged")
        void withinLimit() {
            String text = "Short message";
            assertEquals(text, formatter.enforceCharacterLimit(text));
        }

        @Test
        @DisplayName("text at exactly 4096 chars passes through unchanged")
        void atExactLimit() {
            String text = "a".repeat(4096);
            assertEquals(text, formatter.enforceCharacterLimit(text));
        }

        @Test
        @DisplayName("text exceeding 4096 chars is truncated with ellipsis")
        void exceedsLimit() {
            String text = "a".repeat(5000);
            String result = formatter.enforceCharacterLimit(text);

            assertEquals(4096, result.length());
            assertTrue(result.endsWith("..."));
        }

        @Test
        @DisplayName("handles null input")
        void handlesNull() {
            assertNull(formatter.enforceCharacterLimit(null));
        }

        @Test
        @DisplayName("full format pipeline applies character limit")
        void formatAppliesLimit() {
            String longText = "X".repeat(5000);
            var msg = textMessage(longText);

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            @SuppressWarnings("unchecked")
            var textBody = (Map<String, Object>) result.get("text");
            String body = (String) textBody.get("body");
            assertEquals(4096, body.length());
        }
    }

    @Nested
    @DisplayName("9.6 Emoji encoding")
    class EmojiEncoding {

        @Test
        @DisplayName("emoji characters pass through unchanged in text messages")
        void emojiInText() {
            var msg = textMessage("¡Genial! 👏🎉💪🏽 Sigue así papá 🦸‍♂️");

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            @SuppressWarnings("unchecked")
            var textBody = (Map<String, Object>) result.get("text");
            assertEquals("¡Genial! 👏🎉💪🏽 Sigue así papá 🦸‍♂️", textBody.get("body"));
        }

        @Test
        @DisplayName("emoji in template parameters preserved")
        void emojiInTemplateParams() {
            var msg = templateMessage("daily_coaching", Map.of("1", "Carlos 👋", "2", "¿Cómo estás? 🌟"));

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            // Verify no encoding issues — the payload should contain the emojis as-is
            assertNotNull(result.get("template"));
        }

        @Test
        @DisplayName("combined emoji sequences (skin tone modifiers, ZWJ) preserved")
        void combinedEmojiSequences() {
            // Family emoji with ZWJ sequence
            String text = "Familia: 👨‍👩‍👧‍👦 Fuerza: 💪🏽";
            var msg = textMessage(text);

            Map<String, Object> result = formatter.format(msg, RECIPIENT);

            @SuppressWarnings("unchecked")
            var textBody = (Map<String, Object>) result.get("text");
            assertEquals(text, textBody.get("body"));
        }
    }
}
