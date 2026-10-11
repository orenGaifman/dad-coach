package com.dadcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.scheduled.KindTemplateSeedContractTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D-053 (spec v2 R2/R4): the real LOGIN_LINK requests - a father's and a staff user's, built by
 * {@link LoginLinkPlatform#request} - carry a {@code templateData} the platform's {@code dad_coach} seed resolves: the
 * kind's default message type, with the {@code named.text} its {@code dad_coach_update_he} row reads (one line, the link
 * inside), and never a Meta template name.
 */
class LoginLinkSeedContractTest extends AbstractIntegrationTest {

    @Autowired LoginLinkPlatform platform;
    @Autowired ObjectMapper json;

    private void checkAgainstTheSeed(String body) throws Exception {
        JsonNode r = json.readTree(body);
        assertThat(r.path("kind").asText()).isEqualTo(MessagingSettings.LOGIN_LINK);
        assertThat(r.has("template")).isFalse();
        assertThat(r.has("fallbackTemplates")).isFalse();
        JsonNode data = r.path("templateData");
        String variant = data.path("variant").asText();
        List<JsonNode> rows = KindTemplateSeedContractTest.rows(MessagingSettings.LOGIN_LINK, variant);
        assertThat(KindTemplateSeedContractTest.seed()).isNotEmpty();
        assertThat(rows).as("seed rows for LOGIN_LINK/'%s'", variant).isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> asSent = json.convertValue(data, Map.class);
        KindTemplateSeedContractTest.check(asSent, rows);
        assertThat(data.path("named").path("text").asText()).contains("https://app.dadcoach.test/auth/consume#token=");
    }

    @Test
    @DisplayName("a father's link request resolves in the seed")
    void fatherRequest() throws Exception {
        Father f = data.activeFather("+19995551101");
        String body = platform.request(UUID.randomUUID(), new SignInSubject(f.getId(), null), Optional.of(f), f.getPhone(),
                "https://app.dadcoach.test/auth/consume#token=abc", "turn-1");
        checkAgainstTheSeed(body);
    }

    @Test
    @DisplayName("a staff user's link request resolves in the seed")
    void staffRequest() throws Exception {
        String body = platform.request(UUID.randomUUID(), new SignInSubject(null, UUID.randomUUID()), Optional.empty(),
                "+19995551102", "https://app.dadcoach.test/auth/consume#token=def", null);
        checkAgainstTheSeed(body);
    }
}
