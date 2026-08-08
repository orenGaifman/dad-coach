package com.dadcoach.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT authentication filter that validates tokens on every authenticated request.
 * <p>
 * Father tokens contain a {@code father_id} claim used for resource ownership enforcement.
 * Admin tokens contain {@code role} claims for role-based access control.
 * Expired tokens result in a 401 with TOKEN_EXPIRED error code.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey signingKey;
    private final String issuer;

    public JwtAuthFilter(
            @Value("${dad-coach.security.jwt.secret}") String secret,
            @Value("${dad-coach.security.jwt.issuer}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String role = claims.get("role", String.class);
            if (role == null) {
                filterChain.doFilter(request, response);
                return;
            }

            String subject = claims.getSubject();
            Long actorId = null;

            // Extract father_id from Father tokens (stored as Long, not UUID)
            if ("FATHER".equalsIgnoreCase(role)) {
                String fatherId = claims.get("father_id", String.class);
                if (fatherId != null) {
                    try {
                        actorId = Long.parseLong(fatherId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid father_id in token: {}", fatherId);
                    }
                }
            }

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
            );

            JwtPrincipal principal = new JwtPrincipal(subject, actorId, role.toUpperCase());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token for subject: {}", e.getClaims().getSubject());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_EXPIRED", "The authentication token has expired");
            return;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, int status,
                                    String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write(String.format(
                """
                {
                  "type": "https://dadcoach.app/errors/%s",
                  "title": "Authentication Failure",
                  "status": %d,
                  "detail": "%s",
                  "error_code": "%s",
                  "retryable": true
                }
                """, errorCode, status, message, errorCode));
    }

    /**
     * Principal object carrying JWT claims for the authenticated actor.
     */
    public record JwtPrincipal(String subject, Long fatherId, String role) {
    }
}
