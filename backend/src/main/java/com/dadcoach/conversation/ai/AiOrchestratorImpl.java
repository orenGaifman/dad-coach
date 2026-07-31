package com.dadcoach.conversation.ai;

import com.dadcoach.ai.AiProviderUnavailableException;
import com.dadcoach.ai.IntelligenceLayer;
import com.dadcoach.ai.output.CoachingContext;
import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.ai.safety.SafetyClassification;
import com.dadcoach.ai.safety.SafetyClassifier;
import com.dadcoach.ai.safety.SafetyResponseProvider;
import com.dadcoach.conversation.ConversationType;
import com.dadcoach.conversation.context.ConversationContext;
import com.dadcoach.conversation.dto.InboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the AI orchestration sub-pipeline:
 * safety classification → generate → validate → retry with correction → fallback.
 *
 * <p>This class NEVER throws an exception — it always produces a deliverable response.
 * If AI generation fails or validation cannot be satisfied after one retry, a pre-written
 * fallback response is returned.
 *
 * <p>Pipeline execution order:
 * <ol>
 *   <li>Safety classification runs FIRST (before any coaching generation)</li>
 *   <li>If CRISIS or CHILD_SAFETY → immediate safety response (no generation)</li>
 *   <li>Generate coaching response via IntelligenceLayer</li>
 *   <li>Validate the response</li>
 *   <li>If validation fails → retry once with correction context</li>
 *   <li>If retry also fails → deliver fallback response</li>
 * </ol>
 *
 * <p>Maximum 1 retry (2 total AI calls) per message.
 */
