package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The shared-number routing contract: every Dad Coach button id starts with "dc:" ({@link ButtonIds}). Since D-053 the
 * ids travel in the Send API request (content buttons, templateData payloads), which checks them with this.
 */
class ButtonIdPrefixTest {

    @Test
    void anIdWithoutThePrefixIsRefused() {
        assertThatThrownBy(() -> ButtonIds.require("done:123", "Button"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dc:");
        assertThatThrownBy(() -> ButtonIds.require("dc:", "Button")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ButtonIds.require(null, "Template quick-reply")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ButtonIds.require("dc:done:123", "Button")).isEqualTo("dc:done:123");
    }
}
