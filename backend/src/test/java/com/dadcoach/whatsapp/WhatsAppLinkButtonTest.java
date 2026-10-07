package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto.LinkButton;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-027: Meta's cta_url shape, its limits, and the text form with the link on its own line. */
class WhatsAppLinkButtonTest {

    private final WhatsAppMessageFormatter formatter = new WhatsAppMessageFormatter();
    private static final LinkButton BUTTON = new LinkButton("כניסה לדף שלי", "https://x.test/auth/consume#token=abc", "אישי");

    @Test
    @DisplayName("a link button is an interactive cta_url message: body, footer, action name and parameters")
    @SuppressWarnings("unchecked")
    void ctaUrl() {
        OutboundMessageDto m = OutboundMessageDto.withLinkButton(UUID.randomUUID(), "📊 הדף שלך", BUTTON, Instant.now());
        Map<String, Object> payload = formatter.format(m, "+972501234567");
        assertThat(payload).containsEntry("type", "interactive").containsEntry("to", "972501234567");
        Map<String, Object> interactive = (Map<String, Object>) payload.get("interactive");
        assertThat(interactive).containsEntry("type", "cta_url")
                .containsEntry("body", Map.of("text", "📊 הדף שלך"))
                .containsEntry("footer", Map.of("text", "אישי"));
        Map<String, Object> action = (Map<String, Object>) interactive.get("action");
        assertThat(action).containsEntry("name", "cta_url")
                .containsEntry("parameters", Map.of("display_text", "כניסה לדף שלי", "url", "https://x.test/auth/consume#token=abc"));
    }

    @Test
    @DisplayName("a label over 20 characters (or a body over 1024, a footer over 60) goes out as text, the link on its own line")
    @SuppressWarnings("unchecked")
    void overTheLimitIsText() {
        LinkButton tooLong = new LinkButton("x".repeat(21), BUTTON.url(), null);
        OutboundMessageDto m = OutboundMessageDto.withLinkButton(UUID.randomUUID(), "הדף שלך", tooLong, Instant.now());
        assertThat(WhatsAppMessageFormatter.linkButtonFits(m)).isFalse();
        Map<String, Object> payload = formatter.format(m, "+972501234567");
        assertThat(payload).containsEntry("type", "text");
        String body = (String) ((Map<String, Object>) payload.get("text")).get("body");
        assertThat(body).isEqualTo("הדף שלך\n" + BUTTON.url());

        assertThat(WhatsAppMessageFormatter.linkButtonFits(OutboundMessageDto.withLinkButton(null, "y".repeat(1025), BUTTON, Instant.now()))).isFalse();
        assertThat(WhatsAppMessageFormatter.linkButtonFits(OutboundMessageDto.withLinkButton(null, "y",
                new LinkButton("ok", BUTTON.url(), "z".repeat(61)), Instant.now()))).isFalse();
        assertThat(WhatsAppMessageFormatter.linkButtonFits(OutboundMessageDto.withLinkButton(null, "y", BUTTON, Instant.now()))).isTrue();
    }

    @Test
    @DisplayName("the text form keeps the footer under the link")
    void textForm() {
        assertThat(BUTTON.asText("הדף שלך")).isEqualTo("הדף שלך\n" + BUTTON.url() + "\n\nאישי");
    }
}
