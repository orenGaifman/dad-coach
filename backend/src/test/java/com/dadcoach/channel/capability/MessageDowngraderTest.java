package com.dadcoach.channel.capability;

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
 * Unit tests for MessageDowngrader verifying downgrade rules when a channel
 * does not support a requested message type.
 */
class MessageDowngraderTest {

    private final MessageDowngrader downgrader = new MessageDowngrader();

    private OutboundMessageDto message(MessageType type, String text, UUID mediaRef) {
        return new OutboundMessageDto(
            UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
            type, text, mediaRef, false, null, null,
            MessagePriority.IMMEDIATE, Instant.now()
        );
    }

    private OutboundMessageDto templateMessage(String templateName, String body, Map<String, String> params) {
        return new OutboundMessageDto(
            UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
            MessageType.TEXT, body, null, true, templateName, params,
            MessagePriority.SCHEDULED, Instant.now()
        );
    }

    @Nested
    @DisplayName("No downgrade needed — capability supported")
    class NoDowngradeNeeded {

        @Test
        @DisplayName("text message on channel with text support passes through unchanged")
        void textSupported() {
            var msg = message(MessageType.TEXT, "Hello", null);
            var caps = ChannelCapabilities.allSupported();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals(msg, success.downgradedMessage());
        }

        @Test
        @DisplayName("image message on channel with image support passes through unchanged")
        void imageSupported() {
            var msg = message(MessageType.IMAGE, "Caption", UUID.randomUUID());
            var caps = ChannelCapabilities.allSupported();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals(msg, success.downgradedMessage());
        }
    }

    @Nested
    @DisplayName("9.4 Unsupported media → text description")
    class MediaDowngrade {

        @Test
        @DisplayName("IMAGE → TEXT: delivers text_content only, drops media reference")
        void imageDowngradeToText() {
            var msg = message(MessageType.IMAGE, "Aquí tienes tu progreso", UUID.randomUUID());
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            var downgraded = success.downgradedMessage();

            assertEquals(MessageType.TEXT, downgraded.messageType());
            assertEquals("Aquí tienes tu progreso", downgraded.textContent());
            assertNull(downgraded.mediaReference());
        }

        @Test
        @DisplayName("IMAGE → TEXT: rejects if no text_content available")
        void imageDowngradeRejectsIfNoText() {
            var msg = message(MessageType.IMAGE, null, UUID.randomUUID());
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Rejected.class, result);
        }

        @Test
        @DisplayName("AUDIO → TEXT: always rejects (no text equivalent for voice)")
        void audioAlwaysRejected() {
            var msg = message(MessageType.AUDIO, "Listen to this", UUID.randomUUID());
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Rejected.class, result);
            var rejected = (MessageDowngrader.DowngradeResult.Rejected) result;
            assertTrue(rejected.reason().contains("AUDIO"));
        }

        @Test
        @DisplayName("VIDEO → TEXT: delivers text_content only, drops media reference")
        void videoDowngradeToText() {
            var msg = message(MessageType.VIDEO, "Tutorial de la misión", UUID.randomUUID());
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals(MessageType.TEXT, success.downgradedMessage().messageType());
            assertEquals("Tutorial de la misión", success.downgradedMessage().textContent());
        }

        @Test
        @DisplayName("INTERACTIVE → TEXT: downgrades to text with content preserved")
        void interactiveDowngradeToText() {
            var msg = message(MessageType.INTERACTIVE, "1. Aceptar misión\n2. Rechazar", null);
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            var downgraded = success.downgradedMessage();

            assertEquals(MessageType.TEXT, downgraded.messageType());
            assertEquals("1. Aceptar misión\n2. Rechazar", downgraded.textContent());
        }
    }

    @Nested
    @DisplayName("9.5 Unsupported template → plain text equivalent")
    class TemplateDowngrade {

        @Test
        @DisplayName("TEMPLATE with session open → TEXT with variables substituted")
        void templateDowngradeSessionOpen() {
            var msg = templateMessage("daily_coaching",
                "Hello {{1}} 👋 {{2}}",
                Map.of("1", "Carlos", "2", "How's your day going?"));
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            var downgraded = success.downgradedMessage();

            assertEquals(MessageType.TEXT, downgraded.messageType());
            assertEquals("Hello Carlos 👋 How's your day going?", downgraded.textContent());
            assertFalse(downgraded.isTemplate());
        }

        @Test
        @DisplayName("TEMPLATE with session closed → rejected")
        void templateDowngradeSessionClosed() {
            var msg = templateMessage("daily_coaching",
                "Hello {{1}} 👋",
                Map.of("1", "Carlos"));
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, false);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Rejected.class, result);
            var rejected = (MessageDowngrader.DowngradeResult.Rejected) result;
            assertTrue(rejected.reason().contains("session is closed"));
        }

        @Test
        @DisplayName("TEMPLATE with body only (no params) → TEXT with body as-is")
        void templateDowngradeBodyOnly() {
            var msg = templateMessage("system_notice", "Sistema en mantenimiento", null);
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals("Sistema en mantenimiento", success.downgradedMessage().textContent());
        }

        @Test
        @DisplayName("TEMPLATE with params but no body → concatenates values")
        void templateDowngradeParamsNoBody() {
            var msg = templateMessage("custom", null, Map.of("1", "Hello", "2", "World"));
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals("Hello World", success.downgradedMessage().textContent());
        }
    }

    @Nested
    @DisplayName("Edge cases and validation")
    class EdgeCases {

        @Test
        @DisplayName("throws for null message")
        void throwsForNullMessage() {
            assertThrows(IllegalArgumentException.class,
                () -> downgrader.downgradeIfNeeded(null, ChannelCapabilities.allSupported(), true));
        }

        @Test
        @DisplayName("throws for null capabilities")
        void throwsForNullCapabilities() {
            var msg = message(MessageType.TEXT, "Hello", null);
            assertThrows(IllegalArgumentException.class,
                () -> downgrader.downgradeIfNeeded(msg, null, true));
        }

        @Test
        @DisplayName("preserves messageId and fatherId through downgrade")
        void preservesIdentifiers() {
            UUID messageId = UUID.randomUUID();
            UUID fatherId = UUID.randomUUID();
            var msg = new OutboundMessageDto(
                messageId, fatherId, "WHATSAPP",
                MessageType.IMAGE, "Caption text", UUID.randomUUID(),
                false, null, null,
                MessagePriority.IMMEDIATE, Instant.now()
            );
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals(messageId, success.downgradedMessage().messageId());
            assertEquals(fatherId, success.downgradedMessage().fatherId());
        }

        @Test
        @DisplayName("preserves priority through downgrade")
        void preservesPriority() {
            var msg = new OutboundMessageDto(
                UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
                MessageType.INTERACTIVE, "1. Yes\n2. No", null,
                false, null, null,
                MessagePriority.SCHEDULED, Instant.now()
            );
            var caps = ChannelCapabilities.textOnly();

            var result = downgrader.downgradeIfNeeded(msg, caps, true);

            assertInstanceOf(MessageDowngrader.DowngradeResult.Success.class, result);
            var success = (MessageDowngrader.DowngradeResult.Success) result;
            assertEquals(MessagePriority.SCHEDULED, success.downgradedMessage().priority());
        }
    }
}
