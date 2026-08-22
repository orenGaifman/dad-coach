package com.dadcoach.workflow.message;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderException;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Implementation of {@link MessageGenerator} that uses AI for natural language generation.
 * 
 * <p>This service generates personalized messages in the father's preferred language
 * (English or Hebrew) using AI. It follows the AI Usage Policy strictly:</p>
 * 
 * <ul>
 *   <li>✅ Only generates text content</li>
 *   <li>✅ Localizes based on language preference</li>
 *   <li>✅ Uses fallback templates when AI fails</li>
 *   <li>❌ Does NOT make state transition decisions</li>
 *   <li>❌ Does NOT interpret user messages</li>
 *   <li>❌ Does NOT decide what information to ask</li>
 * </ul>
 * 
 * <p>Implements Requirements 10.1, 10.3, 10.4, 10.5, 10.6 from the 
 * deterministic-workflow-engine spec.</p>
 * 
 * <p><strong>Timeout Handling (Requirement 10.6):</strong> AI generation has a 
 * 5-second timeout. If exceeded, fallback templates are used immediately.</p>
 * 
 * @see MessageGenerator
 * @see FallbackMessages
 */
@Service
public class MessageGeneratorImpl implements MessageGenerator {

    private static final Logger log = LoggerFactory.getLogger(MessageGeneratorImpl.class);
    
    /** Model to use for message generation (fast, low-cost model). */
    private static final String MESSAGE_GENERATION_MODEL = "claude-haiku-4-5-20251001";
    
    /** Temperature for message generation (slightly creative but consistent). */
    private static final double TEMPERATURE = 0.7;
    
    /** Top-p for message generation. */
    private static final double TOP_P = 0.9;
    
    /** Maximum tokens for generated messages. */
    private static final int MAX_TOKENS = 300;

    private final AiProvider aiProvider;
    private final FallbackMessages fallbackMessages;
    private final WorkflowMetrics workflowMetrics;
    private final ExecutorService executorService;

    @Value("${dadcoach.workflow.message-generation.timeout-ms:5000}")
    private long defaultTimeoutMs = DEFAULT_TIMEOUT_MS;

