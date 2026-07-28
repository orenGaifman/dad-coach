package com.dadcoach.channel;

import com.dadcoach.channel.capability.ChannelCapabilities;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionState;

/**
 * Abstraction for a communication channel provider (e.g., WhatsApp, SMS, Telegram).
 *
 * <p>Each provider implements this interface as a Spring bean. The {@link ChannelRouter}
 * selects the appropriate adapter based on the father's primary Communication_Endpoint.
 * Adding a new channel requires only implementing this interface and registering the bean —
 * no changes to the orchestration layer.
 *
 * <p>The adapter is responsible for:
 * <ul>
 *   <li>Normalizing provider-specific inbound payloads into {@link InboundMessageDto}</li>
 *   <li>Translating {@link OutboundMessageDto} into provider format and delivering</li>
 *   <li>Reporting session window state</li>
 *   <li>Reporting delivery status by provider message ID</li>
 * </ul>
 */
public interface ChannelAdapter {

    /**
     * Returns the unique channel name (e.g., "WHATSAPP", "SMS").
     * Used by the {@link ChannelRouter} for adapter lookup.
     */
    String getChannelName();

    /**
     * Declares the capabilities supported by this channel adapter.
     * Used for automatic message downgrade decisions.
     */
    ChannelCapabilities getCapabilities();

    /**
     * Normalizes a provider-specific inbound payload into the internal message format.
     * Provider-specific metadata not in the internal format is discarded.
     *
     * @param rawPayload the raw webhook payload from the provider
     * @return normalized inbound message
     */
    InboundMessageDto normalizeInbound(Object rawPayload);

    /**
     * Delivers an outbound message to the specified channel identity.
     *
     * @param message         the internal outbound message to deliver
     * @param channelIdentity the provider-specific recipient identifier (e.g., E.164 phone number)
     * @return the delivery result including provider message ID on success
     */
    DeliveryResult sendMessage(OutboundMessageDto message, String channelIdentity);

    /**
     * Returns the current session window state for a given channel identity.
     *
     * @param channelIdentity the provider-specific identifier
     * @return session state (open/closed and closure time)
     */
    SessionState getSessionState(String channelIdentity);

    /**
     * Queries the current delivery status for a previously sent message.
     *
     * @param providerMessageId the provider-assigned message identifier
     * @return the current delivery status
     */
    DeliveryStatus getDeliveryStatus(String providerMessageId);
}
