package com.platform.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The product's {@code platform-catalog.json} (what it publishes to the platform, P2_EXTENSIBILITY_SPEC §2) is well formed,
 * names only its own variables, and - while the product still keeps the older {@code kind-template-seed.json} fixture -
 * every kind→template mapping it publishes is the one that fixture holds.
 */
class PlatformCatalogFileTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode catalog() throws Exception {
        return json.readTree(Files.readString(Path.of("src/main/resources/platform-catalog.json")));
    }

    @Test
    void keysAreUniqueAndEndpointsUseOnlyTheProductsOwnVariables() throws Exception {
        JsonNode catalog = catalog();
        String product = catalog.path("product").asText();
        assertThat(product).matches("[a-z][a-z0-9_]*");
        String prefix = product.toUpperCase() + "_";
        for (String[] section : new String[][] {{"tools", "toolKey"}, {"providers", "providerKey"}}) {
            Set<String> keys = new HashSet<>();
            for (JsonNode entry : catalog.path(section[0])) {
                String key = entry.path(section[1]).asText();
                assertThat(keys.add(key)).as(section[0] + " " + key + " listed twice").isTrue();
                assertThat(entry.path("endpointConfig").path("url").asText()).as(key).startsWith("${" + prefix + "BASE_URL}/");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]*)}").matcher(entry.path("endpointConfig").toString());
                while (m.find()) {
                    assertThat(m.group(1)).as(key).startsWith(prefix);
                }
            }
        }
    }

    @Test
    void theOlderSeedFixtureAgreesWithTheCatalogsKindTemplates() throws Exception {
        Path seed = Path.of("src/test/resources/kind-template-seed.json");
        if (!Files.exists(seed)) {
            return;
        }
        JsonNode catalog = catalog();
        String product = catalog.path("product").asText();
        Map<String, List<String>> fromCatalog = new LinkedHashMap<>();
        for (JsonNode group : catalog.path("kindTemplates")) {
            List<String> names = new ArrayList<>();
            group.path("candidates").forEach(c -> names.add(c.path("templateName").asText()));
            fromCatalog.put(group.path("kind").asText() + "/" + group.path("variant").asText(), names);
        }
        Map<String, List<String>> fromSeed = new LinkedHashMap<>();
        List<JsonNode> rows = new ArrayList<>();
        json.readTree(Files.readString(seed)).forEach(rows::add);
        rows.stream().filter(r -> product.equals(r.path("product").asText()))
                .sorted(java.util.Comparator.comparingInt(r -> r.path("position").asInt()))
                .forEach(r -> fromSeed.computeIfAbsent(r.path("kind").asText() + "/" + r.path("variant").asText(""), k -> new ArrayList<>())
                        .add(r.path("template").asText()));
        // every mapping the product publishes is the one its tests assume; the fixture may hold a mapping not published yet
        for (Map.Entry<String, List<String>> e : fromCatalog.entrySet()) {
            assertThat(fromSeed.get(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());
        }
    }
}
