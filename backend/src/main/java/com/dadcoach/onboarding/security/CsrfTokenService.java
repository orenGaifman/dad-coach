package com.dadcoach.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the Synchronizer Token Pattern for CSRF protection.
 * Generates 128-bit random tokens (32 hex chars) per session, stored server-side,
 * and validated on all state-changing requests via the X-CSRF-Token header.
 */
@Service
public class CsrfTokenService {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenService.class);

    public static final String CSRF_HEADER = "X-CSRF-Token";
    private static final int TOKEN_BYTES = 16; // 128 bits
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * In-memory token store mapping session IDs to CSRF tokens.
     * In production, this should be backed by Redis or the database session store.
     */
    private final Map<UUID, String> tokenStore = new ConcurrentHashMap<>();

    /**
     * Generates a new CSRF token for the given session.
     * Replaces any existing token for that session.
     *
     * @param sessionId the onboarding session ID
     * @return the generated 32-character hex token
     */
    public String generateToken(UUID sessionId) {
        String token = generateRandomToken();
        tokenStore.put(sessionId, token);
        log.debug("Generated CSRF token for session {}", sessionId);
        return token;
    }

    /**
     * Validates the provided CSRF token against the stored token for the session.
     *
     * @param sessionId     the onboarding session ID
     * @param providedToken the token from the X-CSRF-Token header
     * @return true if valid, false otherwise
     */
    public boolean validateToken(UUID sessionId, String providedToken) {
        if (sessionId == null || providedToken == null || providedToken.isBlank()) {
            return false;
        }

        String storedToken = tokenStore.get(sessionId);
        if (storedToken == null) {
            log.warn("No CSRF token found for session {}", sessionId);
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        boolean valid = constantTimeEquals(storedToken, providedToken);
        if (!valid) {
            log.warn("Invalid CSRF token for session {}", sessionId);
        }
        return valid;
    }

    /**
     * Retrieves the current CSRF token for a session (for including in responses).
     *
     * @param sessionId the session ID
     * @return the current token, or null if none exists
     */
    public String getToken(UUID sessionId) {
        return tokenStore.get(sessionId);
    }

    /**
     * Removes the CSRF token when a session ends or expires.
     *
     * @param sessionId the session ID to clean up
     */
    public void invalidateToken(UUID sessionId) {
        tokenStore.remove(sessionId);
    }

    /**
     * Generates a 128-bit random token as a 32-character hex string.
     */
    private String generateRandomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * Constant-time string comparison to prevent timing side-channel attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
