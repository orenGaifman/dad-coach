package com.dadcoach.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Fail closed: an unconfigured key opens nothing - not even an empty or missing header. */
class ApiKeyAuthenticationFilterTest {

    @Test
    void aBlankConfiguredKeyMatchesNothing() {
        assertThat(ApiKeyAuthenticationFilter.matches("", "")).isFalse();
        assertThat(ApiKeyAuthenticationFilter.matches(null, "")).isFalse();
        assertThat(ApiKeyAuthenticationFilter.matches("anything", " ")).isFalse();
        assertThat(ApiKeyAuthenticationFilter.matches(null, null)).isFalse();
    }

    @Test
    void onlyTheExactKeyMatches() {
        assertThat(ApiKeyAuthenticationFilter.matches("k-0123456789012345678901", "k-0123456789012345678901")).isTrue();
        assertThat(ApiKeyAuthenticationFilter.matches("k-0123456789012345678902", "k-0123456789012345678901")).isFalse();
        assertThat(ApiKeyAuthenticationFilter.matches(null, "k-0123456789012345678901")).isFalse();
    }
}
