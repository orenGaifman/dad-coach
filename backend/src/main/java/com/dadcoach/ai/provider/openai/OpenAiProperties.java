package com.dadcoach.ai.provider.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the OpenAI provider.
 */
@ConfigurationProperties(prefix = "dad-coach.ai.openai")
public record OpenAiProperties(
    String apiKey,
    String baseUrl,
    int timeoutSeconds
) {

    public OpenAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
    }
}
