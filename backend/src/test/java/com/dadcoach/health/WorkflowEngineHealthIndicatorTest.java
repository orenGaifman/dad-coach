package com.dadcoach.health;

import com.dadcoach.workflow.WorkflowEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link WorkflowEngineHealthIndicator}.
 * 
 * Tests Requirement 16.5: Health endpoint reports Workflow Engine status.
 */
class WorkflowEngineHealthIndicatorTest {

    @Test
    @DisplayName("Should report UP when WorkflowEngine is available")
    void shouldReportUpWhenWorkflowEngineIsAvailable() {
        // Given
        WorkflowEngine mockEngine = mock(WorkflowEngine.class);
        WorkflowEngineHealthIndicator indicator = new WorkflowEngineHealthIndicator(mockEngine);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("component");
        assertThat(health.getDetails().get("component")).isEqualTo("WorkflowEngine");
    }

    @Test
    @DisplayName("Should report DOWN when WorkflowEngine is null")
    void shouldReportDownWhenWorkflowEngineIsNull() {
        // Given
        WorkflowEngineHealthIndicator indicator = new WorkflowEngineHealthIndicator(null);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("WorkflowEngine bean not available");
    }
}
