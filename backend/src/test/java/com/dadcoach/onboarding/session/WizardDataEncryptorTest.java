package com.dadcoach.onboarding.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WizardDataEncryptor} AES-256-GCM encryption/decryption.
 */
class WizardDataEncryptorTest {

    private WizardDataEncryptor encryptor;

    /**
     * Generates a valid 256-bit AES key (32 bytes) for testing.
     */
    private static String generateTestKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    @BeforeEach
    void setUp() {
        encryptor = new WizardDataEncryptor(generateTestKey(), new ObjectMapper());
    }

    @Test
    void encryptAndDecryptRoundTrip() {
        WizardData original = new WizardData();
        original.setDisplayName("אבא Test");
        original.setPhoneNumber("+972501234567");
        original.setEmail("test@example.com");
        original.setTimezone("Asia/Jerusalem");
        original.setLanguage("he");

        byte[] encrypted = encryptor.convertToDatabaseColumn(original);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);

        WizardData decrypted = encryptor.convertToEntityAttribute(encrypted);
        assertNotNull(decrypted);
        assertEquals("אבא Test", decrypted.getDisplayName());
        assertEquals("+972501234567", decrypted.getPhoneNumber());
        assertEquals("test@example.com", decrypted.getEmail());
        assertEquals("Asia/Jerusalem", decrypted.getTimezone());
        assertEquals("he", decrypted.getLanguage());
    }

    @Test
    void encryptNullReturnsNull() {
        assertNull(encryptor.convertToDatabaseColumn(null));
    }

    @Test
    void decryptNullReturnsNull() {
        assertNull(encryptor.convertToEntityAttribute(null));
    }

    @Test
    void decryptEmptyArrayReturnsNull() {
        assertNull(encryptor.convertToEntityAttribute(new byte[0]));
    }

    @Test
    void encryptingTwiceProducesDifferentCiphertext() {
        WizardData data = new WizardData();
        data.setDisplayName("Same Data");

        byte[] firstEncryption = encryptor.convertToDatabaseColumn(data);
        byte[] secondEncryption = encryptor.convertToDatabaseColumn(data);

        assertNotNull(firstEncryption);
        assertNotNull(secondEncryption);
        // Due to random IV, same plaintext should produce different ciphertext
        assertFalse(java.util.Arrays.equals(firstEncryption, secondEncryption),
                "Same plaintext should produce different ciphertext due to random IV");
    }

    @Test
    void roundTripWithChildren() {
        WizardData original = new WizardData();
        original.setChildren(List.of(
                new WizardData.ChildData("יוסי", "2019-05-15", "male"),
                new WizardData.ChildData("שרה", "2021-08-22", "female")
        ));

        byte[] encrypted = encryptor.convertToDatabaseColumn(original);
        WizardData decrypted = encryptor.convertToEntityAttribute(encrypted);

        assertEquals(2, decrypted.getChildren().size());
        assertEquals("יוסי", decrypted.getChildren().get(0).getName());
        assertEquals("2019-05-15", decrypted.getChildren().get(0).getBirthDate());
        assertEquals("male", decrypted.getChildren().get(0).getGender());
        assertEquals("שרה", decrypted.getChildren().get(1).getName());
    }

    @Test
    void roundTripWithGoalsAndPreferences() {
        WizardData original = new WizardData();
        original.setGoals(List.of("patience", "quality_time", "communication"));
        original.setPreferences(Map.of(
                "coaching_style", "supportive",
                "coaching_time", "08:00",
                "notification_frequency", "DAILY"
        ));

        byte[] encrypted = encryptor.convertToDatabaseColumn(original);
        WizardData decrypted = encryptor.convertToEntityAttribute(encrypted);

        assertEquals(3, decrypted.getGoals().size());
        assertTrue(decrypted.getGoals().contains("patience"));
        assertEquals("supportive", decrypted.getPreferences().get("coaching_style"));
    }

    @Test
    void invalidKeyLengthThrowsException() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // 128-bit (wrong)
        assertThrows(IllegalArgumentException.class,
                () -> new WizardDataEncryptor(shortKey, new ObjectMapper()));
    }

    @Test
    void tamperedCiphertextThrowsException() {
        WizardData data = new WizardData();
        data.setDisplayName("Test");

        byte[] encrypted = encryptor.convertToDatabaseColumn(data);
        // Tamper with a byte in the ciphertext (after the 12-byte IV)
        encrypted[15] ^= 0xFF;

        assertThrows(IllegalStateException.class,
                () -> encryptor.convertToEntityAttribute(encrypted));
    }
}
