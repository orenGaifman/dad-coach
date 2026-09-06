package com.dadcoach.api.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;

/**
 * Configuration properties for the Tool API.
 * 
 * <p>Configured via application.yml under the `tool-api` prefix:</p>
 * <pre>
 * tool-api:
 *   api-key: your-secret-api-key
 *   enabled: true
 * </pre>
 * 
 * @see ToolApiController
 * @see ToolApiAuthFilter
 */
@Configuration
@ConfigurationProperties(prefix = "tool-api")
@Validated
public class ToolApiConfig {

    /**
     * The API key required for authenticating Tool API requests.
     * Should be set via environment variable or AWS Parameter Store.
     */
    @NotEmpty(message = "tool-api.api-key must be configured")
    private String apiKey;

    /**
     * Whether the Tool API is enabled.
     * When false, the API will return 503 Service Unavailable.
     */
    private boolean enabled = true;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
