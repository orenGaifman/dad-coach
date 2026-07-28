package com.dadcoach.onboarding.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvitationAuditService (static hash method).
 */
class InvitationAuditServiceTest {

    @Test
    void hashToken_producesConsistentHash() {
        String hash1 = InvitationAuditService.hashToken("test-token-123");
        String hash2 = InvitationAuditService.hashToken("test-token-123");
        assertEquals(hash1, hash2);
    }

    @Test
    void hashToken_produces64CharHex() {
        String hash = InvitationAuditService.hashToken("abc123");
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashToken_differentInputs_differentHashes() {
        String hash1 = InvitationAuditService.hashToken("token-a");
        String hash2 = InvitationAuditService.hashToken("token-b");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashToken_null_returnsNullString() {
        assertEquals("null", InvitationAuditService.hashToken(null));
    }

    @Test
    void hashToken_emptyString_producesValidHash() {
        String hash = InvitationAuditService.hashToken("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
