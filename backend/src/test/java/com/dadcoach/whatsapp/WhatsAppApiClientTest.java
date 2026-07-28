package com.dadcoach.whatsapp;

import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.whatsapp.WhatsAppApiClient.RateLimitException;
import com.dadcoach.whatsapp.WhatsAppApiClient.SendResponse;
import com.dadcoach.whatsapp.WhatsAppApiClient.WhatsAppApiException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WhatsAppApiClient using WireMock to verify HTTP interactions
 * with the WhatsApp Cloud API.
 */
class WhatsAppApiClientTest {

    private static WireMockServer wireMock;
    private WhatsAppApiClient apiClient;

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
    void setUp() {
        wireMock.resetAll();

        WhatsAppProperties properties = new WhatsAppProperties(
                "http://localhost:" + wireMock.port(),
                "v18.0",
                "123456789",
                "test-access-token",
                "test-verify-token",
                "test-webhook-secret"
        );

        apiClient = new WhatsAppApiClient(WebClient.builder(), properties);
    }

    private Map<String, Object> samplePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", "+5491112345678");
        payload.put("type", "text");
        payload.put("text", Map.of("body", "Hello!"));
        return payload;
    }

    @Nested
    @DisplayName("5.2 WebClient outbound API calls")
    class OutboundCalls {

        @Test
        @DisplayName("sends POST request to correct endpoint with Bearer token")
        void sendsToCorrectEndpoint() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "messaging_product": "whatsapp",
                                    "messages": [{"id": "wamid.HBgMNTQ5MTEyMzQ1Njc4"}]
                                }
                                """)));

            apiClient.sendMessage(samplePayload());

            verify(postRequestedFor(urlEqualTo("/v18.0/123456789/messages"))
                    .withHeader("Authorization", equalTo("Bearer test-access-token"))
                    .withHeader("Content-Type", containing("application/json")));
        }

        @Test
        @DisplayName("successful response returns SendResponse with message ID")
        void successfulResponseReturnsSendResponse() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "messaging_product": "whatsapp",
                                    "messages": [{"id": "wamid.ABC123"}]
                                }
                                """)));

            SendResponse result = apiClient.sendMessage(samplePayload());

            assertTrue(result.success());
            assertEquals("wamid.ABC123", result.messageId());
            assertNull(result.errorDetail());
        }

        @Test
        @DisplayName("sends message body as JSON")
        void sendsJsonBody() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {"messaging_product":"whatsapp","messages":[{"id":"wamid.X"}]}
                                """)));

            apiClient.sendMessage(samplePayload());

            verify(postRequestedFor(urlEqualTo("/v18.0/123456789/messages"))
                    .withRequestBody(containing("\"messaging_product\":\"whatsapp\""))
                    .withRequestBody(containing("\"to\":\"+5491112345678\"")));
        }
    }

    @Nested
    @DisplayName("5.5 Rate limit handling")
    class RateLimitHandling {

        @Test
        @DisplayName("HTTP 429 throws RateLimitException with Retry-After duration")
        void http429ThrowsRateLimitException() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withHeader("Retry-After", "45")
                            .withBody("Rate limited")));

            RateLimitException ex = assertThrows(RateLimitException.class,
                    () -> apiClient.sendMessage(samplePayload()));

            assertEquals(Duration.ofSeconds(45), ex.getRetryAfter());
        }

        @Test
        @DisplayName("HTTP 429 without Retry-After defaults to 60s")
        void http429WithoutRetryAfterDefaultsTo60s() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withBody("Rate limited")));

            RateLimitException ex = assertThrows(RateLimitException.class,
                    () -> apiClient.sendMessage(samplePayload()));

            assertEquals(Duration.ofSeconds(60), ex.getRetryAfter());
        }
    }

    @Nested
    @DisplayName("Error responses")
    class ErrorResponses {

        @Test
        @DisplayName("non-2xx non-429 response throws WhatsAppApiException")
        void non2xxThrowsApiException() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\":{\"message\":\"Invalid phone number\"}}")));

            WhatsAppApiException ex = assertThrows(WhatsAppApiException.class,
                    () -> apiClient.sendMessage(samplePayload()));

            assertEquals(400, ex.getHttpStatus());
            assertTrue(ex.getResponseBody().contains("Invalid phone number"));
        }

        @Test
        @DisplayName("500 server error throws WhatsAppApiException")
        void serverErrorThrowsApiException() {
            stubFor(post(urlEqualTo("/v18.0/123456789/messages"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            WhatsAppApiException ex = assertThrows(WhatsAppApiException.class,
                    () -> apiClient.sendMessage(samplePayload()));

            assertEquals(500, ex.getHttpStatus());
        }
    }
}
