package com.dadcoach.systemstate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that guarantees cleanup of {@link SystemStateCache} after request completion.
 * 
 * <p>This filter ensures the ThreadLocal cache is always cleared to prevent memory leaks
 * in servlet container thread pools. It runs after all other processing is complete,
 * using a finally block to guarantee cleanup regardless of exceptions.</p>
 * 
 * <p>The filter runs at a low priority (@Order(100)) to ensure it executes after
 * authentication and other higher-priority filters have set up their context,
 * but the finally block ensures cleanup happens last.</p>
 * 
 * <h2>Request Processing Flow</h2>
 * <pre>
 * Request arrives
 *     ↓
 * Authentication filters run (ActorContextFilter, etc.)
 *     ↓
 * SystemStateCacheFilter.doFilterInternal enters
 *     ↓
 * Downstream filters and controllers run
 *   → WorkflowEngine loads and caches SystemState
 *   → Business logic uses cached state
 *     ↓
 * SystemStateCacheFilter.finally block runs
 *   → SystemStateCache.clear() called
 *     ↓
 * Response sent
 * </pre>
 * 
 * @see SystemStateCache
 * @see com.dadcoach.api.auth.ActorContextFilter
 */
@Component
@Order(100) // Run after auth filters, but cleanup happens in finally block
public class SystemStateCacheFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SystemStateCacheFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear the cache after request completion to prevent thread-local leaks.
            // This is critical because servlet containers reuse threads from a pool.
            if (SystemStateCache.isCached()) {
                log.trace("Clearing SystemStateCache after request to {}", request.getRequestURI());
            }
            SystemStateCache.clear();
        }
    }
}
