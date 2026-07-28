package com.dadcoach.channel;

/**
 * Thrown when the {@link ChannelRouter} cannot find a registered adapter
 * for the requested channel type.
 */
public class ChannelNotSupportedException extends RuntimeException {

    private final String channelName;

    public ChannelNotSupportedException(String channelName) {
        super("No adapter registered for channel: " + channelName);
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }
}
