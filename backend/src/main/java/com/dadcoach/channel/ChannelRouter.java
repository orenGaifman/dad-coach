package com.dadcoach.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes messages to the correct {@link ChannelAdapter} based on the endpoint's channel type.
 *
 * <p>All registered {@link ChannelAdapter} beans are injected at startup and indexed
 * by their channel name. Resolving a channel is O(1) via map lookup.
 *
 * <p>Extensibility: adding a new channel requires only implementing {@link ChannelAdapter}
 * as a Spring bean — the router will automatically discover it via dependency injection.
 */
@Component
public class ChannelRouter {

    private static final Logger log = LoggerFactory.getLogger(ChannelRouter.class);

    private final Map<String, ChannelAdapter> adaptersByChannel;

    /**
     * Constructs the router by indexing all available adapter beans by channel name.
     *
     * @param adapters all registered ChannelAdapter beans (injected by Spring)
     */
    public ChannelRouter(List<ChannelAdapter> adapters) {
        this.adaptersByChannel = adapters.stream()
            .collect(Collectors.toUnmodifiableMap(
                ChannelAdapter::getChannelName,
                Function.identity()
            ));
        log.info("ChannelRouter initialized with {} adapter(s): {}",
            adaptersByChannel.size(), adaptersByChannel.keySet());
    }

    /**
     * Returns the adapter for the given channel type.
     *
     * @param channelName the channel identifier (e.g., "WHATSAPP", "SMS")
     * @return the corresponding adapter
     * @throws ChannelNotSupportedException if no adapter is registered for the channel
     */
    public ChannelAdapter getAdapter(String channelName) {
        ChannelAdapter adapter = adaptersByChannel.get(channelName);
        if (adapter == null) {
            throw new ChannelNotSupportedException(channelName);
        }
        return adapter;
    }

    /**
     * Checks whether an adapter is registered for the given channel.
     *
     * @param channelName the channel identifier
     * @return true if an adapter exists
     */
    public boolean supportsChannel(String channelName) {
        return adaptersByChannel.containsKey(channelName);
    }

    /**
     * Returns the set of all registered channel names.
     */
    public java.util.Set<String> getRegisteredChannels() {
        return adaptersByChannel.keySet();
    }
}
