package com.dadcoach.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The admin API ({@code /api/v1/admin/**}: listing, reading and permanently deleting fathers, searching them, their
 * memories) is for operators only: an {@code X-API-Key} equal to {@code dad-coach.security.admin-api-key}
 * ({@code DADCOACH_ADMIN_API_KEY}) grants {@code ROLE_ADMIN_API}, the only role {@link SecurityConfig} accepts there.
 * No key configured = nobody gets in (fails closed). A JWT never does: nothing issues admin tokens, and a token is
 * only as strong as its signing secret.
 */
@Component
public class AdminApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyAuthFilter.class);
    static final String API_KEY_HEADER = "X-API-Key";
    static final String PATH_PREFIX = "/api/v1/admin/";
    public static final String ROLE = "ADMIN_API";

    private final String adminApiKey;

    public AdminApiKeyAuthFilter(@Value("${dad-coach.security.admin-api-key:}") String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX) && !request.getRequestURI().equals("/api/v1/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(API_KEY_HEADER);
        if (adminApiKey == null || adminApiKey.isBlank() || provided == null
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Admin API refused (missing or wrong key): {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"https://dadcoach.app/errors/UNAUTHORIZED\",\"title\":\"Authentication Required\","
                    + "\"status\":401,\"detail\":\"A valid admin API key is required\",\"error_code\":\"UNAUTHORIZED\",\"retryable\":false}");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-api-key", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        filterChain.doFilter(request, response);
    }
}
