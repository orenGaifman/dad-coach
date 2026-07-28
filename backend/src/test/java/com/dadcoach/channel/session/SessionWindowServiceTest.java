package com.dadcoach.channel.session;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionWindowService.
 * Uses a fixed Clock for deterministic time-based behavior.
 */
class SessionWindowServiceTest {

    private static final Instant NOW = Instant.parse("2024-06-15T10:00:00Z");
    private static final Duration SESSION_DURATION = Duration.ofHours(24);

    private CommunicationEndpointRepository repository;
    private Clock fixedClock;
    private SessionWindowService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommunicationEndpointRepository.class);
        fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new SessionWindowService(repository, fixedClock);

        when(repository.save(any(CommunicationEndpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CommunicationEndpoint createEndpoint() {
        return new CommunicationEndpoint(UUID.randomUUID(), "WHATSAPP", "+5491155551234");
    }

    @Nested
    @DisplayName("onInboundMessage — opens/extends session window")
    class OnInboundMessageTests {

        @Test
        @DisplayName("opens a new session when no prior session exists")
        void opensNewSession() {
            CommunicationEndpoint endpoint = createEndpoint();

            service.onInboundMessage(endpoint);

            assertEquals(NOW, endpoint.getSessionOpensAt());
            assertEquals(NOW.plus(SESSION_DURATION), endpoint.getSessionClosesAt());
            assertEquals(NOW, endpoint.getLastActiveAt());
            verify(repository).save(endpoint);
        }

        @Test
        @DisplayName("extends session_closes_at on subsequent inbound while session is open")
        void extendsOpenSession() {
            CommunicationEndpoint endpoint = createEndpoint();
            // Simulate an already-open session that opened 2 hours ago
            Instant previousOpen = NOW.minus(Duration.ofHours(2));
            endpoint.setSessionOpensAt(previousOpen);
            endpoint.setSessionClosesAt(previousOpen.plus(SESSION_DURATION));

            service.onInboundMessage(endpoint);

            // session_opens_at stays at previous value (session was already open)
            assertEquals(previousOpen, endpoint.getSessionOpensAt());
            // session_closes_at is extended to now + 24h
            assertEquals(NOW.plus(SESSION_DURATION), endpoint.getSessionClosesAt());
            assertEquals(NOW, endpoint.getLastActiveAt());
        }

        @Test
        @DisplayName("re-opens session when prior session has expired")
        void reopensExpiredSession() {
            CommunicationEndpoint endpoint = createEndpoint();
            // Session expired 1 hour ago
            Instant oldOpen = NOW.minus(Duration.ofHours(25));
            endpoint.setSessionOpensAt(oldOpen);
            endpoint.setSessionClosesAt(oldOpen.plus(SESSION_DURATION)); // This is in the past

            service.onInboundMessage(endpoint);

            // New session opens at NOW since the old one was closed
            assertEquals(NOW, endpoint.getSessionOpensAt());
            assertEquals(NOW.plus(SESSION_DURATION), endpoint.getSessionClosesAt());
        }

        @Test
        @DisplayName("persists endpoint after session update")
        void persistsEndpoint() {
            CommunicationEndpoint endpoint = createEndpoint();

            service.onInboundMessage(endpoint);

            ArgumentCaptor<CommunicationEndpoint> captor = ArgumentCaptor.forClass(CommunicationEndpoint.class);
            verify(repository).save(captor.capture());
            assertSame(endpoint, captor.getValue());
        }
    }

    @Nested
    @DisplayName("isOpen — session window state check")
    class IsOpenTests {

        @Test
        @DisplayName("returns true when session_closes_at is in the future")
        void openWhenClosesAtIsFuture() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW.plus(Duration.ofHours(12)));

            assertTrue(service.isOpen(endpoint));
        }

        @Test
        @DisplayName("returns false when session_closes_at is in the past")
        void closedWhenClosesAtIsPast() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW.minus(Duration.ofMinutes(1)));

            assertFalse(service.isOpen(endpoint));
        }

        @Test
        @DisplayName("returns false when session_closes_at is null (no session)")
        void closedWhenClosesAtIsNull() {
            CommunicationEndpoint endpoint = createEndpoint();
            // No session timestamps set

            assertFalse(service.isOpen(endpoint));
        }

        @Test
        @DisplayName("returns false when session_closes_at equals now (boundary)")
        void closedAtExactBoundary() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW); // Exactly now — not before, so isBefore returns false

            assertFalse(service.isOpen(endpoint));
        }
    }

    @Nested
    @DisplayName("getState — returns SessionState record")
    class GetStateTests {

        @Test
        @DisplayName("returns open state with closure time when session is active")
        void returnsOpenState() {
            CommunicationEndpoint endpoint = createEndpoint();
            Instant closesAt = NOW.plus(Duration.ofHours(20));
            endpoint.setSessionClosesAt(closesAt);

            SessionState state = service.getState(endpoint);

            assertTrue(state.open());
            assertEquals(closesAt, state.closesAt());
        }

        @Test
        @DisplayName("returns closed state when session has expired")
        void returnsClosedState() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW.minus(Duration.ofHours(1)));

            SessionState state = service.getState(endpoint);

            assertFalse(state.open());
            assertNull(state.closesAt());
        }

        @Test
        @DisplayName("returns closed state when no session exists")
        void returnsClosedWhenNoSession() {
            CommunicationEndpoint endpoint = createEndpoint();

            SessionState state = service.getState(endpoint);

            assertTrue(state.isClosed());
            assertNull(state.closesAt());
        }
    }

    @Nested
    @DisplayName("checkBeforeDelivery — pre-outbound session validation")
    class CheckBeforeDeliveryTests {

        @Test
        @DisplayName("allows delivery when session is open (non-template)")
        void allowsWhenOpen() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW.plus(Duration.ofHours(10)));

            SessionCheckResult result = service.checkBeforeDelivery(endpoint, false);

            assertTrue(result.allowed());
            assertNull(result.reason());
        }

        @Test
        @DisplayName("allows template delivery when session is closed")
        void allowsTemplateWhenClosed() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW.minus(Duration.ofHours(1)));

            SessionCheckResult result = service.checkBeforeDelivery(endpoint, true);

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("rejects non-template delivery when session is closed")
        void rejectsNonTemplateWhenClosed() {
            CommunicationEndpoint endpoint = createEndpoint();
            endpoint.setSessionClosesAt(NOW.minus(Duration.ofHours(1)));

            SessionCheckResult result = service.checkBeforeDelivery(endpoint, false);

            assertTrue(result.isRejected());
            assertEquals("SESSION_CLOSED", result.reason());
        }

        @Test
        @DisplayName("rejects non-template when no session exists (null closes_at)")
        void rejectsWhenNoSession() {
            CommunicationEndpoint endpoint = createEndpoint();

            SessionCheckResult result = service.checkBeforeDelivery(endpoint, false);

            assertTrue(result.isRejected());
            assertEquals("SESSION_CLOSED", result.reason());
        }

        @Test
        @DisplayName("allows template delivery when no session exists")
        void allowsTemplateWhenNoSession() {
            CommunicationEndpoint endpoint = createEndpoint();

            SessionCheckResult result = service.checkBeforeDelivery(endpoint, true);

            assertTrue(result.allowed());
        }
    }
}
