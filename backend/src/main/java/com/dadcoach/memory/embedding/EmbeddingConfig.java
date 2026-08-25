package com.dadcoach.memory.embedding;

import com.dadcoach.ai.provider.openai.OpenAiProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for embedding-related beans.
 *
 * <p>These beans are only created when OpenAI is configured with an API key.
 * When OpenAI is not configured, the system gracefully degrades via
 * {@link GracefulEmbeddingService} which handles null EmbeddingService.
 */
@Configuration
@ConditionalOnProperty(prefix = "dad-coach.ai.openai", name = "api-key")
public class EmbeddingConfig {

    @Bean
    public EmbeddingService embeddingService(
            OpenAiProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        return new EmbeddingService(properties, circuitBreakerRegistry);
    }

    @Bean
    public EmbeddingRetryProcessor embeddingRetryProcessor(
            EmbeddingRetryQueueService retryQueueService,
            EmbeddingService embeddingService) {
        return new EmbeddingRetryProcessor(retryQueueService, embeddingService);
    }
}
