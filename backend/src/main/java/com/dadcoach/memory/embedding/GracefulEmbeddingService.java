package com.dadcoach.memory.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper service for embedding generation that provides graceful degradation.
 *
 * <p>From SPEC-004 Design Document - Error Handling:
 * <blockquote>
 * Embedding generation fails → Store memory without embedding; queue retry (3 attempts / 24h);
 * exclude from similarity search until embedded
 * </blockquote>
 *
 * <p>This service provides:
 * <ul>
 *   <li>Graceful degradation when embedding service is unavailable</li>
 *   <li>Safe embedding generation that catches exceptions and returns Optional.empty()</li>
 *   <li>Logging for observability and debugging</li>
 *   <li>Circuit breaker awareness to avoid cascading failures</li>
 * </ul>
 *
 * <p><strong>Validates: Task 9 - Memory stored without embedding on failure (excluded from similarity search)</strong>
 *
 * <h3>Usage Pattern</h3>
 * <pre>
 * // Gracefully get embedding, storing memory even if embedding fails
 * Optional&lt;float[]&gt; embedding = gracefulEmbeddingService.generateEmbeddingGracefully(content);
 * memory.setEmbedding(embedding.orElse(null));
 * memoryRepository.save(memory);
 * </pre>
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>If embedding service is available and succeeds → returns Optional.of(embedding)</li>
 *   <li>If embedding service is unavailable (null) → returns Optional.empty()</li>
 *   <li>If embedding generation fails (exception) → logs error, returns Optional.empty()</li>
 *   <li>If circuit breaker is open → returns Optional.empty() without calling API</li>
 * </ul>
 *
 * @see EmbeddingService
 * @see EmbeddingException
 */
@Service
public class GracefulEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(GracefulEmbeddingService.class);

    private final EmbeddingService embeddingService;

    /**
     * Constructs a GracefulEmbeddingService.
     *
     * @param embeddingService the underlying embedding service (may be null in test environments)
     */
    public GracefulEmbeddingService(@Nullable EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * Attempts to generate an embedding for the given text, returning empty on failure.
     *
     * <p>This method provides graceful degradation by catching all exceptions and
     * returning Optional.empty() instead of propagating errors. The caller can then
     * decide to store the memory without an embedding.
     *
     * <p><strong>Error Logging:</strong>
     * All errors are logged at appropriate levels:
     * <ul>
     *   <li>Circuit open: WARN (expected during outages)</li>
     *   <li>Retryable errors (rate limit, timeout, network): WARN</li>
     *   <li>Non-retryable errors (auth, invalid request): ERROR</li>
     * </ul>
     *
     * @param text the text to generate an embedding for
     * @return Optional containing the embedding array, or empty if generation failed
     */
    public Optional<float[]> generateEmbeddingGracefully(String text) {
        if (embeddingService == null) {
            log.debug("EmbeddingService not available, skipping embedding generation");
            return Optional.empty();
        }

        if (text == null || text.isBlank()) {
            log.debug("Text is null or blank, skipping embedding generation");
            return Optional.empty();
        }

        // Check circuit breaker before attempting
        if (embeddingService.isCircuitOpen()) {
            log.warn("Circuit breaker is open for embedding service, skipping embedding generation");
            return Optional.empty();
        }

        try {
            float[] embedding = embeddingService.generateEmbedding(text);
            log.debug("Successfully generated embedding for text (length={})", text.length());
            return Optional.of(embedding);
        } catch (EmbeddingException e) {
            logEmbeddingError(e, text);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error generating embedding for text (length={}): {}",
                    text.length(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Attempts to generate embeddings for multiple texts, returning empty for failures.
     *
     * <p>This method processes texts individually to ensure that a failure on one text
     * doesn't prevent other texts from getting embeddings. The returned list has the
     * same size as the input list, with null entries for failed embeddings.
     *
     * @param texts the list of texts to generate embeddings for
     * @return list of embeddings (same size as input), with null entries for failures
     */
    public List<float[]> generateEmbeddingsGracefully(List<String> texts) {
        if (embeddingService == null) {
            log.debug("EmbeddingService not available, returning null embeddings for {} texts", 
                    texts != null ? texts.size() : 0);
            return createNullList(texts != null ? texts.size() : 0);
        }

        if (texts == null || texts.isEmpty()) {
            log.debug("Text list is null or empty, returning empty list");
            return List.of();
        }

        // Check circuit breaker before attempting
        if (embeddingService.isCircuitOpen()) {
            log.warn("Circuit breaker is open, skipping embedding generation for {} texts", texts.size());
            return createNullList(texts.size());
        }

        // Try batch first for efficiency
        try {
            List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
            log.debug("Successfully generated {} embeddings in batch", embeddings.size());
            return embeddings;
        } catch (EmbeddingException e) {
            log.warn("Batch embedding failed ({}), falling back to individual processing",
                    e.getErrorType());
            return generateEmbeddingsIndividually(texts);
        } catch (Exception e) {
            log.error("Unexpected batch embedding error, falling back to individual processing: {}",
                    e.getMessage());
            return generateEmbeddingsIndividually(texts);
        }
    }

    /**
     * Generates embeddings for texts individually, allowing partial success.
     */
    private List<float[]> generateEmbeddingsIndividually(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>(texts.size());
        int successCount = 0;
        int failureCount = 0;

        for (String text : texts) {
            Optional<float[]> embedding = generateEmbeddingGracefully(text);
            embeddings.add(embedding.orElse(null));
            if (embedding.isPresent()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        log.info("Individual embedding generation complete: {} succeeded, {} failed",
                successCount, failureCount);
        return embeddings;
    }

    /**
     * Creates a list of null values of the specified size.
     */
    private List<float[]> createNullList(int size) {
        List<float[]> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(null);
        }
        return result;
    }

    /**
     * Logs embedding errors at appropriate levels based on error type.
     */
    private void logEmbeddingError(EmbeddingException e, String text) {
        String truncatedContent = text.length() > 50 
                ? text.substring(0, 50) + "..." 
                : text;

        switch (e.getErrorType()) {
            case CIRCUIT_OPEN -> log.warn("Circuit breaker open for embedding service");
            
            case RATE_LIMIT -> log.warn("Rate limited while generating embedding (text='{}...'): {}",
                    truncatedContent.substring(0, Math.min(20, truncatedContent.length())), e.getMessage());
            
            case TIMEOUT, NETWORK_ERROR -> log.warn("Transient error generating embedding: {} - {}",
                    e.getErrorType(), e.getMessage());
            
            case SERVER_ERROR -> log.warn("Server error generating embedding (HTTP {}): {}",
                    e.getHttpStatus(), e.getMessage());
            
            case AUTHENTICATION_ERROR -> log.error("Authentication error for embedding service: {}. " +
                    "Please check API key configuration.", e.getMessage());
            
            case INVALID_REQUEST, INVALID_RESPONSE -> log.error("Invalid request/response for embedding " +
                    "(text='{}...'): {} - {}", truncatedContent, e.getErrorType(), e.getMessage());
        }
    }

    /**
     * Checks if the embedding service is currently available.
     *
     * @return true if embedding service is available and circuit is closed
     */
    public boolean isEmbeddingServiceAvailable() {
        return embeddingService != null && !embeddingService.isCircuitOpen();
    }

    /**
     * Returns the expected embedding dimension.
     *
     * @return 1536 (OpenAI text-embedding-ada-002 dimension), or 0 if service unavailable
     */
    public int getEmbeddingDimension() {
        return embeddingService != null ? embeddingService.getEmbeddingDimension() : 0;
    }
}
