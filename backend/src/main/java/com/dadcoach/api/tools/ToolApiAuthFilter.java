package com.dadcoach.api.tools;

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
 * Authentication filter for the Tool API.
 * 
 * <p>Validates the X-API-Key header for requests to /api/tools/*.</p>
 * 
 * <p>This filter:</p>
 * <ul>
 *   <li>Only applies to requests matching /api/tools/*</li>
 *   <li>Validates the X-API-Key header against the configured API key</li>
 *   <li>Sets authentication in SecurityContext on success</li>
 *   <li>Returns 401 Unauthorized if the key is missing or invalid</li>
 *   <li>Returns 503 Service Unavailable if the API is disabled</li>
 * </ul>
 * 
 * @see ToolApiConfig
 * @see ToolApiController
 */
@Component
public class ToolApiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ToolApiAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TOOL_API_PATH_PREFIX = "/api/tools";

    private final ToolApiConfig toolApiConfig;
    private final ObjectMapper objectMapper;

    public ToolApiAuthFilter(ToolApiConfig toolApiConfig) {
        this.toolApiConfig = toolApiConfig;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only filter requests to /api/tools/*
        return !path.startsWith(TOOL_API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        log.info("ToolApiAuthFilter processing request: {} {}", method, requestPath);

        // Check if API is enabled
        if (!toolApiConfig.isEnabled()) {
            log.warn("Tool API is disabled, rejecting request: {} {}", method, requestPath);
            sendErrorResponse(response, 503, "Tool API is temporarily disabled", "SERVICE_UNAVAILABLE");
            return;
        }

        // Validate API key
        String providedApiKey = request.getHeader(API_KEY_HEADER);
        if (providedApiKey == null || providedApiKey.isBlank()) {
            log.warn("Missing API key for Tool API request: {} {}", method, requestPath);
            sendErrorResponse(response, 401, "Missing API key", "UNAUTHORIZED");
            return;
        }

        String configuredKey = toolApiConfig.getApiKey();
        
        // Debug logging - show first/last 4 chars only for security
        String providedKeyDebug = providedApiKey.length() > 8 
            ? providedApiKey.substring(0, 4) + "..." + providedApiKey.substring(providedApiKey.length() - 4)
            : "***";
        String configuredKeyDebug = configuredKey != null && configuredKey.length() > 8 
            ? configuredKey.substring(0, 4) + "..." + configuredKey.substring(configuredKey.length() - 4)
            : "***";
        log.info("API key validation - provided: {}, configured: {}, lengths: {}/{}", 
            providedKeyDebug, configuredKeyDebug, 
            providedApiKey.length(), configuredKey != null ? configuredKey.length() : 0);

        if (!providedApiKey.equals(configuredKey)) {
            log.warn("Invalid API key for Tool API request: {} {} - key mismatch", method, requestPath);
            sendErrorResponse(response, 401, "Invalid API key", "UNAUTHORIZED");
            return;
        }

        // API key is valid - set authentication in SecurityContext
        // This ensures Spring Security's authorization checks pass
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_TOOL_API")
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("tool-api-client", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("Tool API request authenticated successfully: {} {}", method, requestPath);
        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(
            HttpServletResponse response,
            int status,
            String message,
            String errorCode) throws IOException {
        
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ToolExecutionResponse errorResponse = ToolExecutionResponse.failure(message, errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
