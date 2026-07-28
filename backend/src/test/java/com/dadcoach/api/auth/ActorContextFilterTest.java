package com.dadcoach.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ActorContextFilterTest {

    private ActorContextFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ActorContextFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
        ActorContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ActorContext.clear();
    }

    @Test
    void shouldPopulateActorContext_forFatherPrincipal() throws Exception {
        UUID fatherId = UUID.randomUUID();
        setAuthentication("test-father", fatherId, "FATHER");

        filter.doFilterInternal(request, response, filterChain);

        // Context is cleared after filter completes, so verify during chain execution
        verify(filterChain).doFilter(request, response);
        // After filter completes, context should be cleared
        assertThat(ActorContext.current()).isNull();
    }

    @Test
    void shouldPopulateActorContextDuringRequest_forFather() throws Exception {
        UUID fatherId = UUID.randomUUID();
        setAuthentication("test-father", fatherId, "FATHER");

        // Capture the ActorContext during filter chain execution
        doAnswer(invocation -> {
            ActorContext ctx = ActorContext.current();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getActorType()).isEqualTo(ActorType.FATHER);
            assertThat(ctx.getActorId()).isEqualTo(fatherId);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldPopulateActorContextDuringRequest_forAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        setAuthentication(adminId.toString(), null, "ADMIN");

        doAnswer(invocation -> {
            ActorContext ctx = ActorContext.current();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getActorType()).isEqualTo(ActorType.ADMIN);
            assertThat(ctx.getActorId()).isEqualTo(adminId);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldPopulateActorContextDuringRequest_forService() throws Exception {
        UUID serviceId = UUID.randomUUID();
        setAuthentication(serviceId.toString(), null, "SERVICE");

        doAnswer(invocation -> {
            ActorContext ctx = ActorContext.current();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getActorType()).isEqualTo(ActorType.SERVICE);
            assertThat(ctx.getActorId()).isEqualTo(serviceId);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldClearContextAfterRequest_evenOnException() throws Exception {
        UUID fatherId = UUID.randomUUID();
        setAuthentication("test-father", fatherId, "FATHER");
        doThrow(new RuntimeException("test error")).when(filterChain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        assertThat(ActorContext.current()).isNull();
    }

    @Test
    void shouldNotSetContext_whenNoAuthentication() throws Exception {
        // SecurityContext has no authentication

        doAnswer(invocation -> {
            assertThat(ActorContext.current()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldNotSetContext_whenPrincipalIsNotJwtPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("string-principal", null,
                        List.of(new SimpleGrantedAuthority("ROLE_FATHER"))));

        doAnswer(invocation -> {
            assertThat(ActorContext.current()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldDeriveActorIdFromNonUuidSubject_forAdmin() throws Exception {
        setAuthentication("admin-user-name", null, "ADMIN");

        doAnswer(invocation -> {
            ActorContext ctx = ActorContext.current();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getActorType()).isEqualTo(ActorType.ADMIN);
            // Non-UUID subject gets a deterministic UUID via nameUUIDFromBytes
            assertThat(ctx.getActorId()).isNotNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    private void setAuthentication(String subject, UUID fatherId, String role) {
        JwtAuthFilter.JwtPrincipal principal = new JwtAuthFilter.JwtPrincipal(subject, fatherId, role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
