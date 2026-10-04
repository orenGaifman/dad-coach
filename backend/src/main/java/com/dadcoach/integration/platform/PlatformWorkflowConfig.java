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
 *     worker-key: ${WORKFLOW_PLATFORM_WORKER_KEY:dad_3}          # Worker key (recommended)
 *     workflow-key: ${WORKFLOW_PLATFORM_WORKFLOW_KEY:dad-coach-3} # The worker's workflow to run (workflowKey, required)
 *     workflow-id: ${WORKFLOW_PLATFORM_WORKFLOW_ID:}             # UUID of workflow (legacy)
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 60000
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
     * The worker key for the dad-coach worker in the platform.
     * This is the recommended way to identify the worker (uses /api/v1/worker/execute).
     * When set, this takes precedence over workflowId.
     */
    private String workerKey;

    /**
     * The workflow of that worker every message runs (e.g. {@code dad-coach-3}), sent as workflowKey: the
     * platform executes exactly that workflow and never chooses one, and refuses a request without it.
     */
    private String workflowKey;

    /**
     * The UUID of the dad-coach workflow definition in the platform.
     * This is the legacy way to identify the workflow (uses /api/v1/workflow/execute).
     * @deprecated Use workerKey instead for the new worker-based API.
     */
    @Deprecated
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
    private int readTimeoutMs = 60000;

    /**
     * Dad Coach's one tenant on the platform (a single-tenant product, created by the platform's V99). Named by the
     * person lifecycle API (registering and deleting a father's person).
     */
    private java.util.UUID tenantId = java.util.UUID.fromString("20082bcd-a7bf-57a8-a382-4bad32144b2f");

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

    public String getWorkerKey() {
        return workerKey;
    }

    public String getWorkflowKey() {
        return workflowKey == null || workflowKey.isBlank() ? null : workflowKey.strip();
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }

    public void setWorkerKey(String workerKey) {
        this.workerKey = workerKey;
    }

    /**
     * Returns true if the new worker-based API should be used.
     * Worker key takes precedence over workflowId.
     */
    public boolean isWorkerApiEnabled() {
        return workerKey != null && !workerKey.isBlank();
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public java.util.UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(java.util.UUID tenantId) {
        this.tenantId = tenantId;
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
                ", workerKey='" + workerKey + '\'' +
                ", workflowId=" + workflowId +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                '}';
    }
}
