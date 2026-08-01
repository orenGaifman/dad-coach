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
import com.dadcoach.ai.prompt.PromptAssembler;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.ai.routing.FallbackChain;
import com.dadcoach.ai.routing.ModelRouter;
import com.dadcoach.ai.safety.SafetyClassification;
import com.dadcoach.ai.safety.SafetyClassifier;
import com.dadcoach.conversation.ConversationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the Intelligence Layer facade.
 *
 * <p>Coordinates all AI sub-components (safety, prompt assembly, routing, validation)
 * into a stateless pipeline. Every method receives context and returns structured output.
 * The AI NEVER directly mutates state — all outputs are recommendations.
 *
 * <p>Pipeline order for coaching responses:
 * <ol>
 *   <li>Safety classification (SafetyClassifier) — runs FIRST</li>
 *   <li>Prompt assembly (PromptAssembler) — builds the prompt</li>
 *   <li>Model routing (ModelRouter) — routes to appropriate model via FallbackChain</li>
 *   <li>Validation — validates AI output before returning</li>
 * </ol>
 */
@Service
public class IntelligenceLayerImpl implements IntelligenceLayer {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceLayerImpl.class);

    // Safety responses - English (default) and Hebrew versions will be selected based on context
    private static final String SAFETY_CRISIS_RESPONSE_EN =
        "I understand you're going through a very difficult time. " +
        "Your wellbeing is important. Please reach out to the Crisis Line: 988 " +
        "(available 24/7). You're not alone in this.";

    private static final String SAFETY_CRISIS_RESPONSE_HE =
        "אני מבין שאתה עובר תקופה קשה מאוד. " +
        "הרווחה שלך חשובה. אנא פנה לקו החירום לבריאות הנפש: *2784 " +
        "(זמין 24/7). אתה לא לבד בזה.";

    private static final String SAFETY_CHILD_RESPONSE_EN =
        "I'm concerned about what you're describing. Children's safety is the most important thing. " +
        "Please contact child protective services in your area. " +
        "I'm here to support you.";

    private static final String SAFETY_CHILD_RESPONSE_HE =
        "אני מודאג ממה שאתה מתאר. בטיחות הילדים היא הדבר הכי חשוב. " +
        "אנא פנה לשירותי הרווחה באזורך. " +
        "אני כאן לתמוך בך.";

    private final SafetyClassifier safetyClassifier;
    private final PromptAssembler promptAssembler;
    private final ModelRouter modelRouter;

    public IntelligenceLayerImpl(
            SafetyClassifier safetyClassifier,
            PromptAssembler promptAssembler,
            ModelRouter modelRouter) {
        this.safetyClassifier = safetyClassifier;
        this.promptAssembler = promptAssembler;
        this.modelRouter = modelRouter;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pipeline: safety → prompt → route → validate.
     * If safety classification indicates CRISIS or CHILD_SAFETY, returns
     * a pre-written safety response without calling the AI model.
     */
    @Override
    public CoachingResponse generateCoachingResponse(CoachingContext context) {
        log.debug("Generating coaching response for father={}, type={}",
            context.fatherId(), context.conversationType());

        // Step 1: Safety classification runs FIRST
        SafetyClassification safety = safetyClassifier.classify(context.userMessage());
        if (safety.requiresIntervention()) {
            log.warn("Safety intervention required for father={}: category={}, confidence={}",
                context.fatherId(), safety.category(), safety.confidence());
            return buildSafetyResponse(safety, context.locale());
        }

        // Step 2: Prompt assembly
        List<AiMessage> assembledPrompt = promptAssembler.assemble(
            context.systemPrompt(),
            context.memoryContent(),
            context.contextContent(),
            appendUserMessage(context.conversationHistory(), context.userMessage()),
            context.outputInstructions()
        );

        // Step 3: Model routing via FallbackChain
        AiProviderRequest request = new AiProviderRequest(
            "default", // ModelRouter overrides this based on conversation type
            assembledPrompt,
            0.7, 0.9, 300, false,
            Map.of("fatherId", context.fatherId().toString())
        );
        FallbackChain.FallbackResult result = modelRouter.route(request, context.conversationType());
        AiProviderResponse response = result.response();

        // Step 4: Validation
        boolean valid = validateCoachingResponse(response.content());

        return new CoachingResponse(
            response.content(),
            response.model(),
            response.provider(),
            response.inputTokens(),
            response.outputTokens(),
            response.latency(),
            result.usedFallback(),
            valid,
            valid ? 0.9 : 0.5
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pipeline: prompt assembly → route (MISSION_GENERATION) → validate.
     */
    @Override
    public MissionOutput generateMission(MissionContext context) {
        log.debug("Generating mission for father={}, child={}, difficulty={}",
            context.fatherId(), context.childName(), context.difficulty());

        // Build mission generation prompt
        String missionPrompt = buildMissionPrompt(context);
        List<AiMessage> messages = List.of(
            AiMessage.system(missionPrompt),
            AiMessage.user("Generate a mission for " + context.childName())
        );

        // Route to MISSION_GENERATION model (with JSON mode)
        AiProviderRequest request = new AiProviderRequest(
            "default",
            messages,
            0.3, 0.8, 400, true,
            Map.of("fatherId", context.fatherId().toString())
        );
        FallbackChain.FallbackResult result = modelRouter.route(request, ConversationType.MISSION_GENERATION);
        AiProviderResponse response = result.response();

        // Validate and parse the mission output
        boolean valid = validateMissionOutput(response.content());

        return parseMissionOutput(response.content(), response.model(), valid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pipeline: prompt assembly → route → validate.
     * Extracts memories from the conversation transcript as recommendations.
     */
    @Override
    public MemoryExtractionOutput extractMemories(CompletedConversation conversation) {
        log.debug("Extracting memories from conversation={} for father={}",
            conversation.conversationId(), conversation.fatherId());

        // Build extraction prompt
        String extractionPrompt = buildMemoryExtractionPrompt(conversation);
        List<AiMessage> messages = List.of(
            AiMessage.system(extractionPrompt),
            AiMessage.user(formatConversationTranscript(conversation.messages()))
        );

        // Route to appropriate model (using DAILY_COACHING routing as extraction is low-cost)
        AiProviderRequest request = new AiProviderRequest(
            "default",
            messages,
            0.3, 0.9, 400, true,
            Map.of("fatherId", conversation.fatherId().toString())
        );
        FallbackChain.FallbackResult result = modelRouter.route(request, ConversationType.DAILY_COACHING);
        AiProviderResponse response = result.response();

        boolean valid = validateMemoryOutput(response.content());

        return parseMemoryOutput(response.content(), conversation.conversationId().toString(),
            response.model(), valid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates directly to the SafetyClassifier.
     * This is a pure delegation — no state mutation, just classification.
     */
    @Override
    public SafetyClassification classifyMessage(InboundMessage message) {
        log.debug("Classifying message for father={}", message.fatherId());
        return safetyClassifier.classify(message.content());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Decision Engine (stub for now — Task 7 will provide full implementation).
     * Returns a WAIT action as default until the DecisionEngine is implemented.
     */
    @Override
    public ActionRecommendation decideDailyAction(DailyDecisionContext context) {
        log.debug("Deciding daily action for father={}, phase={}, day={}",
            context.fatherId(), context.currentPhase(), context.phaseDay());

        // Stub implementation — full DecisionEngine in Task 7
        // For now, delegate to a simple priority evaluation
        return evaluateSimplePriorities(context);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pipeline: prompt assembly → route (REFLECTION) → validate.
     */
    @Override
    public WeeklySummaryOutput generateSummary(SummaryPeriod period) {
        log.debug("Generating weekly summary for father={}, period={} to {}",
            period.fatherId(), period.periodStart(), period.periodEnd());

        String summaryPrompt = buildSummaryPrompt(period);
        List<AiMessage> messages = List.of(
            AiMessage.system(summaryPrompt),
            AiMessage.user("Generate weekly summary for period " +
                period.periodStart() + " to " + period.periodEnd())
        );

        AiProviderRequest request = new AiProviderRequest(
            "default",
            messages,
            0.7, 0.9, 400, true,
            Map.of("fatherId", period.fatherId().toString())
        );
        FallbackChain.FallbackResult result = modelRouter.route(request, ConversationType.REFLECTION);
        AiProviderResponse response = result.response();

        boolean valid = response.content() != null && !response.content().isBlank();

        return new WeeklySummaryOutput(
            period.fatherId(),
            period.periodStart(),
            period.periodEnd(),
            response.content(),
            List.of(), // highlights parsed in full implementation
            0, // missions completed would come from context
            0, // streak days would come from context
            response.model(),
            valid
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pipeline: prompt assembly → route (REFLECTION) → validate.
     */
    @Override
    public ReflectionInsightOutput evaluateReflection(ReflectionInput input) {
        log.debug("Evaluating reflection for father={}, phase={}",
            input.fatherId(), input.currentPhase());

        String reflectionPrompt = buildReflectionPrompt(input);
        List<AiMessage> messages = List.of(
            AiMessage.system(reflectionPrompt),
            AiMessage.user(input.reflectionText())
        );

        AiProviderRequest request = new AiProviderRequest(
            "default",
            messages,
            0.7, 0.9, 400, true,
            Map.of("fatherId", input.fatherId().toString())
        );
        FallbackChain.FallbackResult result = modelRouter.route(request, ConversationType.REFLECTION);
        AiProviderResponse response = result.response();

        boolean valid = response.content() != null && !response.content().isBlank();

        return new ReflectionInsightOutput(
            List.of(response.content()),
            List.of(),
            "",
            "neutral",
            response.model(),
            valid
        );
    }

    // ===== Private Helper Methods =====

    private CoachingResponse buildSafetyResponse(SafetyClassification safety, String locale) {
        boolean isHebrew = "he".equals(locale);
        String message = switch (safety.category()) {
            case CRISIS -> isHebrew ? SAFETY_CRISIS_RESPONSE_HE : SAFETY_CRISIS_RESPONSE_EN;
            case CHILD_SAFETY -> isHebrew ? SAFETY_CHILD_RESPONSE_HE : SAFETY_CHILD_RESPONSE_EN;
            default -> isHebrew ? SAFETY_CRISIS_RESPONSE_HE : SAFETY_CRISIS_RESPONSE_EN;
        };
        return CoachingResponse.fallback(message);
    }

    private List<AiMessage> appendUserMessage(List<AiMessage> history, String userMessage) {
        List<AiMessage> combined = new ArrayList<>(history);
        combined.add(AiMessage.user(userMessage));
        return combined;
    }

    private boolean validateCoachingResponse(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // Basic validation: non-empty, reasonable length
        return content.length() >= 10 && content.length() <= 2000;
    }

    private boolean validateMissionOutput(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // Check for JSON-like structure with required fields
        return content.contains("title") && content.contains("description")
            && content.contains("category") && content.contains("difficulty");
    }

    private boolean validateMemoryOutput(String content) {
        return content != null && !content.isBlank();
    }

    private MissionOutput parseMissionOutput(String content, String model, boolean valid) {
        // Simplified parsing — full JSON parsing in OutputValidator (Task 12)
        // For now, extract basic fields or return defaults
        try {
            // Basic extraction from JSON-like content
            String title = extractJsonField(content, "title", "Mission for your child");
            String description = extractJsonField(content, "description", "Complete this mission with your child.");
            String category = extractJsonField(content, "category", "CONNECTION");
            int difficulty = extractJsonIntField(content, "difficulty", 3);
            int minutes = extractJsonIntField(content, "estimated_minutes", 30);

            return new MissionOutput(title, description, category, difficulty, minutes, valid, model);
        } catch (Exception e) {
            log.warn("Failed to parse mission output, using defaults: {}", e.getMessage());
            return new MissionOutput(
                "Mission for your child",
                "Complete this mission with your child.",
                "CONNECTION", 3, 30, false, model
            );
        }
    }

    private MemoryExtractionOutput parseMemoryOutput(String content, String conversationId,
                                                      String model, boolean valid) {
        // Simplified — full parsing in extraction module (Task 9)
        return new MemoryExtractionOutput(List.of(), conversationId, model, valid);
    }

    private String buildMissionPrompt(MissionContext context) {
        return String.format("""
            Generate a parenting mission with these constraints:
            - Child: %s, age %d, interests: %s
            - Category: %s
            - Difficulty: %d/5
            - Day: %s, Time context: %s
            - Father's coaching style: %s
            - Goal alignment: %s
            - Previous missions this week: %s
            - Avoid: %s
            
            Output valid JSON: {"title": "...", "description": "...", "category": "...", "difficulty": N, "estimated_minutes": N}
            Respond in English.
            """,
            context.childName(), context.childAge(), String.join(", ", context.childInterests()),
            context.category(), context.difficulty(),
            context.dayOfWeek(), context.timeContext(),
            context.coachingStyle(), context.primaryGoal(),
            String.join(", ", context.recentCategories()),
            String.join(", ", context.cooldownCategories())
        );
    }

    private String buildMemoryExtractionPrompt(CompletedConversation conversation) {
        return """
            Extract key memories from the following conversation.
            For each memory, provide:
            - category: one of IDENTITY, RELATIONSHIP, PREFERENCE, GOAL, CHALLENGE, MILESTONE, CONTEXT
            - content: the memory content (max 500 characters)
            - importance_score: 1-10
            - confidence_score: 0.0-1.0
            - subject_type: "child", "father", or "family"
            
            Output valid JSON array.
            """;
    }

    private String buildSummaryPrompt(SummaryPeriod period) {
        return String.format("""
            Generate a weekly coaching summary for the period %s to %s.
            Include: key achievements, challenges faced, growth observations.
            Respond in English. Be warm and encouraging.
            """, period.periodStart(), period.periodEnd());
    }

    private String buildReflectionPrompt(ReflectionInput input) {
        return String.format("""
            Analyze this father's reflection. He is in %s phase, day %d.
            Extract: key insights, growth areas, suggested focus areas, emotional tone.
            Be empathetic and strengths-based.
            Respond in English.
            """, input.currentPhase(), input.phaseDay());
    }

    private String formatConversationTranscript(List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiMessage msg : messages) {
            sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Simple priority evaluation stub — full DecisionEngine comes in Task 7.
     */
    private ActionRecommendation evaluateSimplePriorities(DailyDecisionContext context) {
        // Priority 1: Safety check (if inbound message contains crisis indicators)
        if (context.inboundMessage() != null) {
            SafetyClassification safety = safetyClassifier.classify(context.inboundMessage());
            if (safety.requiresIntervention()) {
                return new ActionRecommendation(
                    context.fatherId(),
                    ActionRecommendation.ActionType.SAFETY_RESPONSE,
                    1,
                    "Safety intervention required: " + safety.reason(),
                    safety.confidence(),
                    java.time.Instant.now()
                );
            }
        }

        // Default: WAIT — full decision logic in Task 7
        return ActionRecommendation.wait(context.fatherId(),
            "Default WAIT action — full DecisionEngine pending (Task 7)");
    }

    private String extractJsonField(String json, String field, String defaultValue) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private int extractJsonIntField(String json, String field, int defaultValue) {
        String pattern = "\"" + field + "\"\\s*:\\s*(\\d+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }
}
