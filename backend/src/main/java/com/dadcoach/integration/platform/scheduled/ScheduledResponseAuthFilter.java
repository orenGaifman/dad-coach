package com.dadcoach.integration.platform.scheduled;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * X-API-Key authentication for the Workflow Platform's inbound callbacks ({@code /api/integration/**}),
 * following the same pattern as {@code ToolApiAuthFilter}.
 */
@Component
public class ScheduledResponseAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ScheduledResponseAuthFilter.class);
    static final String API_KEY_HEADER = "X-API-Key";
    static final String PATH_PREFIX = "/api/integration/";

    private final ScheduledResponseCallbackConfig config;

    public ScheduledResponseAuthFilter(ScheduledResponseCallbackConfig config) {
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!config.isEnabled()) {
            log.warn("Scheduled-response callback is disabled, rejecting: {} {}", request.getMethod(), request.getRequestURI());
            reject(response, 503, "Scheduled-response callback is disabled");
            return;
        }
        String configuredKey = config.getApiKey();
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (configuredKey == null || configuredKey.isBlank() || providedKey == null || !constantTimeEquals(providedKey, configuredKey)) {
            log.warn("Invalid or missing API key for platform callback: {} {}", request.getMethod(), request.getRequestURI());
            reject(response, 401, "Invalid or missing API key");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "workflow-platform-callback", null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_CALLBACK"))));
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":\"REJECTED\",\"detail\":\"" + detail + "\"}");
    }
}
