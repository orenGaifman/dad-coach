package com.dadcoach.whatsapp;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.StatusUpdateDto;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WhatsAppMessageParser verifying parsing of all WhatsApp
 * message types, status updates, and error handling for invalid payloads.
 */
class WhatsAppMessageParserTest {

    private final WhatsAppMessageParser parser = new WhatsAppMessageParser();

    // -- Helper Methods --

    private WhatsAppWebhookPayload buildPayload(List<Message> messages, List<Status> statuses) {
        var value = new Value("whatsapp", metadata(), contacts(), messages, statuses);
        var change = new Change(value, "messages");
        var entry = new Entry("BIZ_ID", List.of(change));
        return new WhatsAppWebhookPayload("whatsapp_business_account", List.of(entry));
    }

    private WhatsAppWebhookPayload buildMessagePayload(Message message) {
        return buildPayload(List.of(message), null);
    }

    private WhatsAppWebhookPayload buildStatusPayload(Status status) {
        return buildPayload(null, List.of(status));
    }

    private Metadata metadata() {
        return new Metadata("+1234567890", "PHONE_NUMBER_ID");
    }

    private List<Contact> contacts() {
        return List.of(new Contact(new Profile("Carlos"), "5491112345678"));
    }

    private Message textMessage(String body) {
        return new Message(
            "5491112345678", "wamid.HBgNNTQ5MTExMjM0NTY3OA", "1700000000",
            "text", new TextBody(body), null, null, null, null, null, null, null
        );
    }

    private Message imageMessage(String caption) {
        return new Message(
            "5491112345678", "wamid.IMG001", "1700000000",
            "image", null, new MediaBody("media_id_123", "image/jpeg", "sha256hash", caption),
            null, null, null, null, null, null
        );
    }

    private Message audioMessage() {
        return new Message(
            "5491112345678", "wamid.AUDIO001", "1700000000",
            "audio", null, null, new MediaBody("media_id_audio", "audio/ogg", "sha256hash", null),
            null, null, null, null, null
        );
    }

    private Message videoMessage(String caption) {
        return new Message(
            "5491112345678", "wamid.VIDEO001", "1700000000",
            "video", null, null, null,
            new MediaBody("media_id_video", "video/mp4", "sha256hash", caption),
            null, null, null, null
        );
    }

    private Message documentMessage(String caption) {
        return new Message(
            "5491112345678", "wamid.DOC001", "1700000000",
            "document", null, null, null, null,
            new MediaBody("media_id_doc", "application/pdf", "sha256hash", caption),
            null, null, null
        );
    }

    private Message locationMessage(double lat, double lng, String name, String address) {
        return new Message(
            "5491112345678", "wamid.LOC001", "1700000000",
            "location", null, null, null, null, null,
            new LocationBody(lat, lng, name, address), null, null
        );
    }

    private Message reactionMessage(String emoji) {
        return new Message(
            "5491112345678", "wamid.REACT001", "1700000000",
            "reaction", null, null, null, null, null, null,
            new ReactionBody("wamid.ORIGINAL", emoji), null
        );
    }

    private Message interactiveButtonMessage(String buttonId, String buttonTitle) {
        return new Message(
            "5491112345678", "wamid.INTER001", "1700000000",
            "interactive", null, null, null, null, null, null, null,
            new InteractiveReply("button_reply", new ButtonReply(buttonId, buttonTitle), null)
        );
    }

    private Message interactiveListMessage(String listId, String listTitle) {
        return new Message(
            "5491112345678", "wamid.INTER002", "1700000000",
            "interactive", null, null, null, null, null, null, null,
            new InteractiveReply("list_reply", null, new ListReply(listId, listTitle, "Some description"))
        );
    }

    // -- Tests --

    @Nested
    @DisplayName("4.1 Parse text messages, media messages, and status updates")
    class ParseMessagesAndStatuses {

        @Test
        @DisplayName("parses a text message from webhook payload")
        void parsesTextMessage() {
            var payload = buildMessagePayload(textMessage("Hola, necesito ayuda"));

            var result = parser.parse(payload);

            assertEquals(1, result.messages().size());
            assertEquals(0, result.statusUpdates().size());

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.TEXT, msg.messageType());
            assertEquals("Hola, necesito ayuda", msg.textContent());
        }