    /**
     * Creates a new MessageGeneratorImpl.
     * 
     * @param aiProvider the AI provider for text generation (uses Anthropic for Claude models)
     * @param fallbackMessages the fallback message provider
     * @param workflowMetrics the metrics collector for message generation monitoring (Requirement 16.2)
     */
    public MessageGeneratorImpl(
            @Qualifier("anthropicProvider") AiProvider aiProvider, 
            FallbackMessages fallbackMessages, 
            WorkflowMetrics workflowMetrics) {
        this.aiProvider = aiProvider;
        this.fallbackMessages = fallbackMessages;
        this.workflowMetrics = workflowMetrics;
        // Create a dedicated executor for async AI calls with bounded thread pool
        this.executorService = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "message-generator");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Generates a message using AI. This method may throw if AI generation fails.
     * For production use, prefer {@link #generateWithFallback} which handles errors
     * gracefully.</p>
     */
    @Override
    public String generate(MessageType type, MessageContext context) {
        Objects.requireNonNull(type, "MessageType must not be null");
        Objects.requireNonNull(context, "MessageContext must not be null");
        
        log.debug("Generating message type={} locale={} fatherName={}", 
            type, context.getLocale(), context.getFatherName());
        
        try {
            String prompt = buildPrompt(type, context);
            List<AiMessage> messages = List.of(
                AiMessage.system(prompt),
                AiMessage.user(buildUserInstruction(type, context))
            );
            
            AiProviderRequest request = new AiProviderRequest(
                MESSAGE_GENERATION_MODEL,
                messages,
                TEMPERATURE,
                TOP_P,
                MAX_TOKENS,
                false,  // No JSON mode - we want natural text
                Map.of(
                    "messageType", type.name(),
                    "locale", context.getLocale()
                )
            );
            
            AiProviderResponse response = aiProvider.sendPrompt(request);
            
            if (response.content() == null || response.content().isBlank()) {
                throw new MessageGenerationException(
                    "AI returned empty content",
                    type,
                    context.getLocale()
                );
            }
            
            log.debug("Generated message successfully: type={}, tokens={}", 
                type, response.totalTokens());
            
            return response.content().trim();
            
        } catch (AiProviderException e) {
            log.warn("AI provider error generating message type={}: {} - {}", 
                type, e.getErrorType(), e.getMessage());
            throw new MessageGenerationException(
                "AI generation failed: " + e.getMessage(),
                e,
                type,
                context.getLocale()
            );
        } catch (Exception e) {
            log.error("Unexpected error generating message type={}: {}", type, e.getMessage(), e);
            throw new MessageGenerationException(
                "Unexpected error during message generation: " + e.getMessage(),
                e,
                type,
                context.getLocale()
            );
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Generates a message using AI with automatic fallback on failure.
     * This method is guaranteed to return a non-null message string.</p>
     * 
     * <p>Records message generation metrics (Requirement 16.2):</p>
     * <ul>
     *   <li>Message generation latency timer</li>
     *   <li>AI vs fallback usage counters</li>
     * </ul>
     */
    @Override
    public String generateWithFallback(MessageType type, MessageContext context, long timeoutMs) {
        Objects.requireNonNull(type, "MessageType must not be null");
        Objects.requireNonNull(context, "MessageContext must not be null");
        
        if (timeoutMs <= 0) {
            timeoutMs = defaultTimeoutMs;
        }
        
        log.debug("Generating message with fallback: type={} timeout={}ms", type, timeoutMs);
        
        // Start timing for metrics (Requirement 16.2)
        long startNanos = System.nanoTime();
        
        // Submit AI generation task
        Future<String> future = executorService.submit(() -> generate(type, context));
        
        try {
            // Wait for AI generation with timeout
            String message = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            // Record successful AI generation metrics (Requirement 16.2)
            long durationNanos = System.nanoTime() - startNanos;
            workflowMetrics.recordMessageGenerationLatency(durationNanos, true);
            
            return message;
            
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Message generation timed out after {}ms for type={}. Using fallback.", 
                timeoutMs, type);
            return useFallbackWithMetrics(type, context, startNanos);
            
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("Message generation failed for type={}: {}. Using fallback.", 
                type, cause != null ? cause.getMessage() : e.getMessage());
            return useFallbackWithMetrics(type, context, startNanos);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Message generation interrupted for type={}. Using fallback.", type);
            return useFallbackWithMetrics(type, context, startNanos);
            
        } catch (Exception e) {
            log.error("Unexpected error during message generation for type={}: {}. Using fallback.", 
                type, e.getMessage(), e);
            return useFallbackWithMetrics(type, context, startNanos);
        }
    }

    /**
     * Use fallback template with metrics recording when AI generation fails.
     * Records the fallback usage metric and total latency (Requirement 16.2).
     *
     * @param type the message type
     * @param context the message context
     * @param startNanos the start time in nanoseconds for latency calculation
     * @return the fallback message
     */
    private String useFallbackWithMetrics(MessageType type, MessageContext context, long startNanos) {
        String message = useFallback(type, context);
        
        // Record fallback usage metrics (Requirement 16.2)
        long durationNanos = System.nanoTime() - startNanos;
        workflowMetrics.recordMessageGenerationLatency(durationNanos, false);
        
        return message;
    }

    /**
     * Use fallback template when AI generation fails.
     * 
     * <p>Per Requirement 10.4: "IF the Message_Generator fails or times out,
     * THE Workflow_Engine SHALL use a pre-written fallback message template
     * for that message type."</p>
     * 
     * <p>Per Requirement 10.6: "Message generation latency budget: 5 seconds maximum.
     * If exceeded, use fallback template immediately."</p>
     */
    private String useFallback(MessageType type, MessageContext context) {
        try {
            String message = fallbackMessages.getProcessed(type, context);
            log.warn("Using fallback template for message type={} locale={}", 
                type, context.getLocale());
            return message;
        } catch (Exception e) {
            log.error("Failed to get fallback template for type={}: {}", type, e.getMessage());
            // Ultimate fallback - never fail
            return context.isHebrew() 
                ? "מצטער, משהו השתבש. אנא נסה שוב."
                : "Sorry, something went wrong. Please try again.";
        }
    }

    /**
     * Build the system prompt for AI message generation.
     * 
     * <p>The prompt clearly instructs AI to ONLY generate text, not make decisions.
     * It includes all context data needed to personalize the message.</p>
     */
    private String buildPrompt(MessageType type, MessageContext context) {
        String language = context.isHebrew() ? "Hebrew" : "English";
        String languageInstruction = context.isHebrew() 
            ? "IMPORTANT: Respond ONLY in Hebrew (עברית). Use RTL-appropriate formatting."
            : "Respond in English.";
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are a message generator for Dad Coach, a parenting app that helps fathers 
            build habits of spending quality time with their children.
            
            YOUR ROLE: Generate a single, personalized message text. 
            
            CRITICAL RULES:
            - You ONLY generate natural language text
            - You do NOT make decisions about what the father should do
            - You do NOT recommend state transitions or next steps
            - You do NOT interpret user messages or extract information
            - You output ONLY the message text, nothing else
            - Keep messages warm, encouraging, and concise (1-3 sentences typically)
            
            %s
            
            MESSAGE TYPE: %s
            """.formatted(languageInstruction, type.name()));
        
        // Add context based on what's available
        if (context.getFatherName() != null) {
            prompt.append("\nFather's name: ").append(context.getFatherName());
        }
        if (context.getChildName() != null) {
            prompt.append("\nChild's name: ").append(context.getChildName());
        }
        if (context.getChildAge() != null) {
            prompt.append("\nChild's age: ").append(context.getChildAge());
        }
        if (context.getScheduledTimeFormatted() != null) {
            prompt.append("\nScheduled time: ").append(context.getScheduledTimeFormatted());
        }
        if (context.getStreakCount() != null) {
            prompt.append("\nCurrent streak: ").append(context.getStreakCount());
        }
        if (context.getCurrentBelt() != null) {
            prompt.append("\nCurrent belt: ").append(context.getCurrentBelt().name());
        }
        if (context.getBeltEarned() != null) {
            prompt.append("\nNewly earned belt: ").append(context.getBeltEarned().name());
        }
        if (context.getQualityTimeCount() != null) {
            prompt.append("\nTotal Quality Times completed: ").append(context.getQualityTimeCount());
        }
        if (context.hasTimeSlots()) {
            prompt.append("\nAvailable time slots: ")
                .append(context.getTimeSlots().size())
                .append(" options");
        }
        if (!context.getValidOptions().isEmpty()) {
            prompt.append("\nValid response options: ")
                .append(String.join(", ", context.getValidOptions()));
        }
        
        return prompt.toString();
    }

    /**
     * Build the user instruction that specifies what to generate.
     */
    private String buildUserInstruction(MessageType type, MessageContext context) {
        return switch (type) {
            case WELCOME_GREETING -> "Generate a warm welcome message for the father. " +
                "Explain that Dad Coach helps build quality time habits with children. " +
                "Ask if they're ready to get started.";
                
            case WELCOME_EXPLAIN -> "Explain how Dad Coach works: schedule quality time, " +
                "complete sessions, earn belt progression. Keep it simple and inviting. " +
                "End by asking if they're ready to schedule their first session.";
                
            case SCHEDULE_SLOTS -> "Present available time slots for scheduling quality time. " +
                "Be brief and encouraging.";
                
            case SCHEDULE_CONFIRM -> "Confirm the quality time is scheduled. " +
                "Include the time and child's name. Add encouragement.";
                
            case SCHEDULE_NO_SLOTS -> "Explain that no available time slots were found. " +
                "Suggest checking the calendar or trying again later. Be understanding.";
                
            case WAITING_REMINDER -> "Send a friendly morning reminder about today's " +
                "scheduled quality time. Include the child's name and time. Be encouraging.";
                
            case WAITING_SCHEDULE_INFO -> "Confirm the next scheduled quality time " +
                "with the child's name and time.";
                
            case FOLLOW_UP_QUESTION -> "Ask if the father completed their quality time " +
                "with their child. Keep it simple with clear yes/no expectation.";
                
            case FOLLOW_UP_COMPLETED -> "Celebrate the completion! " +
                (context.hasBeltEarned() 
                    ? "IMPORTANT: They earned a new belt - celebrate this achievement!" 
                    : "Mention their streak count and encourage them to keep going.");
                
            case FOLLOW_UP_MISSED -> "Be understanding that they didn't complete the session. " +
                "No judgment. Encourage them to try again soon.";
                
            case ACTIVITY_IDEAS -> "Introduce activity ideas for quality time. " +
                "Be brief - the actual ideas will be listed separately.";
                
            case DASHBOARD_SUMMARY -> "Provide a brief summary of their progress. " +
                "Mention belt, streak, and total sessions. Include dashboard link mention.";
                
            case CLARIFICATION -> "Ask for clarification. " +
                "Present the valid response options clearly.";
                
            case ERROR_GENERIC -> "Apologize for an error and ask them to try again. " +
                "Be brief and reassuring.";
                
            case ERROR_SCHEDULE_QUALITY_TIME -> "Apologize for having trouble finding available time slots. " +
                "Ask them to try again. Be understanding and helpful.";
                
            case ERROR_QUALITY_TIME_FOLLOW_UP -> "Apologize for the error and ask about their Quality Time completion. " +
                "Ask if they completed their session with their child. Be understanding.";
                
            case ERROR_WAITING -> "Apologize for the processing error and present available options. " +
                "Suggest checking schedule, getting activity ideas, or scheduling new Quality Time.";
                
            case PROCESSING -> "Let the father know you're still working on their request. " +
                "Be brief and reassuring that you'll get back to them shortly.";
        };
    }

    /**
     * Cleanup executor on shutdown.
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
