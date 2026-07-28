package com.dadcoach.workspace.event;

import com.dadcoach.workspace.growth.signal.GrowthSignalProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to external domain events from other specs and delegates processing
 * to the Growth Signal Processor.
 *
 * <p>This class acts as an adapter between external domain events (published by
 * SPEC-002 Mission/Goal domain and SPEC-005 Conversation Engine) and the workspace's
 * internal growth signal processing pipeline. It decouples the Growth System from
 * knowledge of other specs' internal event structures (Requirement 23.3).</p>
 *
 * <p>Each handler method:</p>
 * <ol>
 *   <li>Receives an external domain event via Spring's {@code @EventListener}</li>
 *   <li>Logs the event receipt for observability</li>
 *   <li>Delegates to the appropriate {@link GrowthSignalProcessor} method</li>
 *   <li>Catches and logs any processing errors to prevent event handling failures
 *       from propagating back to the event publisher (Requirement 23.4)</li>
 * </ol>
 *
 * <p>Error handling: failed signal processing does NOT block subsequent signal
 * processing or propagate back to the event publisher. Errors are logged for
 * investigation.</p>
 *
 * @see GrowthSignalProcessor
 * @see MissionCompletedEvent
 * @see MissionReflectedEvent
 * @see GoalProgressEvent
 * @see GoalCompletedEvent
 * @see ConversationCompletedEvent
 */
@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final GrowthSignalProcessor growthSignalProcessor;

    public DomainEventListener(GrowthSignalProcessor growthSignalProcessor) {
        this.growthSignalProcessor = growthSignalProcessor;
    }

    /**
     * Handles mission completion events from the Mission domain (SPEC-002).
     *
     * <p>Triggers recording of a MISSION_COMPLETED growth signal.</p>
     *
     * @param event the mission completed event
     */
    @EventListener
    public void onMissionCompleted(MissionCompletedEvent event) {
        log.debug("Received MissionCompletedEvent: fatherId={}, missionId={}",
                event.getFatherId(), event.getMissionId());
        try {
            growthSignalProcessor.onMissionCompleted(event);
        } catch (Exception e) {
            log.error("Failed to process MissionCompletedEvent for father={}, mission={}: {}",
                    event.getFatherId(), event.getMissionId(), e.getMessage(), e);
        }
    }

    /**
     * Handles mission reflection events from the Mission domain (SPEC-002).
     *
     * <p>Triggers recording of a MISSION_REFLECTED growth signal (bonus on top of completion).</p>
     *
     * @param event the mission reflected event
     */
    @EventListener
    public void onMissionReflected(MissionReflectedEvent event) {
        log.debug("Received MissionReflectedEvent: fatherId={}, missionId={}",
                event.getFatherId(), event.getMissionId());
        try {
            growthSignalProcessor.onMissionReflected(event);
        } catch (Exception e) {
            log.error("Failed to process MissionReflectedEvent for father={}, mission={}: {}",
                    event.getFatherId(), event.getMissionId(), e.getMessage(), e);
        }
    }

    /**
     * Handles goal progress events from the Goal domain (SPEC-002).
     *
     * <p>The GrowthSignalProcessor will only record a GOAL_PROGRESS signal if the
     * progress increase is at least 10%.</p>
     *
     * @param event the goal progress event
     */
    @EventListener
    public void onGoalProgress(GoalProgressEvent event) {
        log.debug("Received GoalProgressEvent: fatherId={}, goalId={}, progress={}->{}",
                event.getFatherId(), event.getGoalId(),
                event.getPreviousProgressPercent(), event.getCurrentProgressPercent());
        try {
            growthSignalProcessor.onGoalProgress(event);
        } catch (Exception e) {
            log.error("Failed to process GoalProgressEvent for father={}, goal={}: {}",
                    event.getFatherId(), event.getGoalId(), e.getMessage(), e);
        }
    }

    /**
     * Handles goal completion events from the Goal domain (SPEC-002).
     *
     * <p>Triggers recording of a GOAL_COMPLETED growth signal.</p>
     *
     * @param event the goal completed event
     */
    @EventListener
    public void onGoalCompleted(GoalCompletedEvent event) {
        log.debug("Received GoalCompletedEvent: fatherId={}, goalId={}",
                event.getFatherId(), event.getGoalId());
        try {
            growthSignalProcessor.onGoalCompleted(event);
        } catch (Exception e) {
            log.error("Failed to process GoalCompletedEvent for father={}, goal={}: {}",
                    event.getFatherId(), event.getGoalId(), e.getMessage(), e);
        }
    }

    /**
     * Handles conversation completion events from the Conversation Engine (SPEC-005).
     *
     * <p>The GrowthSignalProcessor will only record a MEANINGFUL_CONVERSATION signal if the
     * conversation has more than 5 exchanges and a quality rating above 0.6.</p>
     *
     * @param event the conversation completed event
     */
    @EventListener
    public void onConversationCompleted(ConversationCompletedEvent event) {
        log.debug("Received ConversationCompletedEvent: fatherId={}, conversationId={}, " +
                        "exchanges={}, qualityRating={}",
                event.getFatherId(), event.getConversationId(),
                event.getExchangeCount(), event.getQualityRating());
        try {
            growthSignalProcessor.onConversationCompleted(event);
        } catch (Exception e) {
            log.error("Failed to process ConversationCompletedEvent for father={}, conversation={}: {}",
                    event.getFatherId(), event.getConversationId(), e.getMessage(), e);
        }
    }
}
