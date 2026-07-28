package com.dadcoach.onboarding.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA {@link AttributeConverter} that encrypts/decrypts {@link WizardData} to/from byte[]
 * using AES-256-GCM authenticated encryption.
 *
 * <p>The encryption key is sourced from the application property
 * {@code dadcoach.onboarding.security.wizard-data-encryption-key} (Base64-encoded 256-bit key).
 *
 * <p>Wire format: [12-byte IV][ciphertext + 16-byte GCM tag]
 *
 * <p>Each encryption operation generates a fresh random 12-byte IV (nonce) using {@link SecureRandom},
 * ensuring that encrypting the same plaintext twice produces different ciphertext.
 */
@Component
@Converter
public class WizardDataEncryptor implements AttributeConverter<WizardData, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(WizardDataEncryptor.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;    // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128;  // 128-bit authentication tag

    private final SecretKey secretKey;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public WizardDataEncryptor(
            @Value("${dadcoach.onboarding.security.wizard-data-encryption-key}") String encodedKey,
            ObjectMapper objectMapper) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Wizard data encryption key must be exactly 256 bits (32 bytes), got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.objectMapper = objectMapper;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public byte[] convertToDatabaseColumn(WizardData attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(attribute);
            return encrypt(plaintext);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WizardData to JSON", e);
        }
    }

    @Override
    public WizardData convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return null;
        }
        try {
            byte[] plaintext = decrypt(dbData);
            return objectMapper.readValue(plaintext, WizardData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize WizardData from JSON", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a random 12-byte IV.
     *
     * @param plaintext the data to encrypt
     * @return byte array: [12-byte IV][ciphertext + GCM tag]
     */
    byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Prepend IV to ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * Decrypts data previously encrypted by {@link #encrypt(byte[])}.
     *
     * @param encryptedData byte array: [12-byte IV][ciphertext + GCM tag]
     * @return the decrypted plaintext
     */
    byte[] decrypt(byte[] encryptedData) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-256-GCM decryption failed", e);
        }
    }
}
