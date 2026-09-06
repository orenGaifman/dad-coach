package com.dadcoach.workflow.config;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.integration.platform.PlatformWorkflowClient;
import com.dadcoach.integration.platform.PlatformWorkflowConfig;
import com.dadcoach.workflow.PlatformWorkflowEngine;
import com.dadcoach.workflow.WorkflowEngine;
import com.dadcoach.workflow.WorkflowTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;
import java.util.UUID;

/**
 * Configuration for selecting the WorkflowEngine implementation.
 *
 * <p>This configuration provides conditional bean creation based on the
 * {@code workflow.platform.enabled} property:</p>
 * <ul>
 *   <li>When {@code true}: Uses {@link PlatformWorkflowEngine} which delegates
 *       to the ai-workflow-platform API</li>
 *   <li>When {@code false} (default): Uses a stub implementation that logs
 *       warnings for scheduler triggers (the old WorkflowEngineImpl was deleted
 *       as part of the platform migration)</li>
 * </ul>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * workflow:
 *   platform:
 *     enabled: true  # Set to true to use platform, false for stub
 *     base-url: http://workflow-platform:8081
 *     api-key: ${WORKFLOW_PLATFORM_API_KEY}
 *     workflow-id: ${WORKFLOW_PLATFORM_WORKFLOW_ID}
 * </pre>
 *
 * @see PlatformWorkflowEngine
 */
@Configuration
public class WorkflowEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineConfig.class);

    /**
     * Creates a PlatformWorkflowEngine bean when platform integration is enabled.
     *
     * <p>This bean is marked as {@code @Primary} to take precedence over other
     * implementations when both are present.</p>
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

    /**
     * Creates a stub WorkflowEngine bean when platform integration is disabled.
     *
     * <p>This stub is used during the transition period after WorkflowEngineImpl
     * was deleted but before the platform is fully enabled. It logs warnings
     * for scheduler triggers and returns empty responses.</p>
     *
     * <p>Note: This stub should be removed once the platform is fully operational
     * and {@code workflow.platform.enabled=true} is the default.</p>
     *
     * @return a stub WorkflowEngine instance
     */
    @Bean
    @ConditionalOnProperty(name = "workflow.platform.enabled", havingValue = "false", matchIfMissing = true)
    public WorkflowEngine stubWorkflowEngine() {
        log.warn("Platform workflow integration DISABLED - using stub WorkflowEngine");
        log.warn("Scheduler-triggered transitions will be logged but not processed");
        log.warn("To enable full workflow processing, set workflow.platform.enabled=true");
        
        return new WorkflowEngine() {
            @Override
            public OutboundMessageDto processMessage(InboundMessageDto message) {
                log.warn("StubWorkflowEngine.processMessage called - platform integration is disabled. messageId={}",
                        message.messageId());
                throw new UnsupportedOperationException(
                        "Workflow processing unavailable: platform integration is disabled. " +
                        "Set workflow.platform.enabled=true to enable.");
            }

            @Override
            public Optional<OutboundMessageDto> triggerTransition(UUID fatherId, WorkflowTrigger trigger) {
                log.warn("StubWorkflowEngine.triggerTransition called - platform integration is disabled. " +
                        "fatherId={}, trigger={}", fatherId, trigger);
                // Return empty to allow scheduler jobs to complete without sending messages
                return Optional.empty();
            }
        };
    }
}
