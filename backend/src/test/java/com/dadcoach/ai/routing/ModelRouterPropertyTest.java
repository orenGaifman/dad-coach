package com.dadcoach.ai.routing;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderException;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.conversation.ConversationType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ModelRouter and FallbackChain.
 *
 * <p>Validates:
 * <ul>
 *   <li>Property 13: Model Routing Determinism</li>
 *   <li>Property 14: Fallback Chain Ordering</li>
 * </ul>
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 13: Model Routing Determinism")
@Tag("Feature: ai-architecture-intelligence-layer, Property 14: Fallback Chain Ordering")
class ModelRouterPropertyTest {

    // --- Property 13: Model Routing Determinism ---

    /**
     * **Validates: Requirements 10.1**
     *
     * For any conversation type, when no cost or error constraints are active,
     * the Model Router SHALL always return the model specified in the routing table
     * for that type. The same conversation type SHALL always map to the same model
     * configuration under identical conditions.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 13: Model Routing Determinism")
    void sameConversationTypeAlwaysMapsToSameModelConfig(
            @ForAll("allConversationTypes") ConversationType type) {
        
        // Given a router with a working provider
        FallbackChain fallbackChain = createSuccessfulFallbackChain();
        ModelRouter router = new ModelRouter(fallbackChain);

        // When we get the config for the same type multiple times
        ModelConfig config1 = router.getConfigForType(type);
        ModelConfig config2 = router.getConfigForType(type);

        // Then the config is always the same
        assertThat(config1).isEqualTo(config2);
        assertThat(config1.model()).isEqualTo(config2.model());
        assertThat(config1.temperature()).isEqualTo(config2.temperature());
        assertThat(config1.topP()).isEqualTo(config2.topP());
        assertThat(config1.maxTokens()).isEqualTo(config2.maxTokens());
    }

    /**
     * **Validates: Requirements 10.1**
     *
     * Every conversation type has a routing entry — no type is unmapped.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 13: Model Routing Determinism")
    void everyConversationTypeHasRoutingEntry(
            @ForAll("allConversationTypes") ConversationType type) {
        
        ModelRouter router = new ModelRouter(createSuccessfulFallbackChain());
        ModelConfig config = router.getConfigForType(type);
        
        assertThat(config).isNotNull();
        assertThat(config.model()).isNotBlank();
        assertThat(config.temperature()).isBetween(0.0, 2.0);
        assertThat(config.topP()).isBetween(0.0, 1.0);
        assertThat(config.maxTokens()).isGreaterThan(0);
    }

    /**
     * **Validates: Requirements 10.1**
     *
     * The routing table is static and deterministic across multiple router instances.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 13: Model Routing Determinism")
    void routingIsDeterministicAcrossInstances(
            @ForAll("allConversationTypes") ConversationType type) {

        ModelRouter router1 = new ModelRouter(createSuccessfulFallbackChain());
        ModelRouter router2 = new ModelRouter(createSuccessfulFallbackChain());

        ModelConfig config1 = router1.getConfigForType(type);
        ModelConfig config2 = router2.getConfigForType(type);

        assertThat(config1).isEqualTo(config2);
    }

    // --- Property 14: Fallback Chain Ordering ---

    /**
     * **Validates: Requirements 10.3**
     *
     * For any failed primary model request, the system SHALL attempt fallback in strict order:
     * (1) same provider lower-tier model, (2) secondary provider, (3) pre-written fallback.
     * No step SHALL be skipped.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 14: Fallback Chain Ordering")
    void fallbackChainOrderIsNeverViolated(
            @ForAll("allConversationTypes") ConversationType type,
            @ForAll @IntRange(min = 0, max = 3) int failAtStep) {

        // Create providers that fail at the specified step
        List<AiProvider> providers = createProvidersFailingAt(failAtStep);
        FallbackResponseProvider fallbackResponses = new FallbackResponseProvider();
        FallbackChain chain = new FallbackChain(providers, fallbackResponses);

        AiProviderRequest request = createTestRequest("gpt-4o");
        ModelConfig config = new ModelConfig("gpt-4o", 0.7, 0.9, 300);

        FallbackChain.FallbackResult result = chain.execute(request, config, type);

        // The chain always returns a response
        assertThat(result.response()).isNotNull();
        assertThat(result.response().content()).isNotBlank();

        // Verify ordering: attempts are sequential and none skipped
        List<FallbackChain.FallbackAttempt> attempts = result.attempts();
        assertThat(attempts).isNotEmpty();

        // The successful step matches the expected fallback level
        if (failAtStep == 0) {
            // Primary succeeds
            assertThat(result.fallbackLevel()).isEqualTo(0);
        } else if (failAtStep == 1) {
            // Primary fails, lower-tier succeeds
            assertThat(result.fallbackLevel()).isEqualTo(1);
            assertThat(attempts.size()).isGreaterThanOrEqualTo(2);
            assertThat(attempts.get(0).success()).isFalse(); // Primary failed
        } else if (failAtStep == 2) {
            // Primary + lower-tier fail, secondary succeeds
            assertThat(result.fallbackLevel()).isEqualTo(2);
            assertThat(attempts.size()).isGreaterThanOrEqualTo(3);
            assertThat(attempts.get(0).success()).isFalse(); // Primary failed
            assertThat(attempts.get(1).success()).isFalse(); // Lower-tier failed
        } else {
            // All fail, pre-written fallback
            assertThat(result.fallbackLevel()).isEqualTo(3);
            assertThat(attempts.size()).isGreaterThanOrEqualTo(4);
            assertThat(attempts.get(0).success()).isFalse();
            assertThat(attempts.get(1).success()).isFalse();
            assertThat(attempts.get(2).success()).isFalse();
            assertThat(attempts.get(3).success()).isTrue(); // Fallback always succeeds
        }
    }

    /**
     * **Validates: Requirements 10.3**
     *
     * When all providers fail, the fallback chain always returns a pre-written response.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 14: Fallback Chain Ordering")
    void preWrittenFallbackAlwaysSucceeds(
            @ForAll("allConversationTypes") ConversationType type) {

        // All providers fail
        List<AiProvider> providers = List.of(
            createFailingProvider("openai", "gpt-4o", "gpt-4o-mini"),
            createFailingProvider("anthropic", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
        );
        FallbackResponseProvider fallbackResponses = new FallbackResponseProvider();
        FallbackChain chain = new FallbackChain(providers, fallbackResponses);

        AiProviderRequest request = createTestRequest("gpt-4o");
        ModelConfig config = new ModelConfig("gpt-4o", 0.7, 0.9, 300);

        FallbackChain.FallbackResult result = chain.execute(request, config, type);

        // Pre-written fallback always succeeds
        assertThat(result.response()).isNotNull();
        assertThat(result.response().content()).isNotBlank();
        assertThat(result.fallbackLevel()).isEqualTo(3);
        assertThat(result.response().provider()).isEqualTo("fallback");
    }

    /**
     * **Validates: Requirements 10.3**
     *
     * No step in the fallback chain is skipped — each step is attempted before moving to the next.
     */
    @Property(tries = 100)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 14: Fallback Chain Ordering")
    void noStepInChainIsSkipped(
            @ForAll("allConversationTypes") ConversationType type) {

        // Track which providers were called
        List<String> callOrder = new ArrayList<>();
        
        List<AiProvider> providers = List.of(
            createTrackingFailingProvider("openai", callOrder, "gpt-4o", "gpt-4o-mini"),
            createTrackingFailingProvider("anthropic", callOrder, "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
        );
        FallbackResponseProvider fallbackResponses = new FallbackResponseProvider();
        FallbackChain chain = new FallbackChain(providers, fallbackResponses);

        AiProviderRequest request = createTestRequest("gpt-4o");
        ModelConfig config = new ModelConfig("gpt-4o", 0.7, 0.9, 300);

        FallbackChain.FallbackResult result = chain.execute(request, config, type);

        // All steps were attempted in order before reaching pre-written fallback
        assertThat(callOrder).containsExactly("openai:gpt-4o", "openai:gpt-4o-mini", "anthropic:claude-3-5-sonnet-20241022");
        assertThat(result.fallbackLevel()).isEqualTo(3);
    }

    // --- Providers / Arbitraries ---

    @Provide
    Arbitrary<ConversationType> allConversationTypes() {
        return Arbitraries.of(ConversationType.values());
    }

    private FallbackChain createSuccessfulFallbackChain() {
        AiProvider successProvider = new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                return new AiProviderResponse("Hello", request.model(), "openai", 10, 5, "stop", Duration.ofMillis(100));
            }
            @Override
            public String getProviderName() { return "openai"; }
            @Override
            public boolean supportsModel(String model) { return true; }
        };
        return new FallbackChain(List.of(successProvider), new FallbackResponseProvider());
    }

    private List<AiProvider> createProvidersFailingAt(int succeedAtStep) {
        // Step 0 = primary succeeds (gpt-4o on openai)
        // Step 1 = lower-tier succeeds (gpt-4o-mini on openai)
        // Step 2 = secondary provider succeeds (anthropic)
        // Step 3 = all fail, fallback response
        AiProvider openai = new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                if (succeedAtStep == 0 && "gpt-4o".equals(request.model())) {
                    return successResponse(request.model(), "openai");
                }
                if (succeedAtStep == 1 && "gpt-4o-mini".equals(request.model())) {
                    return successResponse(request.model(), "openai");
                }
                throw new AiProviderException("openai", AiProviderException.ErrorType.SERVER_ERROR, 500, "Simulated failure");
            }
            @Override
            public String getProviderName() { return "openai"; }
            @Override
            public boolean supportsModel(String model) {
                return "gpt-4o".equals(model) || "gpt-4o-mini".equals(model);
            }
        };

        AiProvider anthropic = new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                if (succeedAtStep == 2) {
                    return successResponse(request.model(), "anthropic");
                }
                throw new AiProviderException("anthropic", AiProviderException.ErrorType.SERVER_ERROR, 500, "Simulated failure");
            }
            @Override
            public String getProviderName() { return "anthropic"; }
            @Override
            public boolean supportsModel(String model) {
                return "claude-3-5-sonnet-20241022".equals(model) || "claude-3-5-haiku-20241022".equals(model);
            }
        };

        return List.of(openai, anthropic);
    }

    private AiProvider createFailingProvider(String name, String... supportedModels) {
        return new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                throw new AiProviderException(name, AiProviderException.ErrorType.SERVER_ERROR, 500, "All failing");
            }
            @Override
            public String getProviderName() { return name; }
            @Override
            public boolean supportsModel(String model) {
                for (String m : supportedModels) {
                    if (m.equals(model)) return true;
                }
                return false;
            }
        };
    }

    private AiProvider createTrackingFailingProvider(String name, List<String> callOrder, String... supportedModels) {
        return new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                callOrder.add(name + ":" + request.model());
                throw new AiProviderException(name, AiProviderException.ErrorType.SERVER_ERROR, 500, "Tracking failure");
            }
            @Override
            public String getProviderName() { return name; }
            @Override
            public boolean supportsModel(String model) {
                for (String m : supportedModels) {
                    if (m.equals(model)) return true;
                }
                return false;
            }
        };
    }

    private AiProviderRequest createTestRequest(String model) {
        return new AiProviderRequest(
            model,
            List.of(AiMessage.user("Hello")),
            0.7,
            0.9,
            300,
            false,
            Map.of()
        );
    }

    private AiProviderResponse successResponse(String model, String provider) {
        return new AiProviderResponse("Successful response", model, provider, 10, 5, "stop", Duration.ofMillis(50));
    }
}
