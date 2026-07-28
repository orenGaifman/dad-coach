package com.dadcoach.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service for logging invitation token validation attempts.
 * All tokens are hashed with SHA-256 before storage — raw tokens are never persisted.
 */
@Service
public class InvitationAuditService {

    private static final Logger log = LoggerFactory.getLogger(InvitationAuditService.class);

    private final InvitationAuditLogRepository repository;

    public InvitationAuditService(InvitationAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Logs an invitation validation attempt.
     *
     * @param token     the raw invitation token (will be SHA-256 hashed before storage)
     * @param action    the action type: VALIDATION, OPENED, USED
     * @param result    the result: SUCCESS, NOT_FOUND, EXPIRED, REVOKED, EXHAUSTED, RATE_LIMITED
     * @param ipAddress the client IP address
     * @param userAgent the client user-agent string
     */
    @Transactional
    public void logValidationAttempt(String token, String action, String result,
                                     String ipAddress, String userAgent) {
        String tokenHash = hashToken(token);
        // Truncate user-agent to 500 chars
        String truncatedUserAgent = userAgent != null && userAgent.length() > 500
            ? userAgent.substring(0, 500) : userAgent;

        InvitationAuditLog entry = new InvitationAuditLog(
            tokenHash, action, result, ipAddress, truncatedUserAgent
        );
        repository.save(entry);
        log.debug("Audit: token_hash={} action={} result={} ip={}",
            tokenHash.substring(0, 8) + "...", action, result, ipAddress);
    }

    /**
     * Computes SHA-256 hash of the invitation token.
     * Returns the hash as a 64-character hex string.
     */
    public static String hashToken(String token) {
        if (token == null) {
            return "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
