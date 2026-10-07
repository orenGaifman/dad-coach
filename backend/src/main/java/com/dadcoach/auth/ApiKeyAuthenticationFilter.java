package com.dadcoach.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The ops surface's key ({@code X-API-Key}, constant-time compare, Tair). Fails closed: an unconfigured key
 * authenticates nothing, not even an empty header.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String ROLE_OPS = "ROLE_DASHBOARD_OPS";

    private final String expectedKey;

    public ApiKeyAuthenticationFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (TokenHashing.keyMatches(request.getHeader(HEADER), expectedKey)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "operator", null, List.of(new SimpleGrantedAuthority(ROLE_OPS))));
        }
        chain.doFilter(request, response);
    }
}
