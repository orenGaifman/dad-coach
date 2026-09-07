package com.dadcoach.api.context;

import com.dadcoach.api.tools.ToolApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.List;

/**
 * Authentication filter for the Context Provider API.
 * 
 * <p>Validates the X-API-Key header for requests to /api/context/*.</p>
 * 
 * <p>This filter reuses the same API key configuration as the Tool API
 * (tool-api.api-key) for consistency.</p>
 * 
 * <p>This filter:</p>
 * <ul>
 *   <li>Only applies to requests matching /api/context/*</li>
 *   <li>Validates the X-API-Key header against the configured API key</li>
 *   <li>Sets authentication in SecurityContext on success</li>
 *   <li>Returns 401 Unauthorized if the key is missing or invalid</li>
 *   <li>Returns 503 Service Unavailable if the API is disabled</li>
 * </ul>
 * 
 * @see ToolApiConfig
 * @see ContextProviderController
 */
@Component
public class ContextProviderAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ContextProviderAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CONTEXT_API_PATH_PREFIX = "/api/context";

    private final ToolApiConfig toolApiConfig;
    private final ObjectMapper objectMapper;

    public ContextProviderAuthFilter(ToolApiConfig toolApiConfig) {
        this.toolApiConfig = toolApiConfig;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only filter requests to /api/context/*
        return !path.startsWith(CONTEXT_API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        log.debug("ContextProviderAuthFilter processing request: {} {}", method, requestPath);

        // Check if API is enabled (reuse the same enabled flag as Tool API)
        if (!toolApiConfig.isEnabled()) {
            log.warn("Context Provider API is disabled, rejecting request: {} {}", method, requestPath);
            sendErrorResponse(response, 503, "Context Provider API is temporarily disabled", "SERVICE_UNAVAILABLE");
            return;
        }

        // Validate API key
        String providedApiKey = request.getHeader(API_KEY_HEADER);
        if (providedApiKey == null || providedApiKey.isBlank()) {
            log.warn("Missing API key for Context Provider API request: {} {}", method, requestPath);
            sendErrorResponse(response, 401, "Missing API key", "UNAUTHORIZED");
            return;
        }

        if (!providedApiKey.equals(toolApiConfig.getApiKey())) {
            log.warn("Invalid API key for Context Provider API request: {} {}", method, requestPath);
            sendErrorResponse(response, 401, "Invalid API key", "UNAUTHORIZED");
            return;
        }

        // API key is valid - set authentication in SecurityContext
        // This ensures Spring Security's authorization checks pass
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_CONTEXT_API")
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("context-api-client", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Context Provider API request authenticated: {} {}", method, requestPath);
        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(
            HttpServletResponse response,
            int status,
            String message,
            String errorCode) throws IOException {
        
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ContextProviderResponse errorResponse = ContextProviderResponse.failure(message, errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
