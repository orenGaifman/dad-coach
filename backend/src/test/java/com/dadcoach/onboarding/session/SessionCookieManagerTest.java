package com.dadcoach.onboarding.session;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SessionCookieManager}.
 */
class SessionCookieManagerTest {

    private SessionCookieManager cookieManager;

    @BeforeEach
    void setUp() {
        cookieManager = new SessionCookieManager();
    }

    @Test
    void generatedSessionIdIs64HexCharacters() {
        String sessionId = cookieManager.generateSessionId();
        assertNotNull(sessionId);
        assertEquals(64, sessionId.length(), "256-bit ID should be 64 hex chars");
        assertTrue(sessionId.matches("[0-9a-f]{64}"),
                "Session ID should be lowercase hexadecimal");
    }

    @Test
    void generatedSessionIdsAreUnique() {
        String id1 = cookieManager.generateSessionId();
        String id2 = cookieManager.generateSessionId();
        assertNotEquals(id1, id2, "Each generated session ID should be unique");
    }

    @Test
    void createCookieSetsCorrectAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String sessionId = "a".repeat(64);

        cookieManager.createCookie(response, sessionId);

        String setCookieHeader = response.getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("ONBOARDING_SESSION=" + sessionId));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Secure"));
        assertTrue(setCookieHeader.contains("SameSite=Strict"));
        assertTrue(setCookieHeader.contains("Path=/api/v1/onboarding"));
        // Max-Age should be 72 hours = 259200 seconds
        assertTrue(setCookieHeader.contains("Max-Age=259200"));
    }

    @Test
    void readSessionIdFromExistingCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String sessionId = "b".repeat(64);
        request.setCookies(new Cookie("ONBOARDING_SESSION", sessionId));

        Optional<String> result = cookieManager.readSessionId(request);

        assertTrue(result.isPresent());
        assertEquals(sessionId, result.get());
    }

    @Test
    void readSessionIdReturnsEmptyWhenNoCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No cookies set

        Optional<String> result = cookieManager.readSessionId(request);

        assertFalse(result.isPresent());
    }

    @Test
    void readSessionIdReturnsEmptyWhenWrongCookieName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER_COOKIE", "some-value"));

        Optional<String> result = cookieManager.readSessionId(request);

        assertFalse(result.isPresent());
    }

    @Test
    void readSessionIdReturnsEmptyForBlankValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ONBOARDING_SESSION", "   "));

        Optional<String> result = cookieManager.readSessionId(request);

        assertFalse(result.isPresent());
    }

    @Test
    void invalidateCookieSetsMaxAgeToZero() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieManager.invalidateCookie(response);

        String setCookieHeader = response.getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("ONBOARDING_SESSION="));
        assertTrue(setCookieHeader.contains("Max-Age=0"));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Secure"));
        assertTrue(setCookieHeader.contains("SameSite=Strict"));
    }

    @Test
    void cookieNameConstant() {
        assertEquals("ONBOARDING_SESSION", SessionCookieManager.COOKIE_NAME);
    }

    @Test
    void cookiePathConstant() {
        assertEquals("/api/v1/onboarding", SessionCookieManager.COOKIE_PATH);
    }
}
