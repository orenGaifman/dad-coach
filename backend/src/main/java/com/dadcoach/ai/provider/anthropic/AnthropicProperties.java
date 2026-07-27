package com.dadcoach.ai.provider.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Anthropic provider.
 */
@ConfigurationProperties(prefix = "dad-coach.ai.anthropic")
public record AnthropicProperties(
    String apiKey,
    String baseUrl,
    int timeoutSeconds,
    String apiVersion
) {

    public AnthropicProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "2023-06-01";
        }
    }
}
