package com.platform.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The platform client in this product is a byte-identical copy of ai-workflow-platform {@code platform-client/}
 * (P2_EXTENSIBILITY_SPEC §3): any edit here fails. Change it in the platform repo and run
 * {@code scripts/platform_client.py sync <this repo>}.
 */
class PlatformClientCopyTest {

    @Test
    void theVendoredClientIsTheCanonicalOne() throws Exception {
        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of("platform-client.sha256"))) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] parts = line.trim().split("\\s+", 2);
                expected.put(parts[1], parts[0]);
            }
        }
        Map<String, String> actual = new TreeMap<>();
        for (Path root : new Path[] {Path.of("src/main/java/com/platform/client"), Path.of("src/test/java/com/platform/client")}) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    actual.put(f.toString().replace('\\', '/'), HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));
                }
            }
        }
        assertThat(actual).isEqualTo(expected);
    }
}
