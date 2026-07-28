package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppSignatureVerifierTest {

    private WhatsAppSignatureVerifier verifier;
    private static final String SECRET = "test-webhook-secret-12345";

    @BeforeEach
    void setUp() {
        verifier = new WhatsAppSignatureVerifier();
    }

    @Test
    void validSignature_returnsTrue() {
        byte[] body = "{\"entry\":[{\"changes\":[]}]}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac(body, SECRET);

        assertThat(verifier.isValid(body, signature, SECRET)).isTrue();
    }

    @Test
    void invalidSignature_returnsFalse() {
        byte[] body = "{\"entry\":[{\"changes\":[]}]}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

        assertThat(verifier.isValid(body, signature, SECRET)).isFalse();
    }

    @Test
    void missingSignatureHeader_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(body, null, SECRET)).isFalse();
    }

    @Test
    void signatureWithoutPrefix_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signatureWithoutPrefix = computeHmac(body, SECRET);

        assertThat(verifier.isValid(body, signatureWithoutPrefix, SECRET)).isFalse();
    }

    @Test
    void emptyBody_returnsFalse() {
        assertThat(verifier.isValid(new byte[0], "sha256=abc", SECRET)).isFalse();
    }

    @Test
    void nullBody_returnsFalse() {
        assertThat(verifier.isValid(null, "sha256=abc", SECRET)).isFalse();
    }

    @Test
    void nullSecret_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(body, "sha256=abc", null)).isFalse();
    }

    @Test
    void blankSecret_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(body, "sha256=abc", "  ")).isFalse();
    }

    @Test
    void malformedHex_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(body, "sha256=not-valid-hex!", SECRET)).isFalse();
    }

    @Test
    void oddLengthHex_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(body, "sha256=abc", SECRET)).isFalse();
    }

    @Test
    void tamperedBody_returnsFalse() {
        byte[] originalBody = "original payload".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac(originalBody, SECRET);

        byte[] tamperedBody = "tampered payload".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(tamperedBody, signature, SECRET)).isFalse();
    }

    @Test
    void differentSecret_returnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac(body, SECRET);

        assertThat(verifier.isValid(body, signature, "wrong-secret")).isFalse();
    }

    /**
     * Helper to compute HMAC-SHA256 for test assertions.
     */
    private static String computeHmac(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
