package com.dadcoach.onboarding.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Manages the onboarding session cookie (ONBOARDING_SESSION).
 *
 * <p>The cookie contains a 256-bit random session ID (hex-encoded = 64 characters),
 * generated using {@link SecureRandom}. This ID maps to the server-side
 * {@link OnboardingSession} record.
 *
 * <p>Cookie properties (security hardening per Req 6 criteria 4):
 * <ul>
 *   <li>HttpOnly = true (not accessible via JavaScript)</li>
 *   <li>Secure = true (transmitted only over HTTPS)</li>
 *   <li>SameSite = Strict (prevents CSRF from cross-origin requests)</li>
 *   <li>Path = /api/v1/onboarding (scoped to onboarding endpoints)</li>
 * </ul>
 */
@Component
public class SessionCookieManager {

    /** Cookie name used for the onboarding session. */
    public static final String COOKIE_NAME = "ONBOARDING_SESSION";

    /** Cookie path scoped to onboarding API endpoints. */
    public static final String COOKIE_PATH = "/api/v1/onboarding";

    /** Session ID length in bytes (256 bits). */
    private static final int SESSION_ID_BYTES = 32;

    /** Session cookie max-age: 72 hours (matching session TTL). */
    private static final long COOKIE_MAX_AGE_SECONDS = 72 * 60 * 60;

    private final SecureRandom secureRandom;

    public SessionCookieManager() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a cryptographically secure 256-bit random session ID.
     *
     * @return a 64-character hex-encoded session ID
     */
    public String generateSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Creates the session cookie and adds it to the HTTP response.
     *
     * @param response the HTTP response to add the cookie to
     * @param sessionId the session ID value (64-char hex string)
     */
    public void createCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Reads the session ID from the ONBOARDING_SESSION cookie in the request.
     *
     * @param request the HTTP request containing the cookie
     * @return the session ID if the cookie exists and is non-empty, empty otherwise
     */
    public Optional<String> readSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Invalidates the session cookie by setting its max-age to 0.
     * This instructs the browser to delete the cookie immediately.
     *
     * @param response the HTTP response to add the invalidation cookie to
     */
    public void invalidateCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
