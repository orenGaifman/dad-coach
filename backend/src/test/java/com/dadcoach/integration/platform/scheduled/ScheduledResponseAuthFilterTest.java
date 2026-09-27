package com.dadcoach.integration.platform.scheduled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledResponseAuthFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static ScheduledResponseCallbackConfig config(boolean enabled, String key) {
        ScheduledResponseCallbackConfig config = new ScheduledResponseCallbackConfig();
        config.setEnabled(enabled);
        config.setApiKey(key);
        return config;
    }

    private static MockHttpServletRequest callback(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ScheduledResponseController.PATH);
        if (apiKey != null) {
            request.addHeader("X-API-Key", apiKey);
        }
        return request;
    }

    @Test
    void validKeyAuthenticatesAndContinues() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ScheduledResponseAuthFilter(config(true, "secret")).doFilter(callback("secret"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void wrongOrMissingKeyIs401() throws Exception {
        for (String key : new String[] {"wrong", null}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            new ScheduledResponseAuthFilter(config(true, "secret")).doFilter(callback(key), response, chain);

            assertThat(chain.getRequest()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void disabledReceiverIs503EvenWithTheRightKey() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ScheduledResponseAuthFilter(config(false, "secret")).doFilter(callback("secret"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void otherPathsAreNotTouched() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ScheduledResponseAuthFilter(config(false, null))
                .doFilter(new MockHttpServletRequest("POST", "/api/tools/schedule_quality_time"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
