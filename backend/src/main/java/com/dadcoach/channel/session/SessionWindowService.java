package com.dadcoach.channel.session;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Manages the WhatsApp 24-hour session window per communication endpoint.
 *
 * <p>The session window OPENS when a father sends an inbound message and is valid
 * for 24 hours from the last inbound message. While the window is open, free-form
 * messages may be sent. When closed, only template messages are allowed.
 *
 * <p>Session state is persisted in the {@code communication_endpoints} table via
 * {@code session_opens_at} and {@code session_closes_at} columns.
 *
 * <p>This service is the authoritative check BEFORE every outbound delivery.
 */
@Service
public class SessionWindowService {

    private static final Logger log = LoggerFactory.getLogger(SessionWindowService.class);
    private static final Duration SESSION_DURATION = Duration.ofHours(24);

    private final CommunicationEndpointRepository endpointRepository;
    private final Clock clock;

    public SessionWindowService(CommunicationEndpointRepository endpointRepository, Clock clock) {
        this.endpointRepository = endpointRepository;
        this.clock = clock;
    }

    /**
     * Opens or extends the session window for the given endpoint.
     * Called when an inbound message is received from a father.
     *
     * <p>Sets {@code session_opens_at} to now (or keeps existing if already open)
     * and updates {@code session_closes_at} to now + 24 hours.
     *
     * @param endpoint the communication endpoint that received the inbound message
     */
    @Transactional
    public void onInboundMessage(CommunicationEndpoint endpoint) {
        Instant now = clock.instant();
        Instant closesAt = now.plus(SESSION_DURATION);

        if (endpoint.getSessionOpensAt() == null || !isOpen(endpoint)) {
            endpoint.setSessionOpensAt(now);
            log.info("Session window opened for endpoint {} at {}", endpoint.getChannelIdentity(), now);
        }

        endpoint.setSessionClosesAt(closesAt);
        endpoint.setLastActiveAt(now);
        endpointRepository.save(endpoint);

        log.debug("Session window extended for endpoint {} until {}",
                endpoint.getChannelIdentity(), closesAt);
    }

    /**
     * Returns the current session state for the given endpoint.
     *
     * @param endpoint the communication endpoint to check
     * @return the session state (open with closure time, or closed)
     */
    public SessionState getState(CommunicationEndpoint endpoint) {
        if (isOpen(endpoint)) {
            return SessionState.openUntil(endpoint.getSessionClosesAt());
        }
        return SessionState.closed();
    }

    /**
     * Checks whether the session window is currently open for the given endpoint.
     * The window is open if {@code session_closes_at} is set and is in the future.
     *
     * @param endpoint the communication endpoint to check
     * @return true if the session window is open (free-form messages allowed)
     */
    public boolean isOpen(CommunicationEndpoint endpoint) {
        Instant closesAt = endpoint.getSessionClosesAt();
        if (closesAt == null) {
            return false;
        }
        return clock.instant().isBefore(closesAt);
    }

    /**
     * Validates whether an outbound message can be sent as a free-form message.
     * This check MUST be performed BEFORE every outbound delivery.
     *
     * @param endpoint   the target communication endpoint
     * @param isTemplate whether the outbound message is a template message
     * @return the result of the session check
     */
    public SessionCheckResult checkBeforeDelivery(CommunicationEndpoint endpoint, boolean isTemplate) {
        if (isOpen(endpoint)) {
            return SessionCheckResult.deliveryAllowed();
        }

        // Session is closed — only template messages may be sent
        if (isTemplate) {
            return SessionCheckResult.deliveryAllowed();
        }

        log.info("Delivery rejected: session closed for endpoint {} (closed at {})",
                endpoint.getChannelIdentity(), endpoint.getSessionClosesAt());
        return SessionCheckResult.sessionClosed(endpoint.getSessionClosesAt());
    }
}
