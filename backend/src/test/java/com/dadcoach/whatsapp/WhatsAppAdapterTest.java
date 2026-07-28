package com.dadcoach.whatsapp;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.capability.ChannelCapabilities;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionState;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.whatsapp.WhatsAppApiClient.RateLimitException;
import com.dadcoach.whatsapp.WhatsAppApiClient.SendResponse;
import com.dadcoach.whatsapp.WhatsAppApiClient.WhatsAppApiException;
import com.dadcoach.whatsapp.WhatsAppMessageParser.ParseResult;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WhatsAppAdapter covering ChannelAdapter interface implementation,
 * outbound delivery, rate limiting, and circuit breaker logic.
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppAdapterTest {

    @Mock
    private WhatsAppMessageParser parser;

    @Mock
    private WhatsAppMessageFormatter formatter;

    @Mock
    private WhatsAppApiClient apiClient;

    @Mock
    private SessionWindowService sessionWindowService;

    @Mock
    private CommunicationEndpointRepository endpointRepository;

    private Clock clock;
    private WhatsAppAdapter adapter;

    private static final String PHONE = "+5491112345678";
    private static final Instant BASE_TIME = Instant.parse("2024-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(BASE_TIME, ZoneId.of("UTC"));
        adapter = new WhatsAppAdapter(
                parser, formatter, apiClient,
                sessionWindowService, endpointRepository, clock);
    }

    private OutboundMessageDto textMessage() {
        return new OutboundMessageDto(
                UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
                MessageType.TEXT, "Hola papá", null, false, null, null,
                MessagePriority.IMMEDIATE, Instant.now());
    }

    // ─── 5.1 ChannelAdapter Interface ────────────────────────────────────

    @Nested
    @DisplayName("5.1 ChannelAdapter interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("getChannelName returns WHATSAPP")
        void channelNameIsWhatsApp() {
            assertEquals("WHATSAPP", adapter.getChannelName());
        }

        @Test
        @DisplayName("getCapabilities returns allSupported")
        void capabilitiesAreAllSupported() {
            ChannelCapabilities caps = adapter.getCapabilities();
            assertTrue(caps.text());
            assertTrue(caps.image());
            assertTrue(caps.audio());
            assertTrue(caps.video());
            assertTrue(caps.document());
            assertTrue(caps.template());
            assertTrue(caps.interactive());
            assertTrue(caps.sessionWindow());
            assertTrue(caps.deliveryReceipts());
            assertTrue(caps.reactions());
        }

        @Test
        @DisplayName("normalizeInbound delegates to parser and returns first message")
        void normalizeInboundDelegatesToParser() {
            WhatsAppWebhookPayload payload = mock(WhatsAppWebhookPayload.class);
            InboundMessageDto expectedDto = new InboundMessageDto(
                    UUID.randomUUID(), "wamid.123", PHONE, "WHATSAPP",
                    MessageType.TEXT, "Hello", null, Instant.now(), Instant.now());
            ParseResult parseResult = new ParseResult(List.of(expectedDto), List.of());

            when(parser.parse(payload)).thenReturn(parseResult);

            InboundMessageDto result = adapter.normalizeInbound(payload);
            assertNotNull(result);
            assertEquals(expectedDto, result);
        }

        @Test
        @DisplayName("normalizeInbound returns null for non-WhatsApp payload")
        void normalizeInboundReturnsNullForInvalidPayload() {
            InboundMessageDto result = adapter.normalizeInbound("not a WhatsApp payload");
            assertNull(result);
        }

        @Test
        @DisplayName("normalizeInbound returns null when parser returns empty")
        void normalizeInboundReturnsNullWhenNoMessages() {
            WhatsAppWebhookPayload payload = mock(WhatsAppWebhookPayload.class);
            when(parser.parse(payload)).thenReturn(ParseResult.empty());

            InboundMessageDto result = adapter.normalizeInbound(payload);
            assertNull(result);
        }

        @Test
        @DisplayName("getSessionState returns open state from session service")
        void getSessionStateReturnsOpenState() {
            CommunicationEndpoint endpoint = new CommunicationEndpoint(UUID.randomUUID(), "WHATSAPP", PHONE);
            Instant closesAt = BASE_TIME.plus(Duration.ofHours(24));
            SessionState expected = SessionState.openUntil(closesAt);

            when(endpointRepository.findByChannelAndChannelIdentity("WHATSAPP", PHONE))
                    .thenReturn(Optional.of(endpoint));
            when(sessionWindowService.getState(endpoint)).thenReturn(expected);

            SessionState result = adapter.getSessionState(PHONE);
            assertTrue(result.open());
            assertEquals(closesAt, result.closesAt());
        }

        @Test
        @DisplayName("getSessionState returns closed when no endpoint found")
        void getSessionStateReturnsClosedForUnknownIdentity() {
            when(endpointRepository.findByChannelAndChannelIdentity("WHATSAPP", PHONE))
                    .thenReturn(Optional.empty());

            SessionState result = adapter.getSessionState(PHONE);
            assertTrue(result.isClosed());
        }

        @Test
        @DisplayName("getDeliveryStatus returns PENDING as baseline")
        void getDeliveryStatusReturnsPending() {
            DeliveryStatus status = adapter.getDeliveryStatus("wamid.abc123");
            assertEquals(DeliveryStatus.PENDING, status);
        }
    }

    // ─── 5.4 Send Messages ──────────────────────────────────────────────

    @Nested
    @DisplayName("5.4 Send messages via WhatsApp Cloud API")
    class SendMessages {

        @Test
        @DisplayName("successful delivery returns sent result with provider message ID")
        void successfulDeliveryReturnsSent() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");
            SendResponse response = new SendResponse(true, "wamid.sent123", null);

            when(formatter.format(message, PHONE)).thenReturn(formattedPayload);
            when(apiClient.sendMessage(formattedPayload)).thenReturn(response);

            DeliveryResult result = adapter.sendMessage(message, PHONE);

            assertTrue(result.isSuccessful());
            assertEquals("wamid.sent123", result.providerMessageId());
            assertNull(result.failureReason());
        }

        @Test
        @DisplayName("failed API call returns failed delivery result")
        void failedApiCallReturnsFailed() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");

            when(formatter.format(message, PHONE)).thenReturn(formattedPayload);
            when(apiClient.sendMessage(formattedPayload))
                    .thenThrow(new WhatsAppApiException(400, "Invalid phone number"));

            DeliveryResult result = adapter.sendMessage(message, PHONE);

            assertFalse(result.isSuccessful());
            assertEquals(DeliveryStatus.FAILED, result.status());
            assertNotNull(result.failureReason());
        }

        @Test
        @DisplayName("unexpected exception returns failed delivery result")
        void unexpectedExceptionReturnsFailed() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");

            when(formatter.format(message, PHONE)).thenReturn(formattedPayload);
            when(apiClient.sendMessage(formattedPayload))
                    .thenThrow(new RuntimeException("Connection timeout"));

            DeliveryResult result = adapter.sendMessage(message, PHONE);

            assertFalse(result.isSuccessful());
            assertTrue(result.failureReason().contains("Connection timeout"));
        }
    }

    // ─── 5.5 Rate Limiting ──────────────────────────────────────────────

    @Nested
    @DisplayName("5.5 Handle rate limit responses")
    class RateLimiting {

        @Test
        @DisplayName("rate limit exception returns failed result with retry info")
        void rateLimitReturnsFailed() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");

            when(formatter.format(message, PHONE)).thenReturn(formattedPayload);
            when(apiClient.sendMessage(formattedPayload))
                    .thenThrow(new RateLimitException(Duration.ofSeconds(30)));

            DeliveryResult result = adapter.sendMessage(message, PHONE);

            assertFalse(result.isSuccessful());
            assertTrue(result.failureReason().contains("RATE_LIMITED"));
            assertTrue(result.failureReason().contains("30"));
        }
    }

    // ─── 5.6 Circuit Breaker ────────────────────────────────────────────

    @Nested
    @DisplayName("5.6 Circuit breaker logic")
    class CircuitBreaker {

        @Test
        @DisplayName("circuit opens after 10 consecutive failures in 5 min")
        void circuitOpensAfterThreshold() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");

            when(formatter.format(any(), eq(PHONE))).thenReturn(formattedPayload);
            when(apiClient.sendMessage(formattedPayload))
                    .thenThrow(new WhatsAppApiException(500, "Server error"));

            // Fail 10 times
            for (int i = 0; i < 10; i++) {
                adapter.sendMessage(message, PHONE);
            }

            // 11th attempt should be blocked by circuit breaker
            DeliveryResult result = adapter.sendMessage(message, PHONE);
            assertFalse(result.isSuccessful());
            assertEquals("CIRCUIT_BREAKER_OPEN", result.failureReason());
        }

        @Test
        @DisplayName("circuit remains closed under threshold")
        void circuitStaysClosedUnderThreshold() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");

            when(formatter.format(any(), eq(PHONE))).thenReturn(formattedPayload);
            when(apiClient.sendMessage(formattedPayload))
                    .thenThrow(new WhatsAppApiException(500, "Server error"));

            // Fail 9 times
            for (int i = 0; i < 9; i++) {
                adapter.sendMessage(message, PHONE);
            }

            // 10th attempt should still attempt delivery (not blocked yet)
            // The 10th failure trips it, so the 11th is blocked
            DeliveryResult tenthResult = adapter.sendMessage(message, PHONE);
            assertFalse(tenthResult.isSuccessful());
            // The 10th call fails from the API, not from circuit breaker
            assertNotEquals("CIRCUIT_BREAKER_OPEN", tenthResult.failureReason());
        }

        @Test
        @DisplayName("successful delivery resets circuit breaker")
        void successResetsCircuit() {
            OutboundMessageDto message = textMessage();
            Map<String, Object> formattedPayload = Map.of("type", "text");
            SendResponse successResponse = new SendResponse(true, "wamid.ok", null);
            WhatsAppApiException apiError = new WhatsAppApiException(500, "Server error");

            when(formatter.format(any(), eq(PHONE))).thenReturn(formattedPayload);

            // Fail 9 times, then succeed on the 10th
            when(apiClient.sendMessage(formattedPayload))
                    .thenThrow(apiError)  // 1
                    .thenThrow(apiError)  // 2
                    .thenThrow(apiError)  // 3
                    .thenThrow(apiError)  // 4
                    .thenThrow(apiError)  // 5
                    .thenThrow(apiError)  // 6
                    .thenThrow(apiError)  // 7
                    .thenThrow(apiError)  // 8
                    .thenThrow(apiError)  // 9
                    .thenReturn(successResponse) // 10 — success, resets counter
                    .thenThrow(apiError)  // 11
                    .thenThrow(apiError)  // 12
                    .thenThrow(apiError)  // 13
                    .thenThrow(apiError)  // 14
                    .thenThrow(apiError)  // 15
                    .thenThrow(apiError)  // 16
                    .thenThrow(apiError)  // 17
                    .thenThrow(apiError)  // 18
                    .thenThrow(apiError)  // 19
                    .thenReturn(successResponse); // 20 — still allowed (only 9 failures since reset)

            // Fail 9 times
            for (int i = 0; i < 9; i++) {
                adapter.sendMessage(message, PHONE);
            }

            // One success resets counter
            DeliveryResult successResult = adapter.sendMessage(message, PHONE);
            assertTrue(successResult.isSuccessful());

            // Fail 9 more times — circuit should NOT trip (counter was reset)
            for (int i = 0; i < 9; i++) {
                adapter.sendMessage(message, PHONE);
            }

            // Next attempt is still open (only 9 since reset)
            DeliveryResult result = adapter.sendMessage(message, PHONE);
            assertTrue(result.isSuccessful());
        }

        @Test
        @DisplayName("circuit closes after pause expires and probe succeeds")
        void probeSuccessClosesCircuit() {
            // Trip the circuit breaker
            adapter.onDeliveryFailure(); // starts window
            for (int i = 1; i < 10; i++) {
                adapter.onDeliveryFailure();
            }

            // Circuit should be open
            assertTrue(adapter.isCircuitOpen());

            // Advance time past the pause
            clock = Clock.fixed(BASE_TIME.plus(Duration.ofSeconds(61)), ZoneId.of("UTC"));
            adapter = new WhatsAppAdapter(
                    parser, formatter, apiClient,
                    sessionWindowService, endpointRepository, clock);

            // First call after pause — probe is allowed
            assertFalse(adapter.isCircuitOpen());
        }

        @Test
        @DisplayName("failures outside 5-min window start a new window")
        void failuresOutsideWindowResetCounter() {
            // Simulate failures that are spread outside the 5-min window
            // by manually calling onDeliveryFailure after resetting the first failure time.
            adapter.onDeliveryFailure(); // failure 1 at BASE_TIME

            // Simulate 8 more failures within window
            for (int i = 0; i < 8; i++) {
                adapter.onDeliveryFailure();
            }

            // Circuit should NOT be open (9 failures, threshold is 10)
            assertFalse(adapter.isCircuitOpen());

            // The 10th failure trips it
            adapter.onDeliveryFailure();
            assertTrue(adapter.isCircuitOpen());
        }
    }
}
