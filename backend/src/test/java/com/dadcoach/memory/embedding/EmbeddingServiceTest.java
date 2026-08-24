package com.dadcoach.memory.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit Tests for EmbeddingService.
 *
 * <p>These tests verify the embedding generation service that creates 1536-dimension
 * vector embeddings for memory content using OpenAI's text-embedding-ada-002 model.
 *
 * <p><strong>Validates: Task 9 - Embedding Service</strong>
 * <ul>
 *   <li>Generates 1536-dimension embeddings via OpenAI text-embedding-ada-002</li>
 *   <li>Handles errors gracefully (API failures, rate limits, etc.)</li>
 *   <li>Supports batching for efficiency</li>
 * </ul>
 *
 * @see EmbeddingService
 */
@DisplayName("EmbeddingService Tests")
class EmbeddingServiceTest {

    private static final int EMBEDDING_DIMENSION = 1536;
    private static final String TEST_TEXT = "Lucas loves dinosaurs and playing outside";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private WireMockServer wireMockServer;
    private EmbeddingService embeddingService;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Create circuit breaker registry with lenient settings for tests
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-api-key")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("embedding-service");
        embeddingService = new EmbeddingService(webClient, circuitBreaker, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Embedding Dimension
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Embedding Dimension Tests")
    class EmbeddingDimensionTests {

        @Test
        @DisplayName("Should return embedding dimension of 1536")
        void shouldReturnCorrectEmbeddingDimension() {
            assertThat(embeddingService.getEmbeddingDimension()).isEqualTo(1536);
        }

        @Test
        @DisplayName("Constant EMBEDDING_DIMENSION should be 1536")
        void embeddingDimensionConstantShouldBe1536() {
            assertThat(EmbeddingService.EMBEDDING_DIMENSION).isEqualTo(1536);
        }

        @Test
        @DisplayName("Should use text-embedding-ada-002 model")
        void shouldUseCorrectModel() {
            assertThat(EmbeddingService.EMBEDDING_MODEL).isEqualTo("text-embedding-ada-002");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Single Embedding Generation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Single Embedding Generation Tests")
    class SingleEmbeddingTests {

        @Test
        @DisplayName("Should generate 1536-dimension embedding for text")
        void shouldGenerate1536DimensionEmbedding() throws Exception {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(createSuccessResponseBody(1))));

            // Act
            float[] embedding = embeddingService.generateEmbedding(TEST_TEXT);

            // Assert
            assertThat(embedding).hasSize(EMBEDDING_DIMENSION);
            assertThat(embedding[0]).isNotZero();
        }

        @Test
        @DisplayName("Should send correct request body to OpenAI API")
        void shouldSendCorrectRequestBody() throws Exception {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(createSuccessResponseBody(1))));

            // Act
            embeddingService.generateEmbedding(TEST_TEXT);

            // Assert
            verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(containing("\"model\":\"text-embedding-ada-002\""))
                    .withRequestBody(containing(TEST_TEXT)));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null text")
        void shouldThrowForNullText() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbedding(null))
                    .withMessage("Text cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty text")
        void shouldThrowForEmptyText() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbedding(""))
                    .withMessage("Text cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank text")
        void shouldThrowForBlankText() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbedding("   "))
                    .withMessage("Text cannot be null or empty");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Batch Embedding Generation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Batch Embedding Generation Tests")
    class BatchEmbeddingTests {

        @Test
        @DisplayName("Should generate embeddings for multiple texts in single request")
        void shouldGenerateBatchEmbeddings() throws Exception {
            // Arrange
            List<String> texts = List.of(
                    "Lucas loves dinosaurs",
                    "Sofía needs more one-on-one time",
                    "Bedtime routine is a struggle"
            );
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(createSuccessResponseBody(3))));

            // Act
            List<float[]> embeddings = embeddingService.generateEmbeddings(texts);

            // Assert
            assertThat(embeddings).hasSize(3);
            for (float[] embedding : embeddings) {
                assertThat(embedding).hasSize(EMBEDDING_DIMENSION);
            }
        }

        @Test
        @DisplayName("Should preserve order of embeddings matching input texts")
        void shouldPreserveOrderOfEmbeddings() throws Exception {
            // Arrange
            List<String> texts = List.of("first", "second", "third");
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(createSuccessResponseBodyWithDistinctEmbeddings(3))));

            // Act
            List<float[]> embeddings = embeddingService.generateEmbeddings(texts);

            // Assert - each embedding should have a distinct first value
            assertThat(embeddings.get(0)[0]).isEqualTo(0.1f);
            assertThat(embeddings.get(1)[0]).isEqualTo(0.2f);
            assertThat(embeddings.get(2)[0]).isEqualTo(0.3f);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null list")
        void shouldThrowForNullList() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbeddings(null))
                    .withMessage("Texts list cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty list")
        void shouldThrowForEmptyList() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbeddings(List.of()))
                    .withMessage("Texts list cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException if batch exceeds MAX_BATCH_SIZE")
        void shouldThrowForExcessiveBatchSize() {
            // Arrange
            List<String> texts = new ArrayList<>();
            for (int i = 0; i <= EmbeddingService.MAX_BATCH_SIZE; i++) {
                texts.add("text " + i);
            }

            // Act & Assert
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbeddings(texts))
                    .withMessageContaining("exceeds maximum of " + EmbeddingService.MAX_BATCH_SIZE);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException if any text in batch is null")
        void shouldThrowForNullTextInBatch() {
            List<String> texts = new ArrayList<>();
            texts.add("valid text");
            texts.add(null);
            texts.add("another valid text");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbeddings(texts))
                    .withMessage("Text at index 1 cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException if any text in batch is empty")
        void shouldThrowForEmptyTextInBatch() {
            List<String> texts = List.of("valid", "", "also valid");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> embeddingService.generateEmbeddings(texts))
                    .withMessage("Text at index 1 cannot be null or empty");
        }

        @Test
        @DisplayName("MAX_BATCH_SIZE should be 100")
        void maxBatchSizeShouldBe100() {
            assertThat(EmbeddingService.MAX_BATCH_SIZE).isEqualTo(100);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Error Handling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw EmbeddingException with RATE_LIMIT for 429 response")
        void shouldHandleRateLimitError() {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withBody("{\"error\": {\"message\": \"Rate limit exceeded\"}}")));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.RATE_LIMIT);
                        assertThat(ex.getHttpStatus()).isEqualTo(429);
                        assertThat(ex.isRetryable()).isTrue();
                        assertThat(ex.shouldBackoff()).isTrue();
                    });
        }

        @Test
        @DisplayName("Should throw EmbeddingException with AUTHENTICATION_ERROR for 401 response")
        void shouldHandleAuthenticationError() {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withBody("{\"error\": {\"message\": \"Invalid API key\"}}")));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.AUTHENTICATION_ERROR);
                        assertThat(ex.getHttpStatus()).isEqualTo(401);
                        assertThat(ex.isRetryable()).isFalse();
                    });
        }

        @Test
        @DisplayName("Should throw EmbeddingException with SERVER_ERROR for 500 response")
        void shouldHandleServerError() {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("{\"error\": {\"message\": \"Internal server error\"}}")));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.SERVER_ERROR);
                        assertThat(ex.getHttpStatus()).isEqualTo(500);
                        assertThat(ex.isRetryable()).isTrue();
                        assertThat(ex.shouldBackoff()).isTrue();
                    });
        }

        @Test
        @DisplayName("Should throw EmbeddingException with INVALID_REQUEST for 400 response")
        void shouldHandleInvalidRequestError() {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\": {\"message\": \"Invalid input\"}}")));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.INVALID_REQUEST);
                        assertThat(ex.getHttpStatus()).isEqualTo(400);
                        assertThat(ex.isRetryable()).isFalse();
                    });
        }

        @Test
        @DisplayName("Should throw EmbeddingException with INVALID_RESPONSE for malformed response")
        void shouldHandleMalformedResponse() {
            // Arrange
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("{\"unexpected\": \"format\"}")));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.INVALID_RESPONSE);
                        assertThat(ex.isRetryable()).isFalse();
                    });
        }

        @Test
        @DisplayName("Should throw EmbeddingException with INVALID_RESPONSE for wrong embedding count")
        void shouldHandleWrongEmbeddingCount() throws Exception {
            // Arrange: request 2 embeddings but only receive 1
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(createSuccessResponseBody(1))));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbeddings(List.of("text1", "text2")))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.INVALID_RESPONSE);
                        assertThat(ex.getMessage()).contains("Expected 2 embeddings but received 1");
                    });
        }

        @Test
        @DisplayName("Should throw EmbeddingException with INVALID_RESPONSE for wrong embedding dimension")
        void shouldHandleWrongEmbeddingDimension() throws Exception {
            // Arrange: response with wrong dimension (100 instead of 1536)
            stubFor(post(urlEqualTo("/v1/embeddings"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(createResponseBodyWithWrongDimension())));

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.INVALID_RESPONSE);
                        assertThat(ex.getMessage()).contains("invalid dimension");
                    });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Circuit Breaker
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Circuit Breaker Tests")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should report circuit breaker state correctly")
        void shouldReportCircuitBreakerState() {
            assertThat(embeddingService.isCircuitOpen()).isFalse();
        }

        @Test
        @DisplayName("Should throw EmbeddingException when circuit is open")
        void shouldThrowWhenCircuitIsOpen() {
            // Arrange: Force circuit breaker open
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("embedding-service");
            cb.transitionToOpenState();

            // Act & Assert
            assertThat(embeddingService.isCircuitOpen()).isTrue();
            assertThatThrownBy(() -> embeddingService.generateEmbedding(TEST_TEXT))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(e -> {
                        EmbeddingException ex = (EmbeddingException) e;
                        assertThat(ex.getErrorType()).isEqualTo(EmbeddingException.ErrorType.CIRCUIT_OPEN);
                        assertThat(ex.isRetryable()).isFalse();
                    });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: EmbeddingException
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EmbeddingException Tests")
    class EmbeddingExceptionTests {

        @Test
        @DisplayName("Network errors should be retryable")
        void networkErrorShouldBeRetryable() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.NETWORK_ERROR, "Connection failed");
            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.shouldBackoff()).isFalse();
        }

        @Test
        @DisplayName("Timeout errors should be retryable")
        void timeoutErrorShouldBeRetryable() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.TIMEOUT, "Request timed out");
            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.shouldBackoff()).isFalse();
        }

        @Test
        @DisplayName("Rate limit errors should be retryable with backoff")
        void rateLimitErrorShouldBeRetryableWithBackoff() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.RATE_LIMIT, 429, "Rate limit exceeded");
            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.shouldBackoff()).isTrue();
        }

        @Test
        @DisplayName("Server errors should be retryable with backoff")
        void serverErrorShouldBeRetryableWithBackoff() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.SERVER_ERROR, 500, "Internal error");
            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.shouldBackoff()).isTrue();
        }

        @Test
        @DisplayName("Authentication errors should not be retryable")
        void authenticationErrorShouldNotBeRetryable() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.AUTHENTICATION_ERROR, 401, "Invalid API key");
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("Invalid request errors should not be retryable")
        void invalidRequestErrorShouldNotBeRetryable() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.INVALID_REQUEST, 400, "Bad request");
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("toString should include all relevant information")
        void toStringShouldIncludeRelevantInfo() {
            EmbeddingException ex = new EmbeddingException(
                    EmbeddingException.ErrorType.RATE_LIMIT, 429, "Rate limit exceeded");
            String str = ex.toString();
            assertThat(str).contains("RATE_LIMIT");
            assertThat(str).contains("429");
            assertThat(str).contains("Rate limit exceeded");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private String createSuccessResponseBody(int count) throws JsonProcessingException {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            data.add(Map.of(
                    "index", i,
                    "embedding", generateTestEmbedding(0.1f)
            ));
        }

        Map<String, Object> response = Map.of(
                "object", "list",
                "data", data,
                "model", "text-embedding-ada-002",
                "usage", Map.of(
                        "prompt_tokens", 10,
                        "total_tokens", 10
                )
        );

        return objectMapper.writeValueAsString(response);
    }

    private String createSuccessResponseBodyWithDistinctEmbeddings(int count) throws JsonProcessingException {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            data.add(Map.of(
                    "index", i,
                    "embedding", generateTestEmbedding(0.1f * (i + 1))
            ));
        }

        Map<String, Object> response = Map.of(
                "object", "list",
                "data", data,
                "model", "text-embedding-ada-002",
                "usage", Map.of(
                        "prompt_tokens", 10,
                        "total_tokens", 10
                )
        );

        return objectMapper.writeValueAsString(response);
    }

    private String createResponseBodyWithWrongDimension() throws JsonProcessingException {
        // Create embedding with only 100 dimensions instead of 1536
        List<Float> wrongDimensionEmbedding = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            wrongDimensionEmbedding.add(0.1f);
        }

        Map<String, Object> response = Map.of(
                "object", "list",
                "data", List.of(Map.of(
                        "index", 0,
                        "embedding", wrongDimensionEmbedding
                )),
                "model", "text-embedding-ada-002"
        );

        return objectMapper.writeValueAsString(response);
    }

    private List<Float> generateTestEmbedding(float startValue) {
        List<Float> embedding = new ArrayList<>(EMBEDDING_DIMENSION);
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            embedding.add(startValue + (i * 0.0001f));
        }
        return embedding;
    }
}
