package com.dadcoach.onboarding.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsrfTokenService.
 */
class CsrfTokenServiceTest {

    private CsrfTokenService csrfTokenService;

    @BeforeEach
    void setUp() {
        csrfTokenService = new CsrfTokenService();
    }

    @Test
    void generateToken_returns32CharHexString() {
        UUID sessionId = UUID.randomUUID();
        String token = csrfTokenService.generateToken(sessionId);

        assertNotNull(token);
        assertEquals(32, token.length());
        assertTrue(token.matches("[0-9a-f]{32}"));
    }

    @Test
    void generateToken_uniquePerCall() {
        UUID sessionId = UUID.randomUUID();
        String token1 = csrfTokenService.generateToken(sessionId);
        String token2 = csrfTokenService.generateToken(sessionId);

        // Each call replaces the token
        assertNotEquals(token1, token2);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        UUID sessionId = UUID.randomUUID();
        String token = csrfTokenService.generateToken(sessionId);

        assertTrue(csrfTokenService.validateToken(sessionId, token));
    }

    @Test
    void validateToken_wrongToken_returnsFalse() {
        UUID sessionId = UUID.randomUUID();
        csrfTokenService.generateToken(sessionId);

        assertFalse(csrfTokenService.validateToken(sessionId, "wrong-token-value-here-12345678"));
    }

    @Test
    void validateToken_wrongSession_returnsFalse() {
        UUID sessionId = UUID.randomUUID();
        String token = csrfTokenService.generateToken(sessionId);

        assertFalse(csrfTokenService.validateToken(UUID.randomUUID(), token));
    }

    @Test
    void validateToken_nullSession_returnsFalse() {
        assertFalse(csrfTokenService.validateToken(null, "some-token"));
    }

    @Test
    void validateToken_nullToken_returnsFalse() {
        UUID sessionId = UUID.randomUUID();
        csrfTokenService.generateToken(sessionId);

        assertFalse(csrfTokenService.validateToken(sessionId, null));
    }

    @Test
    void validateToken_blankToken_returnsFalse() {
        UUID sessionId = UUID.randomUUID();
        csrfTokenService.generateToken(sessionId);

        assertFalse(csrfTokenService.validateToken(sessionId, "   "));
    }

    @Test
    void getToken_existingSession_returnsToken() {
        UUID sessionId = UUID.randomUUID();
        String token = csrfTokenService.generateToken(sessionId);

        assertEquals(token, csrfTokenService.getToken(sessionId));
    }

    @Test
    void getToken_nonexistentSession_returnsNull() {
        assertNull(csrfTokenService.getToken(UUID.randomUUID()));
    }

    @Test
    void invalidateToken_removesToken() {
        UUID sessionId = UUID.randomUUID();
        csrfTokenService.generateToken(sessionId);

        csrfTokenService.invalidateToken(sessionId);

        assertNull(csrfTokenService.getToken(sessionId));
        assertFalse(csrfTokenService.validateToken(sessionId, "any-token"));
    }

    @Test
    void generateToken_replacesExistingToken() {
        UUID sessionId = UUID.randomUUID();
        String token1 = csrfTokenService.generateToken(sessionId);
        String token2 = csrfTokenService.generateToken(sessionId);

        // Old token should not validate
        assertFalse(csrfTokenService.validateToken(sessionId, token1));
        // New token should validate
        assertTrue(csrfTokenService.validateToken(sessionId, token2));
    }
}
