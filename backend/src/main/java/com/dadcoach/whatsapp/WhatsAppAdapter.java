package com.dadcoach.whatsapp;

import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.capability.ChannelCapabilities;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionState;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.whatsapp.WhatsAppApiClient.RateLimitException;
import com.dadcoach.whatsapp.WhatsAppApiClient.SendResponse;
import com.dadcoach.whatsapp.WhatsAppApiClient.WhatsAppApiException;
import com.dadcoach.whatsapp.WhatsAppMessageParser.ParseResult;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WhatsApp Cloud API implementation of the {@link ChannelAdapter} interface.
 *
 * <p>Coordinates inbound message normalization (via {@link WhatsAppMessageParser}),
 * outbound message delivery (via {@link WhatsAppApiClient}), and session state
 * management (via {@link SessionWindowService}).
 *
 * <p>Implements a circuit breaker: if 10 consecutive outbound deliveries fail
 * within a 5-minute window, all outbound delivery is paused for 60 seconds.
 * After the pause, a single probe message is attempted before resuming normal flow.
 */
@Component
public class WhatsAppAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAdapter.class);

    static final String CHANNEL_NAME = "WHATSAPP";
    static final int CIRCUIT_BREAKER_THRESHOLD = 10;
    static final Duration CIRCUIT_BREAKER_WINDOW = Duration.ofMinutes(5);
    static final Duration CIRCUIT_BREAKER_PAUSE = Duration.ofSeconds(60);

    private final WhatsAppMessageParser parser;
    private final WhatsAppMessageFormatter formatter;
    private final WhatsAppApiClient apiClient;
    private final SessionWindowService sessionWindowService;
    private final CommunicationEndpointRepository endpointRepository;
    private final Clock clock;

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> firstFailureAt = new AtomicReference<>(null);
    private final AtomicReference<Instant> circuitOpenUntil = new AtomicReference<>(null);
    private volatile boolean probing = false;

    public WhatsAppAdapter(
            WhatsAppMessageParser parser,
            WhatsAppMessageFormatter formatter,
            WhatsAppApiClient apiClient,
            SessionWindowService sessionWindowService,
            CommunicationEndpointRepository endpointRepository,
            Clock clock) {
        this.parser = parser;
        this.formatter = formatter;
        this.apiClient = apiClient;
        this.sessionWindowService = sessionWindowService;
        this.endpointRepository = endpointRepository;
        this.clock = clock;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public ChannelCapabilities getCapabilities() {
        return ChannelCapabilities.allSupported();
    }

    @Override
    public InboundMessageDto normalizeInbound(Object rawPayload) {
        if (!(rawPayload instanceof WhatsAppWebhookPayload payload)) {
            log.warn("Received non-WhatsApp payload type: {}",
                    rawPayload != null ? rawPayload.getClass().getSimpleName() : "null");
            return null;
        }

        ParseResult result = parser.parse(payload);
        if (result.messages().isEmpty()) {
            log.debug("No messages extracted from webhook payload");
            return null;
        }

        // Return the first message; multi-message payloads are rare and
        // the webhook controller handles iteration if needed.
        return result.messages().get(0);
    }

    @Override
    public DeliveryResult sendMessage(OutboundMessageDto message, String channelIdentity) {
        // Check circuit breaker
        if (isCircuitOpen()) {
            log.warn("Circuit breaker OPEN for WhatsApp — delivery paused until {}",
                    circuitOpenUntil.get());
            return DeliveryResult.failed("CIRCUIT_BREAKER_OPEN");
        }

        try {
            Map<String, Object> payload = formatter.format(message, channelIdentity);
            SendResponse response = apiClient.sendMessage(payload);

            if (response.success() && response.messageId() != null) {
                onDeliverySuccess();
                return DeliveryResult.sent(response.messageId());
            }

            onDeliveryFailure();
            return DeliveryResult.failed(response.errorDetail() != null
                    ? response.errorDetail() : "Unknown delivery failure");

        } catch (RateLimitException e) {
            log.warn("WhatsApp rate limited. Retry after {}s", e.getRetryAfter().getSeconds());
            onDeliveryFailure();
            return DeliveryResult.failed("RATE_LIMITED: retry after " + e.getRetryAfter().getSeconds() + "s");

        } catch (WhatsAppApiException e) {
            log.error("WhatsApp API error during delivery: {}", e.getMessage());
            onDeliveryFailure();
            return DeliveryResult.failed(e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during WhatsApp delivery", e);
            onDeliveryFailure();
            return DeliveryResult.failed("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public SessionState getSessionState(String channelIdentity) {
        return endpointRepository.findByChannelAndChannelIdentity(CHANNEL_NAME, channelIdentity)
                .map(sessionWindowService::getState)
                .orElse(SessionState.closed());
    }

    @Override
    public DeliveryStatus getDeliveryStatus(String providerMessageId) {
        // Delivery status is tracked by the DeliveryRecord entity;
        // this adapter delegates status queries to the delivery tracking layer.
        // For now, return PENDING as the baseline; real status comes from webhook updates.
        log.debug("Delivery status query for providerMessageId={}", providerMessageId);
        return DeliveryStatus.PENDING;
    }

    // ─── Circuit Breaker Logic ───────────────────────────────────────────

    /**
     * Checks whether the circuit breaker is currently open (outbound paused).
     * If the pause period has elapsed, allows a single probe attempt.
     */
    boolean isCircuitOpen() {
        Instant openUntil = circuitOpenUntil.get();
        if (openUntil == null) {
            return false;
        }

        Instant now = clock.instant();
        if (now.isBefore(openUntil)) {
            return true; // still paused
        }

        // Pause expired — allow a probe
        if (!probing) {
            probing = true;
            log.info("Circuit breaker pause expired. Sending probe message.");
            return false;
        }

        // Probe already in progress, block other messages
        return true;
    }

    /**
     * Called on a successful delivery. Resets the circuit breaker state.
     */
    void onDeliverySuccess() {
        if (probing) {
            log.info("Probe message succeeded. Circuit breaker CLOSED — resuming normal flow.");
            probing = false;
        }
        consecutiveFailures.set(0);
        firstFailureAt.set(null);
        circuitOpenUntil.set(null);
    }

    /**
     * Called on a failed delivery. Increments failure count and potentially trips the circuit breaker.
     */
    void onDeliveryFailure() {
        if (probing) {
            log.warn("Probe message failed. Re-opening circuit breaker for another {}s.",
                    CIRCUIT_BREAKER_PAUSE.getSeconds());
            probing = false;
            circuitOpenUntil.set(clock.instant().plus(CIRCUIT_BREAKER_PAUSE));
            return;
        }

        Instant now = clock.instant();
        Instant firstFail = firstFailureAt.get();

        if (firstFail == null || Duration.between(firstFail, now).compareTo(CIRCUIT_BREAKER_WINDOW) > 0) {
            // Start a new failure window
            firstFailureAt.set(now);
            consecutiveFailures.set(1);
            return;
        }

        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            Instant pauseUntil = now.plus(CIRCUIT_BREAKER_PAUSE);
            circuitOpenUntil.set(pauseUntil);
            log.warn("Circuit breaker TRIPPED: {} consecutive failures in {} window. " +
                    "Pausing outbound until {}", failures, CIRCUIT_BREAKER_WINDOW, pauseUntil);
            // Reset counters for next cycle
            consecutiveFailures.set(0);
            firstFailureAt.set(null);
        }
    }

    // ─── Test visibility ─────────────────────────────────────────────────

    /**
     * Resets circuit breaker state. Visible for testing only.
     */
    void resetCircuitBreaker() {
        consecutiveFailures.set(0);
        firstFailureAt.set(null);
        circuitOpenUntil.set(null);
        probing = false;
    }
}
