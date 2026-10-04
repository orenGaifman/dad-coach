package com.dadcoach.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** The admin API is closed without the admin key - also when no key is configured; a JWT never opens it. */
class AdminApiKeyAuthFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("no key, a wrong key, or no key configured: 401 and the request never reaches the controller")
    void refused() throws Exception {
        assertThat(run(new AdminApiKeyAuthFilter("admin-secret-key"), "DELETE", "/api/v1/admin/fathers/x", null).getStatus()).isEqualTo(401);
        assertThat(run(new AdminApiKeyAuthFilter("admin-secret-key"), "GET", "/api/v1/admin/fathers", "wrong").getStatus()).isEqualTo(401);
        assertThat(run(new AdminApiKeyAuthFilter(""), "GET", "/api/v1/admin/fathers", "").getStatus()).isEqualTo(401);
        assertThat(run(new AdminApiKeyAuthFilter(null), "GET", "/api/v1/admin/memories", "anything").getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("the right key grants ROLE_ADMIN_API; other paths are not touched")
    void granted() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/admin/fathers/x");
        request.addHeader("X-API-Key", "admin-secret-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AdminApiKeyAuthFilter("admin-secret-key").doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN_API");

        MockFilterChain other = new MockFilterChain();
        new AdminApiKeyAuthFilter("admin-secret-key").doFilter(new MockHttpServletRequest("GET", "/api/v1/fathers/me"),
                new MockHttpServletResponse(), other);
        assertThat(other.getRequest()).as("not an admin path - left to the other filters").isNotNull();
    }

    @Test
    @DisplayName("production refuses to start with the public development JWT secret or a short one")
    void jwtSecretGuard() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtSecretGuard("default-dev-secret-change-in-production-must-be-at-least-256-bits-long!!", prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtSecretGuard("short", prod)).isInstanceOf(IllegalStateException.class);
        new JwtSecretGuard("a-private-secret-of-more-than-thirty-two-characters", prod);
        new JwtSecretGuard("default-dev-secret-change-in-production-must-be-at-least-256-bits-long!!", new MockEnvironment());
    }

    private static MockHttpServletResponse run(AdminApiKeyAuthFilter filter, String method, String path, String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (key != null) {
            request.addHeader("X-API-Key", key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).as("never reaches the controller").isNull();
        return response;
    }
}
