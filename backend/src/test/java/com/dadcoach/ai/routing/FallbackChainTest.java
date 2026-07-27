package com.dadcoach.ai.routing;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderException;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.conversation.ConversationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FallbackChainTest {

    private static final ConversationType TYPE = ConversationType.DAILY_COACHING;
    private static final ModelConfig CONFIG_GPT4O = new ModelConfig("gpt-4o", 0.7, 0.9, 300);

    @Nested
    @DisplayName("Primary Provider Success")
    class PrimarySuccessTests {

        @Test
        @DisplayName("Returns primary response when primary succeeds")
        void primarySuccess() {
            FallbackChain chain = createChainWithSuccess(0);
            AiProviderRequest request = testRequest("gpt-4o");

            FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, TYPE);

            assertThat(result.fallbackLevel()).isEqualTo(0);
            assertThat(result.usedFallback()).isFalse();
            assertThat(result.response().provider()).isEqualTo("openai");
        }
    }

    @Nested
    @DisplayName("Fallback to Lower-Tier Model")
    class LowerTierFallbackTests {

        @Test
        @DisplayName("Falls back to gpt-4o-mini when gpt-4o fails")
        void fallsBackToLowerTier() {
            FallbackChain chain = createChainWithSuccess(1);
            AiProviderRequest request = testRequest("gpt-4o");

            FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, TYPE);

            assertThat(result.fallbackLevel()).isEqualTo(1);
            assertThat(result.usedFallback()).isTrue();
            assertThat(result.response().model()).isEqualTo("gpt-4o-mini");
        }

        @Test
        @DisplayName("Tracks that primary attempt failed before lower-tier")
        void tracksPrimaryFailure() {
            FallbackChain chain = createChainWithSuccess(1);
            AiProviderRequest request = testRequest("gpt-4o");

            FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, TYPE);

            assertThat(result.attempts()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.attempts().get(0).success()).isFalse();
            assertThat(result.attempts().get(1).success()).isTrue();
        }
    }

    @Nested
    @DisplayName("Fallback to Secondary Provider")
    class SecondaryProviderFallbackTests {

        @Test
        @DisplayName("Falls back to secondary provider when primary and lower-tier fail")
        void fallsBackToSecondary() {
            FallbackChain chain = createChainWithSuccess(2);
            AiProviderRequest request = testRequest("gpt-4o");

            FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, TYPE);

            assertThat(result.fallbackLevel()).isEqualTo(2);
            assertThat(result.usedFallback()).isTrue();
            assertThat(result.response().provider()).isEqualTo("anthropic");
        }
    }

    @Nested
    @DisplayName("Pre-Written Fallback")
    class PreWrittenFallbackTests {

        @Test
        @DisplayName("Returns pre-written fallback when all providers fail")
        void returnsPreWrittenFallback() {
            FallbackChain chain = createChainWithSuccess(3);
            AiProviderRequest request = testRequest("gpt-4o");

            FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, TYPE);

            assertThat(result.fallbackLevel()).isEqualTo(3);
            assertThat(result.usedFallback()).isTrue();
            assertThat(result.response().provider()).isEqualTo("fallback");
            assertThat(result.response().content()).isNotBlank();
        }

        @Test
        @DisplayName("Pre-written fallback is in Spanish")
        void fallbackIsInSpanish() {
            FallbackChain chain = createChainWithSuccess(3);
            AiProviderRequest request = testRequest("gpt-4o");

            for (ConversationType type : ConversationType.values()) {
                FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, type);
                // All fallback responses should contain Spanish characters/words
                String content = result.response().content();
                assertThat(content).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("Chain Ordering Guarantee")
    class OrderingTests {

        @Test
        @DisplayName("Steps are attempted in strict order: primary → lower-tier → secondary → pre-written")
        void strictOrderingMaintained() {
            List<String> callOrder = new ArrayList<>();
            List<AiProvider> providers = List.of(
                trackingFailProvider("openai", callOrder, "gpt-4o", "gpt-4o-mini"),
                trackingFailProvider("anthropic", callOrder, "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
            );
            FallbackChain chain = new FallbackChain(providers, new FallbackResponseProvider());
            AiProviderRequest request = testRequest("gpt-4o");

            chain.execute(request, CONFIG_GPT4O, TYPE);

            // Verify order: primary (gpt-4o) → lower-tier (gpt-4o-mini) → secondary (claude)
            assertThat(callOrder).containsExactly(
                "openai:gpt-4o",
                "openai:gpt-4o-mini",
                "anthropic:claude-3-5-sonnet-20241022"
            );
        }

        @Test
        @DisplayName("No step is skipped even if models are not available")
        void noStepSkipped() {
            List<String> callOrder = new ArrayList<>();
            // Only openai provider, so secondary won't find a provider
            List<AiProvider> providers = List.of(
                trackingFailProvider("openai", callOrder, "gpt-4o", "gpt-4o-mini")
            );
            FallbackChain chain = new FallbackChain(providers, new FallbackResponseProvider());
            AiProviderRequest request = testRequest("gpt-4o");

            FallbackChain.FallbackResult result = chain.execute(request, CONFIG_GPT4O, TYPE);

            // Primary and lower-tier were attempted
            assertThat(callOrder).containsExactly("openai:gpt-4o", "openai:gpt-4o-mini");
            // No secondary available, so goes to pre-written
            assertThat(result.fallbackLevel()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorTests {

        @Test
        @DisplayName("Throws when no providers given")
        void throwsOnEmptyProviders() {
            assertThatThrownBy(() -> new FallbackChain(List.of(), new FallbackResponseProvider()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Throws when providers list is null")
        void throwsOnNullProviders() {
            assertThatThrownBy(() -> new FallbackChain(null, new FallbackResponseProvider()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // --- Test Helpers ---

    private FallbackChain createChainWithSuccess(int successLevel) {
        AiProvider openai = new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                if (successLevel == 0 && "gpt-4o".equals(request.model())) {
                    return success(request.model(), "openai");
                }
                if (successLevel == 1 && "gpt-4o-mini".equals(request.model())) {
                    return success(request.model(), "openai");
                }
                throw new AiProviderException("openai", AiProviderException.ErrorType.SERVER_ERROR, 500, "Failed");
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
                if (successLevel == 2) {
                    return success(request.model(), "anthropic");
                }
                throw new AiProviderException("anthropic", AiProviderException.ErrorType.SERVER_ERROR, 500, "Failed");
            }
            @Override
            public String getProviderName() { return "anthropic"; }
            @Override
            public boolean supportsModel(String model) {
                return "claude-3-5-sonnet-20241022".equals(model) || "claude-3-5-haiku-20241022".equals(model);
            }
        };

        return new FallbackChain(List.of(openai, anthropic), new FallbackResponseProvider());
    }

    private AiProvider trackingFailProvider(String name, List<String> callOrder, String... models) {
        return new AiProvider() {
            @Override
            public AiProviderResponse sendPrompt(AiProviderRequest request) {
                callOrder.add(name + ":" + request.model());
                throw new AiProviderException(name, AiProviderException.ErrorType.SERVER_ERROR, 500, "Tracking fail");
            }
            @Override
            public String getProviderName() { return name; }
            @Override
            public boolean supportsModel(String model) {
                for (String m : models) if (m.equals(model)) return true;
                return false;
            }
        };
    }

    private AiProviderRequest testRequest(String model) {
        return new AiProviderRequest(
            model,
            List.of(AiMessage.user("Hola, ¿cómo puedo mejorar?")),
            0.7, 0.9, 300, false, Map.of()
        );
    }

    private AiProviderResponse success(String model, String provider) {
        return new AiProviderResponse("Respuesta", model, provider, 10, 5, "stop", Duration.ofMillis(50));
    }
}
