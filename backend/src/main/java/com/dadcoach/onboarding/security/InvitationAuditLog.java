package com.dadcoach.onboarding.security;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for logging invitation token validation attempts.
 * Maps to the "invitation_audit_log" table (V15 migration).
 * Stores SHA-256 hash of token (never raw token), IP, user-agent, and result.
 */
@Entity
@Table(name = "invitation_audit_log")
public class InvitationAuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvitationAuditLog() {
    }

    public InvitationAuditLog(String tokenHash, String action, String result,
                              String ipAddress, String userAgent) {
        this.tokenHash = tokenHash;
        this.action = action;
        this.result = result;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getLogId() { return logId; }
    public String getTokenHash() { return tokenHash; }
    public String getAction() { return action; }
    public String getResult() { return result; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
