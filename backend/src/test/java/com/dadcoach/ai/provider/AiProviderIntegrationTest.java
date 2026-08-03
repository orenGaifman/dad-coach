package com.dadcoach.ai.provider;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.anthropic.AnthropicProperties;
import com.dadcoach.ai.provider.anthropic.AnthropicProvider;
import com.dadcoach.ai.provider.openai.OpenAiProperties;
import com.dadcoach.ai.provider.openai.OpenAiProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for AI provider adapters using WireMock to validate API call format.
 */
class AiProviderIntegrationTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    // ========== OpenAI Tests ==========

    @Test
    @DisplayName("OpenAI adapter sends correct request format to chat completions endpoint")
    void openAi_sendsCorrectRequestFormat() {
        // Arrange
        stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "chatcmpl-123",
                      "object": "chat.completion",
                      "model": "gpt-4o",
                      "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": "Hello, I'm your coach"},
                        "finish_reason": "stop"
                      }],
                      "usage": {"prompt_tokens": 50, "completion_tokens": 10, "total_tokens": 60}
                    }
                    """)));

        OpenAiProvider provider = createOpenAiProvider();
        AiProviderRequest request = new AiProviderRequest(
            "gpt-4o",
            List.of(
                AiMessage.system("You are a coaching assistant"),
                AiMessage.user("Hello")
            ),
            0.7, 0.9, 300, false, Map.of()
        );

        // Act
        AiProviderResponse response = provider.sendPrompt(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Hello, I'm your coach");
        assertThat(response.provider()).isEqualTo("openai");
        assertThat(response.model()).isEqualTo("gpt-4o");
        assertThat(response.inputTokens()).isEqualTo(50);
        assertThat(response.outputTokens()).isEqualTo(10);
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.latency()).isNotNull();

        // Verify the request body format
        verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer test-api-key"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o")))
            .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.7")))
            .withRequestBody(matchingJsonPath("$.top_p", equalTo("0.9")))
            .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("300")))
            .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
            .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("You are a coaching assistant")))
            .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
            .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("Hello")))
        );
    }

    @Test
    @DisplayName("OpenAI adapter includes response_format when jsonMode is true")
    void openAi_jsonMode_includesResponseFormat() {
        stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "chatcmpl-456",
                      "model": "gpt-4o-mini",
                      "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": "{\\"action\\": \\"coaching\\"}"},
                        "finish_reason": "stop"
                      }],
                      "usage": {"prompt_tokens": 30, "completion_tokens": 5, "total_tokens": 35}
                    }
                    """)));

        OpenAiProvider provider = createOpenAiProvider();
        AiProviderRequest request = new AiProviderRequest(
            "gpt-4o-mini",
            List.of(AiMessage.user("Generate JSON")),
            0.3, 0.8, 400, true, Map.of()
        );

        provider.sendPrompt(request);

        verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(matchingJsonPath("$.response_format.type", equalTo("json_object")))
        );
    }

    @Test
    @DisplayName("OpenAI adapter throws AiProviderException on timeout")
    void openAi_timeout_throwsException() {
        stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(12000) // 12 seconds > 10 second timeout
                .withBody("{}")));

        // Use a very short timeout for test
        OpenAiProperties props = new OpenAiProperties(
            "test-api-key",
            "http://localhost:" + wireMock.port(),
            1  // 1 second timeout for fast test
        );
        OpenAiProvider provider = new OpenAiProvider(props);

        AiProviderRequest request = AiProviderRequest.of("gpt-4o",
            List.of(AiMessage.user("Hello")), 0.7, 300);

        assertThatThrownBy(() -> provider.sendPrompt(request))
            .isInstanceOf(AiProviderException.class)
            .satisfies(ex -> {
                AiProviderException ape = (AiProviderException) ex;
                assertThat(ape.getProvider()).isEqualTo("openai");
                assertThat(ape.getErrorType()).isEqualTo(AiProviderException.ErrorType.TIMEOUT);
            });
    }

    @Test
    @DisplayName("OpenAI adapter throws AiProviderException on 429 rate limit")
    void openAi_rateLimited_throwsException() {
        stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(429)
                .withBody("{\"error\": {\"message\": \"Rate limit exceeded\"}}")));

        OpenAiProvider provider = createOpenAiProvider();
        AiProviderRequest request = AiProviderRequest.of("gpt-4o",
            List.of(AiMessage.user("Hello")), 0.7, 300);

        assertThatThrownBy(() -> provider.sendPrompt(request))
            .isInstanceOf(AiProviderException.class)
            .satisfies(ex -> {
                AiProviderException ape = (AiProviderException) ex;
                assertThat(ape.getProvider()).isEqualTo("openai");
                assertThat(ape.getErrorType()).isEqualTo(AiProviderException.ErrorType.RATE_LIMIT);
                assertThat(ape.getHttpStatus()).isEqualTo(429);
                assertThat(ape.isRetryable()).isTrue();
            });
    }

    @Test
    @DisplayName("OpenAI adapter supportsModel correctly identifies supported models")
    void openAi_supportsModel() {
        OpenAiProvider provider = createOpenAiProvider();
        assertThat(provider.supportsModel("gpt-4o")).isTrue();
        assertThat(provider.supportsModel("gpt-4o-mini")).isTrue();
        assertThat(provider.supportsModel("claude-3-5-sonnet-20241022")).isFalse();
    }

    // ========== Anthropic Tests ==========

    @Test
    @DisplayName("Anthropic adapter sends correct request format to messages endpoint")
    void anthropic_sendsCorrectRequestFormat() {
        stubFor(post(urlEqualTo("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "msg_123",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type": "text", "text": "Hello dad, how are you?"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 40, "output_tokens": 12}
                    }
                    """)));

        AnthropicProvider provider = createAnthropicProvider();
        AiProviderRequest request = new AiProviderRequest(
            "claude-3-5-sonnet-20241022",
            List.of(
                AiMessage.system("You are a coaching assistant"),
                AiMessage.user("Hello")
            ),
            0.7, 0.9, 300, false, Map.of()
        );

        // Act
        AiProviderResponse response = provider.sendPrompt(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Hello dad, how are you?");
        assertThat(response.provider()).isEqualTo("anthropic");
        assertThat(response.model()).isEqualTo("claude-3-5-sonnet-20241022");
        assertThat(response.inputTokens()).isEqualTo(40);
        assertThat(response.outputTokens()).isEqualTo(12);
        assertThat(response.finishReason()).isEqualTo("stop"); // end_turn maps to stop

        // Verify request format — system prompt is a top-level field in Anthropic
        verify(postRequestedFor(urlEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("test-anthropic-key"))
            .withHeader("anthropic-version", equalTo("2023-06-01"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-3-5-sonnet-20241022")))
            .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("300")))
            .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.7")))
            .withRequestBody(matchingJsonPath("$.top_p", equalTo("0.9")))
            .withRequestBody(matchingJsonPath("$.system", equalTo("You are a coaching assistant")))
            .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("user")))
            .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("Hello")))
        );
    }

    @Test
    @DisplayName("Anthropic adapter handles max_tokens stop reason")
    void anthropic_maxTokensStopReason() {
        stubFor(post(urlEqualTo("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "msg_456",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type": "text", "text": "Partial response..."}],
                      "stop_reason": "max_tokens",
                      "usage": {"input_tokens": 100, "output_tokens": 400}
                    }
                    """)));

        AnthropicProvider provider = createAnthropicProvider();
        AiProviderRequest request = AiProviderRequest.of("claude-3-5-sonnet-20241022",
            List.of(AiMessage.user("Long question")), 0.7, 400);

        AiProviderResponse response = provider.sendPrompt(request);

        assertThat(response.finishReason()).isEqualTo("length");
    }

    @Test
    @DisplayName("Anthropic adapter throws AiProviderException on 529 overloaded")
    void anthropic_overloaded_throwsServerError() {
        stubFor(post(urlEqualTo("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(529)
                .withBody("{\"type\": \"error\", \"error\": {\"type\": \"overloaded_error\"}}")));

        AnthropicProvider provider = createAnthropicProvider();
        AiProviderRequest request = AiProviderRequest.of("claude-3-5-sonnet-20241022",
            List.of(AiMessage.user("Hello")), 0.7, 300);

        assertThatThrownBy(() -> provider.sendPrompt(request))
            .isInstanceOf(AiProviderException.class)
            .satisfies(ex -> {
                AiProviderException ape = (AiProviderException) ex;
                assertThat(ape.getProvider()).isEqualTo("anthropic");
                assertThat(ape.getErrorType()).isEqualTo(AiProviderException.ErrorType.SERVER_ERROR);
                assertThat(ape.isRetryable()).isTrue();
            });
    }

    @Test
    @DisplayName("Anthropic adapter supportsModel correctly identifies supported models")
    void anthropic_supportsModel() {
        AnthropicProvider provider = createAnthropicProvider();
        assertThat(provider.supportsModel("claude-3-5-sonnet-20241022")).isTrue();
        assertThat(provider.supportsModel("claude-3-5-haiku-20241022")).isTrue();
        assertThat(provider.supportsModel("gpt-4o")).isFalse();
    }

    // ========== Circuit Breaker Tests ==========

    @Test
    @DisplayName("Circuit breaker blocks calls when open")
    void circuitBreaker_whenOpen_blocksCalls() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofMinutes(5))
            .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("openai");

        // Manually transition to OPEN state
        cb.transitionToOpenState();

        OpenAiProperties props = new OpenAiProperties(
            "test-api-key",
            "http://localhost:" + wireMock.port(),
            10
        );
        OpenAiProvider provider = new OpenAiProvider(props, registry);

        AiProviderRequest request = AiProviderRequest.of("gpt-4o",
            List.of(AiMessage.user("Hello")), 0.7, 300);

        assertThatThrownBy(() -> provider.sendPrompt(request))
            .isInstanceOf(AiProviderException.class)
            .satisfies(ex -> {
                AiProviderException ape = (AiProviderException) ex;
                assertThat(ape.getErrorType()).isEqualTo(AiProviderException.ErrorType.CIRCUIT_OPEN);
            });
    }

    // ========== AiProviderRequest Validation Tests ==========

    @Test
    @DisplayName("AiProviderRequest rejects blank model")
    void request_rejectsBlankModel() {
        assertThatThrownBy(() -> AiProviderRequest.of("", List.of(AiMessage.user("Hi")), 0.7, 300))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("model");
    }

    @Test
    @DisplayName("AiProviderRequest rejects empty messages")
    void request_rejectsEmptyMessages() {
        assertThatThrownBy(() -> AiProviderRequest.of("gpt-4o", List.of(), 0.7, 300))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("messages");
    }

    @Test
    @DisplayName("AiProviderRequest rejects invalid temperature")
    void request_rejectsInvalidTemperature() {
        assertThatThrownBy(() -> AiProviderRequest.of("gpt-4o", List.of(AiMessage.user("Hi")), -0.1, 300))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("temperature");
    }

    // ========== Helper Methods ==========

    private OpenAiProvider createOpenAiProvider() {
        OpenAiProperties props = new OpenAiProperties(
            "test-api-key",
            "http://localhost:" + wireMock.port(),
            10
        );
        return new OpenAiProvider(props);
    }

    private AnthropicProvider createAnthropicProvider() {
        AnthropicProperties props = new AnthropicProperties(
            "test-anthropic-key",
            "http://localhost:" + wireMock.port(),
            10,
            "2023-06-01"
        );
        return new AnthropicProvider(props);
    }
}
