package com.dadcoach.onboarding.invitation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationTokenGeneratorTest {

    private static final Pattern BASE62_PATTERN = Pattern.compile("^[0-9a-zA-Z]+$");
    private static final int EXPECTED_TOKEN_LENGTH = 32;

    private InvitationTokenGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new InvitationTokenGenerator();
    }

    @Test
    void generateToken_returnsExactly32Characters() {
        String token = generator.generateToken();
        assertThat(token).hasSize(EXPECTED_TOKEN_LENGTH);
    }

    @Test
    void generateToken_containsOnlyBase62Characters() {
        String token = generator.generateToken();
        assertThat(token).matches(BASE62_PATTERN);
    }

    @Test
    void generateToken_producesUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generator.generateToken());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void generateToken_isUrlSafe() {
        String token = generator.generateToken();
        // Base62 is inherently URL-safe: no +, /, =, or other special characters
        assertThat(token).doesNotContain("+", "/", "=", "&", "?", "#", "%");
    }
}
