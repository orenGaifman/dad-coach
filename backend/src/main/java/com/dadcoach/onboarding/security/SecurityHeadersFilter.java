package com.dadcoach.onboarding.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security headers filter for onboarding endpoints.
 * Adds security-related HTTP headers to all responses from onboarding paths.
 *
 * Headers applied:
 * - Content-Security-Policy: restricts content sources
 * - X-Content-Type-Options: nosniff — prevents MIME type sniffing
 * - X-Frame-Options: DENY — prevents clickjacking
 * - Strict-Transport-Security: enforces HTTPS (max-age=1 year)
 * - Referrer-Policy: strict-origin-when-cross-origin — limits referer info
 * - X-XSS-Protection: 1; mode=block — legacy XSS protection for older browsers
 * - Cache-Control: no-store — prevents caching of sensitive onboarding data
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP_POLICY = String.join("; ",
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data:",
        "font-src 'self'",
        "connect-src 'self'",
        "frame-ancestors 'none'",
        "base-uri 'self'",
        "form-action 'self'"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Content Security Policy
        response.setHeader("Content-Security-Policy", CSP_POLICY);

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Enforce HTTPS (max-age = 1 year, include subdomains)
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Limit referrer information
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Legacy XSS protection for older browsers
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Prevent caching of sensitive onboarding data
        if (request.getRequestURI().contains("/api/v1/onboarding") ||
            request.getRequestURI().contains("/api/v1/invitations")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply to API paths (skip actuator, static resources, etc.)
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}
