package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The shared-number routing contract: every Dad Coach button id starts with "dc:" (ButtonIds). */
class ButtonIdPrefixTest {

    @Test
    void aReplyButtonWithoutThePrefixCannotBeBuilt() {
        assertThatThrownBy(() -> new WhatsAppMessageFormatter.InteractiveButton("done:123", "היה מעולה"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dc:");
        assertThatThrownBy(() -> new WhatsAppMessageFormatter.InteractiveButton("dc:", "היה מעולה"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new WhatsAppMessageFormatter.InteractiveButton("dc:done:123", "היה מעולה").id()).isEqualTo("dc:done:123");
    }

    @Test
    void aListRowWithoutThePrefixCannotBeBuilt() {
        assertThatThrownBy(() -> new WhatsAppMessageFormatter.InteractiveRow("slot:1", "רביעי 17:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new WhatsAppMessageFormatter.InteractiveRow("dc:slot:1", "רביעי 17:00").id()).startsWith("dc:");
    }

    @Test
    void aTemplateQuickReplyPayloadWithoutThePrefixCannotBeBuilt() {
        assertThatThrownBy(() -> WhatsAppMessageFormatter.quickReplyButton(0, "follow_up:done"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WhatsAppMessageFormatter.quickReplyButton(0, null)).isInstanceOf(IllegalArgumentException.class);
        Map<String, Object> button = WhatsAppMessageFormatter.quickReplyButton(1, "dc:follow_up:done");
        assertThat(button).containsEntry("sub_type", "quick_reply").containsEntry("index", "1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) button.get("parameters");
        assertThat(parameters.get(0)).containsEntry("payload", "dc:follow_up:done");
    }
}
