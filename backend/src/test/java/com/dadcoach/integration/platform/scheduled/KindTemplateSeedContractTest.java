package com.dadcoach.integration.platform.scheduled;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-053 (Unified Workflow 6.4, spec v2 R2/R4, §9.2): every (kind, variant) Dad Coach can put in a request's
 * {@code templateData} exists in the platform's kind→template seed for {@code dad_coach} (a copy of the platform's
 * {@code docs/architecture/p64/seed.json} in {@code kind-template-seed.json}), and its values fit the mapped template:
 * as many positional values as the session template's body parameters, as many payloads as its quick replies, and a
 * {@code named.text} wherever a row reads {@code ["text"]} (the general {@code dad_coach_update_he}). The kinds without
 * {@code templateData} are session-only lines (free-form in the window, never a template).
 */
public class KindTemplateSeedContractTest {

    static final String PRODUCT = "dad_coach";
    static List<JsonNode> seed;

    /** The dad_coach seed rows (read once). */
    public static synchronized List<JsonNode> seed() throws Exception {
        if (seed == null) {
            readSeed();
        }
        return seed;
    }

    @BeforeAll
    static void readSeed() throws Exception {
        try (InputStream in = KindTemplateSeedContractTest.class.getResourceAsStream("/kind-template-seed.json")) {
            JsonNode all = new ObjectMapper().readTree(in);
            seed = new ArrayList<>();
            all.forEach(row -> {
                if (PRODUCT.equals(row.path("product").asText())) {
                    seed.add(row);
                }
            });
        }
    }

    public static List<JsonNode> rows(String kind, String variant) throws Exception {
        return seed().stream().filter(r -> kind.equals(r.path("kind").asText()) && variant.equals(r.path("variant").asText()))
                .sorted((a, b) -> Integer.compare(a.path("position").asInt(), b.path("position").asInt())).toList();
    }

    /** Every templateData SCHEDULED can carry: each session template with values / payloads, and the default. */
    static Map<String, Map<String, Object>> scheduledEmits() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (WhatsAppTemplateCatalog.Entry e : WhatsAppTemplateCatalog.ALL) {
            if (e.name().equals(WhatsAppTemplateCatalog.UPDATE_HE)) {
                continue;
            }
            List<String> values = Collections.nCopies(e.maxVariables(), "ערך");
            List<String> payloads = new ArrayList<>();
            for (int i = 0; i < e.quickReplies().size(); i++) {
                payloads.add("dc:done:" + UUID.randomUUID());
            }
            Map<String, Object> data = ScheduledPlatformSend.templateData("❤️ דאד קואץ׳:\nשורה\nועוד",
                    Optional.of(new TemplateCall(e.name(), values, payloads)));
            out.put((String) data.get("variant"), data);
        }
        out.put("", ScheduledPlatformSend.templateData("❤️ דאד קואץ׳:\nבוקר טוב!", Optional.empty()));
        return out;
    }

    @Test
    @DisplayName("every SCHEDULED message type Dad Coach emits is seeded, values and payloads fit the session template, "
            + "the general template's {{1}} is there")
    void scheduledVariantsAreSeeded() throws Exception {
        Map<String, Map<String, Object>> emits = scheduledEmits();
        assertThat(emits.keySet()).containsExactlyInAnyOrder("", "session_morning_he", "session_hour_before_he",
                "session_follow_up_he");
        for (Map.Entry<String, Map<String, Object>> e : emits.entrySet()) {
            List<JsonNode> rows = rows(MessagingSettings.SCHEDULED, e.getKey());
            assertThat(rows).as("seed rows for SCHEDULED/'%s'", e.getKey()).isNotEmpty();
            check(e.getValue(), rows);
        }
    }

    @Test
    @DisplayName("LOGIN_LINK's default message type is seeded as dad_coach_update_he (the real requests: LoginLinkSeedContractTest)")
    void loginLinkIsSeeded() throws Exception {
        List<JsonNode> rows = rows(MessagingSettings.LOGIN_LINK, "");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).path("template").asText()).isEqualTo(WhatsAppTemplateCatalog.UPDATE_HE);
    }

    @Test
    @DisplayName("the seed names only catalog templates (the copy the owner submits), and only kinds Dad Coach sends")
    void seedNamesOnlyCatalogTemplatesAndKnownKinds() {
        assertThat(seed).isNotEmpty();
        for (JsonNode row : seed) {
            assertThat(WhatsAppTemplateCatalog.named(row.path("template").asText())).as(row.toString()).isPresent();
            assertThat(MessagingSettings.KINDS).contains(row.path("kind").asText());
            assertThat(row.path("expect").path("language").asText()).isEqualTo("he");
            WhatsAppTemplateCatalog.Entry entry = WhatsAppTemplateCatalog.require(row.path("template").asText());
            assertThat(row.path("expect").path("bodyParamCount").asInt()).as(row.toString()).isEqualTo(entry.maxVariables());
            assertThat(row.path("expect").path("quickReplies").asInt()).as(row.toString()).isEqualTo(entry.quickReplies().size());
        }
    }

    /** At least the first usable row's references resolve against what Dad Coach sends; every row's refs do. */
    @SuppressWarnings("unchecked")
    public static void check(Map<String, Object> data, List<JsonNode> rows) {
        List<String> values = (List<String>) data.getOrDefault("values", List.of());
        List<String> payloads = (List<String>) data.getOrDefault("payloads", List.of());
        Map<String, String> named = (Map<String, String>) data.getOrDefault("named", Map.of());
        for (JsonNode row : rows) {
            JsonNode refs = row.path("bodyRefs");
            int params = row.path("expect").path("bodyParamCount").asInt();
            if (refs.isNull() || refs.isMissingNode()) {
                assertThat(values).as("values as sent for %s", row.path("template").asText()).hasSize(params);
                assertThat(payloads).as("payloads for %s", row.path("template").asText())
                        .hasSize(row.path("expect").path("quickReplies").asInt());
            } else {
                assertThat(refs).hasSize(params);
                refs.forEach(ref -> {
                    if (ref.isNumber()) {
                        assertThat(values.size()).isGreaterThan(ref.asInt());
                    } else {
                        assertThat(named).as("named '%s' for %s", ref.asText(), row.path("template").asText())
                                .containsKey(ref.asText());
                        assertThat(named.get(ref.asText())).isNotBlank().doesNotContain("\n");
                    }
                });
                assertThat(row.path("expect").path("quickReplies").asInt()).isZero();
            }
        }
    }
}
