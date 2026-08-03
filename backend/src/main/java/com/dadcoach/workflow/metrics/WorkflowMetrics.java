package com.dadcoach.workflow.metrics;

import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.WorkflowState;

import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Metrics collection service for the Workflow Engine.
 * 
 * <p>This component exposes operational metrics for monitoring the deterministic 
 * workflow engine as required by Requirement 16.2. All metrics are exposed via 
 * the Spring Boot Actuator /actuator/metrics endpoint and can be scraped by 
 * Prometheus or other monitoring systems.</p>
 * 
 * <h2>Exposed Metrics:</h2>
 * <ul>
 *   <li><b>workflow_fathers_by_state</b> (Gauge) - Count of fathers in each workflow state</li>
 *   <li><b>workflow_state_transitions_total</b> (Counter) - State transition counts by from/to state</li>
 *   <li><b>workflow_quality_time_completions_total</b> (Counter) - Count of completed quality times</li>
 *   <li><b>workflow_quality_time_missed_total</b> (Counter) - Count of missed quality times</li>
 *   <li><b>workflow_message_generation_latency_seconds</b> (Timer) - Message generation time</li>
 *   <li><b>workflow_message_generation_ai_total</b> (Counter) - Count of AI-generated messages</li>
 *   <li><b>workflow_message_generation_fallback_total</b> (Counter) - Count of fallback template messages</li>
 * </ul>
 * 
 * <p>Implements Requirement 16.2 from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowState
 */
