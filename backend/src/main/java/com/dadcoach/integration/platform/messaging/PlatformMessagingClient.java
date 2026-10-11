package com.dadcoach.integration.platform.messaging;

import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * D-045: the platform's Send API as Dad Coach calls it (platform spec PHASE6_2_SEND_API_SPEC §3.1-3.2, §16, Spec v2
 * v2.5-v2.6). One call, never retried here (the caller decides, by {@link Answer#kind()}): the request body goes out
 * exactly as given, byte for byte, so a re-POST of an ambiguous attempt hashes the same on the platform. Every answer is
 * read, whatever its status - the caller needs the body code of a 503 and the existing message's state on a 409.
 * Never logs a body (it carries the sign-in link).
 */
@Component
public class PlatformMessagingClient {

    public static final String SEND_PATH = "/api/v1/worker/messages/send";
    public static final String MESSAGES_PATH = "/api/v1/worker/messages";
    /** The platform's kill switch: nothing was created (spec v2.5: only with this body code). */
    public static final String DISABLED = "messaging.disabled";
    public static final String IDEMPOTENCY_CONFLICT = "messaging.idempotency_conflict";
    public static final String MESSAGE_NOT_FOUND = "messaging.message_not_found";

    /** What a call came to, from the product's point of view (spec §16 table, v2.5). */
    public enum Kind {
        /** An HTTP answer from the application (its status and body are in the answer). */
        ANSWERED,
        /** The request was provably never written: connection refused, DNS, TLS handshake, or no platform configured. */
        NEVER_WRITTEN,
        /** It may have reached the platform: timeout, connection lost after write, an unreadable answer. */
        AMBIGUOUS
    }

    /** One call's result: {@code status} 0 when there was no HTTP answer; {@code body} null when it was not JSON. */
    public record Answer(Kind kind, int status, JsonNode body, String error, Long retryAfterSeconds) {

        /** The body's {@code error} code ({@code messaging.disabled}, ...), or null. */
        public String errorCode() {
            return body == null || !body.hasNonNull("error") ? null : body.get("error").asText();
        }

        public String text(String field) {
            return body == null || !body.hasNonNull(field) ? null : body.get(field).asText();
        }

        /** What to log: the status, else the transport error. */
        public String summary() {
            return status > 0 ? "HTTP_" + status + (errorCode() == null ? "" : " " + errorCode()) : error;
        }
    }

    private final WorkflowPlatformProperties platform;
    private final MessagingSettings settings;
    private final ObjectMapper json;
    private final WebClient web;

    public PlatformMessagingClient(WorkflowPlatformProperties platform, MessagingSettings settings, ObjectMapper json) {
        this.platform = platform;
        this.settings = settings;
        this.json = json;
        // disableRetry: Reactor Netty would otherwise re-send a request once on a connection closed before the answer -
        // a hidden second POST; every re-POST here is the caller's decision (review L3)
        HttpClient http = HttpClient.create().disableRetry(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, settings.connectTimeoutMs());
        this.web = WebClient.builder()
                .baseUrl(platform.getBaseUrl())
                .defaultHeader("X-API-Key", platform.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }

    public boolean isConfigured() {
        return platform.isEnabled() && !platform.getBaseUrl().isBlank();
    }

    /** {@code POST /send} with exactly these bytes (UTF-8 JSON). */
    public Answer send(String body) {
        if (!isConfigured()) {
            return new Answer(Kind.NEVER_WRITTEN, 0, null, "NOT_CONFIGURED", null);
        }
        return call(web.post().uri(SEND_PATH).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** {@code GET /messages?workerKey&idempotencyKey} (v2.5): 200 = the platform has it, 404 = it created nothing. */
    public Answer byKey(String idempotencyKey) {
        if (!isConfigured()) {
            return new Answer(Kind.NEVER_WRITTEN, 0, null, "NOT_CONFIGURED", null);
        }
        return call(web.get().uri(b -> b.path(MESSAGES_PATH).queryParam("workerKey", "{w}")
                .queryParam("idempotencyKey", "{k}").build(platform.getWorkerKey(), idempotencyKey)));
    }

    private Answer call(WebClient.RequestHeadersSpec<?> request) {
        try {
            Answer answer = request.exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                            .map(bytes -> {
                                int status = response.statusCode().value();
                                Long retryAfter = response.headers().header("Retry-After").stream().findFirst()
                                        .map(PlatformMessagingClient::seconds).orElse(null);
                                return new Answer(Kind.ANSWERED, status, parse(bytes), null, retryAfter);
                            }))
                    .timeout(Duration.ofMillis(settings.readTimeoutMs()))
                    .onErrorResume(e -> Mono.just(failure(e)))
                    .block();
            return answer != null ? answer : new Answer(Kind.AMBIGUOUS, 0, null, "NO_ANSWER", null);
        } catch (RuntimeException e) {
            return failure(e);
        }
    }

    private JsonNode parse(byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        try {
            JsonNode node = json.readTree(bytes);
            return node != null && node.isObject() ? node : null;
        } catch (java.io.IOException e) {
            return null;                       // an edge's HTML page (502/503/504): no body code
        }
    }

    /** Never written = refused before any byte left (spec §16 "connect refused / DNS / TLS"); everything else may have. */
    static Answer failure(Throwable e) {
        Throwable cause = e;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) {
                return new Answer(Kind.AMBIGUOUS, 0, null, "TIMEOUT", null);
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof SSLHandshakeException) {
                return new Answer(Kind.NEVER_WRITTEN, 0, null, t.getClass().getSimpleName(), null);
            }
            cause = t;
        }
        String name = e instanceof WebClientRequestException && cause != null ? cause.getClass().getSimpleName()
                : e.getClass().getSimpleName();
        return new Answer(Kind.AMBIGUOUS, 0, null, name, null);
    }

    private static Long seconds(String raw) {
        try {
            return Math.max(1L, Long.parseLong(raw.strip()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
