package com.dadcoach.workflow.message;

/**
 * Interface for generating natural language messages using AI.
 * 
 * <p>This service is responsible ONLY for generating text content. It receives
 * structured context and produces localized messages in the father's preferred
 * language (English or Hebrew). Per the AI Usage Policy, this service:</p>
 * 
 * <ul>
 *   <li>✅ Generates natural language messages</li>
 *   <li>✅ Localizes content based on language preference</li>
 *   <li>✅ Personalizes messages using context data</li>
 *   <li>❌ Does NOT decide state transitions</li>
 *   <li>❌ Does NOT decide what information to ask for</li>
 *   <li>❌ Does NOT decide whether Quality Time was completed</li>
 *   <li>❌ Does NOT decide which time slots to suggest</li>
 *   <li>❌ Does NOT make any system state determinations</li>
 * </ul>
 * 
 * <p>Implements Requirement 10.1 from the deterministic-workflow-engine spec:
 * "THE Message_Generator service SHALL only generate text content."</p>
 * 
 * <p><strong>Fallback Strategy (Requirement 10.4):</strong> If AI generation fails
 * or times out, the implementation uses pre-written fallback message templates
 * stored in the message_templates table. Every {@link MessageType} has a
 * corresponding fallback template in both English and Hebrew.</p>
 * 
 * <p><strong>Performance Budget (Requirement 10.6):</strong> Message generation
 * has a 5-second maximum latency. If exceeded, fallback templates are used
 * immediately.</p>
 * 
 * <p><strong>AI Usage Budget (Requirement 10.9):</strong> Maximum 2 AI calls
 * per user per day. If exceeded, templates are used for remaining interactions.</p>
 * 
 * @see MessageType
 * @see MessageContext
 * @see MessageTemplate
 */
public interface MessageGenerator {

    /**
     * Default timeout in milliseconds for AI message generation.
     * Per Requirement 10.6, messages must complete within 5 seconds.
     */
    long DEFAULT_TIMEOUT_MS = 5000L;

    /**
     * Generates a message of the specified type using AI.
     * 
     * <p>This method attempts AI-powered message generation. If the AI
     * generation fails for any reason (timeout, error, rate limit),
     * a {@link MessageGenerationException} is thrown.</p>
     * 
     * <p>For production use, prefer {@link #generateWithFallback} which
     * gracefully handles failures by returning fallback templates.</p>
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * MessageContext context = MessageContext.builder("en", "David")
     *     .childName("Maya")
     *     .scheduledStart(Instant.now().plus(1, ChronoUnit.HOURS))
     *     .build();
     * 
     * String message = messageGenerator.generate(MessageType.SCHEDULE_CONFIRM, context);
     * }</pre>
     * 
     * @param type the message type determining the template and structure
     * @param context the message context containing required data fields
     *                and the father's language preference
     * @return the generated message text in the father's preferred language
     * @throws MessageGenerationException if AI generation fails or times out
     * @throws NullPointerException if type or context is null
     */
    String generate(MessageType type, MessageContext context);

    /**
     * Generates a message with automatic fallback on failure.
     * 
     * <p>This method first attempts AI-powered message generation within the
     * specified timeout. If AI generation fails for any reason (timeout, error,
     * rate limit exceeded), it automatically falls back to a pre-written template
     * from the message_templates table.</p>
     * 
     * <p>Per Requirement 10.4: "IF the Message_Generator fails or times out,
     * THE Workflow_Engine SHALL use a pre-written fallback message template
     * for that message type."</p>
     * 
     * <p>This method is guaranteed to return a non-null message string. It will
     * never throw an exception for generation failures, making it safe for
     * production use where message delivery is critical.</p>
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * MessageContext context = MessageContext.builder("he", "דוד")
     *     .childName("מאיה")
     *     .currentStreak(5)
     *     .build();
     * 
     * // Use default 5-second timeout
     * String message = messageGenerator.generateWithFallback(
     *     MessageType.FOLLOW_UP_COMPLETED, 
     *     context, 
     *     MessageGenerator.DEFAULT_TIMEOUT_MS
     * );
     * }</pre>
     * 
     * @param type the message type determining the template and structure
     * @param context the message context containing required data fields
     *                and the father's language preference
     * @param timeoutMs maximum time in milliseconds to wait for AI generation
     *                  (5000ms recommended per Requirement 10.6)
     * @return the generated message text in the father's preferred language,
     *         either from AI or from fallback template
     * @throws NullPointerException if type or context is null
     */
    String generateWithFallback(MessageType type, MessageContext context, long timeoutMs);
}
