package com.dadcoach.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A sign-in link (D-027): reusable until it expires ({@link LoginLinkService#LINK_TTL}) or is revoked; each use is
 * counted atomically ({@link LoginLinkRepository#useIfValid}) and opens a normal dashboard session.
 */
@Entity
@Table(name = "login_link")
public class LoginLink {

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String ISSUED = "ISSUED";

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

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus = ISSUED;

    @Column(name = "delivery_error", length = 200)
    private String deliveryError;

    protected LoginLink() {
    }

    public LoginLink(SignInSubject subject, String tokenHash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.fatherId = subject.fatherId();
        this.staffUserId = subject.staffUserId();
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public Long getFatherId() { return fatherId; }
    public UUID getStaffUserId() { return staffUserId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public String getDeliveryError() { return deliveryError; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public int getUseCount() { return useCount; }
    public Instant getRevokedAt() { return revokedAt; }

    public void recordDelivery(boolean sent, String error) {
        this.deliveryStatus = sent ? SENT : FAILED;
        this.deliveryError = error == null ? null : error.substring(0, Math.min(error.length(), 200));
    }
}
