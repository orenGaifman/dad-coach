package com.dadcoach.whatsapp;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppMessageFormatterTemplateLanguageTest {

    @SuppressWarnings("unchecked")
    private static Object languageCode(String templateName) {
        OutboundMessageDto message = new OutboundMessageDto(UUID.randomUUID(), UUID.randomUUID(), "WHATSAPP",
                MessageType.TEXT, null, null, true, templateName, Map.of("1", "hi"), MessagePriority.IMMEDIATE, Instant.now());
        Map<String, Object> template = (Map<String, Object>) new WhatsAppMessageFormatter().format(message, "+972501234567").get("template");
        return ((Map<String, Object>) template.get("language")).get("code");
    }

    @Test
    void languageCodeFollowsTheTemplateNameSuffix() {
        assertThat(languageCode("dad_coach_update_he")).isEqualTo("he");
        assertThat(languageCode("dad_coach_update_en")).isEqualTo("en");
        assertThat(languageCode("no_suffix")).isEqualTo("en");
    }
}
