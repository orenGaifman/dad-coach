package com.dadcoach.integration.platform;

import com.dadcoach.integration.platform.dto.WorkflowExecuteRequest;
import com.dadcoach.integration.platform.dto.WorkflowExecuteResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;

/**
 * HTTP client for calling the ai-workflow-platform workflow execution API.
 */
@Component
public class PlatformWorkflowClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformWorkflowClient.class);
    
    private static final String CIRCUIT_BREAKER_NAME = "platformWorkflow";
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private final WebClient webClient;
    private final PlatformWorkflowConfig config;

    public PlatformWorkflowClient(PlatformWorkflowConfig config) {
        this.config = config;
        this.webClient = buildWebClient(config);
        
        log.info("PlatformWorkflowClient initialized: baseUrl={}, enabled={}, workflowId={}, apiKeyPresent={}",
                config.getBaseUrl(), config.isEnabled(), config.getWorkflowId(), 
                config.getApiKey() != null && !config.getApiKey().isEmpty());
    }

    private WebClient buildWebClient(PlatformWorkflowConfig config) {
        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", config.getApiKey())
                .build();
    }

    /**
     * Executes a workflow for the given request.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "executeWorkflowFallback")
    public WorkflowExecuteResponse executeWorkflow(WorkflowExecuteRequest request) {
        if (!config.isEnabled()) {
            log.warn("Platform workflow integration is DISABLED - check WORKFLOW_PLATFORM_ENABLED env var");
            throw new PlatformWorkflowException("Platform workflow integration is disabled");
        }

        String targetUrl = config.getBaseUrl() + "/api/v1/workflow/execute";
        log.info("Calling platform workflow: targetUrl={}, userId={}, correlationId={}, workflowId={}", 
                targetUrl, maskUserId(request.userId()), request.correlationId(), config.getWorkflowId());

        PlatformApiRequest platformRequest = buildPlatformRequest(request);
        log.debug("Platform request payload: workflowId={}, channel={}, messageType={}, contentLength={}",
                platformRequest.workflowId(), platformRequest.channelId(), 
                platformRequest.messageType(), 
                platformRequest.content() != null ? platformRequest.content().length() : 0);

        try {
            WorkflowExecuteResponse response = webClient.post()
                    .uri("/api/v1/workflow/execute")
                    .bodyValue(platformRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                            log.error("Platform 4xx error: status={}, url={}", 
                                    clientResponse.statusCode(), targetUrl);
                            return clientResponse.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Platform 4xx response body: {}", body))
                                    .map(body -> new PlatformWorkflowException(
                                            "Client error from platform: status=" + clientResponse.statusCode() 
                                                    + ", body=" + body));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                            log.error("Platform 5xx error: status={}, url={}", 
                                    clientResponse.statusCode(), targetUrl);
                            return clientResponse.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Platform 5xx response body: {}", body))
                                    .map(body -> new PlatformWorkflowException(
                                            "Server error from platform: status=" + clientResponse.statusCode() 
                                                    + ", body=" + body));
                    })
                    .bodyToMono(PlatformApiResponse.class)
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                            .filter(this::isRetryableException)
                            .doBeforeRetry(signal -> 
                                    log.warn("Retrying platform call: attempt={}, error={}, errorType={}", 
                                            signal.totalRetries() + 1, 
                                            signal.failure().getMessage(),
                                            signal.failure().getClass().getSimpleName())))
                    .map(this::mapToResponse)
                    .block();

            log.info("Platform workflow response received: correlationId={}, state={}, success={}, instanceId={}", 
                    request.correlationId(), 
                    response != null ? response.currentState() : "null",
                    response != null ? response.success() : false,
                    response != null ? response.instanceId() : "null");

            return response;

        } catch (WebClientResponseException e) {
            log.error("Platform API HTTP error: status={}, statusText={}, body={}, url={}", 
                    e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), targetUrl);
            throw new PlatformWorkflowException("Platform API error: " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.error("Platform connection error: message={}, url={}, cause={}", 
                    e.getMessage(), targetUrl, e.getCause() != null ? e.getCause().getMessage() : "none");
            throw new PlatformWorkflowException("Platform connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error calling platform: type={}, message={}, url={}, stackTrace={}", 
                    e.getClass().getSimpleName(), e.getMessage(), targetUrl, 
                    e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "none");
            throw new PlatformWorkflowException("Unexpected platform error: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback method when circuit breaker is open.
     */
    @SuppressWarnings("unused")
    private WorkflowExecuteResponse executeWorkflowFallback(WorkflowExecuteRequest request, Throwable throwable) {
        log.error("Platform workflow FALLBACK triggered: correlationId={}, errorType={}, errorMessage={}, baseUrl={}, workflowId={}",
                request.correlationId(), 
                throwable.getClass().getSimpleName(),
                throwable.getMessage(),
                config.getBaseUrl(),
                config.getWorkflowId());
        
        // Log the root cause if available
        Throwable cause = throwable.getCause();
        if (cause != null) {
            log.error("Fallback root cause: type={}, message={}", 
                    cause.getClass().getSimpleName(), cause.getMessage());
        }
        
        return new WorkflowExecuteResponse(
                false,
                null,
                null,
                null,
                "Platform temporarily unavailable. Please try again later.",
                "PLATFORM_UNAVAILABLE"
        );
    }

    /**
     * Builds the platform API request from our internal request format.
     */
    private PlatformApiRequest buildPlatformRequest(WorkflowExecuteRequest request) {
        return new PlatformApiRequest(
                config.getWorkflowId(),
                request.userId(),
                request.channel(),
                request.correlationId(),
                request.message().type(),
                request.message().content(),
                null  // metadata - optional
        );
    }

    /**
     * Maps the platform API response to our internal response format.
     */
    private WorkflowExecuteResponse mapToResponse(PlatformApiResponse apiResponse) {
        log.debug("Mapping platform response: instanceId={}, stateKey={}, responseType={}, contentLength={}",
                apiResponse.instanceId(), apiResponse.currentStateKey(), apiResponse.responseType(),
                apiResponse.responseContent() != null ? apiResponse.responseContent().length() : 0);
        
        if (apiResponse.instanceId() == null) {
            log.warn("Platform returned null instanceId - treating as error");
            return new WorkflowExecuteResponse(
                    false,
                    null,
                    null,
                    null,
                    "Platform returned empty response",
                    "EMPTY_RESPONSE"
            );
        }

        return new WorkflowExecuteResponse(
                true,
                new WorkflowExecuteResponse.ResponsePayload(
                        apiResponse.responseType() != null ? apiResponse.responseType() : "text",
                        apiResponse.responseContent(),
                        null  // buttons - not yet supported
                ),
                apiResponse.currentStateKey(),
                apiResponse.instanceId().toString(),
                null,
                null
        );
    }

    /**
     * Determines if an exception is retryable.
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            log.debug("Exception is retryable (WebClientRequestException): {}", throwable.getMessage());
            return true;
        }
        if (throwable instanceof WebClientResponseException e) {
            boolean retryable = e.getStatusCode().is5xxServerError();
            log.debug("Exception retryable={} (WebClientResponseException status={})", retryable, e.getStatusCode());
            return retryable;
        }
        log.debug("Exception is NOT retryable: {}", throwable.getClass().getSimpleName());
        return false;
    }

    /**
     * Masks user ID for logging.
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 8) {
            return "****";
        }
        return userId.substring(0, 4) + "****" + userId.substring(userId.length() - 4);
    }

    /**
     * Internal record for the platform API request format.
     */
    private record PlatformApiRequest(
            UUID workflowId,
            String userId,
            String channelId,
            String correlationId,
            String messageType,
            String content,
            Object metadata
    ) {}

    /**
     * Internal record for the platform API response format.
     */
    private record PlatformApiResponse(
            UUID instanceId,
            String currentStateKey,
            String responseContent,
            String responseType,
            Object metadata,
            boolean isDuplicate
    ) {}
}
