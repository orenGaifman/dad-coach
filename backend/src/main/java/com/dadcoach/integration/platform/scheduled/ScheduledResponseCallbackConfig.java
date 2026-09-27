package com.dadcoach.integration.platform.scheduled;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Receiver side of the Workflow Platform's scheduled-response callback
 * ({@code workflow.platform.scheduled-response.*}).
 *
 * <p>{@code enabled=false} (default) rejects every callback with 503, so nothing is delivered until
 * the platform has been configured to route this worker's scheduled responses here. The API key
 * is deliberately separate from {@code tool-api.api-key} (the opposite direction).</p>
 */
@Configuration
@ConfigurationProperties(prefix = "workflow.platform.scheduled-response")
public class ScheduledResponseCallbackConfig {

    private boolean enabled = false;
    private String apiKey;
    /**
     * Approved WhatsApp template used when the father's 24h window is closed; its single body
     * parameter {{1}} carries the generated message. Name suffix _he/_en selects the language.
     * Blank = never send outside the window (the delivery is recorded as FAILED instead).
     */
    private String templateName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }
}
