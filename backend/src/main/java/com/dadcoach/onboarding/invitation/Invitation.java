package com.dadcoach.onboarding.invitation;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity representing an invitation to the Dad Coach onboarding system.
 * Maps to the "invitations" table (V11 migration).
 *
 * <p>Invitations can be single-use (one father) or reusable (e.g., shared by a community leader).
 * Each invitation tracks its usage count against a configured maximum and expires after a
 * type-specific duration (7 days for SINGLE_USE, 90 days for REUSABLE).
 *
 * <p>The token field is a 32-character Base62 URL-safe string (~190 bits entropy) generated
 * by {@link InvitationTokenGenerator}.
 */
@Entity
@Table(name = "invitations", indexes = {
    @Index(name = "idx_invitations_token", columnList = "token", unique = true),
    @Index(name = "idx_invitations_status_expires", columnList = "status, expires_at")
})
public class Invitation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "invitation_id")
    private UUID invitationId;

    @Column(nullable = false, unique = true, length = 32)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private InvitationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvitationStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses;

    @Column(name = "current_uses", nullable = false)
    private Integer currentUses = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected Invitation() {
        // JPA requires a no-arg constructor
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getInvitationId() {
        return invitationId;
    }

    public void setInvitationId(UUID invitationId) {
        this.invitationId = invitationId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public InvitationType getType() {
        return type;
    }

    public void setType(InvitationType type) {
        this.type = type;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getCurrentUses() {
        return currentUses;
    }

    public void setCurrentUses(Integer currentUses) {
        this.currentUses = currentUses;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
