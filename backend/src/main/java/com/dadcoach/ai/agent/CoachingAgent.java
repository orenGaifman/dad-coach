package com.dadcoach.ai.agent;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.WorkflowState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * The main AI Coaching Agent that processes user messages using Claude.
 * 
 * <p>This agent replaces the regex-based state machine with natural language
 * understanding. It:</p>
 * <ol>
 *   <li>Receives a message from WhatsApp</li>
 *   <li>Loads the father's context (state, children, quality times, etc.)</li>
 *   <li>Builds a prompt for Claude with available tools</li>
 *   <li>Calls Claude to understand intent and select a tool</li>
 *   <li>Executes the selected tool</li>
 *   <li>Returns the response to send back to WhatsApp</li>
 * </ol>
 */
@Component
public class CoachingAgent {
    
    private static final Logger log = LoggerFactory.getLogger(CoachingAgent.class);
    
    private static final double TEMPERATURE = 0.3; // Low temperature for consistent behavior
    private static final int MAX_TOKENS = 500;     // Keep responses short for WhatsApp
    private static final int MAX_CONVERSATION_HISTORY = 5; // Last 5 turns
    
    private final AiProvider aiProvider;
    private final AgentPromptBuilder promptBuilder;
    private final ToolExecutor toolExecutor;
    private final SystemStateLoader systemStateLoader;
    private final ObjectMapper objectMapper;
    
    @Value("${ai.agent.model:claude-sonnet-5}")
    private String modelName;
    
    @Value("${ai.agent.enabled:true}")
    private boolean agentEnabled;
    
