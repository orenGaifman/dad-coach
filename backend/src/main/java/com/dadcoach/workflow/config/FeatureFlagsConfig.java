package com.dadcoach.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags configuration for the deterministic workflow engine.
 * 
 * <p>These flags allow gradual rollout and easy rollback of the new workflow engine
 * architecture. Feature flags are defined per requirement 15.6 for backwards compatibility.
 * 
 * <p>Example application.yml:
 * <pre>
 * dadcoach:
 *   features:
 *     deterministic-workflow-engine: true  # Master switch for new workflow engine
 *     ai-message-generation: true          # Toggle AI vs fallback messages
 *     morning-reminders: true              # Toggle morning reminder scheduler job
 * </pre>
 * 
 * <p>Usage:
 * <ul>
 *   <li>{@code deterministicWorkflowEngine} — Master switch that enables the new WorkflowEngine.
 *       When disabled, the system falls back to the previous ConversationOrchestrator.</li>
 *   <li>{@code aiMessageGeneration} — When enabled, uses AI to generate natural language messages.
 *       When disabled, uses fallback templates for all messages.</li>
 *   <li>{@code morningReminders} — When enabled, the morning reminder scheduler job runs.
 *       When disabled, no morning reminders are sent for Quality Time events.</li>
 * </ul>
 * 
 * @see com.dadcoach.workflow.WorkflowEngine
 * @see com.dadcoach.workflow.message.MessageGenerator
 * @see com.dadcoach.workflow.scheduler.WorkflowScheduler
 */
@Configuration
@ConfigurationProperties(prefix = "dadcoach.features")
public class FeatureFlagsConfig {

    /**
     * Master switch for the deterministic workflow engine.
     * When enabled (default: true), the new WorkflowEngine handles all message processing.
     * When disabled, falls back to the previous ConversationOrchestrator architecture.
     * 
     * <p>This allows for safe rollback if critical issues are discovered after deployment.
     */
    private boolean deterministicWorkflowEngine = true;

    /**
     * Toggle for AI-powered message generation.
     * When enabled (default: true), the MessageGenerator uses AI to create personalized messages.
     * When disabled, all messages use pre-written fallback templates.
     * 
     * <p>Useful for:
     * <ul>
     *   <li>Reducing AI costs during high traffic periods</li>
     *   <li>Ensuring deterministic behavior for testing</li>
     *   <li>Fallback when AI provider is unavailable</li>
     * </ul>
     */
    private boolean aiMessageGeneration = true;

    /**
     * Toggle for morning reminder scheduler job.
     * When enabled (default: true), fathers receive WhatsApp reminders on the day of scheduled Quality Time.
     * When disabled, no morning reminders are sent.
     * 
     * <p>Useful for:
     * <ul>
     *   <li>Temporarily disabling reminders during maintenance</li>
     *   <li>Testing scheduler behavior in isolation</li>
     *   <li>Compliance with notification preferences</li>
     * </ul>
     */
    private boolean morningReminders = true;

    public boolean isDeterministicWorkflowEngine() {
        return deterministicWorkflowEngine;
    }

    public void setDeterministicWorkflowEngine(boolean deterministicWorkflowEngine) {
        this.deterministicWorkflowEngine = deterministicWorkflowEngine;
    }

    public boolean isAiMessageGeneration() {
        return aiMessageGeneration;
    }

    public void setAiMessageGeneration(boolean aiMessageGeneration) {
        this.aiMessageGeneration = aiMessageGeneration;
    }

    public boolean isMorningReminders() {
        return morningReminders;
    }

    public void setMorningReminders(boolean morningReminders) {
        this.morningReminders = morningReminders;
    }

    @Override
    public String toString() {
        return "FeatureFlagsConfig{" +
                "deterministicWorkflowEngine=" + deterministicWorkflowEngine +
                ", aiMessageGeneration=" + aiMessageGeneration +
                ", morningReminders=" + morningReminders +
                '}';
    }
}
