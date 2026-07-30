package com.dadcoach.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implements CSRF protection using HMAC-based token derivation.
 * <p>
 * Tokens are derived deterministically from the session ID using HMAC-SHA256,
 * eliminating the need for server-side token storage. This means tokens survive
 * server restarts and work correctly on platforms like Render where instances
 * may be recycled.
 * <p>
 * The HMAC secret is derived from the application's JWT secret for simplicity.
 */
@Service
public class CsrfTokenService {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenService.class);

    public static final String CSRF_HEADER = "X-CSRF-Token";
    private final byte[] hmacSecret;

    public CsrfTokenService(@Value("${dad-coach.security.jwt.secret:default-dev-secret-change-in-production-must-be-at-least-256-bits-long!!}") String jwtSecret) {
        // Derive CSRF HMAC key from the JWT secret
        this.hmacSecret = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates a CSRF token for the given session.
     * The token is deterministically derived from the session ID via HMAC,
     * so it can be regenerated without storage.
     *
     * @param sessionId the onboarding session ID
     * @return the generated 64-character hex token
     */
    public String generateToken(UUID sessionId) {
        String token = computeHmac(sessionId);
        log.debug("Generated CSRF token for session {}", sessionId);
        return token;
    }

    /**
     * Validates the provided CSRF token against the expected token for the session.
     *
     * @param sessionId     the onboarding session ID
     * @param providedToken the token from the X-CSRF-Token header
     * @return true if valid, false otherwise
     */
    public boolean validateToken(UUID sessionId, String providedToken) {
        if (sessionId == null || providedToken == null || providedToken.isBlank()) {
            return false;
        }

        String expectedToken = computeHmac(sessionId);

        // Constant-time comparison to prevent timing attacks
        boolean valid = constantTimeEquals(expectedToken, providedToken);
        if (!valid) {
            log.warn("Invalid CSRF token for session {}", sessionId);
        }
        return valid;
    }

    /**
     * Retrieves the CSRF token for a session (deterministic, no lookup needed).
     *
     * @param sessionId the session ID
     * @return the token
     */
    public String getToken(UUID sessionId) {
        return computeHmac(sessionId);
    }

    /**
     * No-op: tokens are derived, not stored.
     */
    public void invalidateToken(UUID sessionId) {
        // No storage to clean up — tokens are derived from HMAC
    }

    private String computeHmac(UUID sessionId) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(hmacSecret, "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sessionId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute CSRF HMAC", e);
        }
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
