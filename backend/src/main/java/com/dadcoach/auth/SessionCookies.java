package com.dadcoach.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The {@code DADCOACH_SESSION} cookie: HttpOnly, Secure (outside local dev), SameSite=Lax, path /. Its own name, so
 * Dad Coach never shares a cookie with Big Boss or Tair on one host (localhost cookies ignore the port).
 */
@Component
public class SessionCookies {

    public static final String NAME = "DADCOACH_SESSION";
    /** The CSRF double-submit cookie (readable by the SPA, echoed as X-XSRF-TOKEN). */
    public static final String CSRF_COOKIE = "DADCOACH_XSRF";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final DashboardProperties properties;

    public SessionCookies(DashboardProperties properties) {
        this.properties = properties;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    public void write(HttpServletResponse response, String rawToken) {
        set(response, rawToken, SessionService.IDLE_TTL);
    }

    public void clear(HttpServletResponse response) {
        set(response, "", Duration.ZERO);
    }

    public boolean secure() {
        return properties.isCookieSecure();
    }

    private void set(HttpServletResponse response, String value, Duration maxAge) {
        response.addHeader("Set-Cookie", ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString());
    }
}
