package com.dadcoach.ai.agent;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.WelcomeStep;
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
    private static final int MAX_TOKENS = 1000;    // Increased from 500 to prevent JSON truncation with Hebrew/emoji
    private static final int MAX_CONVERSATION_HISTORY = 10; // Last 10 turns - more context for smarter responses
    private static final int MAX_CLARIFY_RETRIES = 1; // Max retries when AI wants to clarify
    
    private final AiProvider aiProvider;
    private final AgentPromptBuilder promptBuilder;
    private final ToolExecutor toolExecutor;
    private final SystemStateLoader systemStateLoader;
    private final ObjectMapper objectMapper;
    private final ToolWishlistService toolWishlistService;
    private final StateGoalResolver stateGoalResolver;
    
    @Value("${ai.agent.model:claude-sonnet-5}")
    private String modelName;
    
    @Value("${ai.agent.enabled:true}")
    private boolean agentEnabled;
    
    public CoachingAgent(
            @Qualifier("anthropicProvider") AiProvider aiProvider,
            AgentPromptBuilder promptBuilder,
            ToolExecutor toolExecutor,
            SystemStateLoader systemStateLoader,
            ObjectMapper objectMapper,
            ToolWishlistService toolWishlistService,
            StateGoalResolver stateGoalResolver
    ) {
        this.aiProvider = aiProvider;
        this.promptBuilder = promptBuilder;
        this.toolExecutor = toolExecutor;
        this.systemStateLoader = systemStateLoader;
        this.objectMapper = objectMapper;
        this.toolWishlistService = toolWishlistService;
        this.stateGoalResolver = stateGoalResolver;
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
            
            // NOTE: Calendar connection is handled during web onboarding, not WhatsApp.
            // The AI should proceed with the conversation regardless of calendar status.
            
            // 4. Load available calendar slots (for smarter scheduling suggestions)
            List<com.dadcoach.systemstate.AvailableSlot> availableSlots = loadAvailableSlots(fatherId);
            
            // 5. Resolve the proactive state goal (what the user should do next)
            StateGoal stateGoal = stateGoalResolver.resolve(systemState);
            log.info("Resolved state goal: type={}, priority={}, userIsOnTrack={}", 
                stateGoal.priorityGoal() != null ? stateGoal.priorityGoal().type() : "NONE",
                stateGoal.priorityGoal() != null ? stateGoal.priorityGoal().priority() : "NONE",
                stateGoal.userIsOnTrack());
            
            // 6. Build context
            List<AgentTool> availableTools = getToolsForState(state, systemState);
            AgentContext context = AgentContext.builder()
                .fatherId(fatherId)
                .fatherName(fatherName)
                .currentState(state)
                .welcomeStep(getWelcomeStep(systemState))
                .inboundMessage(inboundMessage)
                .systemState(systemState)
                .conversationHistory(limitHistory(conversationHistory))
                .availableTools(availableTools)
                .availableSlots(availableSlots)
                .stateGoal(stateGoal)
                .build();
            
            // 6. Build prompts
            String systemPrompt = promptBuilder.buildSystemPrompt(context);
            String userPrompt = promptBuilder.buildUserPrompt(context);
            
            // 7. Call AI
            AiProviderResponse aiResponse = callAiProvider(systemPrompt, userPrompt, fatherId);
            
            // 8. Parse AI response
            AiDecision decision = parseAiResponse(aiResponse.content(), fatherId, inboundMessage, context);
            log.info("AI decision: tool={}, params={}", decision.tool(), decision.parameters());
            
            // ═══════════════════════════════════════════════════════════════════════
            // FEATURE 2: SMART FALLBACK WHEN AI CHOOSES "CLARIFY"
            // Instead of immediately saying "I don't understand", try a second AI call
            // with enhanced context to actually understand the user's intent.
            // ═══════════════════════════════════════════════════════════════════════
            if ("clarify".equals(decision.tool())) {
                log.info("AI chose clarify tool, attempting smart fallback for father: {}", fatherId);
                
                AiDecision smartDecision = attemptSmartFallback(context, fatherId);
                if (smartDecision != null && !"clarify".equals(smartDecision.tool())) {
                    log.info("Smart fallback succeeded: new tool={}", smartDecision.tool());
                    decision = smartDecision;
                } else {
                    log.info("Smart fallback still chose clarify, using intelligent acknowledgment");
                    // Try to give a more helpful response based on context
                    String intelligentResponse = buildIntelligentClarifyResponse(context);
                    if (intelligentResponse != null) {
                        decision = new AiDecision("acknowledge", Map.of(), intelligentResponse);
                    }
                }
            }
            // ═══════════════════════════════════════════════════════════════════════
            
            // 8. Validate and execute tool
            AgentToolResult toolResult;
            if (!toolExecutor.canExecute(decision.tool())) {
                log.warn("AI requested unknown tool: {}. Falling back to show_help.", decision.tool());
                // Record the unknown tool as a wish for future development
                recordUnknownToolWish(decision.tool(), inboundMessage, context);
                // Fall back to show_help with a friendly message
                toolResult = toolExecutor.execute("show_help", Map.of(), context);
            } else {
                toolResult = toolExecutor.execute(decision.tool(), decision.parameters(), context);
            }
            
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
                // Improved fallback - offer helpful options instead of generic error
                responseMessage = "היי! 😊 אשמח לעזור.\n\n" +
                    "אפשר לכתוב:\n" +
                    "🎯 \"קבע זמן\" - לקבוע זמן איכות\n" +
                    "💡 \"רעיונות\" - לקבל רעיונות לפעילויות\n" +
                    "📊 \"התקדמות\" - לראות את ההתקדמות שלך\n\n" +
                    "מה תרצה לעשות?";
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
    
    /**
     * Load available calendar slots for scheduling suggestions.
     * Returns empty list if calendar is not connected or loading fails.
     */
    private List<com.dadcoach.systemstate.AvailableSlot> loadAvailableSlots(UUID fatherId) {
        try {
            return systemStateLoader.loadAvailableSlots(fatherId, 7);
        } catch (Exception e) {
            log.debug("Could not load available slots for father {}: {}", fatherId, e.getMessage());
            return List.of();
        }
    }
    
    private String getFatherName(SystemState systemState) {
        if (systemState != null && systemState.fatherProfile() != null) {
            return systemState.fatherProfile().displayName();
        }
        return null;
    }
    
    /**
     * Get the current welcome step from system state.
     * Returns null if not in WELCOME state or if welcome step is not set.
     */
    private WelcomeStep getWelcomeStep(SystemState systemState) {
        if (systemState == null || systemState.fatherProfile() == null) {
            return WelcomeStep.INTRO; // Default for new users
        }
        
        // Check if we're in WELCOME state
        WorkflowState currentState = systemState.workflowState();
        if (currentState != WorkflowState.WELCOME) {
            return null; // Not in welcome flow
        }
        
        // Get welcome step from father profile
        WelcomeStep step = systemState.fatherProfile().welcomeStep();
        return step != null ? step : WelcomeStep.INTRO;
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
    
    private AiDecision parseAiResponse(String content, UUID fatherId, String originalMessage, AgentContext context) {
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
            
            // Extract tool_wish if present (ML-style learning mechanism)
            if (root.has("tool_wish") && fatherId != null) {
                extractAndRecordToolWish(root.get("tool_wish"), fatherId, originalMessage, context);
            }
            
            return new AiDecision(tool, parameters, response);
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI response as JSON: {}", content, e);
            return AiDecision.fallback();
        }
    }
    
    /**
     * Extract and record a tool wish from the AI response.
     * 
     * <p>When the AI identifies a user need that doesn't match any existing tool,
     * it can suggest a new tool via the tool_wish field. This enables ML-style
     * learning where we track what capabilities users are asking for.</p>
     */
    private void extractAndRecordToolWish(JsonNode wishNode, UUID fatherId, String originalMessage, AgentContext context) {
        try {
            String suggestedName = wishNode.has("suggested_name") 
                ? wishNode.get("suggested_name").asText() : null;
            String userNeed = wishNode.has("user_need") 
                ? wishNode.get("user_need").asText() : null;
            String suggestedCapability = wishNode.has("suggested_capability") 
                ? wishNode.get("suggested_capability").asText() : null;
            
            if (suggestedName != null && !suggestedName.isBlank()) {
                // Get the database father ID (Long) from context if available
                Long fatherDbId = getFatherDbId(context);
                
                toolWishlistService.recordWish(
                    suggestedName,
                    userNeed,
                    suggestedCapability,
                    originalMessage,
                    fatherDbId
                );
                log.info("Recorded tool wish: name={}, fatherId={}", suggestedName, fatherDbId);
            }
        } catch (Exception e) {
            // Don't fail the main flow if wish recording fails
            log.warn("Failed to record tool wish: {}", e.getMessage());
        }
    }
    
    /**
     * Get the father's database ID (Long) from the context.
     */
    private Long getFatherDbId(AgentContext context) {
        if (context != null && context.systemState() != null && context.systemState().fatherProfile() != null) {
            return context.systemState().fatherProfile().fatherId();
        }
        return null;
    }
    
    /**
     * Record an unknown tool that the AI tried to use.
     * 
     * <p>When the AI hallucinates a tool that doesn't exist, we record it as a wish.
     * This helps identify patterns where we might need to add new tools.</p>
     */
    private void recordUnknownToolWish(String unknownTool, String userMessage, AgentContext context) {
        try {
            Long fatherDbId = getFatherDbId(context);
            toolWishlistService.recordWish(
                unknownTool,
                "AI tried to use non-existent tool",
                "Unknown - AI hallucinated this tool name",
                userMessage,
                fatherDbId
            );
            log.info("Recorded unknown tool as wish: name={}, fatherId={}", unknownTool, fatherDbId);
        } catch (Exception e) {
            // Don't fail the main flow if wish recording fails
            log.warn("Failed to record unknown tool wish: {}", e.getMessage());
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Smart Fallback Helpers
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Attempt a smarter fallback when the AI initially chose "clarify".
     * This makes a second AI call with enhanced context to better understand intent.
     */
    private AiDecision attemptSmartFallback(AgentContext context, UUID fatherId) {
        try {
            // Build an enhanced prompt that asks AI to reconsider with explicit guidance
            String enhancedSystemPrompt = buildSmartFallbackSystemPrompt(context);
            String enhancedUserPrompt = buildSmartFallbackUserPrompt(context);
            
            // Make the second AI call
            AiProviderResponse retryResponse = callAiProvider(enhancedSystemPrompt, enhancedUserPrompt, fatherId);
            
            return parseAiResponse(retryResponse.content(), fatherId, context.inboundMessage(), context);
            
        } catch (Exception e) {
            log.warn("Smart fallback failed for father {}: {}", fatherId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Build the system prompt for the smart fallback attempt.
     */
    private String buildSmartFallbackSystemPrompt(AgentContext context) {
        return """
            אתה מאמן הורות חכם. המשימה שלך היא להבין מה המשתמש באמת רוצה, גם כשההודעה קצרה או לא ברורה.
            
            ## הקשר נוכחי
            %s
            
            ## כללים חשובים
            1. **הודעות קצרות כמו "כן", "אוקי", "סבבה", "בסדר", "יאללה", "טוב" = הסכמה/אישור**
               - אם ההודעה האחרונה שלך שאלה משהו - המשתמש מסכים
               - אם קבעת זמן איכות - המשתמש מאשר
            
            2. **הודעות "כבר עשיתי" = המשתמש כבר ביצע את הפעולה המבוקשת!**
               - "כבר עזינן", "כבר עזבתי", "כבר", "עשיתי", "סיימתי" = האב כבר עשה מה שביקשת
               - **התעלם מהבקשה הקודמת והמשך הלאה בתהליך**
               - אם ביקשת לחבר יומן והאב אומר "כבר" - היומן מחובר, המשך לשלב הבא!
               - אם ביקשת לקבוע זמן והאב אומר "כבר" - הזמן נקבע, תן פידבק חיובי!
               
            3. **הודעות שמתייחסות להקשר**
               - "מה להסביר?" = בלבול, תן סיכום ברור של המצב
               - "מה קורה?" = רוצה לראות התקדמות
               - "אבל..." = יש התנגדות או שאלה, תעזור להבין
               
            4. **לעולם אל תשתמש ב-clarify אלא אם באמת אין דרך להבין**
            
            ## מה לעשות
            - אם המשתמש אומר "כבר" או "עשיתי" - השתמש ב-acknowledge ותמשיך לשלב הבא
            - אם המשתמש אישר משהו - השתמש ב-acknowledge
            - אם רוצה לראות מצב - השתמש ב-show_progress
            - אם יש בלבול - תן סיכום ברור וקצר של איפה אנחנו ומה הצעד הבא
            
            ## פורמט התשובה
            ```json
            {
              "tool": "שם_הכלי",
              "parameters": {},
              "response": "תשובה חמה ומועילה"
            }
            ```
            """.formatted(context.buildContextSummary());
    }
    
    /**
     * Build the user prompt for the smart fallback attempt.
     */
    private String buildSmartFallbackUserPrompt(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        
        // Add recent conversation for context
        String history = context.buildConversationHistory();
        if (!history.isEmpty()) {
            sb.append("היסטוריית שיחה אחרונה:\n").append(history).append("\n\n");
        }
        
        sb.append("הודעה שצריך להבין: \"").append(context.inboundMessage()).append("\"\n\n");
        sb.append("ניסיון ראשון בחר clarify. נסה להבין מה המשתמש באמת רוצה ותבחר כלי מתאים.");
        
        return sb.toString();
    }
    
    /**
     * Build an intelligent response when we still can't understand,
     * instead of generic "I don't understand".
     */
    private String buildIntelligentClarifyResponse(AgentContext context) {
        // Check conversation history to understand context
        List<AgentContext.ConversationTurn> history = context.conversationHistory();
        String message = context.inboundMessage();
        String messageLower = message.toLowerCase();
        
        // Common acknowledgment patterns (text)
        Set<String> textAcknowledgments = Set.of(
            // Affirmative responses
            "כן", "אוקי", "סבבה", "בסדר", "יאללה", "טוב", "נשמע טוב", 
            "מעולה", "אחלה", "תודה", "תנקס", "ok", "yes", "sure", "yep",
            "בטח", "נכון", "מסכים", "קדימה", "יופי", "אוקיי", "קול",
            // "Already done" patterns (Hebrew slang) - user saying they already did something
            "כבר עשיתי", "כבר עזבתי", "כבר עזינו", "כבר עזינן", "עשיתי",
            "כבר", "עשוי", "בוצע", "סיימתי", "גמרתי", "נעשה", "הסתדר",
            "סגור", "סגרתי", "סדרתי", "מוכן", "מסודר",
            // Confusion/request for context
            "אבל", "למה", "רגע"
        );
        
        // Common acknowledgment emojis
        Set<String> emojiAcknowledgments = Set.of(
            "👍", "✅", "🙏", "👌", "💪", "🎉", "😊", "🙂", "👏", "❤️",
            "💙", "🔥", "✔️", "☑️", "🤝", "😁", "😄", "🥳", "💯"
        );
        
        // Check if it's a text acknowledgment
        for (String ack : textAcknowledgments) {
            if (messageLower.contains(ack)) {
                return buildAcknowledgmentResponse(context);
            }
        }
        
        // Check if it's an emoji acknowledgment
        for (String emoji : emojiAcknowledgments) {
            if (message.contains(emoji)) {
                return buildAcknowledgmentResponse(context);
            }
        }
        
        // If not a known acknowledgment, ask AI to determine intent
        String aiResponse = askAiForIntentClassification(message, context.fatherId());
        if (aiResponse != null) {
            if ("acknowledgment".equals(aiResponse)) {
                return buildAcknowledgmentResponse(context);
            } else if ("question".equals(aiResponse)) {
                return buildStatusSummary(context);
            }
            // For other intents, let the original flow handle it
        }
        
        // Check if asking for clarification about something bot said
        if (messageLower.contains("מה") && (messageLower.contains("להסביר") || messageLower.contains("זה"))) {
            return buildStatusSummary(context);
        }
        
        // Default - instead of generic "I don't understand", offer helpful options
        // This is the KEY FIX for Bug 5: never return null, always give helpful context
        return buildHelpfulFallbackResponse(context);
    }
    
    /**
     * Build a helpful fallback response instead of generic "I don't understand".
     * This provides context-aware options based on the user's current state.
     */
    private String buildHelpfulFallbackResponse(AgentContext context) {
        SystemState state = context.systemState();
        StringBuilder sb = new StringBuilder();
        
        sb.append("היי! 😊 אשמח לעזור.\n\n");
        
        // Show what we can do based on context
        sb.append("אלו הדברים שאני יכול לעזור בהם:\n\n");
        
        // Check if has scheduled QT
        if (state != null && state.getNextScheduledQualityTime() != null) {
            var qt = state.getNextScheduledQualityTime();
            sb.append("📅 יש לך זמן איכות מתוכנן עם ").append(qt.childName()).append("\n");
            sb.append("   • לשנות את הזמן? כתוב \"שנה זמן\"\n");
            sb.append("   • רעיונות לפעילויות? כתוב \"רעיונות\"\n\n");
        } else {
            sb.append("🎯 לקבוע זמן איכות? כתוב \"קבע זמן\"\n");
            sb.append("💡 רעיונות לפעילויות? כתוב \"רעיונות\"\n\n");
        }
        
        // Weekly goal status
        if (state != null && state.weeklyGoalInfo() != null && state.weeklyGoalInfo().hasGoal()) {
            var goal = state.weeklyGoalInfo();
            sb.append("📊 התקדמות השבוע: ")
              .append(goal.completedQualityTimes())
              .append("/")
              .append(goal.targetQualityTimes())
              .append(" זמני איכות הושלמו\n\n");
        }
        
        sb.append("מה תרצה לעשות? 🙂");
        
        return sb.toString();
    }
    
    /**
     * Ask AI to classify user intent when we can't determine it from patterns.
     * Returns: "acknowledgment", "question", "request", or null if unsure.
     */
    private String askAiForIntentClassification(String message, UUID fatherId) {
        try {
            String systemPrompt = """
                אתה מסווג הודעות. המשתמש שלח הודעה ואתה צריך לקבוע את הכוונה.
                
                סוגי כוונות:
                - acknowledgment: המשתמש מסכים, מאשר, אומר תודה, שולח אימוג'י חיובי (כמו 👍, ✅, 🙏),
                  או אומר שהוא כבר עשה משהו ("כבר", "כבר עשיתי", "כבר עזינן", "עשיתי", "סיימתי")
                - question: המשתמש שואל שאלה או מבקש הסבר
                - request: המשתמש מבקש לעשות משהו ספציפי
                - unclear: לא ברור מה הכוונה
                
                **חשוב:** הודעות כמו "כבר עזינן", "כבר עשיתי", "סיימתי" = acknowledgment!
                האב אומר שהוא כבר עשה את מה שביקשנו.
                
                החזר רק מילה אחת: acknowledgment, question, request, או unclear
                """;
            
            String userPrompt = "הודעת המשתמש: \"" + message + "\"";
            
            AiProviderResponse response = callAiProviderSimple(systemPrompt, userPrompt, fatherId);
            
            if (response != null && response.content() != null) {
                String intent = response.content().trim().toLowerCase();
                if (intent.contains("acknowledgment")) {
                    return "acknowledgment";
                } else if (intent.contains("question")) {
                    return "question";
                } else if (intent.contains("request")) {
                    return "request";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to classify intent with AI: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Simplified AI call for quick classification tasks.
     */
    private AiProviderResponse callAiProviderSimple(String systemPrompt, String userPrompt, UUID fatherId) {
        List<AiMessage> messages = List.of(
            AiMessage.system(systemPrompt),
            AiMessage.user(userPrompt)
        );
        
        Map<String, String> metadata = Map.of(
            "fatherId", fatherId.toString(),
            "component", "CoachingAgent-IntentClassifier"
        );
        
        AiProviderRequest request = new AiProviderRequest(
            modelName,
            messages,
            0.1,    // Very low temperature for consistent classification
            1.0,
            50,     // Short response - just the classification
            false,  // No JSON mode needed
            metadata
        );
        
        return aiProvider.sendPrompt(request);
    }
    
    /**
     * Build a positive response for acknowledgment messages.
     */
    private String buildAcknowledgmentResponse(AgentContext context) {
        SystemState state = context.systemState();
        
        // Check what's scheduled
        if (state != null && state.getNextScheduledQualityTime() != null) {
            var qt = state.getNextScheduledQualityTime();
            return "מעולה! 👍\n\n" +
                   "📅 הזמן איכות הבא שלך מתוכנן.\n" +
                   "תקבל תזכורת שעה לפני.\n\n" +
                   "רוצה לקבוע עוד זמן איכות השבוע? 🎯";
        }
        
        // No scheduled quality time
        return "מעולה! 👍\n\n" +
               "אז מה נעשה עכשיו?\n" +
               "🎯 לקבוע זמן איכות?\n" +
               "📊 לראות התקדמות?\n" +
               "💡 לקבל רעיונות לפעילויות?";
    }
    
    /**
     * Build a status summary when user seems confused.
     */
    private String buildStatusSummary(AgentContext context) {
        SystemState state = context.systemState();
        
        StringBuilder sb = new StringBuilder();
        sb.append("סליחה על הבלבול! 😅 בוא נסכם את המצב:\n\n");
        
        // Weekly goal status
        if (state != null && state.weeklyGoalInfo() != null && state.weeklyGoalInfo().hasGoal()) {
            var goal = state.weeklyGoalInfo();
            sb.append("🎯 יעד השבוע: ").append(goal.targetQualityTimes()).append(" זמני איכות\n");
            sb.append("✅ הושלמו: ").append(goal.completedQualityTimes()).append("\n\n");
        }
        
        // Next scheduled quality time
        if (state != null && state.getNextScheduledQualityTime() != null) {
            var qt = state.getNextScheduledQualityTime();
            sb.append("📅 מתוכנן: זמן איכות עם ").append(qt.childName()).append("\n\n");
        } else {
            sb.append("📅 אין זמני איכות מתוכננים כרגע\n\n");
        }
        
        sb.append("הכל מסודר מהצד שלי - אין צורך לעשות כלום עכשיו, פשוט תיהנה מהזמן עם הילדים! 💙");
        
        return sb.toString();
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
            // Improved fallback message - offers helpful options instead of generic "I don't understand"
            return new AiDecision("clarify", Map.of("question", 
                "היי! 😊 אשמח לעזור.\n\n" +
                "אפשר לכתוב:\n" +
                "🎯 \"קבע זמן\" - לקבוע זמן איכות\n" +
                "💡 \"רעיונות\" - לקבל רעיונות לפעילויות\n" +
                "📊 \"התקדמות\" - לראות את ההתקדמות שלך\n\n" +
                "מה תרצה לעשות?"), null);
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
