package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.support.Webhooks;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** D-029: a voice note keeps its Meta media id so it can be heard; nothing else carries one. */
class WhatsAppMessageParserVoiceTest {

    private final WhatsAppMessageParser parser = new WhatsAppMessageParser();
    private final ObjectMapper json = new ObjectMapper();

    private InboundMessageDto parse(String body) throws Exception {
        return parser.parse(json.readValue(body, WhatsAppWebhookPayload.class)).messages().get(0);
    }

    @Test
    void aVoiceNoteCarriesItsMediaIdAndNoText() throws Exception {
        InboundMessageDto in = parse(Webhooks.audio("+972501234567", "wamid.v1", "media-77"));
        assertThat(in.messageType()).isEqualTo(MessageType.AUDIO);
        assertThat(in.mediaId()).isEqualTo("media-77");
        assertThat(in.textContent()).isNull();
        assertThat(in.idempotencyKey()).isEqualTo("wamid.v1");
    }

    @Test
    void typedTextHasNoMediaId() throws Exception {
        InboundMessageDto in = parse(Webhooks.text("+972501234567", "wamid.t1", "שלום"));
        assertThat(in.mediaId()).isNull();
        assertThat(in.textContent()).isEqualTo("שלום");
    }

    @Test
    void aVoiceNoteWithoutAMediaIdHasNone() throws Exception {
        InboundMessageDto in = parse(Webhooks.envelope("{\"messages\":[{\"from\":\"972501234567\",\"id\":\"wamid.v2\","
                + "\"timestamp\":\"1793700000\",\"type\":\"audio\",\"audio\":{}}]}"));
        assertThat(in.messageType()).isEqualTo(MessageType.AUDIO);
        assertThat(in.mediaId()).isNull();
    }
}
