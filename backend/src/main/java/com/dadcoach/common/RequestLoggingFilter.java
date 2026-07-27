package com.dadcoach.common;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every HTTP request with method, path, status, duration, and content-length as
 * structured key-value fields. Uses logstash-logback-encoder StructuredArguments so that
 * JSON output produces proper top-level fields rather than embedded strings.
 *
 * <p>Only safe metadata is logged — request/response bodies are never read or logged,
 * preventing accidental exposure of sensitive webhook payload content.
 *
 * <p>Produces JSON like:
 * {@code {"message":"HTTP request completed","method":"POST","path":"/webhooks/whatsapp","status":200,"durationMs":45,"contentLength":128}}
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            String method = request.getMethod();
            String path = request.getRequestURI();
            int status = response.getStatus();
            long contentLength = request.getContentLengthLong();

            // Log only safe metadata — never read or log request/response body content
            log.info("HTTP request completed",
                    kv("method", method),
                    kv("path", path),
                    kv("status", status),
                    kv("durationMs", durationMs),
                    kv("contentLength", contentLength));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip actuator health check endpoints to reduce log noise
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}
