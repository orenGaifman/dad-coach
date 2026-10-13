package com.dadcoach.integration.platform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import org.springframework.stereotype.Component;

/**
 * The platform's Send API as Dad Coach calls it: the shared client {@link com.platform.client.PlatformMessagingClient}
 * (platform P2 §3, vendored byte-identically) on Dad Coach's platform and messaging settings.
 */
@Component
public class PlatformMessagingClient extends com.platform.client.PlatformMessagingClient {

    public PlatformMessagingClient(WorkflowPlatformProperties platform, MessagingSettings settings, ObjectMapper json) {
        super(() -> new Config(platform.isEnabled(), platform.getBaseUrl(), platform.getApiKey(), platform.getWorkerKey(),
                settings.connectTimeoutMs(), settings.readTimeoutMs(), settings.lookupReadTimeoutMs()), json);
    }
}
