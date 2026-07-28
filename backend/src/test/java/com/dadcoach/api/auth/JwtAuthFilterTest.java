package com.dadcoach.api.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256!!";
    private static final String ISSUER = "dad-coach";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET, ISSUER);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChainWithoutAuth_whenNoAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueFilterChainWithoutAuth_whenAuthHeaderNotBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateFatherToken_withFatherIdClaim() throws Exception {
        UUID fatherId = UUID.randomUUID();
        String token = buildToken("FATHER", fatherId.toString(), null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_FATHER");

        JwtAuthFilter.JwtPrincipal principal = (JwtAuthFilter.JwtPrincipal) auth.getPrincipal();
        assertThat(principal.fatherId()).isEqualTo(fatherId);
        assertThat(principal.role()).isEqualTo("FATHER");
    }

    @Test
    void shouldAuthenticateAdminToken_withAdminRole() throws Exception {
        String token = buildToken("ADMIN", null, null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_ADMIN");

        JwtAuthFilter.JwtPrincipal principal = (JwtAuthFilter.JwtPrincipal) auth.getPrincipal();
        assertThat(principal.role()).isEqualTo("ADMIN");
        assertThat(principal.fatherId()).isNull();
    }

    @Test
    void shouldAuthenticateServiceToken_withServiceRole() throws Exception {
        String token = buildToken("SERVICE", null, null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_SERVICE");
    }

    @Test
    void shouldReturn401WithTokenExpired_whenTokenIsExpired() throws Exception {
        String expiredToken = Jwts.builder()
                .subject("test-user")
                .issuer(ISSUER)
                .claim("role", "FATHER")
                .claim("father_id", UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(SIGNING_KEY)
                .compact();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(response).setContentType("application/problem+json");
        verify(filterChain, never()).doFilter(request, response);

        printWriter.flush();
        String body = stringWriter.toString();
        assertThat(body).contains("TOKEN_EXPIRED");
    }

    @Test
    void shouldContinueWithoutAuth_whenTokenHasInvalidSignature() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-that-is-at-least-256-bits-long-for-hs256!!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("test-user")
                .issuer(ISSUER)
                .claim("role", "FATHER")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueWithoutAuth_whenTokenHasNoRoleClaim() throws Exception {
        String token = Jwts.builder()
                .subject("test-user")
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY)
                .compact();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private String buildToken(String role, String fatherId, String subject) {
        var builder = Jwts.builder()
                .subject(subject != null ? subject : "test-user")
                .issuer(ISSUER)
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY);

        if (fatherId != null) {
            builder.claim("father_id", fatherId);
        }

        return builder.compact();
    }
}