@Service
public class AiOrchestratorImpl implements AiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorImpl.class);

    private final SafetyClassifier safetyClassifier;
    private final SafetyResponseProvider safetyResponseProvider;
    private final IntelligenceLayer intelligenceLayer;
    private final ResponseValidator responseValidator;
    private final FallbackResponseProvider fallbackProvider;

    public AiOrchestratorImpl(
            SafetyClassifier safetyClassifier,
            SafetyResponseProvider safetyResponseProvider,
            IntelligenceLayer intelligenceLayer,
            ResponseValidator responseValidator,
            FallbackResponseProvider fallbackProvider
    ) {
        this.safetyClassifier = safetyClassifier;
        this.safetyResponseProvider = safetyResponseProvider;
        this.intelligenceLayer = intelligenceLayer;
        this.responseValidator = responseValidator;
        this.fallbackProvider = fallbackProvider;
    }

    /**
     * Executes the AI orchestration pipeline.
     * Guaranteed to return a valid, deliverable result (never null, never throws).
     */
    @Override
    public AiResult orchestrate(ConversationContext context, InboundMessageDto message) {
        Instant startTime = Instant.now();

        // Extract locale for fallback messages
        String locale = getLocale(context);

        try {
            // Step 1: Safety classification runs FIRST — before any coaching generation
            SafetyClassification safety = safetyClassifier.classify(message.content());

            // Step 2: If escalation (CRISIS or CHILD_SAFETY), return immediate safety response
            if (safety.requiresIntervention()) {
                log.warn("Safety escalation detected: category={}, confidence={}, reason={}",
                        safety.category(), safety.confidence(), safety.reason());
                String safetyResponse = safetyResponseProvider.getResponse(safety);
                return AiResult.safetyEscalation(safetyResponse, safety.category().name());
            }

            // Step 3: Generate coaching response
            CoachingContext coachingContext = buildCoachingContext(context, message);
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(coachingContext);

            // Step 4: Validate the response
            ValidationResult validation = responseValidator.validate(response, context);
            if (validation.passed()) {
                return buildSuccessResult(response, startTime, false);
            }

            log.info("First AI response failed validation: failures={}", validation.failures());

            // Step 5: Retry once with correction context
            CoachingContext retryContext = buildRetryContext(context, message, validation.failures());
            CoachingResponse retryResponse = intelligenceLayer.generateCoachingResponse(retryContext);

            // Validate the retry response
            ValidationResult retryValidation = responseValidator.validate(retryResponse, context);
            if (retryValidation.passed()) {
                return buildSuccessResult(retryResponse, startTime, true);
            }

            // Step 6: Both attempts failed validation — deliver fallback
            log.warn("AI response failed validation after retry: failures={}", retryValidation.failures());
            return buildFallbackResult(context.conversationType(), startTime, locale);

        } catch (AiProviderUnavailableException e) {
            // Provider exception → deliver fallback
            log.error("AI provider unavailable during orchestration: {}", e.getMessage());
            return buildFallbackResult(context.conversationType(), startTime, locale);
        } catch (Exception e) {
            // Any other unexpected exception → deliver fallback (never throw)
            log.error("Unexpected error during AI orchestration, delivering fallback", e);
            return buildFallbackResult(context.conversationType(), startTime, locale);
        }
    }

    /**
     * Extracts the locale from context, defaults to English.
     */
    private String getLocale(ConversationContext context) {
        if (context.fatherProfile() != null && context.fatherProfile().get("locale") != null) {
            return context.fatherProfile().get("locale").toString();
        }
        return "en";
    }

    // ===== Private helpers =====

    private CoachingContext buildCoachingContext(ConversationContext context, InboundMessageDto message) {
        ConversationType conversationType = parseConversationType(context.conversationType());
        return new CoachingContext(
                context.fatherId(),
                conversationType,
                message.content(),
                List.of(), // conversation history (simplified — populated from context)
                buildSystemPrompt(context),
                formatMemories(context),
                formatContextContent(context),
                "" // output instructions
        );
    }

    private CoachingContext buildRetryContext(ConversationContext context, InboundMessageDto message,
                                             List<String> validationFailures) {
        ConversationType conversationType = parseConversationType(context.conversationType());
        String correctionPrompt = buildSystemPrompt(context)
                + "\n\n[CORRECTION REQUIRED] Previous response failed validation: "
                + String.join("; ", validationFailures)
                + ". Please regenerate addressing these issues.";

        return new CoachingContext(
                context.fatherId(),
                conversationType,
                message.content(),
                List.of(),
                correctionPrompt,
                formatMemories(context),
                formatContextContent(context),
                ""
        );
    }

    private AiResult buildSuccessResult(CoachingResponse response, Instant startTime, boolean retried) {
        Duration latency = Duration.between(startTime, Instant.now());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model_used", response.model());
        metadata.put("provider", response.provider());
        metadata.put("latency_ms", latency.toMillis());
        metadata.put("input_tokens", response.inputTokens());
        metadata.put("output_tokens", response.outputTokens());

        String followUpAction = "NONE"; // default; CoachingResponse doesn't include action directly

        if (retried) {
            metadata.put("retried", true);
            return AiResult.retried(response.message(), followUpAction, metadata);
        }
        return AiResult.success(response.message(), followUpAction, metadata);
    }

    private AiResult buildFallbackResult(String conversationType, Instant startTime, String locale) {
        Duration latency = Duration.between(startTime, Instant.now());
        String fallbackContent;
        try {
            if (fallbackProvider instanceof FallbackResponseProviderImpl impl) {
                fallbackContent = impl.getForType(conversationType, locale);
            } else {
                fallbackContent = fallbackProvider.getForType(conversationType);
            }
        } catch (Exception e) {
            log.error("FallbackProvider.getForType failed, using generic fallback", e);
            try {
                if (fallbackProvider instanceof FallbackResponseProviderImpl impl) {
                    fallbackContent = impl.getGenericFallback(locale);
                } else {
                    fallbackContent = fallbackProvider.getGenericFallback();
                }
            } catch (Exception e2) {
                log.error("FallbackProvider.getGenericFallback also failed, using hardcoded last-resort", e2);
                fallbackContent = "he".equals(locale)
                        ? "סליחה, אני חווה קשיים טכניים. אנא נסה שוב מאוחר יותר."
                        : "Sorry, I'm experiencing technical difficulties. Please try again later.";
            }
        }
        log.info("Delivering fallback response for conversationType={} after {}ms",
                conversationType, latency.toMillis());
        return AiResult.fallback(fallbackContent);
    }

    private ConversationType parseConversationType(String type) {
        try {
            return ConversationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ConversationType.DAILY_COACHING;
        }
    }

    private String buildSystemPrompt(ConversationContext context) {
        // Get the father's language preference
        String locale = "en"; // default to English
        if (context.fatherProfile() != null && context.fatherProfile().get("locale") != null) {
            locale = context.fatherProfile().get("locale").toString();
        }

        String languageInstruction;
        if ("he".equals(locale)) {
            languageInstruction = "Respond in conversational Hebrew. ";
        } else {
            languageInstruction = "Respond in conversational English. ";
        }

        return "You are Dad Coach, a warm and supportive parenting coach for fathers. "
                + languageInstruction
                + "Conversation type: " + context.conversationType();
    }

    private String formatMemories(ConversationContext context) {
        if (context.rankedMemories().isEmpty()) {
            return null;
        }
        return "Memories: " + context.rankedMemories().toString();
    }

    private String formatContextContent(ConversationContext context) {
        StringBuilder sb = new StringBuilder();
        if (!context.children().isEmpty()) {
            sb.append("Children: ").append(context.children());
        }
        if (!context.activeGoals().isEmpty()) {
            sb.append(" Goals: ").append(context.activeGoals());
        }
        if (!context.activeMissions().isEmpty()) {
            sb.append(" Missions: ").append(context.activeMissions());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
