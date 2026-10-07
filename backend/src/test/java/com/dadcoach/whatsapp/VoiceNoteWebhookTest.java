package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
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
 * D-027 end to end: a signed voice-note webhook, Meta's media and ElevenLabs on FakeServers, the real database and
 * the admin's switch. A heard note is the same turn as the words typed; the reply opens with what was heard.
 */
class VoiceNoteWebhookTest extends AbstractIntegrationTest {

    static final String HEARD = "רוצה לקבוע זמן עם נועה ביום שישי";
    static final String DOWN = "משהו השתבש אצלי, נסה שוב עוד רגע 🙏";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions webhook(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        return mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", Webhooks.sign(raw, WEBHOOK_SECRET)).content(raw));
    }

    private String sentText(int index) throws Exception {
        return json.readTree(fake.metaSends().get(index).body()).path("text").path("body").asText();
    }

    @Test
    void aHeardNoteIsTheSameTurnAsTheWordsTypedAndTheReplyOpensWithThem() throws Exception {
        Father father = data.activeFather("+19995550201");
        fake.onTurn(c -> FakeServers.Reply.json(FakeServers.turnReply("❤️ דאד קואץ׳:\\nמעולה, נקבע לשישי?", "GENERATED")));

        webhook(Webhooks.audio(father.getPhone(), "wamid.voice-1", "media-41")).andExpect(status().isOk());

        // Meta: the lookup under the Graph version, then the file - both with Dad Coach's WhatsApp token
        assertThat(fake.mediaCalls()).extracting(FakeServers.Call::path).containsExactly("/v25.0/media-41", "/media-files/media-41");
        assertThat(fake.mediaCalls()).allSatisfy(c -> assertThat(c.header("Authorization")).isEqualTo("Bearer test-access-token"));
        // ElevenLabs: the key, Hebrew Scribe
        assertThat(fake.speechToTextCalls()).hasSize(1);
        assertThat(fake.speechToTextCalls().get(0).header("xi-api-key")).isEqualTo(ELEVENLABS_KEY);
        assertThat(fake.speechToTextCalls().get(0).body()).contains("scribe_v2").contains("heb");
        // the turn: the father's own turn, as text, with a Hebrew note that it was spoken
        JsonNode turn = json.readTree(fake.turns().get(0).body());
        assertThat(fake.turns()).hasSize(1);
        assertThat(turn.path("messageType").asText()).isEqualTo("text");
        assertThat(turn.path("correlationId").asText()).isEqualTo("wamid.voice-1");
        assertThat(turn.path("personRef").asText()).isEqualTo(new java.util.UUID(0L, father.getId()).toString());
        assertThat(turn.path("content").asText()).startsWith("[הודעה קולית").endsWith("\n" + HEARD);
        // the reply: identity line, then what was heard, then the coach
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(sentText(0)).isEqualTo("❤️ דאד קואץ׳:\n🎙️ שמעתי: \"" + HEARD + "\"\n\nמעולה, נקבע לשישי?");
        // his 24-hour window opened, like any message
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_endpoints WHERE channel_identity = ?", Integer.class,
                father.getPhone())).isEqualTo(1);
    }

    @Test
    void theAdminSwitchOffAnswersTheFixedLineWithoutDownloading() throws Exception {
        jdbc.update("INSERT INTO system_setting (key, value) VALUES ('voice_notes.enabled', 'false')");
        webhook(Webhooks.audio("+19995550202", "wamid.voice-off")).andExpect(status().isOk());
        assertThat(fake.mediaCalls()).isEmpty();
        assertThat(fake.speechToTextCalls()).isEmpty();
        assertThat(fake.turns()).isEmpty();
        assertThat(sentText(0)).isEqualTo("אני עדיין לא יכול לשמוע הקלטות או לראות קבצים 🙏 אפשר לכתוב לי במילים?");
    }

    @Test
    void outOfCreditIsItsOwnLineNoTurn() throws Exception {
        fake.onSpeechToText(c -> new FakeServers.Reply(401, "{\"detail\":{\"status\":\"quota_exceeded\"}}"));
        webhook(Webhooks.audio("+19995550203", "wamid.voice-quota")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(sentText(0)).startsWith("❤️ דאד קואץ׳:\n🎙️ לא הצלחתי לשמוע את ההקלטה");
    }

    @Test
    void aNoteTooLongIsNotDownloadedNorSent() throws Exception {
        fake.onMedia(c -> FakeServers.Reply.json("{\"url\":\"" + fake.baseUrl() + "/media-files/big\",\"mime_type\":\"audio/ogg\","
                + "\"file_size\":99000000}"));
        webhook(Webhooks.audio("+19995550204", "wamid.voice-long")).andExpect(status().isOk());
        assertThat(fake.mediaCalls()).extracting(FakeServers.Call::path).containsExactly("/v25.0/media1");
        assertThat(fake.speechToTextCalls()).isEmpty();
        assertThat(fake.turns()).isEmpty();
        assertThat(sentText(0)).contains("ארוכה מדי");
    }

    @Test
    void aSilentNoteAsksAgain() throws Exception {
        fake.onSpeechToText(c -> FakeServers.Reply.json("{\"language_code\":\"heb\",\"text\":\"  \"}"));
        webhook(Webhooks.audio("+19995550205", "wamid.voice-silent")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(sentText(0)).contains("לא שמעתי מילים").contains("נסה שוב");
    }

    @Test
    void metaNotGivingTheFileIsItsOwnLineNoTurn() throws Exception {
        fake.onMedia(c -> new FakeServers.Reply(404, "{}"));
        webhook(Webhooks.audio("+19995550206", "wamid.voice-404")).andExpect(status().isOk());
        assertThat(fake.speechToTextCalls()).isEmpty();
        assertThat(fake.turns()).isEmpty();
        assertThat(sentText(0)).contains("לא הצלחתי לשמוע");
    }

    @Test
    void thePlatformDownStillShowsWhatWasHeard() throws Exception {
        fake.onTurn(c -> new FakeServers.Reply(500, "{\"error\":\"boom\"}"));
        webhook(Webhooks.audio("+19995550207", "wamid.voice-down")).andExpect(status().isOk());
        assertThat(sentText(0)).isEqualTo("🎙️ שמעתי: \"" + HEARD + "\"\n\n" + DOWN);
    }

    @Test
    void theSpokenDeletionPhraseDeletesLikeTheTypedOne() throws Exception {
        Father father = data.activeFather("+19995550208");
        fake.onSpeechToText(c -> FakeServers.Reply.json("{\"text\":\"מחק את המידע שלי.\"}"));
        webhook(Webhooks.audio(father.getPhone(), "wamid.voice-del")).andExpect(status().isOk());
        assertThat(fake.turns()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, father.getId())).isEqualTo("DELETED");
    }

    @Test
    void typedTextIsUntouched() throws Exception {
        webhook(Webhooks.text("+19995550209", "wamid.typed", "היי")).andExpect(status().isOk());
        assertThat(fake.mediaCalls()).isEmpty();
        assertThat(json.readTree(fake.turns().get(0).body()).path("content").asText()).isEqualTo("היי");
        assertThat(sentText(0)).isEqualTo("שלום! מה שלומך?");
    }
}
