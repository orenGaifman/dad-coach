package com.dadcoach.api.ratelimit;

import com.dadcoach.api.auth.ActorType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    @Test
    void shouldHaveCorrectDefaults() {
        RateLimitConfig config = new RateLimitConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getWindowSeconds()).isEqualTo(60);
        assertThat(config.getLimitForActorType(ActorType.FATHER)).isEqualTo(60);
        assertThat(config.getLimitForActorType(ActorType.ADMIN)).isEqualTo(300);
        assertThat(config.getLimitForActorType(ActorType.SERVICE)).isEqualTo(1000);
    }

    @Test
    void shouldAllowCustomLimits() {
        RateLimitConfig config = new RateLimitConfig();
        config.setLimits(new EnumMap<>(Map.of(
                ActorType.FATHER, 100,
                ActorType.ADMIN, 500,
                ActorType.SERVICE, 2000
        )));

        assertThat(config.getLimitForActorType(ActorType.FATHER)).isEqualTo(100);
        assertThat(config.getLimitForActorType(ActorType.ADMIN)).isEqualTo(500);
        assertThat(config.getLimitForActorType(ActorType.SERVICE)).isEqualTo(2000);
    }

    @Test
    void shouldFallbackToDefaultLimitForUnknownActorType() {
        RateLimitConfig config = new RateLimitConfig();
        // Clear the limits map and only set one type
        config.setLimits(new EnumMap<>(Map.of(ActorType.FATHER, 60)));

        // ADMIN and SERVICE should fall back to 60 default
        assertThat(config.getLimitForActorType(ActorType.ADMIN)).isEqualTo(60);
        assertThat(config.getLimitForActorType(ActorType.SERVICE)).isEqualTo(60);
    }

    @Test
    void shouldAllowConfigurationChanges() {
        RateLimitConfig config = new RateLimitConfig();

        config.setEnabled(false);
        assertThat(config.isEnabled()).isFalse();

        config.setWindowSeconds(120);
        assertThat(config.getWindowSeconds()).isEqualTo(120);
    }
}
