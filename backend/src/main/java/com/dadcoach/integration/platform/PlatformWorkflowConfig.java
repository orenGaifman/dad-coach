package com.dadcoach.integration.platform;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/**
 * Configuration properties for the ai-workflow-platform integration.
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * workflow:
 *   platform:
 *     enabled: true                                              # Feature flag
 *     base-url: ${WORKFLOW_PLATFORM_BASE_URL:http://localhost:8081}
 *     api-key: ${WORKFLOW_PLATFORM_API_KEY:}
 *     workflow-id: ${WORKFLOW_PLATFORM_WORKFLOW_ID:}             # UUID of dad-coach workflow
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 30000
 * </pre>
 *
 * @see PlatformWorkflowClient
 */
@Configuration
@ConfigurationProperties(prefix = "workflow.platform")
@Validated
public class PlatformWorkflowConfig {

    /**
     * Feature flag to enable/disable platform workflow integration.
     * When false, the existing local WorkflowEngineImpl is used.
     * When true, workflow execution is delegated to ai-workflow-platform.
     */
    private boolean enabled = false;

    /**
     * Base URL of the ai-workflow-platform API.
     * Example: http://localhost:8081 or https://workflow-platform.example.com
     */
    @NotBlank(message = "Platform base URL is required when enabled")
    private String baseUrl = "http://localhost:8081";

    /**
     * API key for authenticating with the platform.
     * This should be stored securely and not committed to version control.
     */
    private String apiKey = "";

    /**
     * The UUID of the dad-coach workflow definition in the platform.
     * This is the workflow that will be executed for all dad-coach messages.
     */
    private UUID workflowId;

    /**
     * Connection timeout in milliseconds for establishing HTTP connection.
     */
    @Min(value = 1000, message = "Connect timeout must be at least 1000ms")
    private int connectTimeoutMs = 5000;

    /**
     * Read timeout in milliseconds for waiting for response.
     * This should be long enough to accommodate AI processing time.
     */
    @Min(value = 5000, message = "Read timeout must be at least 5000ms")
    private int readTimeoutMs = 30000;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public String toString() {
        return "PlatformWorkflowConfig{" +
                "enabled=" + enabled +
                ", baseUrl='" + baseUrl + '\'' +
                ", apiKey='[REDACTED]'" +
                ", workflowId=" + workflowId +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                '}';
    }
}