    public CoachingAgent(
            @Qualifier("anthropicProvider") AiProvider aiProvider,
            AgentPromptBuilder promptBuilder,
            ToolExecutor toolExecutor,
            SystemStateLoader systemStateLoader,
            ObjectMapper objectMapper
    ) {
        this.aiProvider = aiProvider;
        this.promptBuilder = promptBuilder;
        this.toolExecutor = toolExecutor;
        this.systemStateLoader = systemStateLoader;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process an incoming message and return the response to send back.
     * 
     * @param fatherId the UUID of the father sending the message
     * @param inboundMessage the text message received from WhatsApp
     * @param currentState the current workflow state (optional, loaded from system state if null)
     * @param conversationHistory recent conversation messages (optional)
     * @return the agent response containing message to send and state transition
     */
    public AgentResponse processMessage(
            UUID fatherId,
            String inboundMessage,
            WorkflowState currentState,
            List<AgentContext.ConversationTurn> conversationHistory
    ) {
        log.info("Processing message for father: {}, message: {}", fatherId, truncate(inboundMessage, 50));
        
        if (!agentEnabled) {
            log.warn("AI Agent is disabled, returning fallback response");
            return AgentResponse.fallback("המערכת זמינה בקרוב. נסה שוב מאוחר יותר.");
        }
        
        try {
            // 1. Load system state
            SystemState systemState = loadSystemState(fatherId);
            
            // 2. Determine current state
            WorkflowState state = currentState;
            if (state == null && systemState != null) {
                state = systemState.workflowState();
            }
            if (state == null) {
                state = WorkflowState.WELCOME; // Default to welcome for new users
            }
            
            // 3. Get father name
            String fatherName = getFatherName(systemState);
            
            // 4. Build context
            List<AgentTool> availableTools = getToolsForState(state, systemState);
            AgentContext context = AgentContext.builder()
                .fatherId(fatherId)
                .fatherName(fatherName)
                .currentState(state)
                .inboundMessage(inboundMessage)
                .systemState(systemState)
                .conversationHistory(limitHistory(conversationHistory))
                .availableTools(availableTools)
                .build();
            
            // 5. Build prompts
            String systemPrompt = promptBuilder.buildSystemPrompt(context);
            String userPrompt = promptBuilder.buildUserPrompt(context);
            
            // 6. Call AI
            AiProviderResponse aiResponse = callAiProvider(systemPrompt, userPrompt, fatherId);
            
            // 7. Parse AI response
            AiDecision decision = parseAiResponse(aiResponse.content());
            log.info("AI decision: tool={}, params={}", decision.tool(), decision.parameters());
            
            // 8. Execute tool
            AgentToolResult toolResult = toolExecutor.execute(decision.tool(), decision.parameters(), context);
            
            // 9. Determine response message
            // For tools that generate URLs/links, ALWAYS use tool result (AI can't know the URL)
            String responseMessage;
            boolean useToolResponse = Set.of(
                "connect_calendar", 
                "get_dashboard_link", 
                "show_progress",
                "schedule_quality_time"
            ).contains(decision.tool());
            
            if (useToolResponse && toolResult.responseMessage() != null && !toolResult.responseMessage().isEmpty()) {
                responseMessage = toolResult.responseMessage();
            } else if (decision.response() != null && !decision.response().isEmpty()) {
                responseMessage = decision.response();
            } else if (toolResult.responseMessage() != null && !toolResult.responseMessage().isEmpty()) {
                responseMessage = toolResult.responseMessage();
            } else {
                responseMessage = "לא הבנתי. אפשר לנסות שוב?";
            }
            
            // 10. Return response
            return new AgentResponse(
                responseMessage,
                toolResult.newState(),
                decision.tool(),
                decision.parameters(),
                true,
                null
            );
            
        } catch (Exception e) {
            log.error("Error processing message for father: {}", fatherId, e);
            return AgentResponse.error("משהו השתבש. אפשר לנסות שוב?");
        }
    }
    
    /**
     * Check if the agent is enabled and ready to process messages.
     */
    public boolean isEnabled() {
        return agentEnabled && aiProvider != null;
    }
    
    // ─── Private Methods ────────────────────────────────────────────────────
    
    private SystemState loadSystemState(UUID fatherId) {
        try {
            return systemStateLoader.loadState(fatherId);
        } catch (Exception e) {
            log.warn("Failed to load system state for father: {}", fatherId, e);
            return null;
        }
    }
    
    private String getFatherName(SystemState systemState) {
        if (systemState != null && systemState.fatherProfile() != null) {
            return systemState.fatherProfile().displayName();
        }
        return null;
    }
    
    private List<AgentTool> getToolsForState(WorkflowState state, SystemState systemState) {
        // Check if there's an active quality time
        boolean hasScheduledQualityTime = systemState != null && 
            systemState.getNextScheduledQualityTime() != null;
        
        return switch (state) {
            case WELCOME, SCHEDULE_QUALITY_TIME -> AgentPromptBuilder.getSchedulingTools();
            case WAITING, QUALITY_TIME_FOLLOW_UP -> hasScheduledQualityTime 
                ? AgentPromptBuilder.getActiveQualityTimeTools()
                : AgentPromptBuilder.getSchedulingTools();
            default -> AgentPromptBuilder.getDefaultTools();
        };
    }
    
    private List<AgentContext.ConversationTurn> limitHistory(List<AgentContext.ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (history.size() <= MAX_CONVERSATION_HISTORY * 2) {
            return history;
        }
        return history.subList(history.size() - MAX_CONVERSATION_HISTORY * 2, history.size());
    }
    
    private AiProviderResponse callAiProvider(String systemPrompt, String userPrompt, UUID fatherId) {
        List<AiMessage> messages = List.of(
            AiMessage.system(systemPrompt),
            AiMessage.user(userPrompt)
        );
        
        Map<String, String> metadata = Map.of(
            "fatherId", fatherId.toString(),
            "component", "CoachingAgent"
        );
        
        AiProviderRequest request = new AiProviderRequest(
            modelName,
            messages,
            TEMPERATURE,
            1.0,
            MAX_TOKENS,
            true, // JSON mode
            metadata
        );
        
        return aiProvider.sendPrompt(request);
    }
    
    private AiDecision parseAiResponse(String content) {
        if (content == null || content.isBlank()) {
            return AiDecision.fallback();
        }
        
        try {
            // Clean up the response - remove markdown code blocks if present
            String cleaned = content.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
            
            // Parse JSON
            JsonNode root = objectMapper.readTree(cleaned);
            
            String tool = root.has("tool") ? root.get("tool").asText() : "clarify";
            String response = root.has("response") ? root.get("response").asText() : null;
            
            Map<String, Object> parameters = new HashMap<>();
            if (root.has("parameters")) {
                JsonNode paramsNode = root.get("parameters");
                Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode value = field.getValue();
                    if (value.isInt()) {
                        parameters.put(field.getKey(), value.asInt());
                    } else if (value.isDouble()) {
                        parameters.put(field.getKey(), value.asDouble());
                    } else if (value.isBoolean()) {
                        parameters.put(field.getKey(), value.asBoolean());
                    } else {
                        parameters.put(field.getKey(), value.asText());
                    }
                }
            }
            
            return new AiDecision(tool, parameters, response);
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI response as JSON: {}", content, e);
            return AiDecision.fallback();
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    // ─── Inner Classes ────────────────────────────────────────────────────
    
    /**
     * Represents the AI's decision on which tool to use.
     */
    public record AiDecision(
        String tool,
        Map<String, Object> parameters,
        String response
    ) {
        public static AiDecision fallback() {
            return new AiDecision("clarify", Map.of("question", "לא הבנתי. אפשר להסביר שוב?"), null);
        }
    }
    
    /**
     * The response from the coaching agent.
     */
    public record AgentResponse(
        String message,
        WorkflowState newState,
        String toolUsed,
        Map<String, Object> parameters,
        boolean success,
        String errorMessage
    ) {
        public static AgentResponse fallback(String message) {
            return new AgentResponse(message, null, "fallback", Map.of(), true, null);
        }
        
        public static AgentResponse error(String message) {
            return new AgentResponse(message, null, null, Map.of(), false, message);
        }
        
        public boolean hasStateTransition() {
            return newState != null;
        }
    }
}
