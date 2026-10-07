package com.dadcoach.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the dashboard chain from the session cookie. The principal is a {@link DashboardPrincipal} with
 * ROLE_FATHER and/or ROLE_STAFF; an absent, unknown, revoked or expired session leaves the request unauthenticated
 * (401). When the idle window slides, the cookie is re-sent.
 */
public class SessionCookieAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE_FATHER = "ROLE_DASHBOARD_FATHER";
    public static final String ROLE_STAFF = "ROLE_DASHBOARD_STAFF";

    private final SessionService sessions;
    private final SessionCookies cookies;

    public SessionCookieAuthenticationFilter(SessionService sessions, SessionCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.read(request).ifPresent(raw -> sessions.authenticate(raw).ifPresent(auth -> {
            List<SimpleGrantedAuthority> roles = new ArrayList<>();
            if (auth.principal().isFather()) {
                roles.add(new SimpleGrantedAuthority(ROLE_FATHER));
            }
            if (auth.principal().isAdmin()) {
                roles.add(new SimpleGrantedAuthority(ROLE_STAFF));
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(auth.principal(), null, roles));
            if (auth.renewed()) {
                cookies.write(response, raw);
            }
        }));
        chain.doFilter(request, response);
    }
}
