package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Signed webhooks end to end (playbook §34): the platform and Meta are a real HTTP stub (FakeServers), the
 * database is real. Turns run inline in tests (dad-coach.whatsapp.inbound-sync); serial execution per sender is
 * covered by InboundTurnExecutorTest.
 */
class WhatsAppWebhookTest extends AbstractIntegrationTest {

    static final String NEW_NUMBER = "+19995550100";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        return mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw));
    }

    private JsonNode lastTurn() throws Exception {
        return json.readTree(fake.turns().get(fake.turns().size() - 1).body());
    }

    private String sentText(int index) throws Exception {
        return json.readTree(fake.metaSends().get(index).body()).path("text").path("body").asText();
    }

    @Test
    void theVerificationHandshake() throws Exception {
        mvc.perform(get("/webhook/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "test-verify-token")
                .param("hub.challenge", "42")).andExpect(status().isOk()).andExpect(content().string("42"));
        mvc.perform(get("/webhook/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                .param("hub.challenge", "42")).andExpect(status().isForbidden());
    }

    @Test
    void anUnsignedOrWronglySignedWebhookIsRefusedAndRunsNothing() throws Exception {
        byte[] raw = Webhooks.text(NEW_NUMBER, "wamid.bad", "שלום").getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(raw)).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(raw)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, "another-secret"))).andExpect(status().isUnauthorized());
        assertThat(fake.turns()).isEmpty();
    }

    @Test
    void aNewNumberRunsTheTurnWithTheRequiredEnvelopeAndGetsTheReply() throws Exception {
        webhook(Webhooks.text(NEW_NUMBER, "wamid.first", "היי")).andExpect(status().isOk());

        JsonNode turn = lastTurn();
        assertThat(turn.path("workerKey").asText()).isEqualTo("dad_3");
        assertThat(turn.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(turn.path("userId").asText()).isEqualTo("whatsapp:" + NEW_NUMBER);
        assertThat(turn.path("channelId").asText()).isEqualTo("whatsapp");
        assertThat(turn.path("correlationId").asText()).isEqualTo("wamid.first");
        assertThat(turn.path("content").asText()).isEqualTo("היי");
        assertThat(turn.path("metadata").path("timezone").asText()).isEqualTo("Asia/Jerusalem");
        assertThat(turn.path("tenantId").asText()).isEqualTo("20082bcd-a7bf-57a8-a382-4bad32144b2f");
        assertThat(turn.path("personRef").isNull()).isTrue();
        assertThat(fake.turns().get(0).header("X-API-Key")).isEqualTo("test-platform-key-0123456789");
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(sentText(0)).isEqualTo("שלום! מה שלומך?");
    }

    @Test
    void aKnownFatherSendsHisPersonRefNameAndTimezoneAndGetsAnEndpointWithAnOpenWindow() throws Exception {
        Father father = data.activeFather("+19995550101");
        father.setTimezone("Europe/London");
        jdbc.update("UPDATE father SET timezone = 'Europe/London' WHERE id = ?", father.getId());

        webhook(Webhooks.text(father.getPhone(), "wamid.known", "מה המצב?")).andExpect(status().isOk());

        JsonNode turn = lastTurn();
        assertThat(turn.path("personRef").asText()).isEqualTo(new java.util.UUID(0L, father.getId()).toString());
        assertThat(turn.path("personName").asText()).isEqualTo("דני");
        assertThat(turn.path("metadata").path("timezone").asText()).isEqualTo("Europe/London");
        // F1: the endpoint every proactive message needs exists, with the 24-hour window open from now
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_endpoints WHERE channel = 'WHATSAPP' "
                + "AND channel_identity = ? AND session_closes_at > ?", Integer.class, father.getPhone(),
                java.sql.Timestamp.from(clock.instant()))).isEqualTo(1);
    }

    @Test
    void aMetaRetryOfTheSameMessageIsDroppedNeverAnsweredTwice() throws Exception {
        webhook(Webhooks.text(NEW_NUMBER, "wamid.dup", "היי")).andExpect(status().isOk());
        webhook(Webhooks.text(NEW_NUMBER, "wamid.dup", "היי")).andExpect(status().isOk());
        assertThat(fake.turns()).hasSize(1);
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency WHERE scope = 'WHATSAPP_INBOUND' "
                + "AND idempotency_key = 'wamid.dup'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aSuppressedOrBlankReplySendsNothing() throws Exception {
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("[[SUPPRESS_RESPONSE]]", "SUPPRESSED")));
        webhook(Webhooks.text(NEW_NUMBER, "wamid.sup", "תודה")).andExpect(status().isOk());
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("  ", "GENERATED")));
        webhook(Webhooks.text(NEW_NUMBER, "wamid.blank", "תודה")).andExpect(status().isOk());
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply(null, "GENERATED")));
        webhook(Webhooks.text(NEW_NUMBER, "wamid.null", "תודה")).andExpect(status().isOk());
        assertThat(fake.turns()).hasSize(3);
        assertThat(fake.metaSends()).isEmpty();
    }

    /** Owner, 2026-10-08: the button to his page is the whole answer - no "sent you a button" line under it. */
    @Test
    void theButtonToHisPageIsTheWholeAnswer() throws Exception {
        Father father = data.activeFather("+19995550102");
        String sentLine = com.dadcoach.api.tools.DashboardTools.SENT_REPLY;
        // the coach's tool sends the button in this turn (a SENT link row at the turn's time; the test clock stands
        // still), then the coach says only that
        jdbc.update("INSERT INTO login_link (id, father_id, token_hash, created_at, expires_at, delivery_status) "
                + "VALUES (?, ?, ?, ?, ?, 'SENT')", java.util.UUID.randomUUID(), father.getId(), "h" + System.nanoTime(),
                java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant().plusSeconds(3600)));
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("❤️ דאד קואץ׳:\\n" + sentLine, "GENERATED")));
        webhook(Webhooks.text(father.getPhone(), "wamid.page1", "תן לי דשבורד")).andExpect(status().isOk());
        assertThat(fake.metaSends()).isEmpty();
        // the same line ending with an emoji instead of the period
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply(sentLine.replace(".", " 🙂"), "GENERATED")));
        webhook(Webhooks.text(father.getPhone(), "wamid.page1b", "שלח לי את הדף שלי")).andExpect(status().isOk());
        assertThat(fake.metaSends()).isEmpty();
        // his own words (prod 2026-10-08 12:46 - this one went out under the card)
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("❤️ דאד קואץ׳:\\nשלחתי לך כפתור לדף שלך 😊", "GENERATED")));
        webhook(Webhooks.text(father.getPhone(), "wamid.page1c", "הייתי לי דשבורד")).andExpect(status().isOk());
        assertThat(fake.metaSends()).isEmpty();
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("הנה הכפתור לדף שלך בדאד קואץ׳, הוא ממשיך לעבוד תמיד 👇", "GENERATED")));
        webhook(Webhooks.text(father.getPhone(), "wamid.page1d", "דשבורד")).andExpect(status().isOk());
        assertThat(fake.metaSends()).isEmpty();
        // a reply that says more than "sent" still goes, even with the button in the same turn
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply(
                "❤️ דאד קואץ׳:\\nאפשר לתקן את השם בדף שלך תחת ילדים, שלחתי לך כפתור", "GENERATED")));
        webhook(Webhooks.text(father.getPhone(), "wamid.page1e", "השם של הבן שלי נכתב לא נכון")).andExpect(status().isOk());
        assertThat(fake.metaSends()).hasSize(1);

        // no button this turn (already sent a minute ago): his line goes
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("❤️ דאד קואץ׳:\\n" + sentLine, "GENERATED")));
        clock.advance(java.time.Duration.ofMinutes(1));
        webhook(Webhooks.text(father.getPhone(), "wamid.page2", "תן לי דשבורד")).andExpect(status().isOk());
        assertThat(fake.metaSends()).hasSize(2);
    }

    @Test
    void aPlatformFailureGetsOneShortHebrewLineNeverEnglish() throws Exception {
        fake.onTurn(c -> new FakeServers.Reply(500, "{\"error\":\"boom\"}"));
        webhook(Webhooks.text(NEW_NUMBER, "wamid.down", "היי")).andExpect(status().isOk());
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(sentText(0)).isEqualTo("❤️ דאד קואץ׳:\nמשהו השתבש אצלי. נסה שוב עוד רגע.");

        fake.onTurn(c -> new FakeServers.Reply(409, "{\"code\":\"person.inactive\"}"));
        webhook(Webhooks.text(NEW_NUMBER, "wamid.refused", "היי")).andExpect(status().isOk());
        assertThat(fake.metaSends()).hasSize(2);
        assertThat(sentText(1)).isEqualTo("❤️ דאד קואץ׳:\nמשהו השתבש אצלי. נסה שוב עוד רגע.");
    }

    @Test
    void aVoiceNoteWithVoiceNotesTurnedOffGetsTheFixedLineAndNoTurn() throws Exception {
        jdbc.update("INSERT INTO system_setting (key, value) VALUES ('voice_notes.enabled', 'false')");
        webhook(Webhooks.audio(NEW_NUMBER, "wamid.voice")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(fake.mediaCalls()).isEmpty();
        assertThat(sentText(0)).contains("אפשר לכתוב לי במילים");
    }

    @Test
    void aPhotoWithACaptionReachesTheCoachMarkedAsAPhoto() throws Exception {
        webhook(Webhooks.image(NEW_NUMBER, "wamid.photo", "תראה מה בנינו!")).andExpect(status().isOk());
        assertThat(lastTurn().path("content").asText()).isEqualTo("[photo] תראה מה בנינו!");
    }

    @Test
    void aReactionIsNeverAnswered() throws Exception {
        webhook(Webhooks.reaction(NEW_NUMBER, "wamid.reaction", "❤️")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void statusReceiptsAreAcknowledgedAndIgnored() throws Exception {
        webhook(Webhooks.status("wamid.out.1")).andExpect(status().isOk());
        webhook("{\"object\":\"whatsapp_business_account\",\"entry\":[]}").andExpect(status().isOk());
        webhook("not json").andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void aDeletedFathersMessagesNeverReachTheAi() throws Exception {
        Father father = data.father("+19995550102", "יוסי", FatherStatus.DELETED);
        webhook(Webhooks.text(father.getPhone(), "wamid.deleted", "היי")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    void deleteMyDataIsHandledHereNeverByTheAi() throws Exception {
        Father father = data.father("+19995550103", "אבי", FatherStatus.ONBOARDING);
        webhook(Webhooks.text(father.getPhone(), "wamid.del", "DELETE MY DATA")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(sentText(0)).startsWith("❤️ דאד קואץ׳:\nקיבלתי. אני מוחק עכשיו את כל המידע שלך:\n• ");
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father.getId())).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT purge_local FROM platform_person_deletion WHERE father_id = ?", Boolean.class,
                father.getId())).isTrue();
    }

    @Test
    void theHebrewPhraseDeletesToo() throws Exception {
        Father father = data.father("+19995550104", "אבי", FatherStatus.ACTIVE);
        webhook(Webhooks.text(father.getPhone(), "wamid.del-he", "מחק את המידע שלי")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(sentText(0)).contains("דאד קואץ׳");
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father.getId())).isEqualTo("DELETED");
    }
}
