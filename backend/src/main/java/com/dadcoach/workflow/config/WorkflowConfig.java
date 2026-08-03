package com.dadcoach.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the deterministic workflow engine.
 *
 * <p>These settings control the behavior of the workflow engine including:</p>
 * <ul>
 *   <li>Exchange limits per state to prevent infinite conversation loops</li>
 *   <li>Calendar lookahead period for slot suggestions</li>
 *   <li>Minimum duration for Quality Time slots</li>
 *   <li>AI message generation timeout</li>
 * </ul>
 *
 * <p>Configuration can be set via application.yml under the prefix {@code dadcoach.workflow}:</p>
 * <pre>
 * dadcoach:
 *   workflow:
 *     max-exchanges-per-state: 5
 *     calendar-lookahead-days: 7
 *     min-slot-duration-minutes: 30
 *     message-generator-timeout-ms: 5000
 * </pre>
 *
 * <p>Implements Requirements 5.6, 7.5, 10.6 from the deterministic-workflow-engine spec.</p>
 *
 * @see com.dadcoach.workflow.WorkflowEngine
 * @see com.dadcoach.workflow.state.ScheduleStateHandler
 * @see com.dadcoach.workflow.message.MessageGenerator
 */
@Configuration
@ConfigurationProperties(prefix = "dadcoach.workflow")
@Validated
public class WorkflowConfig {

    /**
     * Maximum number of message exchanges allowed per workflow state before
     * forcing a summary message or state exit.
     *
     * <p>This prevents infinite conversation loops and ensures the workflow
     * progresses. In SCHEDULE_QUALITY_TIME state, a summary message is sent
     * after this many exchanges (Requirement 5.6).</p>
     *
     * <p>In QUALITY_TIME_FOLLOW_UP state, the conversation must complete
     * within 3 exchanges (Requirement 7.5).</p>
     *
     * <p>Default: 5 exchanges</p>
     */
    @Min(value = 1, message = "maxExchangesPerState must be at least 1")
    @Max(value = 20, message = "maxExchangesPerState must be at most 20")
    private int maxExchangesPerState = 5;

    /**
     * Number of days ahead to look in Google Calendar when suggesting
     * available time slots for Quality Time scheduling.
     *
     * <p>Per Requirement 2.3, the system reads the father's Google Calendar
     * for the next N days to calculate available slots.</p>
     *
     * <p>Default: 7 days</p>
     */
    @Min(value = 1, message = "calendarLookaheadDays must be at least 1")
    @Max(value = 14, message = "calendarLookaheadDays must be at most 14")
    private int calendarLookaheadDays = 7;

    /**
     * Minimum duration in minutes for a Quality Time slot to be suggested.
     *
     * <p>Per Requirement 2.3, available slots must be at least this duration
     * to be considered valid for Quality Time scheduling.</p>
     *
     * <p>Default: 30 minutes</p>
     */
    @Min(value = 15, message = "minSlotDurationMinutes must be at least 15")
    @Max(value = 120, message = "minSlotDurationMinutes must be at most 120")
    private int minSlotDurationMinutes = 30;

    /**
     * Timeout in milliseconds for AI message generation.
     *
     * <p>Per Requirement 10.6, message generation has a latency budget. If AI
     * generation exceeds this timeout, fallback templates are used immediately.</p>
     *
     * <p>Default: 5000ms (5 seconds)</p>
     */
    @Positive(message = "messageGeneratorTimeoutMs must be positive")
    @Min(value = 1000, message = "messageGeneratorTimeoutMs must be at least 1000ms")
    @Max(value = 30000, message = "messageGeneratorTimeoutMs must be at most 30000ms")
    private long messageGeneratorTimeoutMs = 5000L;

    /**
     * Maximum exchanges allowed for follow-up conversations.
     *
     * <p>Per Requirement 7.5, the QUALITY_TIME_FOLLOW_UP state should complete
     * within a limited number of exchanges (typically 3).</p>
     *
     * <p>Default: 3 exchanges</p>
     */
    @Min(value = 1, message = "maxFollowUpExchanges must be at least 1")
    @Max(value = 10, message = "maxFollowUpExchanges must be at most 10")
    private int maxFollowUpExchanges = 3;

    // ========== Getters and Setters ==========

    public int getMaxExchangesPerState() {
        return maxExchangesPerState;
    }

    public void setMaxExchangesPerState(int maxExchangesPerState) {
        this.maxExchangesPerState = maxExchangesPerState;
    }

    public int getCalendarLookaheadDays() {
        return calendarLookaheadDays;
    }

    public void setCalendarLookaheadDays(int calendarLookaheadDays) {
        this.calendarLookaheadDays = calendarLookaheadDays;
    }

    public int getMinSlotDurationMinutes() {
        return minSlotDurationMinutes;
    }

    public void setMinSlotDurationMinutes(int minSlotDurationMinutes) {
        this.minSlotDurationMinutes = minSlotDurationMinutes;
    }

    public long getMessageGeneratorTimeoutMs() {
        return messageGeneratorTimeoutMs;
    }

    public void setMessageGeneratorTimeoutMs(long messageGeneratorTimeoutMs) {
        this.messageGeneratorTimeoutMs = messageGeneratorTimeoutMs;
    }

    public int getMaxFollowUpExchanges() {
        return maxFollowUpExchanges;
    }

    public void setMaxFollowUpExchanges(int maxFollowUpExchanges) {
        this.maxFollowUpExchanges = maxFollowUpExchanges;
    }

    @Override
    public String toString() {
        return "WorkflowConfig{" +
                "maxExchangesPerState=" + maxExchangesPerState +
                ", calendarLookaheadDays=" + calendarLookaheadDays +
                ", minSlotDurationMinutes=" + minSlotDurationMinutes +
                ", messageGeneratorTimeoutMs=" + messageGeneratorTimeoutMs +
                ", maxFollowUpExchanges=" + maxFollowUpExchanges +
                '}';
    }
}
