package com.dadcoach.integration.platform;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The AI Workflow Platform connection ({@code workflow.platform.*}, env WORKFLOW_PLATFORM_*). Dad Coach is
 * platform-only (D-001): every conversation turn runs worker {@link #workerKey} / workflow {@link #workflowKey}
 * (the caller selects; the platform never chooses a workflow) in the product's single tenant {@link #tenantId}.
 */
@Component
@ConfigurationProperties(prefix = "workflow.platform")
public class WorkflowPlatformProperties {

    private boolean enabled = false;
    private String baseUrl = "";
    private String apiKey = "";
    private String workerKey = "dad_3";
    private String workflowKey = "dad-coach-3";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 90000;
    /** Dad Coach's one tenant on the platform (multi-tenancy: a product with a single, fixed tenant). */
    private UUID tenantId = UUID.fromString("20082bcd-a7bf-57a8-a382-4bad32144b2f");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getWorkerKey() { return workerKey; }
    public void setWorkerKey(String workerKey) { this.workerKey = workerKey; }
    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
}
