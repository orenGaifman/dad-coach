package com.dadcoach.whatsapp;

import com.dadcoach.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Non-blocking HTTP client wrapping Spring WebClient for outbound calls
 * to the WhatsApp Cloud API.
 *
 * <p>Handles authorization, request serialization, and response classification
 * (success, rate-limited, or error). Rate limit responses (HTTP 429) are surfaced
 * via {@link RateLimitException} with the Retry-After duration.
 */
@Component
public class WhatsAppApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final WhatsAppProperties properties;

    public WhatsAppApiClient(WebClient.Builder webClientBuilder, WhatsAppProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .build();
    }

    /**
     * Sends a message payload to the WhatsApp Cloud API.
     *
     * @param payload the formatted message payload (as produced by WhatsAppMessageFormatter)
     * @return the API response containing the provider message ID
     * @throws RateLimitException if the API returns HTTP 429
     * @throws WhatsAppApiException for other non-2xx responses
     */
    public SendResponse sendMessage(Map<String, Object> payload) {
        String uri = String.format("/%s/%s/messages", properties.apiVersion(), properties.phoneNumberId());

        return webClient.post()
                .uri(uri)
                .bodyValue(payload)
                .exchangeToMono(this::handleResponse)
                .block(REQUEST_TIMEOUT);
    }

    /**
     * Sends an interactive button message.
     *
     * @param recipientPhone the recipient's phone number
     * @param bodyText the message body
     * @param buttons the buttons to display
     * @return the send response
     */
    public SendResponse sendButtonMessage(
            String recipientPhone,
            String bodyText,
            java.util.List<WhatsAppMessageFormatter.InteractiveButton> buttons,
            WhatsAppMessageFormatter formatter) {
        
        Map<String, Object> payload = formatter.formatButtonMessage(recipientPhone, bodyText, buttons);
        return sendMessage(payload);
    }

    /**
     * Sends an interactive list message.
     *
     * @param recipientPhone the recipient's phone number
     * @param headerText optional header text
     * @param bodyText the message body
     * @param buttonText the list button text
     * @param sections the list sections
     * @return the send response
     */
    public SendResponse sendListMessage(
            String recipientPhone,
            String headerText,
            String bodyText,
            String buttonText,
            java.util.List<WhatsAppMessageFormatter.InteractiveSection> sections,
            WhatsAppMessageFormatter formatter) {
        
        Map<String, Object> payload = formatter.formatListMessage(
            recipientPhone, headerText, bodyText, buttonText, sections);
        return sendMessage(payload);
    }

    /**
     * Sends an image message with URL.
     *
     * @param recipientPhone the recipient's phone number
     * @param imageUrl the URL of the image to send
     * @param caption optional caption for the image
     * @return the send response
     */
    public SendResponse sendImageMessage(
            String recipientPhone,
            String imageUrl,
            String caption,
            WhatsAppMessageFormatter formatter) {
        
        Map<String, Object> payload = formatter.formatImageMessage(recipientPhone, imageUrl, caption);
        return sendMessage(payload);
    }

    private Mono<SendResponse> handleResponse(ClientResponse response) {
        HttpStatusCode status = response.statusCode();

        if (status.is2xxSuccessful()) {
            return response.bodyToMono(SendResponseBody.class)
                    .map(body -> {
                        String messageId = extractMessageId(body);
                        return new SendResponse(true, messageId, null);
                    });
        }

        if (status.value() == 429) {
            String retryAfter = response.headers().asHttpHeaders().getFirst("Retry-After");
            Duration backoff = parseRetryAfter(retryAfter);
            return response.releaseBody()
                    .then(Mono.error(new RateLimitException(backoff)));
        }

        return response.bodyToMono(String.class)
                .defaultIfEmpty("No response body")
                .flatMap(body -> {
                    log.error("WhatsApp API error (HTTP {}): {}", status.value(), body);
                    return Mono.error(new WhatsAppApiException(status.value(), body));
                });
    }

    private String extractMessageId(SendResponseBody body) {
        if (body != null && body.messages() != null && !body.messages().isEmpty()) {
            return body.messages().get(0).id();
        }
        return null;
    }

    private Duration parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return Duration.ofSeconds(60); // default backoff if header missing
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(60);
        }
    }

    // ─── Inner types ─────────────────────────────────────────────────────

    /**
     * Result of a send attempt.
     */
    public record SendResponse(boolean success, String messageId, String errorDetail) {}

    /**
     * Deserialized WhatsApp Cloud API send response body.
     */
    record SendResponseBody(
        String messagingProduct,
        java.util.List<MessageRef> messages
    ) {}

    record MessageRef(String id) {}

    /**
     * Thrown when the WhatsApp API returns HTTP 429 (rate limited).
     */
    public static class RateLimitException extends RuntimeException {
        private final Duration retryAfter;

        public RateLimitException(Duration retryAfter) {
            super("WhatsApp API rate limited. Retry after " + retryAfter.getSeconds() + "s");
            this.retryAfter = retryAfter;
        }

        public Duration getRetryAfter() {
            return retryAfter;
        }
    }

    /**
     * Thrown when the WhatsApp API returns a non-2xx, non-429 error.
     */
    public static class WhatsAppApiException extends RuntimeException {
        private final int httpStatus;
        private final String responseBody;

        public WhatsAppApiException(int httpStatus, String responseBody) {
            super("WhatsApp API error (HTTP " + httpStatus + "): " + responseBody);
            this.httpStatus = httpStatus;
            this.responseBody = responseBody;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
