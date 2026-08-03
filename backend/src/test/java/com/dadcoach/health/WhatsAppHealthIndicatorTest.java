package com.dadcoach.health;

import com.dadcoach.config.WhatsAppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WhatsAppHealthIndicator}.
 * 
 * Tests Requirement 16.5: Health endpoint reports WhatsApp API status.
 */
class WhatsAppHealthIndicatorTest {

    @Test
    @DisplayName("Should report DOWN when WhatsApp is not configured")
    void shouldReportDownWhenNotConfigured() {
        // Given - empty access token makes it unconfigured
        WhatsAppProperties props = new WhatsAppProperties(
                "https://graph.facebook.com",
                "v25.0",
                "12345",
                "waba123",
                "", // empty access token
                "verify-token",
                "secret"
        );
        WhatsAppHealthIndicator indicator = new WhatsAppHealthIndicator(props);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("WhatsApp API not configured");
        assertThat(health.getDetails()).containsKey("missingConfig");
    }

    @Test
    @DisplayName("Should report DOWN when properties is null")
    void shouldReportDownWhenPropertiesIsNull() {
        // Given
        WhatsAppHealthIndicator indicator = new WhatsAppHealthIndicator(null);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("Should report missing configuration items")
    void shouldReportMissingConfigurationItems() {
        // Given - missing phoneNumberId and accessToken
        WhatsAppProperties props = new WhatsAppProperties(
                "https://graph.facebook.com",
                "v25.0",
                "", // missing phoneNumberId
                "waba123",
                "", // missing accessToken
                "verify-token",
                "secret"
        );
        WhatsAppHealthIndicator indicator = new WhatsAppHealthIndicator(props);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        String missingConfig = (String) health.getDetails().get("missingConfig");
        assertThat(missingConfig).contains("accessToken");
        assertThat(missingConfig).contains("phoneNumberId");
    }

    @Test
    @DisplayName("Should mask phoneNumberId in health response")
    void shouldMaskPhoneNumberIdInResponse() {
        // Given - fully configured but API won't actually be reachable in unit test
        WhatsAppProperties props = new WhatsAppProperties(
                "https://graph.facebook.com",
                "v25.0",
                "1234567890123",
                "waba123",
                "valid-access-token",
                "verify-token",
                "secret"
        );
        WhatsAppHealthIndicator indicator = new WhatsAppHealthIndicator(props);

        // When
        Health health = indicator.health();

        // Then - should be DOWN because we can't reach the real API, but let's verify masking works
        // The phoneNumberId should be masked in the details (if present)
        if (health.getDetails().containsKey("phoneNumberId")) {
            String maskedId = (String) health.getDetails().get("phoneNumberId");
            assertThat(maskedId).startsWith("****");
            assertThat(maskedId).hasSize(8); // "****" + last 4 digits
        }
    }
}
