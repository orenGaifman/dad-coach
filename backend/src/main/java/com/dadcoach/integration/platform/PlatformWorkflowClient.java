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
 *
 * <p>This client communicates with the ai-workflow-platform to execute workflows.
 * It handles:</p>
 * <ul>
 *   <li>HTTP request/response mapping</li>
 *   <li>Authentication via API key</li>
 *   <li>Retry logic with exponential backoff</li>
 *   <li>Circuit breaker for resilience</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>
 * workflow:
 *   platform:
 *     enabled: true
 *     base-url: http://localhost:8081
 *     api-key: ${WORKFLOW_PLATFORM_API_KEY}
 *     workflow-id: ${WORKFLOW_PLATFORM_WORKFLOW_ID}
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 30000
 * </pre>
 *
 * @see WorkflowExecuteRequest
 * @see WorkflowExecuteResponse
 * @see PlatformWorkflowConfig
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
        
        log.info("PlatformWorkflowClient initialized: baseUrl={}, enabled={}, workflowId={}",
                config.getBaseUrl(), config.isEnabled(), config.getWorkflowId());
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
     *
     * <p>This method calls the ai-workflow-platform's POST /api/v1/workflow/execute endpoint.
     * It includes retry logic with exponential backoff and circuit breaker protection.</p>
     *
     * @param request the workflow execution request
     * @return the workflow execution response
     * @throws PlatformWorkflowException if the platform call fails
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "executeWorkflowFallback")
    public WorkflowExecuteResponse executeWorkflow(WorkflowExecuteRequest request) {
        if (!config.isEnabled()) {
            throw new PlatformWorkflowException("Platform workflow integration is disabled");
        }

        log.info("Calling platform workflow: userId={}, correlationId={}", 
                maskUserId(request.userId()), request.correlationId());

        try {
            WorkflowExecuteResponse response = webClient.post()
                    .uri("/api/v1/workflow/execute")
                    .bodyValue(buildPlatformRequest(request))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> new PlatformWorkflowException(
                                            "Client error from platform: status=" + clientResponse.statusCode() 
                                                    + ", body=" + body)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> new PlatformWorkflowException(
                                            "Server error from platform: status=" + clientResponse.statusCode() 
                                                    + ", body=" + body)))
                    .bodyToMono(PlatformApiResponse.class)
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                            .filter(this::isRetryableException)
                            .doBeforeRetry(signal -> 
                                    log.warn("Retrying platform call: attempt={}, error={}", 
                                            signal.totalRetries() + 1, signal.failure().getMessage())))
                    .map(this::mapToResponse)
                    .block();

            log.info("Platform workflow response received: correlationId={}, state={}, success={}", 
                    request.correlationId(), 
                    response != null ? response.currentState() : "null",
                    response != null ? response.success() : false);

            return response;

        } catch (WebClientResponseException e) {
            log.error("Platform API error: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformWorkflowException("Platform API error: " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.error("Platform connection error: message={}", e.getMessage());
            throw new PlatformWorkflowException("Platform connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error calling platform: type={}, message={}", 
                    e.getClass().getSimpleName(), e.getMessage());
            throw new PlatformWorkflowException("Unexpected platform error: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback method when circuit breaker is open.
     *
     * @param request the original request
     * @param throwable the exception that triggered the fallback
     * @return a fallback response indicating the platform is unavailable
     */
    @SuppressWarnings("unused")
    private WorkflowExecuteResponse executeWorkflowFallback(WorkflowExecuteRequest request, Throwable throwable) {
        log.warn("Platform workflow circuit breaker open: correlationId={}, error={}", 
                request.correlationId(), throwable.getMessage());
        
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
        if (apiResponse.instanceId() == null) {
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
     * We retry on connection errors and 5xx server errors, but not on 4xx client errors.
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            return true;  // Connection error - retry
        }
        if (throwable instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();  // Server error - retry
        }
        return false;
    }

    /**
     * Masks user ID for logging (shows first and last 4 characters).
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 8) {
            return "****";
        }
        return userId.substring(0, 4) + "****" + userId.substring(userId.length() - 4);
    }

    /**
     * Internal record for the platform API request format.
     * Maps to ai-workflow-platform's WorkflowRequest.
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
     * Maps from ai-workflow-platform's WorkflowResponse.
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
