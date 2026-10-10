package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-038 rollback switches: with {@code dad-coach.whatsapp.receipts.enabled=false} receipts are ignored again (the
 * wamid is still kept), and with {@code dad-coach.whatsapp.inbound.retry-unanswered=false} an inbound message is marked
 * handled on arrival, as before D-038.
 */
@TestPropertySource(properties = {"dad-coach.whatsapp.receipts.enabled=false",
        "dad-coach.whatsapp.inbound.retry-unanswered=false"})
class DeliveryFlagsOffTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private void webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        assertThat(mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void receiptsAreIgnoredAndTheMessageIsMarkedOnArrival() throws Exception {
        Father f = data.activeFather("+19995550821");
        data.endpoint(f, true);
        AtomicBoolean platformUp = new AtomicBoolean(false);
        fake.onTurn(c -> platformUp.get() ? FakeServers.Reply.json(FakeServers.turnReply("שלום!", "GENERATED"))
                : new FakeServers.Reply(500, "{\"error\":\"boom\"}"));

        webhook(Webhooks.text(f.getPhone(), "wamid.off", "היי"));
        assertThat(jdbc.queryForObject("SELECT status FROM tool_idempotency WHERE scope = 'WHATSAPP_INBOUND' "
                + "AND idempotency_key = 'wamid.off'", String.class)).isEqualTo("SUCCEEDED");
        int turns = fake.turns().size();
        platformUp.set(true);
        webhook(Webhooks.text(f.getPhone(), "wamid.off", "היי"));
        assertThat(fake.turns()).as("old behaviour: the redelivery is dropped").hasSize(turns);

        // the dashboard link's wamid is still stored; its failed receipt is ignored
        jdbc.update("INSERT INTO login_link (id, father_id, token_hash, created_at, expires_at, delivery_status, provider_message_id) "
                + "VALUES (gen_random_uuid(), ?, 'h-off', now(), now() + interval '1 day', 'SENT', 'wamid.link.off')", f.getId());
        webhook(Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"statuses\":[{\"id\":\"wamid.link.off\","
                + "\"status\":\"failed\",\"timestamp\":\"1793700100\",\"recipient_id\":\"19995550821\",\"errors\":[{\"code\":131047,"
                + "\"title\":\"Re-engagement message\"}]}]}"));
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM login_link WHERE father_id = ?", String.class, f.getId()))
                .isEqualTo("SENT");
    }
}
