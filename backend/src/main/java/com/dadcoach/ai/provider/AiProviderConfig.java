package com.dadcoach.ai.provider;

import com.dadcoach.ai.provider.anthropic.AnthropicProperties;
import com.dadcoach.ai.provider.anthropic.AnthropicProvider;
import com.dadcoach.ai.provider.openai.OpenAiProperties;
import com.dadcoach.ai.provider.openai.OpenAiProvider;
import com.dadcoach.ai.routing.FallbackChain;
import com.dadcoach.ai.routing.FallbackResponseProvider;
import com.dadcoach.ai.routing.ModelRouter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Spring configuration for AI provider beans.
 * Registers OpenAI and Anthropic providers with their respective circuit breakers.
 */
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, AnthropicProperties.class})
public class AiProviderConfig {

    @Bean
    public CircuitBreakerRegistry aiCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(60) // 60 one-second buckets for the sliding window
            .minimumNumberOfCalls(20)
            .failureRateThreshold(5.0f) // trips at 5% error rate
            .waitDurationInOpenState(Duration.ofMinutes(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dad-coach.ai.openai", name = "api-key")
    public OpenAiProvider openAiProvider(OpenAiProperties properties, CircuitBreakerRegistry registry) {
        return new OpenAiProvider(properties, registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dad-coach.ai.anthropic", name = "api-key")
    public AnthropicProvider anthropicProvider(AnthropicProperties properties, CircuitBreakerRegistry registry) {
        return new AnthropicProvider(properties, registry);
    }

    @Bean
    public FallbackChain fallbackChain(List<AiProvider> providers, FallbackResponseProvider fallbackResponseProvider) {
        if (providers.isEmpty()) {
            // No AI providers configured — create chain with fallback-only behaviour
            providers = List.of();
        }
        return new FallbackChain(providers, fallbackResponseProvider);
    }

    @Bean
    public ModelRouter modelRouter(FallbackChain fallbackChain) {
        return new ModelRouter(fallbackChain);
    }
}
