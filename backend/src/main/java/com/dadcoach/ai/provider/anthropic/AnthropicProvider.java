package com.dadcoach.ai.provider.anthropic;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderException;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Anthropic provider adapter implementing the AiProvider interface.
 * Supports Claude 3.5 Sonnet via the Anthropic Messages API.
 *
 * <p>Features:
 * <ul>
 *   <li>10-second timeout per call</li>
 *   <li>Resilience4j circuit breaker (trips at 5% error rate over 1h sliding window)</li>
 *   <li>Translates standardized request/response to/from Anthropic API format</li>
 *   <li>Handles Anthropic's system prompt as a top-level parameter (not a message)</li>
 * </ul>
 */
public class AnthropicProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String PROVIDER_NAME = "anthropic";
    private static final Set<String> SUPPORTED_MODELS = Set.of(
        "claude-sonnet-5",
        "claude-haiku-4-5-20251001",
        "claude-opus-5"
    );

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;

    public AnthropicProvider(AnthropicProperties properties) {
        this(properties, null);
    }

    public AnthropicProvider(AnthropicProperties properties, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.timeout = Duration.ofSeconds(properties.timeoutSeconds());
        this.webClient = WebClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-api-key", properties.apiKey())
            .defaultHeader("anthropic-version", properties.apiVersion())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        if (circuitBreakerRegistry != null) {
            this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(PROVIDER_NAME);
        } else {
            this.circuitBreaker = createDefaultCircuitBreaker();
        }
    }

    /**
     * Constructor for testing with a pre-configured WebClient.
     */
    AnthropicProvider(WebClient webClient, CircuitBreaker circuitBreaker, Duration timeout) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.timeout = timeout;
    }

    @Override
    public AiProviderResponse sendPrompt(AiProviderRequest request) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.CIRCUIT_OPEN,
                "Circuit breaker is open for Anthropic provider");
        }

        Instant start = Instant.now();
        try {
            Map<String, Object> requestBody = buildRequestBody(request);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = circuitBreaker.executeSupplier(() ->
                webClient.post()
                    .uri("/v1/messages")
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

            Duration latency = Duration.between(start, Instant.now());
            return parseResponse(responseBody, latency);

        } catch (WebClientResponseException e) {
            Duration latency = Duration.between(start, Instant.now());
            log.error("Anthropic API error: status={}, body={}, latency={}ms",
                e.getStatusCode().value(), e.getResponseBodyAsString(), latency.toMillis());

            AiProviderException.ErrorType errorType = mapHttpStatusToErrorType(e.getStatusCode().value());
            throw new AiProviderException(PROVIDER_NAME, errorType, e.getStatusCode().value(),
                "Anthropic API returned " + e.getStatusCode().value() + ": " + e.getMessage(), e);

        } catch (WebClientRequestException e) {
            log.error("Anthropic network error: {}", e.getMessage());
            if (e.getCause() instanceof TimeoutException) {
                throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.TIMEOUT, 0,
                    "Anthropic call timed out after " + timeout.getSeconds() + "s", e);
            }
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.NETWORK_ERROR, 0,
                "Network error calling Anthropic: " + e.getMessage(), e);

        } catch (AiProviderException e) {
            throw e;

        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException || e.getMessage() != null && e.getMessage().contains("Timeout")) {
                throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.TIMEOUT, 0,
                    "Anthropic call timed out after " + timeout.getSeconds() + "s", e);
            }
            log.error("Unexpected error calling Anthropic: {}", e.getMessage(), e);
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.SERVER_ERROR, 0,
                "Unexpected error: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsModel(String model) {
        return SUPPORTED_MODELS.contains(model);
    }

    private Map<String, Object> buildRequestBody(AiProviderRequest request) {
        // Anthropic uses system prompt as a separate top-level field
        String systemPrompt = null;
        List<Map<String, String>> messages = new ArrayList<>();

        for (AiMessage msg : request.messages()) {
            if ("system".equals(msg.role())) {
                // Anthropic's system goes in a top-level field, not in messages
                systemPrompt = msg.content();
            } else {
                messages.add(Map.of("role", msg.role(), "content", msg.content()));
            }
        }

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("messages", messages);
        // Anthropic doesn't allow both temperature and top_p - use only temperature
        body.put("temperature", request.temperature());

        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private AiProviderResponse parseResponse(Map<String, Object> responseBody, Duration latency) {
        if (responseBody == null) {
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.INVALID_RESPONSE,
                "Anthropic returned null response");
        }

        // Parse content blocks
        List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) responseBody.get("content");
        StringBuilder content = new StringBuilder();
        if (contentBlocks != null) {
            for (Map<String, Object> block : contentBlocks) {
                if ("text".equals(block.get("type"))) {
                    content.append(block.get("text"));
                }
            }
        }

        String model = (String) responseBody.get("model");
        String stopReason = (String) responseBody.get("stop_reason");

        // Parse usage
        Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
        int inputTokens = 0;
        int outputTokens = 0;
        if (usage != null) {
            inputTokens = toInt(usage.get("input_tokens"));
            outputTokens = toInt(usage.get("output_tokens"));
        }

        // Map Anthropic stop_reason to standardized finish_reason
        String finishReason = mapStopReason(stopReason);

        return new AiProviderResponse(content.toString(), model, PROVIDER_NAME, inputTokens, outputTokens, finishReason, latency);
    }

    private String mapStopReason(String stopReason) {
        if (stopReason == null) return "unknown";
        return switch (stopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "stop_sequence" -> "stop";
            default -> stopReason;
        };
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private AiProviderException.ErrorType mapHttpStatusToErrorType(int status) {
        return switch (status) {
            case 401, 403 -> AiProviderException.ErrorType.AUTHENTICATION_ERROR;
            case 429 -> AiProviderException.ErrorType.RATE_LIMIT;
            case 400, 422 -> AiProviderException.ErrorType.INVALID_REQUEST;
            default -> AiProviderException.ErrorType.SERVER_ERROR;
        };
    }

    private static CircuitBreaker createDefaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(60) // 60 seconds per bucket
            .minimumNumberOfCalls(20)
            .failureRateThreshold(5.0f) // trips at 5% error rate
            .waitDurationInOpenState(Duration.ofMinutes(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker(PROVIDER_NAME);
    }
}
