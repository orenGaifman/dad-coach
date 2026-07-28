package com.dadcoach.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dadcoach.onboarding.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filter that validates Content-Type header on requests with bodies (POST, PUT, PATCH).
 * Returns 415 Unsupported Media Type for non-JSON request bodies.
 */
@Component
@Order(2)
public class ContentTypeFilter extends OncePerRequestFilter {

    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");
    private final ObjectMapper objectMapper;

    public ContentTypeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (METHODS_WITH_BODY.contains(request.getMethod().toUpperCase())) {
            String contentType = request.getContentType();
            if (contentType == null || !isJsonContentType(contentType)) {
                response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorResponse errorResponse = ErrorResponse.of(
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Content-Type must be application/json"
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isJsonContentType(String contentType) {
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return mediaType.isCompatibleWith(MediaType.APPLICATION_JSON);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/onboarding") && !path.startsWith("/api/v1/invitations");
    }
}
