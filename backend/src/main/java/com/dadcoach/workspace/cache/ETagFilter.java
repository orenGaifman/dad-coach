package com.dadcoach.workspace.cache;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Filter that adds ETag support for frequently-polled workspace endpoints.
 *
 * <p>Generates an ETag from the SHA-256 hash of the response body for GET requests
 * to workspace endpoints. Returns 304 Not Modified when the client's If-None-Match
 * header matches the generated ETag.</p>
 *
 * <p>Supported endpoints:</p>
 * <ul>
 *   <li>/api/v1/workspace/summary</li>
 *   <li>/api/v1/workspace/notifications</li>
 *   <li>/api/v1/workspace/growth/belt</li>
 *   <li>/api/v1/workspace/growth/streak</li>
 * </ul>
 */
@Component
@Order(1)
public class ETagFilter extends OncePerRequestFilter {

    private static final Set<String> ETAG_ENDPOINTS = Set.of(
            "/api/v1/workspace/summary",
            "/api/v1/workspace/notifications",
            "/api/v1/workspace/growth/belt",
            "/api/v1/workspace/growth/streak"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!isETagEligible(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        // Only add ETag for successful responses
        if (responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 300) {
            byte[] body = responseWrapper.getContentAsByteArray();

            if (body.length > 0) {
                String etag = generateETag(body);
                responseWrapper.setHeader("ETag", etag);

                String ifNoneMatch = request.getHeader("If-None-Match");
                if (etag.equals(ifNoneMatch)) {
                    responseWrapper.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    responseWrapper.resetBuffer();
                    responseWrapper.copyBodyToResponse();
                    return;
                }
            }
        }

        responseWrapper.copyBodyToResponse();
    }

    private boolean isETagEligible(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return ETAG_ENDPOINTS.contains(request.getRequestURI());
    }

    private String generateETag(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return "\"" + HexFormat.of().formatHex(hash).substring(0, 32) + "\"";
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
