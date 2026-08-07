package com.dadcoach.ai.extraction;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.output.CompletedConversation;
import com.dadcoach.ai.output.MemoryExtractionOutput;
import com.dadcoach.ai.output.MemoryExtractionOutput.ExtractedMemory;
import com.dadcoach.ai.prompt.TokenBudgetManager;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.ai.routing.FallbackChain;
import com.dadcoach.ai.routing.ModelRouter;
import com.dadcoach.domain.conversation.ConversationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes completed conversations and produces structured memory recommendations.
 *
 * <p>The MemoryExtractor sends the conversation transcript to an AI model and parses
 * the structured JSON response into a list of {@link ExtractedMemory} objects.
 * The output is purely advisory — the application layer decides whether to persist these memories.
 *
 * <p>Key constraints enforced:
 * <ul>
 *   <li>Each memory content is limited to 500 characters</li>
 *   <li>importance_score must be between 1 and 10</li>
 *   <li>confidence_score must be between 0.0 and 1.0</li>
 *   <li>subject_type must be one of: child, father, family</li>
 *   <li>category must be one of the defined memory categories</li>
 *   <li>The extraction prompt must never exceed the token budget</li>
 * </ul>
 *
 * @see MemoryExtractionOutput
 * @see CompletedConversation
 */
public class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    /**
     * Maximum token budget for the entire extraction prompt (system + transcript).
     * This ensures we never exceed the model's context window.
     */
    static final int EXTRACTION_TOKEN_BUDGET = 3000;

    /**
     * Maximum tokens allocated for the system/instruction portion of the prompt.
     */
    static final int SYSTEM_PROMPT_TOKEN_BUDGET = 500;

    /**
     * Maximum tokens allocated for the conversation transcript.
     */
    static final int TRANSCRIPT_TOKEN_BUDGET = EXTRACTION_TOKEN_BUDGET - SYSTEM_PROMPT_TOKEN_BUDGET;

    static final int MAX_CONTENT_LENGTH = 500;
    static final int MIN_IMPORTANCE = 1;
    static final int MAX_IMPORTANCE = 10;
    static final double MIN_CONFIDENCE = 0.0;
    static final double MAX_CONFIDENCE = 1.0;

    static final Set<String> VALID_CATEGORIES = Set.of(
        "IDENTITY", "RELATIONSHIP", "PREFERENCE", "GOAL",
        "CHALLENGE", "MILESTONE", "CONTEXT", "CONVERSATION_SUMMARY"
    );

    static final Set<String> VALID_SUBJECT_TYPES = Set.of(
        "child", "father", "family"
    );

    private static final String EXTRACTION_SYSTEM_PROMPT = """
        You are an expert memory extraction system for a parenting coaching app.
        Analyze the following conversation between a father and his coaching assistant.
        Extract key memories that should be remembered for future coaching sessions.
        
        For each memory, output a JSON object with:
        - "category": one of IDENTITY, RELATIONSHIP, PREFERENCE, GOAL, CHALLENGE, MILESTONE, CONTEXT, CONVERSATION_SUMMARY
        - "content": the memory content (MAXIMUM 500 characters, be concise)
        - "importance_score": integer from 1 (low) to 10 (critical)
        - "confidence_score": float from 0.0 (uncertain) to 1.0 (certain)
        - "subject_type": one of "child", "father", or "family"
        
        Output a valid JSON array of memory objects. Only extract genuinely important memories.
        Do not invent information not present in the conversation.
        Respond ONLY with the JSON array, no other text.
        """;

    private final ModelRouter modelRouter;
    private final TokenBudgetManager tokenBudgetManager;
    private final ObjectMapper objectMapper;

    public MemoryExtractor(ModelRouter modelRouter) {
        this(modelRouter, new TokenBudgetManager(), new ObjectMapper());
    }

    public MemoryExtractor(ModelRouter modelRouter, TokenBudgetManager tokenBudgetManager, ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.tokenBudgetManager = tokenBudgetManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Extract memories from a completed conversation.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Build extraction prompt within token budget</li>
     *   <li>Route to AI model via ModelRouter</li>
     *   <li>Parse structured JSON response</li>
     *   <li>Validate and sanitize each extracted memory</li>
     *   <li>Return structured MemoryExtractionOutput</li>
     * </ol>
     *
     * @param conversation the completed conversation to extract memories from
     * @return structured memory extraction output (never null)
     */
    public MemoryExtractionOutput extract(CompletedConversation conversation) {
        log.debug("Starting memory extraction for conversation={}, father={}",
            conversation.conversationId(), conversation.fatherId());

        // Step 1: Build the extraction prompt within token budget
        List<AiMessage> messages = buildExtractionPrompt(conversation);

        // Step 2: Route to AI model (using DAILY_COACHING routing - extraction is low-cost)
        AiProviderRequest request = new AiProviderRequest(
            "default",
            messages,
            0.3, // Low temperature for structured output
            0.9,
            400,
            true, // JSON mode for structured output
            Map.of("fatherId", conversation.fatherId().toString())
        );

        FallbackChain.FallbackResult result;
        try {
            result = modelRouter.route(request, ConversationType.DAILY_COACHING);
        } catch (Exception e) {
            log.error("AI call failed for memory extraction, conversation={}: {}",
                conversation.conversationId(), e.getMessage());
            return emptyOutput(conversation, false);
        }

        AiProviderResponse response = result.response();

        // Step 3: Parse the AI response into structured memories
        List<ExtractedMemory> memories = parseAndValidateResponse(response.content());

        boolean valid = !memories.isEmpty();
        log.debug("Extracted {} memories from conversation={}", memories.size(), conversation.conversationId());

        return new MemoryExtractionOutput(
            memories,
            conversation.conversationId().toString(),
            response.model(),
            valid
        );
    }

    /**
     * Build the extraction prompt, ensuring it stays within the token budget.
     * The system prompt is fixed; the transcript is truncated if needed.
     */
    List<AiMessage> buildExtractionPrompt(CompletedConversation conversation) {
        // Ensure system prompt fits within its budget
        String systemPrompt = tokenBudgetManager.truncateToFit(
            EXTRACTION_SYSTEM_PROMPT, SYSTEM_PROMPT_TOKEN_BUDGET);

        // Format and truncate the conversation transcript
        String transcript = formatTranscript(conversation.messages());
        transcript = tokenBudgetManager.truncateToFit(transcript, TRANSCRIPT_TOKEN_BUDGET);

        return List.of(
            AiMessage.system(systemPrompt),
            AiMessage.user(transcript)
        );
    }

    /**
     * Get the total token count for an extraction prompt.
     * Useful for verifying the prompt stays within budget.
     */
    int countPromptTokens(List<AiMessage> messages) {
        int total = 0;
        for (AiMessage message : messages) {
            total += tokenBudgetManager.countTokens(message.content());
        }
        return total;
    }

    /**
     * Parse the AI response JSON and validate each memory.
     * Invalid memories are filtered out rather than causing the entire extraction to fail.
     */
    List<ExtractedMemory> parseAndValidateResponse(String content) {
        if (content == null || content.isBlank()) {
            log.warn("Empty response from AI for memory extraction");
            return List.of();
        }

        try {
            // Strip any markdown code block wrappers if present
            String jsonContent = stripMarkdownWrapper(content.trim());

            List<Map<String, Object>> rawMemories = objectMapper.readValue(
                jsonContent, new TypeReference<>() {});

            List<ExtractedMemory> validated = new ArrayList<>();
            for (Map<String, Object> raw : rawMemories) {
                ExtractedMemory memory = validateAndBuildMemory(raw);
                if (memory != null) {
                    validated.add(memory);
                }
            }
            return validated;

        } catch (Exception e) {
            log.warn("Failed to parse memory extraction response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Validate and build a single ExtractedMemory from raw parsed data.
     * Returns null if the memory is invalid and cannot be salvaged.
     */
    ExtractedMemory validateAndBuildMemory(Map<String, Object> raw) {
        try {
            String category = normalizeCategory(getString(raw, "category"));
            String content = getString(raw, "content");
            int importanceScore = getInt(raw, "importance_score");
            double confidenceScore = getDouble(raw, "confidence_score");
            String subjectType = normalizeSubjectType(getString(raw, "subject_type"));

            // Validate category
            if (!VALID_CATEGORIES.contains(category)) {
                log.debug("Invalid category '{}', skipping memory", category);
                return null;
            }

            // Validate and truncate content
            if (content == null || content.isBlank()) {
                log.debug("Empty content, skipping memory");
                return null;
            }
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }

            // Clamp importance score
            importanceScore = Math.max(MIN_IMPORTANCE, Math.min(MAX_IMPORTANCE, importanceScore));

            // Clamp confidence score
            confidenceScore = Math.max(MIN_CONFIDENCE, Math.min(MAX_CONFIDENCE, confidenceScore));

            // Validate subject type
            if (!VALID_SUBJECT_TYPES.contains(subjectType)) {
                log.debug("Invalid subject_type '{}', defaulting to 'family'", subjectType);
                subjectType = "family";
            }

            return new ExtractedMemory(category, content, importanceScore, confidenceScore, subjectType);

        } catch (Exception e) {
            log.debug("Failed to build memory from raw data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Format the conversation transcript for inclusion in the extraction prompt.
     */
    String formatTranscript(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AiMessage msg : messages) {
            String role = switch (msg.role()) {
                case "user" -> "Father";
                case "assistant" -> "Coach";
                case "system" -> "System";
                default -> msg.role();
            };
            sb.append(role).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    private MemoryExtractionOutput emptyOutput(CompletedConversation conversation, boolean valid) {
        return new MemoryExtractionOutput(
            List.of(),
            conversation.conversationId().toString(),
            "none",
            valid
        );
    }

    private String stripMarkdownWrapper(String content) {
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    private String normalizeCategory(String category) {
        if (category == null) return "";
        return category.toUpperCase().trim();
    }

    private String normalizeSubjectType(String subjectType) {
        if (subjectType == null) return "";
        return subjectType.toLowerCase().trim();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
