package com.platform.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.net.ssl.SSLHandshakeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * The platform's Send API as a product calls it (platform PHASE6_2_SEND_API_SPEC §3.1-3.2, §16, v2.5-v2.6). One call,
 * never retried here (the caller decides, by {@link Answer#kind()}): the request body goes out exactly as given, byte for
 * byte, so a re-POST of an ambiguous attempt hashes the same on the platform. Every answer is read, whatever its status -
 * the caller needs the body code of a 503 and the existing message's state on a 409. Never logs a body (it may carry a
 * sign-in link). A product wires it with its own settings ({@link Config}, read on every call).
 *
 * <p>Canonical source: ai-workflow-platform {@code platform-client/}. Products carry a byte-identical copy
 * ({@code scripts/platform_client.py sync}); never edit it in a product.</p>
 */
public class PlatformMessagingClient {

    public static final String SEND_PATH = "/api/v1/worker/messages/send";
    public static final String MESSAGES_PATH = "/api/v1/worker/messages";
    /** Spec 6.4 §2.1: the platform's kind→template mapping of the product's worker, with each candidate's Meta status. */
    public static final String KINDS_PATH = "/api/v1/worker/messaging/kinds";
    /** Spec 6.4 §4.4: the templates the platform read from the sender's WABA. */
    public static final String TEMPLATES_PATH = "/api/v1/worker/whatsapp/templates";
    /** The platform's kill switch: nothing was created (spec v2.5: only with this body code). */
    public static final String DISABLED = "messaging.disabled";
    public static final String IDEMPOTENCY_CONFLICT = "messaging.idempotency_conflict";
    public static final String MESSAGE_NOT_FOUND = "messaging.message_not_found";

    /**
     * The product's settings: base URL and key (fixed when the client is built), worker key, timeouts and whether the
     * platform is on (read on every call).
     */
    public record Config(boolean enabled, String baseUrl, String apiKey, String workerKey, int connectTimeoutMs,
                         int readTimeoutMs, int lookupTimeoutMs) {}

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

    private final Supplier<Config> config;
    private final ObjectMapper json;
    private final WebClient web;

    public PlatformMessagingClient(Supplier<Config> config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        Config c = config.get();
        // disableRetry: Reactor Netty would otherwise re-send a request once on a connection closed before the answer -
        // a hidden second POST; every re-POST here is the caller's decision
        HttpClient http = HttpClient.create().disableRetry(true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, c.connectTimeoutMs());
        this.web = WebClient.builder()
                .baseUrl(c.baseUrl() == null ? "" : c.baseUrl())
                .defaultHeader("X-API-Key", c.apiKey() == null ? "" : c.apiKey())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }

    public boolean isConfigured() {
        Config c = config.get();
        return c.enabled() && c.baseUrl() != null && !c.baseUrl().isBlank();
    }

    /** The platform worker the product's messages belong to. */
    public String workerKey() {
        return config.get().workerKey();
    }

    /** {@code POST /send} with exactly these bytes (UTF-8 JSON). */
    public Answer send(String body) {
        return send(body, config.get().readTimeoutMs());
    }

    /** {@code POST /send} with its own read timeout (e.g. a turn reply that must not wait long). */
    public Answer send(String body, int readTimeoutMs) {
        if (!isConfigured()) {
            return new Answer(Kind.NEVER_WRITTEN, 0, null, "NOT_CONFIGURED", null);
        }
        return call(web.post().uri(SEND_PATH).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.getBytes(StandardCharsets.UTF_8)), readTimeoutMs);
    }

    /** {@code GET /messages?workerKey&idempotencyKey} (v2.5): 200 = the platform has it, 404 = it created nothing. */
    public Answer byKey(String idempotencyKey) {
        if (!isConfigured()) {
            return new Answer(Kind.NEVER_WRITTEN, 0, null, "NOT_CONFIGURED", null);
        }
        // a short wait: a lookup must not hold the product's messaging lane
        return call(web.get().uri(b -> b.path(MESSAGES_PATH).queryParam("workerKey", "{w}")
                .queryParam("idempotencyKey", "{k}").build(workerKey(), idempotencyKey)), config.get().lookupTimeoutMs());
    }

    /** {@code GET /messaging/kinds?workerKey}: which mapped (kind, variant) could go as which template now. */
    public Answer kinds() {
        return workerGet(KINDS_PATH);
    }

    /** {@code GET /whatsapp/templates?workerKey}: the templates the platform read from the sender's WABA. */
    public Answer templates() {
        return workerGet(TEMPLATES_PATH);
    }

    private Answer workerGet(String path) {
        if (!isConfigured()) {
            return new Answer(Kind.NEVER_WRITTEN, 0, null, "NOT_CONFIGURED", null);
        }
        return call(web.get().uri(b -> b.path(path).queryParam("workerKey", "{w}").build(workerKey())), config.get().lookupTimeoutMs());
    }

    private Answer call(WebClient.RequestHeadersSpec<?> request, int timeoutMs) {
        try {
            Answer answer = request.exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                            .map(bytes -> {
                                int status = response.statusCode().value();
                                Long retryAfter = response.headers().header("Retry-After").stream().findFirst()
                                        .map(PlatformMessagingClient::seconds).orElse(null);
                                return new Answer(Kind.ANSWERED, status, parse(bytes), null, retryAfter);
                            }))
                    .timeout(Duration.ofMillis(timeoutMs))
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
    public static Answer failure(Throwable e) {
        Throwable cause = e;
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
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
