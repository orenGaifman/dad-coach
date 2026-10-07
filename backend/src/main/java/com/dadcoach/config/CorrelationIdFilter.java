package com.dadcoach.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Generates (or propagates) a correlationId for every request, puts it in the MDC so every log
 * line for this request carries it, and echoes it back as a response header so a user-reported
 * correlationId can be grepped straight to the relevant log lines.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is required, not cosmetic: Spring Boot registers a plain
 * {@code @Component} filter at the lowest precedence by default, which places it AFTER Spring
 * Security's own {@code FilterChainProxy} in the servlet container's actual filter order — a
 * request Spring Security rejects would then carry no correlationId (lesson inherited from the
 * Big Boss reference implementation).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final String START_TIME_ATTRIBUTE = "httpAccessLog.startTimeNanos";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Milliseconds since this request started, or -1 if called for a request this filter never saw. */
    public static long elapsedMillis(HttpServletRequest request) {
        Object startedAt = request.getAttribute(START_TIME_ATTRIBUTE);
        return startedAt instanceof Long startNanos ? (System.nanoTime() - startNanos) / 1_000_000 : -1;
    }

    /** Reads the correlationId for the request currently being handled, if any. */
    public static String currentCorrelationId() {
        return MDC.get(MDC_KEY);
    }
}
