package com.dadcoach.api.ratelimit;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import com.dadcoach.api.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private RateLimitConfig config;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        config = new RateLimitConfig();
        filter = new RateLimitFilter(config);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void shouldPassThrough_whenRateLimitDisabled() throws Exception {
        config.setEnabled(false);
        ActorContext.set(new ActorContext(ActorType.FATHER, UUID.randomUUID()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThrough_whenNoActorContext() throws Exception {
        // No actor context set — anonymous or pre-authentication
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowRequests_withinLimit() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        // Father limit is 60/min by default — send 5 requests (well within limit)
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    void shouldThrowRateLimitExceeded_whenLimitExceeded() throws Exception {
        // Configure a very low limit for testing
        config.getLimits().put(ActorType.FATHER, 3);

        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        // Send 3 requests (at the limit)
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // 4th request should throw
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                    assertThat(rle.getRetryAfterSeconds()).isLessThanOrEqualTo(60);
                });
    }

    @Test
    void shouldEnforceLimitsPerActor_notShared() throws Exception {
        config.getLimits().put(ActorType.FATHER, 2);

        UUID actor1 = UUID.randomUUID();
        UUID actor2 = UUID.randomUUID();

        // Actor 1 uses up their limit
        ActorContext.set(new ActorContext(ActorType.FATHER, actor1));
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // Actor 2 should still be allowed
        ActorContext.clear();
        ActorContext.set(new ActorContext(ActorType.FATHER, actor2));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Actor 2 request should succeed (3 total filter chain calls: 2 for actor1, 1 for actor2)
        verify(filterChain, times(3)).doFilter(any(), any());
    }

    @Test
    void shouldApplyDifferentLimitsPerActorType() throws Exception {
        config.getLimits().put(ActorType.FATHER, 2);
        config.getLimits().put(ActorType.ADMIN, 5);

        UUID fatherId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        // Father hits limit at 2
        ActorContext.set(new ActorContext(ActorType.FATHER, fatherId));
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // Father's 3rd request should be rejected
        MockHttpServletRequest fatherReq = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse fatherResp = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilterInternal(fatherReq, fatherResp, filterChain))
                .isInstanceOf(RateLimitExceededException.class);

        // Admin should still have capacity (different limit: 5)
        ActorContext.clear();
        ActorContext.set(new ActorContext(ActorType.ADMIN, adminId));
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/fathers");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // Admin's 6th should be rejected
        MockHttpServletRequest adminReq = new MockHttpServletRequest("GET", "/api/v1/admin/fathers");
        MockHttpServletResponse adminResp = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilterInternal(adminReq, adminResp, filterChain))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldReturnPositiveRetryAfterSeconds() throws Exception {
        config.getLimits().put(ActorType.FATHER, 1);
        config.setWindowSeconds(30);

        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        // First request succeeds
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilterInternal(request1, response1, filterChain);

        // Second request should fail with retry-after
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/fathers/me");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request2, response2, filterChain))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    // Retry-after should be between 1 and the window duration
                    assertThat(rle.getRetryAfterSeconds()).isBetween(1L, 30L);
                });
    }

    @Test
    void shouldApplyToAllHttpMethods() throws Exception {
        config.getLimits().put(ActorType.FATHER, 3);

        UUID actorId = UUID.randomUUID();
        ActorContext.set(new ActorContext(ActorType.FATHER, actorId));

        // Mix of methods should all count toward the limit
        String[] methods = {"GET", "POST", "PUT"};
        for (String method : methods) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/fathers/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // 4th request (any method) should be rejected
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/fathers/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
