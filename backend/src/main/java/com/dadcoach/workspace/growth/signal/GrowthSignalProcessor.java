package com.dadcoach.workspace.growth.signal;

import com.dadcoach.workspace.event.ConversationCompletedEvent;
import com.dadcoach.workspace.event.GoalCompletedEvent;
import com.dadcoach.workspace.event.GoalProgressEvent;
import com.dadcoach.workspace.event.MissionCompletedEvent;
import com.dadcoach.workspace.event.MissionReflectedEvent;
import com.dadcoach.workspace.event.PositiveActivityReportedEvent;
import com.dadcoach.workspace.event.QualityTimeReportedEvent;

import java.util.UUID;

/**
 * Interface for the growth signal processor that converts domain events into growth signals.
 *
 * <p>The Growth Signal Processor orchestrates the signal recording pipeline:
 * duplicate check → record signal → update cached score → publish GrowthSignalRecordedEvent.
 * It serves as the entry point for event-driven growth signal processing
 * (Design Decision AD-2, Requirement 23.1).</p>
 *
 * <p>Each handler method applies domain-specific filtering before recording a signal:</p>
 * <ul>
 *   <li>{@code onGoalProgress} — only records if progress increase ≥ 10%</li>
 *   <li>{@code onConversationCompleted} — only records if quality &gt; 0.6 and exchanges &gt; 5</li>
 * </ul>
 *
 * <p>The {@link com.dadcoach.workspace.event.DomainEventListener} subscribes to external
 * domain events and delegates processing to the implementation of this interface.</p>
 *
 * @see GrowthSignalProcessorImpl
 * @see GrowthSignalService
 * @see com.dadcoach.workspace.event.DomainEventListener
 */
public interface GrowthSignalProcessor {

    /**
     * Processes a mission completion event.
     * Records a MISSION_COMPLETED signal for the father.
     *
     * @param event the mission completed event
     */
    void onMissionCompleted(MissionCompletedEvent event);

    /**
     * Processes a mission reflection event.
     * Records a MISSION_REFLECTED signal (bonus on top of completion).
     *
     * @param event the mission reflected event
     */
    void onMissionReflected(MissionReflectedEvent event);

    /**
     * Processes a goal progress event.
     * Only records a GOAL_PROGRESS signal if the progress increase is at least 10%.
     *
     * @param event the goal progress event
     */
    void onGoalProgress(GoalProgressEvent event);

    /**
     * Processes a goal completion event.
     * Records a GOAL_COMPLETED signal for the father.
     *
     * @param event the goal completed event
     */
    void onGoalCompleted(GoalCompletedEvent event);

    /**
     * Processes a conversation completion event.
     * Only records a MEANINGFUL_CONVERSATION signal if the conversation has more than
     * 5 exchanges and a quality rating above 0.6.
     *
     * @param event the conversation completed event
     */
    void onConversationCompleted(ConversationCompletedEvent event);

    /**
     * Processes a quality time reported event.
     * Records a QUALITY_TIME_REPORTED signal.
     *
     * @param event the quality time reported event
     */
    void onQualityTimeReported(QualityTimeReportedEvent event);

    /**
     * Processes a positive activity reported event.
     * Records a POSITIVE_ACTIVITY signal.
     *
     * @param event the positive activity reported event
     */
    void onPositiveActivityReported(PositiveActivityReportedEvent event);

    /**
     * Replays all historical events for a father to recalculate their signals.
     * Used for administrative recalculation (Requirement 23.6).
     *
     * @param fatherId the father to replay signals for
     */
    void replaySignalsForFather(UUID fatherId);
}
