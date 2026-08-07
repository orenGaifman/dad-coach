package com.dadcoach.ai.routing;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderException;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.domain.conversation.ConversationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ModelRouterTest {

    private ModelRouter router;
    private FallbackChain fallbackChain;

    @BeforeEach
    void setUp() {
        AiProvider successProvider = new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                return new AiProviderResponse("OK", request.model(), "openai", 10, 5, "stop", Duration.ofMillis(50));
            }
            @Override
            public String getProviderName() { return "openai"; }
            @Override
            public boolean supportsModel(String model) { return true; }
        };
        fallbackChain = new FallbackChain(List.of(successProvider), new FallbackResponseProvider());
        router = new ModelRouter(fallbackChain);
    }

    @Nested
    @DisplayName("Routing Table Mapping")
    class RoutingTableTests {

        @Test
        @DisplayName("ONBOARDING routes to gpt-4o with correct parameters")
        void onboardingRoutesToGpt4o() {
            ModelConfig config = router.getConfigForType(ConversationType.ONBOARDING);
            assertThat(config.model()).isEqualTo("gpt-4o");
            assertThat(config.temperature()).isEqualTo(0.7);
            assertThat(config.topP()).isEqualTo(0.9);
            assertThat(config.maxTokens()).isEqualTo(300);
        }

        @Test
        @DisplayName("DIFFICULT_SITUATION routes to gpt-4o with higher max tokens")
        void difficultSituationRoutesToGpt4o() {
            ModelConfig config = router.getConfigForType(ConversationType.DIFFICULT_SITUATION);
            assertThat(config.model()).isEqualTo("gpt-4o");
            assertThat(config.temperature()).isEqualTo(0.7);
            assertThat(config.topP()).isEqualTo(0.9);
            assertThat(config.maxTokens()).isEqualTo(400);
        }

        @Test
        @DisplayName("REFLECTION routes to gpt-4o")
        void reflectionRoutesToGpt4o() {
            ModelConfig config = router.getConfigForType(ConversationType.REFLECTION);
            assertThat(config.model()).isEqualTo("gpt-4o");
            assertThat(config.temperature()).isEqualTo(0.7);
            assertThat(config.topP()).isEqualTo(0.9);
            assertThat(config.maxTokens()).isEqualTo(400);
        }

        @Test
        @DisplayName("DAILY_COACHING routes to gpt-4o-mini")
        void dailyCoachingRoutesToMini() {
            ModelConfig config = router.getConfigForType(ConversationType.DAILY_COACHING);
            assertThat(config.model()).isEqualTo("gpt-4o-mini");
            assertThat(config.temperature()).isEqualTo(0.8);
            assertThat(config.topP()).isEqualTo(0.95);
            assertThat(config.maxTokens()).isEqualTo(300);
        }

        @Test
        @DisplayName("FOLLOW_UP routes to gpt-4o-mini")
        void followUpRoutesToMini() {
            ModelConfig config = router.getConfigForType(ConversationType.FOLLOW_UP);
            assertThat(config.model()).isEqualTo("gpt-4o-mini");
            assertThat(config.temperature()).isEqualTo(0.8);
            assertThat(config.topP()).isEqualTo(0.95);
            assertThat(config.maxTokens()).isEqualTo(250);
        }

        @Test
        @DisplayName("CELEBRATION routes to gpt-4o-mini with high temperature")
        void celebrationRoutesToMiniHighTemp() {
            ModelConfig config = router.getConfigForType(ConversationType.CELEBRATION);
            assertThat(config.model()).isEqualTo("gpt-4o-mini");
            assertThat(config.temperature()).isEqualTo(0.9);
            assertThat(config.topP()).isEqualTo(0.95);
            assertThat(config.maxTokens()).isEqualTo(200);
        }

        @Test
        @DisplayName("MISSION_GENERATION routes to gpt-4o-mini with low temperature")
        void missionGenerationRoutesToMiniLowTemp() {
            ModelConfig config = router.getConfigForType(ConversationType.MISSION_GENERATION);
            assertThat(config.model()).isEqualTo("gpt-4o-mini");
            assertThat(config.temperature()).isEqualTo(0.3);
            assertThat(config.topP()).isEqualTo(0.8);
            assertThat(config.maxTokens()).isEqualTo(400);
        }

        @Test
        @DisplayName("INACTIVITY_CHECK routes to gpt-4o-mini")
        void inactivityCheckRoutesToMini() {
            ModelConfig config = router.getConfigForType(ConversationType.INACTIVITY_CHECK);
            assertThat(config.model()).isEqualTo("gpt-4o-mini");
            assertThat(config.temperature()).isEqualTo(0.8);
            assertThat(config.topP()).isEqualTo(0.95);
            assertThat(config.maxTokens()).isEqualTo(200);
        }

        @Test
        @DisplayName("All conversation types have a routing entry")
        void allTypesHaveRoutingEntries() {
            for (ConversationType type : ConversationType.values()) {
                ModelConfig config = router.getConfigForType(type);
                assertThat(config).isNotNull();
            }
        }

        @Test
        @DisplayName("Routing table is unmodifiable")
        void routingTableIsUnmodifiable() {
            Map<ConversationType, ModelConfig> table = router.getRoutingTable();
            assertThatThrownBy(() -> table.put(ConversationType.ONBOARDING, new ModelConfig("test", 0.5, 0.5, 100)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Route Method")
    class RouteMethodTests {

        @Test
        @DisplayName("route() applies routing table parameters to request")
        void routeAppliesRoutingTableParams() {
            AiProviderRequest request = new AiProviderRequest(
                "ignored-model",
                List.of(AiMessage.user("test")),
                0.5, 0.5, 100, false, Map.of()
            );

            FallbackChain.FallbackResult result = router.route(request, ConversationType.ONBOARDING);
            // Should succeed with primary model
            assertThat(result.response()).isNotNull();
            assertThat(result.fallbackLevel()).isEqualTo(0);
        }
    }
}