        @Test
        @DisplayName("parses an image message with caption")
        void parsesImageMessage() {
            var payload = buildMessagePayload(imageMessage("Mi hijo jugando ⚽"));

            var result = parser.parse(payload);

            assertEquals(1, result.messages().size());
            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.IMAGE, msg.messageType());
            assertEquals("Mi hijo jugando ⚽", msg.textContent());
            assertNull(msg.mediaReference()); // Populated later by media service
        }

        @Test
        @DisplayName("parses status updates from webhook payload")
        void parsesStatusUpdates() {
            var status = new Status("wamid.SENT001", "delivered", "1700000100", "5491112345678", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            assertEquals(0, result.messages().size());
            assertEquals(1, result.statusUpdates().size());

            StatusUpdateDto update = result.statusUpdates().get(0);
            assertEquals("wamid.SENT001", update.providerMessageId());
            assertEquals("delivered", update.status());
            assertEquals("5491112345678", update.recipientId());
        }

        @Test
        @DisplayName("parses payload with both messages and status updates")
        void parsesBothMessagesAndStatuses() {
            var message = textMessage("Hola");
            var status = new Status("wamid.X", "read", "1700000200", "5491112345678", null);
            var payload = buildPayload(List.of(message), List.of(status));

            var result = parser.parse(payload);

            assertEquals(1, result.messages().size());
            assertEquals(1, result.statusUpdates().size());
        }

        @Test
        @DisplayName("parses multiple messages in a single payload")
        void parsesMultipleMessages() {
            var msg1 = textMessage("First");
            var msg2 = textMessage("Second");
            var payload = buildPayload(List.of(msg1, msg2), null);

            var result = parser.parse(payload);

            assertEquals(2, result.messages().size());
        }
    }

    @Nested
    @DisplayName("4.2 Extract sender phone, content, type, timestamp, provider_message_id")
    class ExtractFields {

        @Test
        @DisplayName("extracts sender phone number as fatherChannelIdentity")
        void extractsSenderPhone() {
            var payload = buildMessagePayload(textMessage("Hi"));

            var result = parser.parse(payload);
            InboundMessageDto msg = result.messages().get(0);

            assertEquals("5491112345678", msg.fatherChannelIdentity());
        }

        @Test
        @DisplayName("extracts provider_message_id as idempotencyKey")
        void extractsProviderMessageId() {
            var payload = buildMessagePayload(textMessage("Hi"));

            var result = parser.parse(payload);
            InboundMessageDto msg = result.messages().get(0);

            assertEquals("wamid.HBgNNTQ5MTExMjM0NTY3OA", msg.idempotencyKey());
        }

        @Test
        @DisplayName("extracts Unix timestamp as receivedAt")
        void extractsTimestamp() {
            var payload = buildMessagePayload(textMessage("Hi"));

            var result = parser.parse(payload);
            InboundMessageDto msg = result.messages().get(0);

            assertEquals(Instant.ofEpochSecond(1700000000L), msg.receivedAt());
        }

        @Test
        @DisplayName("assigns WHATSAPP as channel")
        void assignsChannel() {
            var payload = buildMessagePayload(textMessage("Hi"));

            var result = parser.parse(payload);
            InboundMessageDto msg = result.messages().get(0);

            assertEquals("WHATSAPP", msg.channel());
        }

        @Test
        @DisplayName("assigns a unique messageId (UUID)")
        void assignsUniqueMessageId() {
            var payload = buildMessagePayload(textMessage("Hi"));

            var result = parser.parse(payload);
            InboundMessageDto msg = result.messages().get(0);

            assertNotNull(msg.messageId());
        }

        @Test
        @DisplayName("sets ingestedAt to approximately now")
        void setsIngestedAt() {
            Instant before = Instant.now();
            var payload = buildMessagePayload(textMessage("Hi"));

            var result = parser.parse(payload);
            InboundMessageDto msg = result.messages().get(0);

            Instant after = Instant.now();
            assertNotNull(msg.ingestedAt());
            assertFalse(msg.ingestedAt().isBefore(before));
            assertFalse(msg.ingestedAt().isAfter(after));
        }
    }

    @Nested
    @DisplayName("4.3 Handle all WhatsApp message types")
    class HandleAllMessageTypes {

        @Test
        @DisplayName("handles text messages")
        void handlesText() {
            var payload = buildMessagePayload(textMessage("Hello world"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.TEXT, msg.messageType());
            assertEquals("Hello world", msg.textContent());
        }

        @Test
        @DisplayName("handles image messages without caption")
        void handlesImageWithoutCaption() {
            var payload = buildMessagePayload(imageMessage(null));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.IMAGE, msg.messageType());
            assertNull(msg.textContent());
        }

        @Test
        @DisplayName("handles image messages with caption")
        void handlesImageWithCaption() {
            var payload = buildMessagePayload(imageMessage("Look at this!"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.IMAGE, msg.messageType());
            assertEquals("Look at this!", msg.textContent());
        }

        @Test
        @DisplayName("handles audio messages (no text content)")
        void handlesAudio() {
            var payload = buildMessagePayload(audioMessage());
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.AUDIO, msg.messageType());
            assertNull(msg.textContent());
        }

        @Test
        @DisplayName("handles video messages with caption")
        void handlesVideoWithCaption() {
            var payload = buildMessagePayload(videoMessage("Watch this 🎬"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.VIDEO, msg.messageType());
            assertEquals("Watch this 🎬", msg.textContent());
        }

        @Test
        @DisplayName("handles video messages without caption")
        void handlesVideoWithoutCaption() {
            var payload = buildMessagePayload(videoMessage(null));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.VIDEO, msg.messageType());
            assertNull(msg.textContent());
        }

        @Test
        @DisplayName("handles document messages")
        void handlesDocument() {
            var payload = buildMessagePayload(documentMessage("My report"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.DOCUMENT, msg.messageType());
            assertEquals("My report", msg.textContent());
        }

        @Test
        @DisplayName("handles location messages with name and address")
        void handlesLocationWithNameAndAddress() {
            var payload = buildMessagePayload(
                locationMessage(-34.6037, -58.3816, "Plaza de Mayo", "Buenos Aires, Argentina"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.LOCATION, msg.messageType());
            assertEquals("Plaza de Mayo - Buenos Aires, Argentina", msg.textContent());
        }

        @Test
        @DisplayName("handles location messages with coordinates only")
        void handlesLocationCoordinatesOnly() {
            var payload = buildMessagePayload(locationMessage(-34.6037, -58.3816, null, null));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.LOCATION, msg.messageType());
            assertEquals("-34.603700, -58.381600", msg.textContent());
        }

        @Test
        @DisplayName("handles reaction messages")
        void handlesReaction() {
            var payload = buildMessagePayload(reactionMessage("👍"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.REACTION, msg.messageType());
            assertEquals("👍", msg.textContent());
        }

        @Test
        @DisplayName("handles interactive button reply messages")
        void handlesInteractiveButton() {
            var payload = buildMessagePayload(interactiveButtonMessage("btn_1", "Aceptar misión"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.INTERACTIVE, msg.messageType());
            assertEquals("Aceptar misión", msg.textContent());
        }

        @Test
        @DisplayName("handles interactive list reply messages")
        void handlesInteractiveList() {
            var payload = buildMessagePayload(interactiveListMessage("opt_2", "Jugar con mi hijo"));
            var result = parser.parse(payload);

            InboundMessageDto msg = result.messages().get(0);
            assertEquals(MessageType.INTERACTIVE, msg.messageType());
            assertEquals("Jugar con mi hijo", msg.textContent());
        }
    }

    @Nested
    @DisplayName("4.4 Extract status updates for delivery tracking")
    class StatusUpdates {

        @Test
        @DisplayName("extracts 'sent' status update")
        void extractsSentStatus() {
            var status = new Status("wamid.MSG001", "sent", "1700000100", "5491112345678", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            assertEquals(1, result.statusUpdates().size());
            StatusUpdateDto update = result.statusUpdates().get(0);
            assertEquals("sent", update.status());
            assertEquals("wamid.MSG001", update.providerMessageId());
        }

        @Test
        @DisplayName("extracts 'delivered' status update")
        void extractsDeliveredStatus() {
            var status = new Status("wamid.MSG002", "delivered", "1700000200", "5491112345678", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            StatusUpdateDto update = result.statusUpdates().get(0);
            assertEquals("delivered", update.status());
            assertEquals(Instant.ofEpochSecond(1700000200L), update.timestamp());
        }

        @Test
        @DisplayName("extracts 'read' status update")
        void extractsReadStatus() {
            var status = new Status("wamid.MSG003", "read", "1700000300", "5491112345678", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            StatusUpdateDto update = result.statusUpdates().get(0);
            assertEquals("read", update.status());
        }

        @Test
        @DisplayName("extracts 'failed' status with error details")
        void extractsFailedStatusWithError() {
            var errors = List.of(new WhatsAppWebhookPayload.Error(131047, "Re-engagement message", "Message failed to send"));
            var status = new Status("wamid.MSG004", "failed", "1700000400", "5491112345678", errors);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            StatusUpdateDto update = result.statusUpdates().get(0);
            assertEquals("failed", update.status());
            assertEquals(131047, update.errorCode());
            assertEquals("Re-engagement message", update.errorMessage());
        }

        @Test
        @DisplayName("extracts recipient ID from status update")
        void extractsRecipientId() {
            var status = new Status("wamid.MSG005", "delivered", "1700000500", "5491198765432", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            StatusUpdateDto update = result.statusUpdates().get(0);
            assertEquals("5491198765432", update.recipientId());
        }

        @Test
        @DisplayName("handles multiple status updates in one payload")
        void handlesMultipleStatuses() {
            var status1 = new Status("wamid.A", "sent", "1700000100", "5491112345678", null);
            var status2 = new Status("wamid.B", "delivered", "1700000200", "5491112345678", null);
            var payload = buildPayload(null, List.of(status1, status2));

            var result = parser.parse(payload);

            assertEquals(2, result.statusUpdates().size());
        }
    }

    @Nested
    @DisplayName("4.5 Log and discard invalid/unparseable payloads")
    class InvalidPayloads {

        @Test
        @DisplayName("returns empty result for null payload")
        void handlesNullPayload() {
            var result = parser.parse(null);

            assertNotNull(result);
            assertTrue(result.messages().isEmpty());
            assertTrue(result.statusUpdates().isEmpty());
        }

        @Test
        @DisplayName("returns empty result for wrong object type")
        void handlesWrongObjectType() {
            var payload = new WhatsAppWebhookPayload("instagram", List.of());

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
            assertTrue(result.statusUpdates().isEmpty());
        }

        @Test
        @DisplayName("returns empty result for payload with no entries")
        void handlesNoEntries() {
            var payload = new WhatsAppWebhookPayload("whatsapp_business_account", List.of());

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
        }

        @Test
        @DisplayName("returns empty result for payload with null entries")
        void handlesNullEntries() {
            var payload = new WhatsAppWebhookPayload("whatsapp_business_account", null);

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
        }

        @Test
        @DisplayName("skips messages with null id")
        void skipsMessageWithNullId() {
            var msg = new Message("5491112345678", null, "1700000000", "text",
                new TextBody("Hi"), null, null, null, null, null, null, null);
            var payload = buildMessagePayload(msg);

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
        }

        @Test
        @DisplayName("skips messages with null from")
        void skipsMessageWithNullFrom() {
            var msg = new Message(null, "wamid.X", "1700000000", "text",
                new TextBody("Hi"), null, null, null, null, null, null, null);
            var payload = buildMessagePayload(msg);

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
        }

        @Test
        @DisplayName("skips messages with unsupported type (e.g., sticker)")
        void skipsUnsupportedType() {
            var msg = new Message("5491112345678", "wamid.STICKER", "1700000000",
                "sticker", null, null, null, null, null, null, null, null);
            var payload = buildMessagePayload(msg);

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
        }

        @Test
        @DisplayName("skips status updates with null id")
        void skipsStatusWithNullId() {
            var status = new Status(null, "delivered", "1700000100", "5491112345678", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            assertTrue(result.statusUpdates().isEmpty());
        }

        @Test
        @DisplayName("skips status updates with null status field")
        void skipsStatusWithNullStatus() {
            var status = new Status("wamid.X", null, "1700000100", "5491112345678", null);
            var payload = buildStatusPayload(status);

            var result = parser.parse(payload);

            assertTrue(result.statusUpdates().isEmpty());
        }

        @Test
        @DisplayName("handles invalid timestamp gracefully (uses current time)")
        void handlesInvalidTimestamp() {
            var msg = new Message("5491112345678", "wamid.BAD_TS", "not_a_number",
                "text", new TextBody("Hi"), null, null, null, null, null, null, null);
            var payload = buildMessagePayload(msg);

            Instant before = Instant.now();
            var result = parser.parse(payload);
            Instant after = Instant.now();

            assertEquals(1, result.messages().size());
            InboundMessageDto parsed = result.messages().get(0);
            assertFalse(parsed.receivedAt().isBefore(before));
            assertFalse(parsed.receivedAt().isAfter(after));
        }

        @Test
        @DisplayName("handles null changes in entry gracefully")
        void handlesNullChanges() {
            var entry = new Entry("BIZ_ID", null);
            var payload = new WhatsAppWebhookPayload("whatsapp_business_account", List.of(entry));

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
            assertTrue(result.statusUpdates().isEmpty());
        }

        @Test
        @DisplayName("handles null value in change gracefully")
        void handlesNullValue() {
            var change = new Change(null, "messages");
            var entry = new Entry("BIZ_ID", List.of(change));
            var payload = new WhatsAppWebhookPayload("whatsapp_business_account", List.of(entry));

            var result = parser.parse(payload);

            assertTrue(result.messages().isEmpty());
            assertTrue(result.statusUpdates().isEmpty());
        }

        @Test
        @DisplayName("does not throw exception for any invalid payload — always returns result")
        void neverThrows() {
            // All these should return empty results without throwing
            assertDoesNotThrow(() -> parser.parse(null));
            assertDoesNotThrow(() -> parser.parse(new WhatsAppWebhookPayload(null, null)));
            assertDoesNotThrow(() -> parser.parse(new WhatsAppWebhookPayload("", List.of())));
        }
    }

    @Nested
    @DisplayName("Internal helper methods")
    class HelperMethods {

        @Test
        @DisplayName("mapMessageType returns correct enum for all supported types")
        void mapMessageTypeAllSupported() {
            assertEquals(MessageType.TEXT, parser.mapMessageType("text"));
            assertEquals(MessageType.IMAGE, parser.mapMessageType("image"));
            assertEquals(MessageType.AUDIO, parser.mapMessageType("audio"));
            assertEquals(MessageType.VIDEO, parser.mapMessageType("video"));
            assertEquals(MessageType.DOCUMENT, parser.mapMessageType("document"));
            assertEquals(MessageType.LOCATION, parser.mapMessageType("location"));
            assertEquals(MessageType.REACTION, parser.mapMessageType("reaction"));
            assertEquals(MessageType.INTERACTIVE, parser.mapMessageType("interactive"));
        }

        @Test
        @DisplayName("mapMessageType is case-insensitive")
        void mapMessageTypeCaseInsensitive() {
            assertEquals(MessageType.TEXT, parser.mapMessageType("TEXT"));
            assertEquals(MessageType.IMAGE, parser.mapMessageType("Image"));
        }

        @Test
        @DisplayName("mapMessageType returns null for unsupported types")
        void mapMessageTypeUnsupported() {
            assertNull(parser.mapMessageType("sticker"));
            assertNull(parser.mapMessageType("contacts"));
            assertNull(parser.mapMessageType("live_location"));
            assertNull(parser.mapMessageType(null));
        }

        @Test
        @DisplayName("parseTimestamp parses valid unix timestamps")
        void parseTimestampValid() {
            assertEquals(Instant.ofEpochSecond(1700000000L), parser.parseTimestamp("1700000000"));
        }

        @Test
        @DisplayName("parseTimestamp handles null/blank by returning approximately now")
        void parseTimestampNull() {
            Instant before = Instant.now();
            Instant result = parser.parseTimestamp(null);
            Instant after = Instant.now();

            assertFalse(result.isBefore(before));
            assertFalse(result.isAfter(after));
        }
    }
}
