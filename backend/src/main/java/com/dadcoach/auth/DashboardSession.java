package com.dadcoach.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One signed-in browser (Tair's UserSession). Valid while not revoked and its idle expiry is in the future;
 * use slides the expiry forward.
 */
@Entity
@Table(name = "dashboard_session")
public class DashboardSession {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_LOGOUT_ALL = "LOGOUT_ALL";
    public static final String REASON_REPLACED = "REPLACED_BY_NEW_SIGN_IN";
    public static final String REASON_DEACTIVATED = "DEACTIVATED";
    public static final String REASON_DELETED = "DELETED";

    @Id
    private UUID id;

    @Column(name = "father_id")
    private Long fatherId;

    @Column(name = "staff_user_id")
    private UUID staffUserId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    protected DashboardSession() {
    }

    public DashboardSession(SignInSubject subject, String tokenHash, Instant now, Duration idleTtl, String userAgent) {
        this.id = UUID.randomUUID();
        this.fatherId = subject.fatherId();
        this.staffUserId = subject.staffUserId();
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.idleExpiresAt = now.plus(idleTtl);
        this.userAgent = userAgent;
    }

    public UUID getId() { return id; }
    public Long getFatherId() { return fatherId; }
    public UUID getStaffUserId() { return staffUserId; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }

    public SignInSubject subject() {
        return new SignInSubject(fatherId, staffUserId);
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && idleExpiresAt.isAfter(now);
    }

    public void touch(Instant now, Duration idleTtl) {
        this.lastSeenAt = now;
        this.idleExpiresAt = now.plus(idleTtl);
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }
}
