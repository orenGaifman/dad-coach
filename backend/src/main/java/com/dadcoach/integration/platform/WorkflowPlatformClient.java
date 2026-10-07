package com.dadcoach.integration.platform;

import com.dadcoach.api.error.PlatformUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

/**
 * Calls the platform's turn API (playbook §10.3, Big Boss's client): a circuit breaker (20-call
 * window, 50% failures, 30s open) plus at most 2 retries with backoff — only on 5xx and connection
 * failures. A timeout is deliberately NOT retried (it would run the turn twice) and a 4xx never is
 * (the request itself is wrong). Logs identifiers and result classes, never content.
 */
@Component
public class WorkflowPlatformClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPlatformClient.class);
    static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private final WorkflowPlatformProperties properties;
    private final WebClient web;
    private final CircuitBreaker breaker;

    public WorkflowPlatformClient(WorkflowPlatformProperties properties) {
        this.properties = properties;
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs());
        this.web = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", properties.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
        this.breaker = CircuitBreaker.of("workflowPlatform", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(e -> e instanceof PlatformUnavailableException)
                .build());
    }

    public boolean isEnabled() {
        return properties.isEnabled() && !properties.getBaseUrl().isBlank();
    }

    CircuitBreaker.State circuitState() {
        return breaker.getState();
    }

    public WorkerExecuteResponse execute(WorkerExecuteRequest request) {
        Instant started = Instant.now();
        if (!isEnabled()) {
            logResult(request, null, "DISABLED", started);
            throw new PlatformUnavailableException("the workflow platform is not configured");
        }
        try {
            WorkerExecuteResponse response = breaker.executeSupplier(() -> call(request));
            logResult(request, response.currentStateKey(), response.suppressed() ? "SUPPRESSED" : "SUCCESS", started);
            return response;
        } catch (CallNotPermittedException open) {
            logResult(request, null, "CIRCUIT_OPEN", started);
            throw new PlatformUnavailableException("the workflow platform is temporarily unavailable");
        } catch (PlatformUnavailableException e) {
            logResult(request, null, e.getMessage(), started);
            throw e;
        }
    }

    private WorkerExecuteResponse call(WorkerExecuteRequest request) {
        try {
            return web.post().uri("/api/v1/worker/execute").bodyValue(request)
                    .retrieve()
                    .bodyToMono(WorkerExecuteResponse.class)
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY).filter(WorkflowPlatformClient::isRetryable))
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new PlatformRejectedException(e.getStatusCode().value(), e.getResponseBodyAsString());
            }
            throw new PlatformUnavailableException("HTTP_" + e.getStatusCode().value());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                throw new PlatformUnavailableException("TIMEOUT");
            }
            if (cause instanceof WebClientResponseException wcre && wcre.getStatusCode().is5xxServerError()) {
                throw new PlatformUnavailableException("HTTP_" + wcre.getStatusCode().value());
            }
            throw new PlatformUnavailableException("NO_RESPONSE");
        }
    }

    /**
     * Records a message Dad Coach sent into the person's conversation, so the agent reads her reply in
     * context. Best effort, outside the breaker: a failure costs context, never the send.
     */
    public boolean recordOutbound(RecordOutboundRequest request) {
        if (!isEnabled()) {
            return false;
        }
        try {
            web.post().uri("/api/v1/worker/messages/outbound").bodyValue(request)
                    .retrieve().toBodilessEntity()
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY).filter(WorkflowPlatformClient::isRetryable))
                    .block();
            return true;
        } catch (RuntimeException e) {
            log.atWarn().setMessage("workflow.outbound_record.failed")
                    .addKeyValue("correlationId", request.correlationId())
                    .addKeyValue("workflowKey", request.workflowKey())
                    .addKeyValue("error", e.getClass().getSimpleName())
                    .log();
            return false;
        }
    }

    private static boolean isRetryable(Throwable t) {
        return t instanceof WebClientRequestException
                || t instanceof WebClientResponseException e && e.getStatusCode().is5xxServerError();
    }

    private void logResult(WorkerExecuteRequest r, String state, String result, Instant started) {
        log.atInfo().setMessage("workflow.call.result")
                .addKeyValue("workerKey", r.workerKey())
                .addKeyValue("workflowKey", r.workflowKey())
                .addKeyValue("externalUserId", mask(r.userId()))
                .addKeyValue("correlationId", r.correlationId())
                .addKeyValue("state", state)
                .addKeyValue("result", result)
                .addKeyValue("durationMs", Duration.between(started, Instant.now()).toMillis())
                .log();
    }

    static String mask(String externalUserId) {
        if (externalUserId == null || externalUserId.length() <= 4) {
            return "***";
        }
        return "*".repeat(externalUserId.length() - 4) + externalUserId.substring(externalUserId.length() - 4);
    }

    /** The platform refused the request (4xx): a bug or a lifecycle refusal (inactive person), never retried. */
    public static class PlatformRejectedException extends RuntimeException {
        private final int status;

        public PlatformRejectedException(int status, String body) {
            super("platform refused the turn: HTTP " + status + (body == null ? "" : " " + body));
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