@Component
public class WorkflowMetrics {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMetrics.class);

    // Metric names (following Prometheus naming conventions)
    private static final String METRIC_FATHERS_BY_STATE = "workflow.fathers.by.state";
    private static final String METRIC_STATE_TRANSITIONS = "workflow.state.transitions.total";
    private static final String METRIC_QUALITY_TIME_COMPLETIONS = "workflow.quality.time.completions.total";
    private static final String METRIC_QUALITY_TIME_MISSED = "workflow.quality.time.missed.total";
    private static final String METRIC_MESSAGE_GENERATION_LATENCY = "workflow.message.generation.latency";
    private static final String METRIC_MESSAGE_GENERATION_AI = "workflow.message.generation.ai.total";
    private static final String METRIC_MESSAGE_GENERATION_FALLBACK = "workflow.message.generation.fallback.total";

    private final MeterRegistry meterRegistry;
    private final FatherRepository fatherRepository;

    // Pre-registered counters for state transitions (from_state -> to_state)
    private final Map<WorkflowState, Map<WorkflowState, Counter>> transitionCounters;

    // Counter for Quality Time completions and misses
    private Counter qualityTimeCompletionsCounter;
    private Counter qualityTimeMissedCounter;

    // Message generation metrics
    private Timer messageGenerationTimer;
    private Counter messageGenerationAiCounter;
    private Counter messageGenerationFallbackCounter;

    /**
     * Creates a new WorkflowMetrics instance.
     * 
     * @param meterRegistry the Micrometer registry for metrics (auto-wired by Spring Boot Actuator)
     * @param fatherRepository the repository for querying father counts by state
     */
    public WorkflowMetrics(MeterRegistry meterRegistry, FatherRepository fatherRepository) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.fatherRepository = Objects.requireNonNull(fatherRepository, "fatherRepository must not be null");
        this.transitionCounters = new EnumMap<>(WorkflowState.class);
    }

    /**
     * Initializes all metrics after the component is constructed.
     * Registers gauges, counters, and timers with the Micrometer registry.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing workflow metrics collection");

        // Register gauges for fathers by state (Requirement 16.2: Count of fathers in each workflow state)
        registerFathersByStateGauges();

        // Pre-register counters for all valid state transitions (Requirement 16.2: State transition rates)
        registerStateTransitionCounters();

        // Register Quality Time completion/miss counters (Requirement 16.2: Quality Time completion rate)
        registerQualityTimeCounters();

        // Register message generation metrics (Requirement 16.2: Message generation latency, AI vs fallback usage)
        registerMessageGenerationMetrics();

        log.info("Workflow metrics initialized successfully");
    }

    // ─── Gauge: Fathers by Workflow State ───────────────────────────────────────

    /**
     * Registers gauges for counting fathers in each workflow state.
     * 
     * <p>These gauges query the database on each scrape to provide the current
     * count of fathers in each state. The gauges are tagged with the state name.</p>
     */
    private void registerFathersByStateGauges() {
        for (WorkflowState state : WorkflowState.values()) {
            Gauge.builder(METRIC_FATHERS_BY_STATE, () -> countFathersInState(state))
                    .description("Count of fathers in workflow state " + state.name())
                    .tag("state", state.name())
                    .register(meterRegistry);

            log.debug("Registered gauge for fathers in state: {}", state);
        }
    }

    /**
     * Queries the database for the count of fathers in a given workflow state.
     * 
     * @param state the workflow state to count
     * @return the number of fathers currently in that state
     */
    private long countFathersInState(WorkflowState state) {
        try {
            return fatherRepository.countByCurrentWorkflowState(state);
        } catch (Exception e) {
            log.warn("Error counting fathers in state {}: {}", state, e.getMessage());
            return 0L;
        }
    }

    // ─── Counter: State Transitions ─────────────────────────────────────────────

    /**
     * Registers counters for all valid state transitions.
     * 
     * <p>Pre-registers counters for all possible from/to state combinations
     * that are valid according to the state machine definition.</p>
     */
    private void registerStateTransitionCounters() {
        for (WorkflowState fromState : WorkflowState.values()) {
            Map<WorkflowState, Counter> toCounters = new EnumMap<>(WorkflowState.class);

            for (WorkflowState toState : fromState.getValidTransitions()) {
                Counter counter = Counter.builder(METRIC_STATE_TRANSITIONS)
                        .description("Count of state transitions")
                        .tag("from_state", fromState.name())
                        .tag("to_state", toState.name())
                        .register(meterRegistry);

                toCounters.put(toState, counter);
                log.debug("Registered transition counter: {} -> {}", fromState, toState);
            }

            transitionCounters.put(fromState, toCounters);
        }
    }

    /**
     * Records a state transition.
     * 
     * <p>Increments the counter for the specified from/to state transition.
     * This should be called after a successful state transition in the workflow engine.</p>
     * 
     * @param fromState the state transitioning from
     * @param toState the state transitioning to
     */
    public void recordStateTransition(WorkflowState fromState, WorkflowState toState) {
        if (fromState == null || toState == null) {
            log.warn("Cannot record transition with null states: from={}, to={}", fromState, toState);
            return;
        }

        Map<WorkflowState, Counter> toCounters = transitionCounters.get(fromState);
        if (toCounters != null) {
            Counter counter = toCounters.get(toState);
            if (counter != null) {
                counter.increment();
                log.debug("Recorded state transition: {} -> {}", fromState, toState);
            } else {
                // This is an unexpected transition - still record it with a dynamic counter
                log.warn("Unexpected state transition (not pre-registered): {} -> {}", fromState, toState);
                Counter dynamicCounter = Counter.builder(METRIC_STATE_TRANSITIONS)
                        .description("Count of state transitions")
                        .tag("from_state", fromState.name())
                        .tag("to_state", toState.name())
                        .tag("unexpected", "true")
                        .register(meterRegistry);
                dynamicCounter.increment();
            }
        }
    }

    // ─── Counter: Quality Time Completions and Misses ───────────────────────────

    /**
     * Registers counters for Quality Time completion and miss events.
     */
    private void registerQualityTimeCounters() {
        qualityTimeCompletionsCounter = Counter.builder(METRIC_QUALITY_TIME_COMPLETIONS)
                .description("Total count of completed Quality Time sessions")
                .register(meterRegistry);

        qualityTimeMissedCounter = Counter.builder(METRIC_QUALITY_TIME_MISSED)
                .description("Total count of missed Quality Time sessions")
                .register(meterRegistry);

        log.debug("Registered Quality Time completion/miss counters");
    }

    /**
     * Records a Quality Time completion.
     * 
     * <p>Increments the completion counter when a father reports completing
     * their Quality Time session.</p>
     */
    public void recordQualityTimeCompletion() {
        qualityTimeCompletionsCounter.increment();
        log.debug("Recorded Quality Time completion");
    }

    /**
     * Records a Quality Time miss.
     * 
     * <p>Increments the missed counter when a father reports not completing
     * their Quality Time session or when the follow-up times out.</p>
     */
    public void recordQualityTimeMissed() {
        qualityTimeMissedCounter.increment();
        log.debug("Recorded Quality Time missed");
    }

    /**
     * Records a Quality Time outcome based on the status.
     * 
     * @param status the Quality Time status (COMPLETED or MISSED)
     */
    public void recordQualityTimeOutcome(QualityTimeStatus status) {
        if (status == QualityTimeStatus.COMPLETED) {
            recordQualityTimeCompletion();
        } else if (status == QualityTimeStatus.MISSED) {
            recordQualityTimeMissed();
        }
    }

    // ─── Timer and Counters: Message Generation ─────────────────────────────────

    /**
     * Registers metrics for message generation.
     */
    private void registerMessageGenerationMetrics() {
        // Timer for message generation latency
        messageGenerationTimer = Timer.builder(METRIC_MESSAGE_GENERATION_LATENCY)
                .description("Time taken to generate messages")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        // Counter for AI-generated messages
        messageGenerationAiCounter = Counter.builder(METRIC_MESSAGE_GENERATION_AI)
                .description("Total count of AI-generated messages")
                .register(meterRegistry);

        // Counter for fallback template messages
        messageGenerationFallbackCounter = Counter.builder(METRIC_MESSAGE_GENERATION_FALLBACK)
                .description("Total count of fallback template messages")
                .register(meterRegistry);

        log.debug("Registered message generation metrics");
    }

    /**
     * Records the time taken for a message generation operation.
     * 
     * @param durationNanos the duration in nanoseconds
     * @param usedAi true if AI was used, false if fallback was used
     */
    public void recordMessageGenerationLatency(long durationNanos, boolean usedAi) {
        messageGenerationTimer.record(durationNanos, TimeUnit.NANOSECONDS);

        if (usedAi) {
            messageGenerationAiCounter.increment();
            log.debug("Recorded AI message generation: {}ms", TimeUnit.NANOSECONDS.toMillis(durationNanos));
        } else {
            messageGenerationFallbackCounter.increment();
            log.debug("Recorded fallback message generation: {}ms", TimeUnit.NANOSECONDS.toMillis(durationNanos));
        }
    }

    /**
     * Records the time taken for a message generation operation using Duration.
     * 
     * @param duration the duration of the operation
     * @param usedAi true if AI was used, false if fallback was used
     */
    public void recordMessageGenerationLatency(Duration duration, boolean usedAi) {
        recordMessageGenerationLatency(duration.toNanos(), usedAi);
    }

    /**
     * Records a message generation operation using a timer sample.
     * 
     * @return a Timer.Sample that should be stopped when the operation completes
     */
    public Timer.Sample startMessageGenerationTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stops a message generation timer sample and records the result.
     * 
     * @param sample the timer sample from startMessageGenerationTimer()
     * @param usedAi true if AI was used, false if fallback was used
     */
    public void stopMessageGenerationTimer(Timer.Sample sample, boolean usedAi) {
        if (sample == null) {
            log.warn("Cannot stop null timer sample");
            return;
        }

        sample.stop(messageGenerationTimer);

        if (usedAi) {
            messageGenerationAiCounter.increment();
        } else {
            messageGenerationFallbackCounter.increment();
        }
    }

    /**
     * Records that AI-generated message was used (without timing).
     * 
     * <p>Use this when timing is handled separately.</p>
     */
    public void recordAiMessageUsed() {
        messageGenerationAiCounter.increment();
    }

    /**
     * Records that a fallback template message was used (without timing).
     * 
     * <p>Use this when timing is handled separately, or when AI failed/timed out.</p>
     */
    public void recordFallbackMessageUsed() {
        messageGenerationFallbackCounter.increment();
        log.warn("Fallback message used instead of AI generation");
    }

    // ─── Accessor Methods for Testing ───────────────────────────────────────────

    /**
     * Gets the total count of state transitions from a specific state.
     * 
     * @param fromState the state to count transitions from
     * @return the total count of transitions from that state
     */
    public double getTransitionCount(WorkflowState fromState, WorkflowState toState) {
        Map<WorkflowState, Counter> toCounters = transitionCounters.get(fromState);
        if (toCounters != null) {
            Counter counter = toCounters.get(toState);
            if (counter != null) {
                return counter.count();
            }
        }
        return 0.0;
    }

    /**
     * Gets the total count of Quality Time completions.
     * 
     * @return the total count
     */
    public double getQualityTimeCompletionsCount() {
        return qualityTimeCompletionsCounter.count();
    }

    /**
     * Gets the total count of Quality Time misses.
     * 
     * @return the total count
     */
    public double getQualityTimeMissedCount() {
        return qualityTimeMissedCounter.count();
    }

    /**
     * Gets the total count of AI-generated messages.
     * 
     * @return the total count
     */
    public double getAiMessageCount() {
        return messageGenerationAiCounter.count();
    }

    /**
     * Gets the total count of fallback messages.
     * 
     * @return the total count
     */
    public double getFallbackMessageCount() {
        return messageGenerationFallbackCounter.count();
    }
}
