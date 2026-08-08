package com.dadcoach.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags configuration for the workflow engine.
 * 
 * <p>Example application.yml:
 * <pre>
 * dadcoach:
 *   features:
 *     ai-message-generation: true          # Toggle AI vs fallback messages
 *     morning-reminders: true              # Toggle morning reminder scheduler job
 *     ai-agent-enabled: true               # Toggle AI Agent vs pattern-based routing
 * </pre>
 * 
 * @see com.dadcoach.workflow.message.MessageGenerator
 * @see com.dadcoach.workflow.scheduler.WorkflowScheduler
 * @see com.dadcoach.ai.agent.CoachingAgent
 */
@Configuration
@ConfigurationProperties(prefix = "dadcoach.features")
public class FeatureFlagsConfig {

    /**
     * Toggle for AI-powered message generation.
     * When enabled (default: true), the MessageGenerator uses AI to create personalized messages.
     * When disabled, all messages use pre-written fallback templates.
     */
    private boolean aiMessageGeneration = true;

    /**
     * Toggle for morning reminder scheduler job.
     * When enabled (default: true), fathers receive WhatsApp reminders on the day of scheduled Quality Time.
     * When disabled, no morning reminders are sent.
     */
    private boolean morningReminders = true;
    
    /**
     * Toggle for AI Agent-based message routing.
     * When enabled, the CoachingAgent uses Claude to understand user intent and select tools.
     * When disabled (default: false), the traditional pattern matching approach is used.
     * 
     * <p>When enabled, this bypasses the regex-based state machine in favor of
     * natural language understanding via the AI Agent with Tools architecture.</p>
     */
    private boolean aiAgentEnabled = false;

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
    
    public boolean isAiAgentEnabled() {
        return aiAgentEnabled;
    }
    
    public void setAiAgentEnabled(boolean aiAgentEnabled) {
        this.aiAgentEnabled = aiAgentEnabled;
    }

    @Override
    public String toString() {
        return "FeatureFlagsConfig{" +
                "aiMessageGeneration=" + aiMessageGeneration +
                ", morningReminders=" + morningReminders +
                ", aiAgentEnabled=" + aiAgentEnabled +
                '}';
    }
}
