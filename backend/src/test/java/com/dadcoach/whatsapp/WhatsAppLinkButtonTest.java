package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.channel.dto.OutboundMessageDto.LinkButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-027: the text form of a link button (the Send API's {@code fallbackText}: the link on its own line). */
class WhatsAppLinkButtonTest {

    private static final LinkButton BUTTON = new LinkButton("כניסה לדף שלי", "https://x.test/auth/consume#token=abc", "אישי");

    @Test
    @DisplayName("the text form keeps the footer under the link")
    void textForm() {
        assertThat(BUTTON.asText("הדף שלך")).isEqualTo("הדף שלך\n" + BUTTON.url() + "\n\nאישי");
    }
}
