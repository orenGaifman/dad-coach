package com.dadcoach.api.idempotency;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IdempotencyFilterTest {

    private IdempotencyFilter filter;
    private IdempotencyStore store;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        store = new IdempotencyStore();
        filter = new IdempotencyFilter(store);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void shouldPassThrough_forGetRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        request.addHeader("Idempotency-Key", "some-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThrough_whenNoIdempotencyKeyHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThrough_whenIdempotencyKeyIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        request.addHeader("Idempotency-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThrough_whenNoActorContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        request.addHeader("Idempotency-Key", "key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // No actor context set
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnCachedResponse_forDuplicateKey() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        String compositeKey = store.compositeKey(actorId, "dup-key");
        byte[] cachedBody = "{\"id\":\"child-123\"}".getBytes();
        store.put(compositeKey, 201, cachedBody, "application/json");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        request.addHeader("Idempotency-Key", "dup-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Filter chain should NOT be called — cached response returned
        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"child-123\"}");
        assertThat(response.getContentType()).isEqualTo("application/json");
    }

    @Test
    void shouldProcessAndCacheResponse_forNewKey() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        request.addHeader("Idempotency-Key", "new-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate the filter chain writing a response
        doAnswer(invocation -> {
            HttpServletResponse resp = (HttpServletResponse) invocation.getArgument(1);
            resp.setStatus(201);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"id\":\"new-child\"}");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        // Verify the response was cached
        String compositeKey = store.compositeKey(actorId, "new-key");
        var cached = store.get(compositeKey);
        assertThat(cached).isPresent();
        assertThat(cached.get().status()).isEqualTo(201);
        assertThat(new String(cached.get().body())).isEqualTo("{\"id\":\"new-child\"}");
    }

    @Test
    void shouldScopeKeyToActor_differentActorsSameKey() throws Exception {
        UUID actor1 = UUID.randomUUID();
        UUID actor2 = UUID.randomUUID();

        // Actor 1 caches a response
        String compositeKey1 = store.compositeKey(actor1, "shared-key");
        store.put(compositeKey1, 200, "actor1-response".getBytes(), "text/plain");

        // Actor 2 sends the same idempotency key — should NOT get actor 1's response
        ActorContext.set(new ActorContext(ActorType.FATHER, actor2));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        request.addHeader("Idempotency-Key", "shared-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse resp = (HttpServletResponse) invocation.getArgument(1);
            resp.setStatus(201);
            resp.setContentType("text/plain");
            resp.getWriter().write("actor2-response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        // Filter chain SHOULD be called (different actor, different composite key)
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void shouldApplyToPutRequests() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        String compositeKey = store.compositeKey(actorId, "put-key");
        store.put(compositeKey, 200, "cached-put".getBytes(), "text/plain");

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/fathers/me");
        request.addHeader("Idempotency-Key", "put-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldApplyToDeleteRequests() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        String compositeKey = store.compositeKey(actorId, "delete-key");
        store.put(compositeKey, 204, new byte[0], null);

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/fathers/me");
        request.addHeader("Idempotency-Key", "delete-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void shouldRemoveReservation_whenProcessingFails() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fathers/me/children");
        request.addHeader("Idempotency-Key", "fail-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new RuntimeException("processing error")).when(filterChain).doFilter(any(), any());

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        // The key reservation should be removed so it can be retried
        String compositeKey = store.compositeKey(actorId, "fail-key");
        assertThat(store.get(compositeKey)).isEmpty();
        assertThat(store.isProcessing(compositeKey)).isFalse();
    }
}
