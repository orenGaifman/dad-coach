package com.dadcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthUnitsTest {

    @Test
    @DisplayName("phones: Israeli local forms become +972, +-numbers stay, garbage is empty")
    void phones() {
        assertThat(PhoneInput.normalize("050-123-4567")).contains("+972501234567");
        assertThat(PhoneInput.normalize("0501234567")).contains("+972501234567");
        assertThat(PhoneInput.normalize("972501234567")).contains("+972501234567");
        assertThat(PhoneInput.normalize("501234567")).contains("+972501234567");
        assertThat(PhoneInput.normalize("+1 999 000 0001")).contains("+19990000001");
        assertThat(PhoneInput.normalize("00972501234567")).contains("+972501234567");
        assertThat(PhoneInput.normalize("hello")).isEmpty();
        assertThat(PhoneInput.normalize("123")).isEmpty();
        assertThat(PhoneInput.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("a link's next path: dashboard paths only - no open redirect")
    void nextPaths() {
        assertThat(DashboardPaths.sanitize("/sessions")).contains("/sessions");
        assertThat(DashboardPaths.sanitize("/admin/fathers/12")).contains("/admin/fathers/12");
        assertThat(DashboardPaths.sanitize("//evil.example")).isEmpty();
        assertThat(DashboardPaths.sanitize("https://evil.example")).isEmpty();
        assertThat(DashboardPaths.sanitize("/home?x=1")).isEmpty();
        assertThat(DashboardPaths.sanitize(null)).isEmpty();
    }

    @Test
    @DisplayName("service keys: constant-time match; an unset key matches nothing")
    void keys() {
        assertThat(TokenHashing.keyMatches("abc", "abc")).isTrue();
        assertThat(TokenHashing.keyMatches("abd", "abc")).isFalse();
        assertThat(TokenHashing.keyMatches("", "")).isFalse();
        assertThat(TokenHashing.keyMatches(null, "abc")).isFalse();
        assertThat(TokenHashing.sha256Hex("x")).hasSize(64);
        assertThat(TokenHashing.newRawToken()).isNotEqualTo(TokenHashing.newRawToken());
    }

    @Test
    @DisplayName("rate limits: 5 per person and 20 per client in 15 minutes")
    void rateLimits() {
        LoginLinkRateLimiter limiter = new LoginLinkRateLimiter(Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC));
        SignInSubject s = new SignInSubject(1L, null);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.trySubject(s)).isTrue();
        }
        assertThat(limiter.trySubject(s)).isFalse();
        assertThat(limiter.trySubject(new SignInSubject(2L, null))).isTrue();
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryClient("1.2.3.4")).isTrue();
        }
        assertThat(limiter.tryClient("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("a deployed instance refuses an insecure cookie or a short ops key; an unset ops key is allowed (closed)")
    void startupGuard() {
        DashboardProperties p = new DashboardProperties();
        assertThat(DashboardStartupGuard.problems(p)).isEmpty();
        p.setOpsApiKey("short");
        p.setCookieSecure(false);
        assertThat(DashboardStartupGuard.problems(p)).hasSize(2);
        p.setOpsApiKey("x".repeat(32));
        p.setCookieSecure(true);
        assertThat(DashboardStartupGuard.problems(p)).isEmpty();
    }

    @Test
    void subjectNeedsSomeone() {
        assertThat(Optional.of(new SignInSubject(null, java.util.UUID.randomUUID()))).isPresent();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SignInSubject(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
