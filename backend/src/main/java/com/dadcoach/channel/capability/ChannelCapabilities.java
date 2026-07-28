package com.dadcoach.channel.capability;

/**
 * Value object describing the supported features of a Channel_Adapter.
 * Each adapter declares its capabilities at registration time, enabling
 * the system to determine downgrade rules when a message type is unsupported.
 *
 * @param text             plain text messages supported
 * @param image            image messages with optional caption
 * @param audio            voice/audio messages
 * @param video            video messages with optional caption
 * @param document         file attachment support
 * @param interactive      buttons, lists, quick replies
 * @param template         pre-approved template messages
 * @param sessionWindow    provider-managed messaging window
 * @param deliveryReceipts read/delivered status callbacks
 * @param reactions        emoji reactions to messages
 */
public record ChannelCapabilities(
    boolean text,
    boolean image,
    boolean audio,
    boolean video,
    boolean document,
    boolean interactive,
    boolean template,
    boolean sessionWindow,
    boolean deliveryReceipts,
    boolean reactions
) {

    /**
     * Returns a ChannelCapabilities with all features supported.
     * Useful as a starting point for providers with full capability (e.g., WhatsApp).
     */
    public static ChannelCapabilities allSupported() {
        return new ChannelCapabilities(true, true, true, true, true, true, true, true, true, true);
    }

    /**
     * Returns a ChannelCapabilities with only text supported.
     * Useful for minimal providers (e.g., basic SMS).
     */
    public static ChannelCapabilities textOnly() {
        return new ChannelCapabilities(true, false, false, false, false, false, false, false, false, false);
    }
}
