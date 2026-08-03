package com.dadcoach.channel.delivery;

import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.capability.ChannelCapabilities;
import com.dadcoach.channel.capability.MessageDowngrader;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.channel.template.TemplateMessage;
import com.dadcoach.channel.template.TemplateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeliveryService verifying the outbound delivery orchestration pipeline:
 * resolve endpoint → check session → check template → check capabilities → send via adapter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService Unit Tests")
class DeliveryServiceTest {

    @Mock
    private CommunicationEndpointRepository endpointRepository;

    @Mock
    private SessionWindowService sessionWindowService;

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private MessageDowngrader messageDowngrader;

    @Mock
    private TemplateRegistry templateRegistry;

    @Mock
    private ChannelAdapter channelAdapter;

    private DeliveryService deliveryService;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final String CHANNEL_IDENTITY = "+5491155551234";
    private static final String PROVIDER_MSG_ID = "wamid.abc123";

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(
                endpointRepository, sessionWindowService, channelRouter, messageDowngrader, templateRegistry);
    }

    private OutboundMessageDto textMessage() {
        return new OutboundMessageDto(
                MESSAGE_ID, FATHER_ID, null, MessageType.TEXT,
                "Hello!", null, false, null, null,
                MessagePriority.IMMEDIATE, Instant.now());
    }

    private OutboundMessageDto templateMessage(String templateName) {
        return new OutboundMessageDto(
                MESSAGE_ID, FATHER_ID, null, MessageType.TEXT,
                null, null, true, templateName,
                Map.of("1", "Carlos"), MessagePriority.SCHEDULED, Instant.now());
    }

    private OutboundMessageDto messageWithExplicitChannel(String channel) {
        return new OutboundMessageDto(
                MESSAGE_ID, FATHER_ID, channel, MessageType.TEXT,
                "Hello!", null, false, null, null,
                MessagePriority.IMMEDIATE, Instant.now());
    }

    private CommunicationEndpoint whatsappEndpoint() {
        return new CommunicationEndpoint(FATHER_ID, "WHATSAPP", CHANNEL_IDENTITY);
    }

    // ───────────────────────────────────────────────────────────────────────
    // 7.1 Resolve primary endpoint for father
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7.1 Resolve primary endpoint")
    class ResolveEndpointTests {

        @Test
        @DisplayName("resolves primary endpoint when no explicit channel specified")
        void resolvesPrimaryEndpoint() {
            var endpoint = whatsappEndpoint();
            var message = textMessage();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(message);

            assertTrue(result.isSuccessful());
            assertEquals(PROVIDER_MSG_ID, result.providerMessageId());
        }

        @Test
        @DisplayName("resolves endpoint by explicit channel when specified")
        void resolvesEndpointByExplicitChannel() {
            var endpoint = whatsappEndpoint();
            var message = messageWithExplicitChannel("WHATSAPP");

            when(endpointRepository.findByFatherId(FATHER_ID)).thenReturn(List.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(message);

            assertTrue(result.isSuccessful());
        }

        @Test
        @DisplayName("returns ENDPOINT_NOT_FOUND when no primary endpoint exists")
        void rejectsWhenNoEndpointFound() {
            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.empty());

            DeliveryResult result = deliveryService.deliver(textMessage());

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryService.ENDPOINT_NOT_FOUND, result.failureReason());
        }

        @Test
        @DisplayName("returns ENDPOINT_NOT_FOUND when explicit channel not found for father")
        void rejectsWhenExplicitChannelNotFound() {
            when(endpointRepository.findByFatherId(FATHER_ID)).thenReturn(List.of());

            DeliveryResult result = deliveryService.deliver(messageWithExplicitChannel("SMS"));

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryService.ENDPOINT_NOT_FOUND, result.failureReason());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 7.2 Check session window (closed + non-template → rejected)
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7.2 Session window check")
    class SessionWindowTests {

        @Test
        @DisplayName("allows delivery when session is open")
        void allowsWhenSessionOpen() {
            var endpoint = whatsappEndpoint();
            var message = textMessage();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(message);

            assertTrue(result.isSuccessful());
        }

        @Test
        @DisplayName("rejects non-template message when session is closed")
        void rejectsNonTemplateWhenSessionClosed() {
            var endpoint = whatsappEndpoint();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);

            DeliveryResult result = deliveryService.deliver(textMessage());

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryService.SESSION_CLOSED, result.failureReason());
        }

        @Test
        @DisplayName("allows template message when session is closed")
        void allowsTemplateWhenSessionClosed() {
            var endpoint = whatsappEndpoint();
            var message = templateMessage("daily_coaching");
            var tmpl = mock(TemplateMessage.class);

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);
            when(templateRegistry.findApprovedTemplate("daily_coaching")).thenReturn(Optional.of(tmpl));
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), false))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(message);

            assertTrue(result.isSuccessful());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 7.3 Check capabilities and downgrade if needed
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7.3 Capabilities and downgrade")
    class CapabilitiesAndDowngradeTests {

        @Test
        @DisplayName("delivers original message when no downgrade needed")
        void deliversOriginalWhenNoDowngradeNeeded() {
            var endpoint = whatsappEndpoint();
            var message = textMessage();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(message);

            assertTrue(result.isSuccessful());
            verify(channelAdapter).sendMessage(message, CHANNEL_IDENTITY);
        }

        @Test
        @DisplayName("delivers downgraded message when downgrade succeeds")
        void deliversDowngradedMessage() {
            var endpoint = whatsappEndpoint();
            var originalMessage = new OutboundMessageDto(
                    MESSAGE_ID, FATHER_ID, null, MessageType.INTERACTIVE,
                    "Choose: 1. Yes 2. No", null, false, null, null,
                    MessagePriority.IMMEDIATE, Instant.now());
            var downgradedMessage = new OutboundMessageDto(
                    MESSAGE_ID, FATHER_ID, null, MessageType.TEXT,
                    "Choose: 1. Yes 2. No", null, false, null, null,
                    MessagePriority.IMMEDIATE, Instant.now());

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.textOnly());
            when(messageDowngrader.downgradeIfNeeded(originalMessage, ChannelCapabilities.textOnly(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(downgradedMessage));
            when(channelAdapter.sendMessage(downgradedMessage, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(originalMessage);

            assertTrue(result.isSuccessful());
            verify(channelAdapter).sendMessage(downgradedMessage, CHANNEL_IDENTITY);
        }

        @Test
        @DisplayName("rejects when downgrade is not possible")
        void rejectsWhenDowngradeRejected() {
            var endpoint = whatsappEndpoint();
            var audioMessage = new OutboundMessageDto(
                    MESSAGE_ID, FATHER_ID, null, MessageType.AUDIO,
                    null, UUID.randomUUID(), false, null, null,
                    MessagePriority.IMMEDIATE, Instant.now());

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.textOnly());
            when(messageDowngrader.downgradeIfNeeded(audioMessage, ChannelCapabilities.textOnly(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Rejected("No text equivalent for audio"));

            DeliveryResult result = deliveryService.deliver(audioMessage);

            assertFalse(result.isSuccessful());
            assertTrue(result.failureReason().contains(DeliveryService.UNSUPPORTED_TYPE));
            verify(channelAdapter, never()).sendMessage(any(), any());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 7.4 Return structured DeliveryResult
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7.4 Structured DeliveryResult")
    class DeliveryResultTests {

        @Test
        @DisplayName("returns success with provider message ID on successful delivery")
        void returnsSuccessResult() {
            var endpoint = whatsappEndpoint();
            var message = textMessage();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = deliveryService.deliver(message);

            assertEquals(DeliveryStatus.SENT, result.status());
            assertEquals(PROVIDER_MSG_ID, result.providerMessageId());
            assertNull(result.failureReason());
        }

        @Test
        @DisplayName("returns failure result when adapter reports failure")
        void returnsFailureResult() {
            var endpoint = whatsappEndpoint();
            var message = textMessage();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(true);
            when(channelRouter.getAdapter("WHATSAPP")).thenReturn(channelAdapter);
            when(channelAdapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
            when(messageDowngrader.downgradeIfNeeded(message, ChannelCapabilities.allSupported(), true))
                    .thenReturn(new MessageDowngrader.DowngradeResult.Success(message));
            when(channelAdapter.sendMessage(message, CHANNEL_IDENTITY))
                    .thenReturn(DeliveryResult.failed("Provider timeout"));

            DeliveryResult result = deliveryService.deliver(message);

            assertEquals(DeliveryStatus.FAILED, result.status());
            assertEquals("Provider timeout", result.failureReason());
            assertNull(result.providerMessageId());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 7.5 Return SESSION_CLOSED result to Conversation Engine
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7.5 SESSION_CLOSED result")
    class SessionClosedResultTests {

        @Test
        @DisplayName("returns SESSION_CLOSED rejection with correct reason string")
        void returnsSessionClosedRejection() {
            var endpoint = whatsappEndpoint();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);

            DeliveryResult result = deliveryService.deliver(textMessage());

            assertFalse(result.isSuccessful());
            assertEquals("SESSION_CLOSED", result.failureReason());
            assertEquals(DeliveryStatus.FAILED, result.status());
            assertNull(result.providerMessageId());
        }

        @Test
        @DisplayName("does not attempt delivery when session is closed for non-template")
        void doesNotAttemptDeliveryWhenSessionClosed() {
            var endpoint = whatsappEndpoint();

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);

            deliveryService.deliver(textMessage());

            verify(channelRouter, never()).getAdapter(any());
            verify(channelAdapter, never()).sendMessage(any(), any());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 7.6 Return TEMPLATE_UNAVAILABLE when template not approved
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7.6 TEMPLATE_UNAVAILABLE result")
    class TemplateUnavailableTests {

        @Test
        @DisplayName("rejects when template is not found in registry")
        void rejectsWhenTemplateNotFound() {
            var endpoint = whatsappEndpoint();
            var message = templateMessage("nonexistent_template");

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);
            when(templateRegistry.findApprovedTemplate("nonexistent_template")).thenReturn(Optional.empty());

            DeliveryResult result = deliveryService.deliver(message);

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryService.TEMPLATE_UNAVAILABLE, result.failureReason());
        }

        @Test
        @DisplayName("rejects when template name is null")
        void rejectsWhenTemplateNameIsNull() {
            var endpoint = whatsappEndpoint();
            var message = new OutboundMessageDto(
                    MESSAGE_ID, FATHER_ID, null, MessageType.TEXT,
                    null, null, true, null, null,
                    MessagePriority.SCHEDULED, Instant.now());

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);

            DeliveryResult result = deliveryService.deliver(message);

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryService.TEMPLATE_UNAVAILABLE, result.failureReason());
        }

        @Test
        @DisplayName("rejects when template name is blank")
        void rejectsWhenTemplateNameIsBlank() {
            var endpoint = whatsappEndpoint();
            var message = new OutboundMessageDto(
                    MESSAGE_ID, FATHER_ID, null, MessageType.TEXT,
                    null, null, true, "  ", null,
                    MessagePriority.SCHEDULED, Instant.now());

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);

            DeliveryResult result = deliveryService.deliver(message);

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryService.TEMPLATE_UNAVAILABLE, result.failureReason());
        }

        @Test
        @DisplayName("does not attempt delivery when template is unavailable")
        void doesNotAttemptDeliveryWhenTemplateUnavailable() {
            var endpoint = whatsappEndpoint();
            var message = templateMessage("rejected_template");

            when(endpointRepository.findPrimaryByFatherId(FATHER_ID)).thenReturn(Optional.of(endpoint));
            when(sessionWindowService.isOpen(endpoint)).thenReturn(false);
            when(templateRegistry.findApprovedTemplate("rejected_template")).thenReturn(Optional.empty());

            deliveryService.deliver(message);

            verify(channelRouter, never()).getAdapter(any());
            verify(channelAdapter, never()).sendMessage(any(), any());
        }
    }
}
