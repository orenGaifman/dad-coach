package com.dadcoach.ai.provider.openai;

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
 * OpenAI provider adapter implementing the AiProvider interface.
 * Supports GPT-4o and GPT-4o-mini models via the OpenAI Chat Completions API.
 * 
 * <p>Features:
 * <ul>
 *   <li>10-second timeout per call</li>
 *   <li>Resilience4j circuit breaker (trips at 5% error rate over 1h sliding window)</li>
 *   <li>Translates standardized request/response to/from OpenAI API format</li>
 * </ul>
 */
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String PROVIDER_NAME = "openai";
    private static final Set<String> SUPPORTED_MODELS = Set.of("gpt-4o", "gpt-4o-mini");

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;

    public OpenAiProvider(OpenAiProperties properties) {
        this(properties, null);
    }

    public OpenAiProvider(OpenAiProperties properties, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.timeout = Duration.ofSeconds(properties.timeoutSeconds());
        this.webClient = WebClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
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
    OpenAiProvider(WebClient webClient, CircuitBreaker circuitBreaker, Duration timeout) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.timeout = timeout;
    }

    @Override
    public AiProviderResponse sendPrompt(AiProviderRequest request) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.CIRCUIT_OPEN,
                "Circuit breaker is open for OpenAI provider");
        }

        Instant start = Instant.now();
        try {
            Map<String, Object> requestBody = buildRequestBody(request);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = circuitBreaker.executeSupplier(() ->
                webClient.post()
                    .uri("/v1/chat/completions")
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
            log.error("OpenAI API error: status={}, body={}, latency={}ms",
                e.getStatusCode().value(), e.getResponseBodyAsString(), latency.toMillis());

            AiProviderException.ErrorType errorType = mapHttpStatusToErrorType(e.getStatusCode().value());
            throw new AiProviderException(PROVIDER_NAME, errorType, e.getStatusCode().value(),
                "OpenAI API returned " + e.getStatusCode().value() + ": " + e.getMessage(), e);

        } catch (WebClientRequestException e) {
            log.error("OpenAI network error: {}", e.getMessage());
            if (e.getCause() instanceof TimeoutException) {
                throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.TIMEOUT, 0,
                    "OpenAI call timed out after " + timeout.getSeconds() + "s", e);
            }
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.NETWORK_ERROR, 0,
                "Network error calling OpenAI: " + e.getMessage(), e);

        } catch (AiProviderException e) {
            throw e;

        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException || e.getMessage() != null && e.getMessage().contains("Timeout")) {
                throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.TIMEOUT, 0,
                    "OpenAI call timed out after " + timeout.getSeconds() + "s", e);
            }
            log.error("Unexpected error calling OpenAI: {}", e.getMessage(), e);
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
        List<Map<String, String>> messages = new ArrayList<>();
        for (AiMessage msg : request.messages()) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("model", request.model());
        body.put("messages", messages);
        body.put("temperature", request.temperature());
        body.put("top_p", request.topP());
        body.put("max_tokens", request.maxTokens());

        if (request.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private AiProviderResponse parseResponse(Map<String, Object> responseBody, Duration latency) {
        if (responseBody == null) {
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.INVALID_RESPONSE,
                "OpenAI returned null response");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiProviderException(PROVIDER_NAME, AiProviderException.ErrorType.INVALID_RESPONSE,
                "OpenAI response contains no choices");
        }

        Map<String, Object> firstChoice = choices.get(0);
        Map<String, String> message = (Map<String, String>) firstChoice.get("message");
        String content = message != null ? message.get("content") : "";
        String finishReason = (String) firstChoice.get("finish_reason");

        String model = (String) responseBody.get("model");

        Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
        int inputTokens = 0;
        int outputTokens = 0;
        if (usage != null) {
            inputTokens = toInt(usage.get("prompt_tokens"));
            outputTokens = toInt(usage.get("completion_tokens"));
        }

        return new AiProviderResponse(content, model, PROVIDER_NAME, inputTokens, outputTokens, finishReason, latency);
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
            .slidingWindowSize(60) // 60 seconds per bucket, combined with minimumNumberOfCalls
            .minimumNumberOfCalls(20)
            .failureRateThreshold(5.0f) // trips at 5% error rate
            .waitDurationInOpenState(Duration.ofMinutes(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker(PROVIDER_NAME);
    }
}
