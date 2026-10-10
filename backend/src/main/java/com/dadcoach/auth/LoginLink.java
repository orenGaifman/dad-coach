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
    /** receipt_status: the shared-number gateway keeps the message until the father is back on Dad Coach. */
    public static final String HELD = "HELD";

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

    /** Meta's wamid of the message that carried the link (DC-B1): the key its status receipts are matched on. */
    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    /** The gateway's {@code held:<n>} when the shared number held the message; never a wamid. */
    @Column(name = "gateway_hold_id", length = 64)
    private String gatewayHoldId;

    /**
     * Where the message is (DC-B2): null = accepted by Meta, HELD = kept by the shared-number gateway, then Meta's
     * receipts SENT / DELIVERED / READ / FAILED. delivery_status keeps SENT for all but FAILED (the "already on his
     * screen" check reads it).
     */
    @Column(name = "receipt_status", length = 20)
    private String receiptStatus;

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

    public String getProviderMessageId() { return providerMessageId; }
    public String getReceiptStatus() { return receiptStatus; }

    public void recordDelivery(com.dadcoach.channel.delivery.DeliveryResult result) {
        String error = result.failureReason();
        this.deliveryStatus = result.isSuccessful() ? SENT : FAILED;
        this.deliveryError = error == null ? null : error.substring(0, Math.min(error.length(), 200));
        if (result.isHeld()) {
            this.gatewayHoldId = result.providerMessageId();
            this.receiptStatus = HELD;
        } else {
            this.providerMessageId = result.metaMessageId();
        }
    }
}
