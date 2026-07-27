package com.dadcoach.ai;

import com.dadcoach.ai.output.ActionRecommendation;
import com.dadcoach.ai.output.CoachingContext;
import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.ai.output.CompletedConversation;
import com.dadcoach.ai.output.DailyDecisionContext;
import com.dadcoach.ai.output.InboundMessage;
import com.dadcoach.ai.output.MemoryExtractionOutput;
import com.dadcoach.ai.output.MissionContext;
import com.dadcoach.ai.output.MissionOutput;
import com.dadcoach.ai.output.ReflectionInput;
import com.dadcoach.ai.output.ReflectionInsightOutput;
import com.dadcoach.ai.output.SummaryPeriod;
import com.dadcoach.ai.output.WeeklySummaryOutput;
import com.dadcoach.ai.safety.SafetyClassification;

/**
 * Public interface for all AI capabilities in Dad Coach.
 *
 * <p>The Intelligence Layer operates as a stateless advisory subsystem.
 * Every method receives all required context as input and returns a structured
 * output record. The AI NEVER directly mutates state — all outputs are
 * recommendations validated and acted upon by the application layer.
 *
 * <p>Key design principles:
 * <ul>
 *   <li>Stateless: no hidden state, no session affinity, no in-memory caches</li>
 *   <li>Advisory: outputs are recommendations, not commands</li>
 *   <li>Typed: all inputs and outputs are strongly typed records</li>
 *   <li>Safe: safety classification always runs before coaching generation</li>
 * </ul>
 *
 * <p>The facade coordinates sub-components in a defined pipeline:
 * Safety Classification → Prompt Assembly → Model Routing → Output Validation
 */
public interface IntelligenceLayer {

    /**
     * Generate a coaching response for the father.
     * Coordinates: safety check → prompt assembly → model routing → validation.
     *
     * @param context all context needed for response generation
     * @return the generated coaching response (recommendation, not a mutation)
     */
    CoachingResponse generateCoachingResponse(CoachingContext context);

    /**
     * Generate a personalized mission for a father's child.
     * Coordinates: prompt assembly → model routing → validation.
     *
     * @param context mission generation context including child, difficulty, and constraints
     * @return the generated mission output (recommendation, not a mutation)
     */
    MissionOutput generateMission(MissionContext context);

    /**
     * Extract memories from a completed conversation.
     * Coordinates: prompt assembly → model routing → validation.
     *
     * @param conversation the completed conversation to extract memories from
     * @return extracted memories (recommendations for persistence, not actual mutations)
     */
    MemoryExtractionOutput extractMemories(CompletedConversation conversation);

    /**
     * Classify an inbound message for safety.
     * This MUST be called BEFORE any coaching generation occurs.
     *
     * @param message the inbound message to classify
     * @return the safety classification (never null, always exactly one category)
     */
    SafetyClassification classifyMessage(InboundMessage message);

    /**
     * Decide what action to take for a father.
     * Delegates to the Decision Engine's priority tree.
     *
     * @param context the decision context including phase, engagement, and history
     * @return the recommended action (advisory, not executed by this layer)
     */
    ActionRecommendation decideDailyAction(DailyDecisionContext context);

    /**
     * Generate a weekly summary for a father.
     * Coordinates: prompt assembly → model routing → validation.
     *
     * @param period the summary period definition
     * @return the generated summary (recommendation, not a mutation)
     */
    WeeklySummaryOutput generateSummary(SummaryPeriod period);

    /**
     * Evaluate a father's reflection for insights.
     * Coordinates: prompt assembly → model routing → validation.
     *
     * @param input the reflection input to evaluate
     * @return extracted insights (recommendations, not mutations)
     */
    ReflectionInsightOutput evaluateReflection(ReflectionInput input);
}
