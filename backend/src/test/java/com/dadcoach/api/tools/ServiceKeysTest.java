package com.dadcoach.api.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceKeysTest {

    @Test
    void matchesOnlyTheConfiguredNonBlankKey() {
        assertThat(ServiceKeys.matches("k-123", "k-123")).isTrue();
        assertThat(ServiceKeys.matches("k-124", "k-123")).isFalse();
        assertThat(ServiceKeys.matches(null, "k-123")).isFalse();
        assertThat(ServiceKeys.matches("", "")).as("an unconfigured key never matches").isFalse();
        assertThat(ServiceKeys.matches("x", null)).isFalse();
    }
}
