package com.dadcoach.onboarding.invitation;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates cryptographically secure invitation tokens using Base62 encoding.
 * Produces 32-character URL-safe tokens with ~190 bits of entropy (log2(62^32) ≈ 190.5).
 */
@Component
public class InvitationTokenGenerator {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int TOKEN_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a new 32-character Base62 token using a CSPRNG.
     *
     * @return a URL-safe token string of exactly 32 characters
     */
    public String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(BASE62_CHARS.charAt(secureRandom.nextInt(BASE62_CHARS.length())));
        }
        return token.toString();
    }
}
