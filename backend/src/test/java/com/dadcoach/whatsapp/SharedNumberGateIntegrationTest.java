package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.support.FakeServers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The running app asks the platform's gate before each WhatsApp send (2026-10-08), with its own worker key. */
class SharedNumberGateIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WhatsAppApiClient whatsApp;

    @Test
    void aMessageForAFatherOnAnotherProductWaitsAtTheGatewayAndNeverReachesMeta() {
        fake.onGate(c -> FakeServers.Reply.json(
                "{\"send\":false,\"heldId\":31,\"route\":\"dad-coach\",\"activeRoute\":\"tair\",\"why\":\"ON_ANOTHER_PRODUCT\"}"));

        WhatsAppApiClient.SendResponse response = whatsApp.sendMessage(text("היום ב-17:00 זה הזמן שלך ושל איתמר 🙂"));

        assertThat(response.messageId()).isEqualTo("held:31");
        assertThat(fake.metaSends()).isEmpty();
        FakeServers.Call asked = fake.calls("/api/v1/worker/whatsapp/outbound-gate").get(0);
        assertThat(asked.header("X-API-Key")).isEqualTo("test-platform-key-0123456789");
        assertThat(asked.body()).contains("\"workerKey\":\"dad_3\"").contains("972503020551");
    }

    @Test
    void aMessageForAFatherOnDadCoachIsSentAsAlways() {
        WhatsAppApiClient.SendResponse response = whatsApp.sendMessage(text("hi"));

        assertThat(response.messageId()).startsWith("wamid.out.");
        assertThat(fake.metaSends()).hasSize(1);
    }

    private static Map<String, Object> text(String body) {
        return Map.of("messaging_product", "whatsapp", "to", "972503020551", "type", "text", "text", Map.of("body", body));
    }
}
