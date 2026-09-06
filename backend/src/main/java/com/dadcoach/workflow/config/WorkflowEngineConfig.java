package com.dadcoach.workflow.config;

import com.dadcoach.integration.platform.PlatformWorkflowClient;
import com.dadcoach.integration.platform.PlatformWorkflowConfig;
import com.dadcoach.workflow.PlatformWorkflowEngine;
import com.dadcoach.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for selecting the WorkflowEngine implementation.
 *
 * <p>This configuration provides conditional bean creation based on the
 * {@code workflow.platform.enabled} property:</p>
 * <ul>
 *   <li>When {@code true}: Uses {@link PlatformWorkflowEngine} which delegates
 *       to the ai-workflow-platform API</li>
 *   <li>When {@code false} (default): Uses the existing {@code WorkflowEngineImpl}
 *       which is auto-registered via {@code @Service}</li>
 * </ul>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * workflow:
 *   platform:
 *     enabled: true  # Set to true to use platform, false for local engine
 *     base-url: http://workflow-platform:8081
 *     api-key: ${WORKFLOW_PLATFORM_API_KEY}
 *     workflow-id: ${WORKFLOW_PLATFORM_WORKFLOW_ID}
 * </pre>
 *
 * @see PlatformWorkflowEngine
 * @see com.dadcoach.workflow.WorkflowEngineImpl
 */
@Configuration
public class WorkflowEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineConfig.class);

    /**
     * Creates a PlatformWorkflowEngine bean when platform integration is enabled.
     *
     * <p>This bean is marked as {@code @Primary} to take precedence over the
     * auto-registered {@code WorkflowEngineImpl} when both are present.</p>
     *
     * @param platformClient the platform HTTP client
     * @param config the platform configuration
     * @return a PlatformWorkflowEngine instance
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "workflow.platform.enabled", havingValue = "true")
    public WorkflowEngine platformWorkflowEngine(
            PlatformWorkflowClient platformClient,
            PlatformWorkflowConfig config) {
        
        log.info("Platform workflow integration ENABLED - using PlatformWorkflowEngine");
        log.info("Platform configuration: baseUrl={}, workflowId={}", 
                config.getBaseUrl(), config.getWorkflowId());
        
        return new PlatformWorkflowEngine(platformClient, config);
    }
}
