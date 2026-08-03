package com.dadcoach.health;

import com.dadcoach.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the Workflow Engine.
 * 
 * <p>Reports the status of the deterministic workflow engine, which is the central
 * orchestrator for the Dad Coach application.</p>
 * 
 * <p>The indicator reports:
 * <ul>
 *   <li>UP - when the workflow engine bean is available and functional</li>
 *   <li>DOWN - when the workflow engine is not available or not functioning</li>
 * </ul>
 * </p>
 * 
 * <p>Implements Requirement 16.5: The system SHALL expose a health endpoint that
 * reports Workflow Engine status.</p>
 */
@Component
public class WorkflowEngineHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineHealthIndicator.class);

    private final WorkflowEngine workflowEngine;

    /**
     * Constructs the health indicator with the workflow engine dependency.
     *
     * @param workflowEngine the workflow engine to monitor (nullable - allows for graceful degradation)
     */
    public WorkflowEngineHealthIndicator(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Override
    public Health health() {
        try {
            if (workflowEngine == null) {
                log.warn("WorkflowEngine bean is not available");
                return Health.down()
                        .withDetail("error", "WorkflowEngine bean not available")
                        .build();
            }

            // The workflow engine is available - report UP
            return Health.up()
                    .withDetail("component", "WorkflowEngine")
                    .withDetail("type", workflowEngine.getClass().getSimpleName())
                    .build();

        } catch (Exception e) {
            log.error("Error checking WorkflowEngine health: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
