package com.dadcoach.integration.platform;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Dad Coach shares its WhatsApp number with other products. Before each message it asks the platform's gateway
 * ({@code POST /api/v1/worker/whatsapp/outbound-gate}) whether the father is on Dad Coach right now. When he is on
 * another product the gateway keeps the message and sends it once he moves to Dad Coach (owner 2026-10-08: "I'm on
 * Dad Coach, yet all three wrote to me at 08:00") - for Dad Coach it counts as sent. Any failure to ask sends as
 * before: the gate never silences Dad Coach.
 */
@Component
public class SharedNumberGate {

    private static final Logger log = LoggerFactory.getLogger(SharedNumberGate.class);
    /** The message id Dad Coach keeps for a message the gateway holds (Meta's own id comes when it is sent). */
    public static final String HELD_PREFIX = "held:";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WorkflowPlatformProperties properties;
    private final boolean enabled;
    private final WebClient web;

    public SharedNumberGate(WorkflowPlatformProperties properties,
                            @Value("${dadcoach.whatsapp.shared-number-gate:true}") boolean enabled) {
        this.properties = properties;
        this.enabled = enabled;
        this.web = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", properties.getApiKey())
                .build();
    }

    record GateResponse(boolean send, Long heldId, String route, String activeRoute, String why) {}

    /** The held id ({@code held:<n>}) when the gateway keeps the message for later; empty means send it now. */
    public Optional<String> holdIfOnAnotherProduct(Map<String, Object> payload) {
        Object to = payload.get("to");
        if (!enabled || !properties.isEnabled() || properties.getBaseUrl().isBlank() || to == null) {
            return Optional.empty();
        }
        try {
            GateResponse response = web.post()
                    .uri("/api/v1/worker/whatsapp/outbound-gate")
                    .bodyValue(Map.of("workerKey", properties.getWorkerKey(), "phone", to.toString(), "payload", payload))
                    .retrieve()
                    .bodyToMono(GateResponse.class)
                    .timeout(TIMEOUT)
                    .block();
            if (response == null || response.send() || response.heldId() == null) {
                return Optional.empty();
            }
            log.info("WhatsApp message held for the shared number: heldId={}, activeRoute={}",
                    response.heldId(), response.activeRoute());
            return Optional.of(HELD_PREFIX + response.heldId());
        } catch (RuntimeException e) {
            log.warn("Shared-number gate unavailable, sending as before: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
