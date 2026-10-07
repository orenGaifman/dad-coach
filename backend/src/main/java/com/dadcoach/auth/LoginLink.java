package com.dadcoach.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A one-time, short-lived sign-in link; consumed atomically ({@link LoginLinkRepository#consumeIfValid}). */
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

    public void recordDelivery(boolean sent, String error) {
        this.deliveryStatus = sent ? SENT : FAILED;
        this.deliveryError = error == null ? null : error.substring(0, Math.min(error.length(), 200));
    }
}
