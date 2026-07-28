package com.dadcoach.channel;

import com.dadcoach.channel.capability.ChannelCapabilities;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChannelRouter verifying adapter selection by channel name
 * and the extensibility model (new adapter = new bean, no core changes).
 */
class ChannelRouterTest {

    // ===== Stub adapters for testing =====

    private static class WhatsAppStubAdapter implements ChannelAdapter {
        @Override
        public String getChannelName() { return "WHATSAPP"; }
        @Override
        public ChannelCapabilities getCapabilities() { return ChannelCapabilities.allSupported(); }
        @Override
        public InboundMessageDto normalizeInbound(Object rawPayload) { return null; }
        @Override
        public DeliveryResult sendMessage(OutboundMessageDto message, String channelIdentity) {
            return DeliveryResult.sent("wamid.123");
        }
        @Override
        public SessionState getSessionState(String channelIdentity) { return SessionState.closed(); }
        @Override
        public DeliveryStatus getDeliveryStatus(String providerMessageId) { return DeliveryStatus.PENDING; }
    }

    private static class SmsStubAdapter implements ChannelAdapter {
        @Override
        public String getChannelName() { return "SMS"; }
        @Override
        public ChannelCapabilities getCapabilities() { return ChannelCapabilities.textOnly(); }
        @Override
        public InboundMessageDto normalizeInbound(Object rawPayload) { return null; }
        @Override
        public DeliveryResult sendMessage(OutboundMessageDto message, String channelIdentity) {
            return DeliveryResult.sent("sms-123");
        }
        @Override
        public SessionState getSessionState(String channelIdentity) { return SessionState.closed(); }
        @Override
        public DeliveryStatus getDeliveryStatus(String providerMessageId) { return DeliveryStatus.PENDING; }
    }

    // ===== Tests =====

    @Nested
    @DisplayName("Adapter lookup by channel name")
    class AdapterLookupTests {

        @Test
        @DisplayName("returns correct adapter for registered channel")
        void getAdapter_returnsCorrectAdapter() {
            ChannelRouter router = new ChannelRouter(List.of(
                new WhatsAppStubAdapter(), new SmsStubAdapter()
            ));

            ChannelAdapter adapter = router.getAdapter("WHATSAPP");

            assertEquals("WHATSAPP", adapter.getChannelName());
        }

        @Test
        @DisplayName("returns SMS adapter when requested")
        void getAdapter_returnsSmsAdapter() {
            ChannelRouter router = new ChannelRouter(List.of(
                new WhatsAppStubAdapter(), new SmsStubAdapter()
            ));

            ChannelAdapter adapter = router.getAdapter("SMS");

            assertEquals("SMS", adapter.getChannelName());
        }

        @Test
        @DisplayName("throws ChannelNotSupportedException for unknown channel")
        void getAdapter_throwsForUnknownChannel() {
            ChannelRouter router = new ChannelRouter(List.of(new WhatsAppStubAdapter()));

            ChannelNotSupportedException exception = assertThrows(
                ChannelNotSupportedException.class,
                () -> router.getAdapter("TELEGRAM")
            );

            assertEquals("TELEGRAM", exception.getChannelName());
        }
    }

    @Nested
    @DisplayName("Channel support checks")
    class SupportChecksTests {

        @Test
        @DisplayName("supportsChannel returns true for registered channel")
        void supportsChannel_trueForRegistered() {
            ChannelRouter router = new ChannelRouter(List.of(new WhatsAppStubAdapter()));

            assertTrue(router.supportsChannel("WHATSAPP"));
        }

        @Test
        @DisplayName("supportsChannel returns false for unregistered channel")
        void supportsChannel_falseForUnregistered() {
            ChannelRouter router = new ChannelRouter(List.of(new WhatsAppStubAdapter()));

            assertFalse(router.supportsChannel("TELEGRAM"));
        }

        @Test
        @DisplayName("getRegisteredChannels returns all registered channel names")
        void getRegisteredChannels_returnsAll() {
            ChannelRouter router = new ChannelRouter(List.of(
                new WhatsAppStubAdapter(), new SmsStubAdapter()
            ));

            var channels = router.getRegisteredChannels();

            assertEquals(2, channels.size());
            assertTrue(channels.contains("WHATSAPP"));
            assertTrue(channels.contains("SMS"));
        }
    }

    @Nested
    @DisplayName("Extensibility — new channel = new adapter bean")
    class ExtensibilityTests {

        @Test
        @DisplayName("adding a new adapter requires no changes to ChannelRouter")
        void newAdapter_noRouterChanges() {
            // Simulates adding a Telegram adapter — just implement the interface
            ChannelAdapter telegramAdapter = new ChannelAdapter() {
                @Override
                public String getChannelName() { return "TELEGRAM"; }
                @Override
                public ChannelCapabilities getCapabilities() { return ChannelCapabilities.allSupported(); }
                @Override
                public InboundMessageDto normalizeInbound(Object rawPayload) { return null; }
                @Override
                public DeliveryResult sendMessage(OutboundMessageDto message, String channelIdentity) {
                    return DeliveryResult.sent("tg-456");
                }
                @Override
                public SessionState getSessionState(String channelIdentity) { return SessionState.closed(); }
                @Override
                public DeliveryStatus getDeliveryStatus(String providerMessageId) { return DeliveryStatus.PENDING; }
            };

            // Router discovers new adapter automatically via the list
            ChannelRouter router = new ChannelRouter(List.of(
                new WhatsAppStubAdapter(), new SmsStubAdapter(), telegramAdapter
            ));

            // New channel is immediately routable
            assertEquals("TELEGRAM", router.getAdapter("TELEGRAM").getChannelName());
            assertEquals(3, router.getRegisteredChannels().size());
        }

        @Test
        @DisplayName("router works with zero adapters registered")
        void emptyRouter_throwsForAnyLookup() {
            ChannelRouter router = new ChannelRouter(List.of());

            assertTrue(router.getRegisteredChannels().isEmpty());
            assertThrows(ChannelNotSupportedException.class, () -> router.getAdapter("WHATSAPP"));
        }
    }
}
