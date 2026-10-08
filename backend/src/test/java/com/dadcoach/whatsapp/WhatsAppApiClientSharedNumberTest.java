package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.integration.platform.SharedNumberGate;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Dad Coach shares its WhatsApp number (2026-10-08): before each message it asks the platform's gateway whether the
 * father is on Dad Coach; when he is on another product the message is kept there and never reaches Meta now.
 */
class WhatsAppApiClientSharedNumberTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicInteger metaCalls = new AtomicInteger();
    private final AtomicReference<String> gateBody = new AtomicReference<>();
    private final AtomicReference<String> gateKey = new AtomicReference<>();
    private final AtomicReference<String> gateAnswer = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v21.0/PNID/messages", exchange -> {
            metaCalls.incrementAndGet();
            respond(exchange, 200, "{\"messages\":[{\"id\":\"wamid.DC\"}]}");
        });
        server.createContext("/api/v1/worker/whatsapp/outbound-gate", exchange -> {
            gateKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            gateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = gateAnswer.get();
            respond(exchange, answer == null ? 500 : 200, answer == null ? "{}" : answer);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aMessageForAFatherOnAnotherProductIsKeptByTheGateway() throws Exception {
        gateAnswer.set("{\"send\":false,\"heldId\":12,\"route\":\"dad-coach\",\"activeRoute\":\"tair\",\"why\":\"ON_ANOTHER_PRODUCT\"}");

        WhatsAppApiClient.SendResponse response = client(true).sendMessage(text("היום ב-17:00 זה הזמן שלך ושל איתמר 🙂"));

        assertThat(response.success()).isTrue();
        assertThat(response.messageId()).isEqualTo("held:12");
        assertThat(metaCalls.get()).isZero();
        assertThat(gateKey.get()).isEqualTo("platform-key");
        JsonNode asked = MAPPER.readTree(gateBody.get());
        assertThat(asked.get("workerKey").asText()).isEqualTo("dad_3");
        assertThat(asked.get("phone").asText()).isEqualTo("972503020551");
        assertThat(asked.at("/payload/text/body").asText()).isEqualTo("היום ב-17:00 זה הזמן שלך ושל איתמר 🙂");
    }

    @Test
    void onDadCoachOrWithoutAnAnswerItIsSentAsBefore() {
        gateAnswer.set("{\"send\":true,\"route\":\"dad-coach\",\"activeRoute\":\"dad-coach\",\"why\":\"ON_THIS_PRODUCT\"}");
        assertThat(client(true).sendMessage(text("hi")).messageId()).isEqualTo("wamid.DC");

        gateAnswer.set(null);                                            // the platform fails: never silences Dad Coach
        assertThat(client(true).sendMessage(text("hi")).messageId()).isEqualTo("wamid.DC");

        gateBody.set(null);
        assertThat(client(false).sendMessage(text("hi")).messageId()).as("gate switched off").isEqualTo("wamid.DC");
        assertThat(gateBody.get()).isNull();
        assertThat(metaCalls.get()).isEqualTo(3);
    }

    private WhatsAppApiClient client(boolean gateOn) {
        String base = "http://localhost:" + server.getAddress().getPort();
        WorkflowPlatformProperties platform = new WorkflowPlatformProperties();
        platform.setEnabled(true);
        platform.setBaseUrl(base);
        platform.setApiKey("platform-key");
        WhatsAppProperties whatsApp = new WhatsAppProperties(base, "v21.0", "PNID", null, "token", null, null);
        return new WhatsAppApiClient(WebClient.builder(), whatsApp, new SharedNumberGate(platform, gateOn));
    }

    private static Map<String, Object> text(String body) {
        return Map.of("messaging_product", "whatsapp", "to", "972503020551", "type", "text", "text", Map.of("body", body));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
