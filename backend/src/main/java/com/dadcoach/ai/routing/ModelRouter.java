package com.dadcoach.ai.routing;

import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.domain.conversation.ConversationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Selects the provider + model per conversation type, and delegates to the FallbackChain
 * when the primary model fails.
 *
 * <p>The routing table maps each {@link ConversationType} to a specific {@link ModelConfig}
 * defining the model, temperature, top_p, and max_tokens. Under normal conditions (no cost
 * or error constraints), the same conversation type always maps to the same model config.
 *
 * <p>Routing table (using Anthropic Claude models):
 * <ul>
 *   <li>ONBOARDING → claude-sonnet-5 (0.7, 0.9, 300)</li>
 *   <li>DIFFICULT_SITUATION → claude-sonnet-5 (0.7, 0.9, 400)</li>
 *   <li>REFLECTION → claude-sonnet-5 (0.7, 0.9, 400)</li>
 *   <li>DAILY_COACHING → claude-haiku-4-5 (0.8, 0.95, 300)</li>
 *   <li>FOLLOW_UP → claude-haiku-4-5 (0.8, 0.95, 250)</li>
 *   <li>CELEBRATION → claude-haiku-4-5 (0.9, 0.95, 200)</li>
 *   <li>MISSION_GENERATION → claude-haiku-4-5 (0.3, 0.8, 400)</li>
 *   <li>INACTIVITY_CHECK → claude-haiku-4-5 (0.8, 0.95, 200)</li>
 * </ul>
 */
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    /**
     * Static routing table — same conversation type always maps to same model config
     * under normal (no cost/error constraints) conditions.
     */
    private static final Map<ConversationType, ModelConfig> ROUTING_TABLE;

    static {
        var table = new EnumMap<ConversationType, ModelConfig>(ConversationType.class);
        // Using Anthropic Claude models (Sonnet 5 for complex, Haiku 4.5 for routine)
        table.put(ConversationType.ONBOARDING, new ModelConfig("claude-sonnet-5", 0.7, 0.9, 300));
        table.put(ConversationType.DIFFICULT_SITUATION, new ModelConfig("claude-sonnet-5", 0.7, 0.9, 400));
        table.put(ConversationType.REFLECTION, new ModelConfig("claude-sonnet-5", 0.7, 0.9, 400));
        table.put(ConversationType.DAILY_COACHING, new ModelConfig("claude-haiku-4-5-20251001", 0.8, 0.95, 300));
        table.put(ConversationType.FOLLOW_UP, new ModelConfig("claude-haiku-4-5-20251001", 0.8, 0.95, 250));
        table.put(ConversationType.CELEBRATION, new ModelConfig("claude-haiku-4-5-20251001", 0.9, 0.95, 200));
        table.put(ConversationType.MISSION_GENERATION, new ModelConfig("claude-haiku-4-5-20251001", 0.3, 0.8, 400));
        table.put(ConversationType.INACTIVITY_CHECK, new ModelConfig("claude-haiku-4-5-20251001", 0.8, 0.95, 200));
        ROUTING_TABLE = Map.copyOf(table);
    }

    private final FallbackChain fallbackChain;

    public ModelRouter(FallbackChain fallbackChain) {
        this.fallbackChain = fallbackChain;
    }

    /**
     * Route a request to the appropriate model based on conversation type.
     * If the primary model fails, the fallback chain handles retries.
     *
     * @param request          the AI provider request (model field will be overridden by routing table)
     * @param conversationType the type of conversation determining model selection
     * @return the response from the successful provider
     */
    public FallbackChain.FallbackResult route(AiProviderRequest request, ConversationType conversationType) {
        ModelConfig config = getConfigForType(conversationType);
        log.debug("Routing {} to model={}, temp={}, topP={}, maxTokens={}",
            conversationType, config.model(), config.temperature(), config.topP(), config.maxTokens());

        // Build the request with routing table parameters
        AiProviderRequest routedRequest = new AiProviderRequest(
            config.model(),
            request.messages(),
            config.temperature(),
            config.topP(),
            config.maxTokens(),
            request.jsonMode(),
            request.metadata()
        );

        return fallbackChain.execute(routedRequest, config, conversationType);
    }

    /**
     * Returns the model configuration for a given conversation type.
     * This mapping is deterministic — same type always returns same config.
     *
     * @param conversationType the conversation type
     * @return the corresponding ModelConfig
     * @throws IllegalArgumentException if the conversation type has no routing entry
     */
    public ModelConfig getConfigForType(ConversationType conversationType) {
        ModelConfig config = ROUTING_TABLE.get(conversationType);
        if (config == null) {
            throw new IllegalArgumentException(
                "No routing configuration for conversation type: " + conversationType);
        }
        return config;
    }

    /**
     * Returns an unmodifiable view of the routing table for inspection/testing.
     */
    public Map<ConversationType, ModelConfig> getRoutingTable() {
        return ROUTING_TABLE;
    }
}
