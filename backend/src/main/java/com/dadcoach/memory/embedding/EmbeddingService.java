package com.dadcoach.memory.embedding;

import com.dadcoach.ai.provider.openai.OpenAiProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Service for generating vector embeddings using OpenAI's text-embedding-ada-002 model.
 *
 * <p>This service generates 1536-dimension embeddings for memory content, supporting:
 * <ul>
 *   <li>Single text embedding generation</li>
 *   <li>Batch embedding generation for efficiency</li>
 *   <li>Circuit breaker protection for resilience</li>
 *   <li>Graceful error handling with detailed exceptions</li>
 * </ul>
 *
 * <p><strong>SPEC-004 Design Document Reference:</strong>
 * <pre>
 * embedding/
 * ├── EmbeddingService.java           # Generates embeddings via AI provider
 * └── EmbeddingQueue.java             # Retry queue for failed embeddings
 * </pre>
 *
 * <p>This bean is only created when the OpenAI API key is configured.
 *
 * @see com.dadcoach.memory.Memory#EMBEDDING_DIMENSION
 */
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /**
     * OpenAI embedding model name.
     */
    public static final String EMBEDDING_MODEL = "text-embedding-ada-002";

    /**
     * Expected embedding dimension for text-embedding-ada-002.
     */
    public static final int EMBEDDING_DIMENSION = 1536;

    /**
     * Maximum number of texts in a single batch request.
     * OpenAI limit is 2048, but we use a conservative limit for safety.
     */
    public static final int MAX_BATCH_SIZE = 100;

    /**
     * Circuit breaker name for the embedding service.
     */
    private static final String CIRCUIT_BREAKER_NAME = "embedding-service";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;

    /**
     * Creates an EmbeddingService with the given OpenAI properties and circuit breaker registry.
     *
     * @param properties OpenAI configuration properties
     * @param circuitBreakerRegistry circuit breaker registry (can be null for default)
     */
    public EmbeddingService(OpenAiProperties properties, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.timeout = Duration.ofSeconds(properties.timeoutSeconds());
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (circuitBreakerRegistry != null) {
            this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        } else {
            this.circuitBreaker = createDefaultCircuitBreaker();
        }
    }

    /**
     * Constructor for testing with a pre-configured WebClient.
     *
     * @param webClient pre-configured WebClient
     * @param circuitBreaker circuit breaker instance
     * @param timeout request timeout duration
     */
    EmbeddingService(WebClient webClient, CircuitBreaker circuitBreaker, Duration timeout) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.timeout = timeout;
    }

    /**
     * Generates a 1536-dimension embedding for the given text.
     *
     * @param text the text to embed (non-null, non-empty)
     * @return the embedding as a float array of 1536 dimensions
     * @throws EmbeddingException if embedding generation fails
     * @throws IllegalArgumentException if text is null or empty
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        List<float[]> results = generateEmbeddings(List.of(text));
        return results.get(0);
    }

    /**
     * Generates embeddings for multiple texts in a single batch request.
     * This is more efficient than calling generateEmbedding for each text individually.
     *
     * @param texts list of texts to embed (non-null, non-empty, max MAX_BATCH_SIZE)
     * @return list of embeddings, each a float array of 1536 dimensions
     * @throws EmbeddingException if embedding generation fails
     * @throws IllegalArgumentException if texts is null, empty, or exceeds MAX_BATCH_SIZE
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Texts list cannot be null or empty");
        }
        if (texts.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Batch size " + texts.size() + " exceeds maximum of " + MAX_BATCH_SIZE);
        }

        // Validate all texts
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i) == null || texts.get(i).isBlank()) {
                throw new IllegalArgumentException("Text at index " + i + " cannot be null or empty");
            }
        }

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            throw new EmbeddingException(EmbeddingException.ErrorType.CIRCUIT_OPEN,
                    "Circuit breaker is open for embedding service");
        }

        try {
            Map<String, Object> requestBody = buildRequestBody(texts);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = circuitBreaker.executeSupplier(() ->
                    webClient.post()
                            .uri("/v1/embeddings")
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .flatMap(body -> reactor.core.publisher.Mono.error(
                                                    WebClientResponseException.create(
                                                            clientResponse.statusCode().value(),
                                                            "Client error: " + body,
                                                            null, null, null))))
                            .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .flatMap(body -> reactor.core.publisher.Mono.error(
                                                    WebClientResponseException.create(
                                                            clientResponse.statusCode().value(),
                                                            "Server error: " + body,
                                                            null, null, null))))
                            .bodyToMono(Map.class)
                            .timeout(timeout)
                            .block()
            );

            return parseResponse(responseBody, texts.size());

        } catch (WebClientResponseException e) {
            log.error("OpenAI Embedding API error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            EmbeddingException.ErrorType errorType = mapHttpStatusToErrorType(e.getStatusCode().value());
            throw new EmbeddingException(errorType, e.getStatusCode().value(),
                    "OpenAI Embedding API returned " + e.getStatusCode().value() + ": " + e.getMessage(), e);

        } catch (WebClientRequestException e) {
            log.error("OpenAI Embedding network error: {}", e.getMessage());
            if (e.getCause() instanceof TimeoutException) {
                throw new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, 0,
                        "OpenAI Embedding call timed out after " + timeout.getSeconds() + "s", e);
            }
            throw new EmbeddingException(EmbeddingException.ErrorType.NETWORK_ERROR, 0,
                    "Network error calling OpenAI Embedding API: " + e.getMessage(), e);

        } catch (EmbeddingException e) {
            throw e;

        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException || 
                    (e.getMessage() != null && e.getMessage().contains("Timeout"))) {
                throw new EmbeddingException(EmbeddingException.ErrorType.TIMEOUT, 0,
                        "OpenAI Embedding call timed out after " + timeout.getSeconds() + "s", e);
            }
            log.error("Unexpected error calling OpenAI Embedding API: {}", e.getMessage(), e);
            throw new EmbeddingException(EmbeddingException.ErrorType.SERVER_ERROR, 0,
                    "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the circuit breaker is open.
     *
     * @return true if the circuit breaker is open (service unavailable)
     */
    public boolean isCircuitOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * Returns the expected embedding dimension.
     *
     * @return 1536 (the dimension of text-embedding-ada-002)
     */
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }

    // ─── Private Methods ─────────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(List<String> texts) {
        return Map.of(
                "model", EMBEDDING_MODEL,
                "input", texts
        );
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseResponse(Map<String, Object> responseBody, int expectedCount) {
        if (responseBody == null) {
            throw new EmbeddingException(EmbeddingException.ErrorType.INVALID_RESPONSE,
                    "OpenAI Embedding API returned null response");
        }

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseBody.get("data");
        if (dataList == null || dataList.isEmpty()) {
            throw new EmbeddingException(EmbeddingException.ErrorType.INVALID_RESPONSE,
                    "OpenAI Embedding API response contains no data");
        }

        if (dataList.size() != expectedCount) {
            throw new EmbeddingException(EmbeddingException.ErrorType.INVALID_RESPONSE,
                    "Expected " + expectedCount + " embeddings but received " + dataList.size());
        }

        List<float[]> embeddings = new ArrayList<>(expectedCount);

        // Sort by index to ensure correct order
        dataList.sort((a, b) -> {
            int indexA = toInt(a.get("index"));
            int indexB = toInt(b.get("index"));
            return Integer.compare(indexA, indexB);
        });

        for (Map<String, Object> item : dataList) {
            List<Number> embeddingList = (List<Number>) item.get("embedding");
            if (embeddingList == null || embeddingList.size() != EMBEDDING_DIMENSION) {
                int size = embeddingList == null ? 0 : embeddingList.size();
                throw new EmbeddingException(EmbeddingException.ErrorType.INVALID_RESPONSE,
                        "Embedding has invalid dimension: expected " + EMBEDDING_DIMENSION + ", got " + size);
            }

            float[] embedding = new float[EMBEDDING_DIMENSION];
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }
            embeddings.add(embedding);
        }

        log.debug("Generated {} embeddings with {} dimensions each", embeddings.size(), EMBEDDING_DIMENSION);
        return embeddings;
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private EmbeddingException.ErrorType mapHttpStatusToErrorType(int status) {
        return switch (status) {
            case 401, 403 -> EmbeddingException.ErrorType.AUTHENTICATION_ERROR;
            case 429 -> EmbeddingException.ErrorType.RATE_LIMIT;
            case 400, 422 -> EmbeddingException.ErrorType.INVALID_REQUEST;
            default -> EmbeddingException.ErrorType.SERVER_ERROR;
        };
    }

    private static CircuitBreaker createDefaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(60)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(10.0f)  // More lenient for embedding service
                .waitDurationInOpenState(Duration.ofMinutes(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    }
}
