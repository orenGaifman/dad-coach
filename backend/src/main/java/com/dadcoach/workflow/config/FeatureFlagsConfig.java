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
 * </pre>
 * 
 * @see com.dadcoach.workflow.message.MessageGenerator
 * @see com.dadcoach.workflow.scheduler.WorkflowScheduler
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
                "aiMessageGeneration=" + aiMessageGeneration +
                ", morningReminders=" + morningReminders +
                '}';
    }
}
