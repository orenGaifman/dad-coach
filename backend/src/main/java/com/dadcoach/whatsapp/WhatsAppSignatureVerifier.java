package com.dadcoach.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verifies WhatsApp webhook payload authenticity using HMAC-SHA256 signatures.
 * <p>
 * WhatsApp sends a {@code X-Hub-Signature-256} header in the format {@code sha256=<hex>}.
 * This component computes the expected HMAC-SHA256 of the raw request body using the
 * configured webhook secret and compares it to the provided signature in constant time.
 */
@Component
public class WhatsAppSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * Verifies the HMAC-SHA256 signature of a webhook payload.
     *
     * @param rawBody         the raw request body bytes
     * @param signatureHeader the value of the X-Hub-Signature-256 header (format: "sha256=<hex>")
     * @param webhookSecret   the shared secret used to compute the HMAC
     * @return true if the signature is valid, false otherwise
     */
    public boolean isValid(byte[] rawBody, String signatureHeader, String webhookSecret) {
        if (rawBody == null || rawBody.length == 0) {
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        String providedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());
        byte[] expectedMac = computeHmacSha256(rawBody, webhookSecret);
        byte[] providedMac = hexToBytes(providedHex);

        if (expectedMac == null || providedMac == null) {
            return false;
        }

        return MessageDigest.isEqual(expectedMac, providedMac);
    }

    private byte[] computeHmacSha256(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return null;
        }
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        try {
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
