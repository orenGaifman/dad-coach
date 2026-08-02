package com.dadcoach.workspace.magiclink;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a magic link token for passwordless authentication.
 * 
 * Magic links are short-lived tokens sent via WhatsApp that allow fathers
 * to authenticate and access their dashboard without manual login.
 * 
 * Security considerations:
 * - Tokens are cryptographically random (32 chars, ~190 bits entropy)
 * - Single use only (consumed after successful validation)
 * - Short expiration (60 minutes by default)
 * - One active token per father (new request invalidates previous)
 */
@Entity
@Table(name = "magic_link", indexes = {
    @Index(name = "idx_magic_link_token", columnList = "token", unique = true),
    @Index(name = "idx_magic_link_father_id", columnList = "father_id")
})
public class MagicLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The 32-character cryptographically secure token.
     */
    @Column(nullable = false, unique = true, length = 32)
    private String token;

    /**
     * The father this token authenticates.
     */
    @Column(name = "father_id", nullable = false)
    private Long fatherId;

    /**
     * When the token was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When the token expires.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * When the token was consumed (null if not yet used).
     */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    /**
     * The intended redirect path after authentication.
     */
    @Column(name = "redirect_path", length = 255)
    private String redirectPath;

    /**
     * Context about when/why the link was generated (for analytics).
     */
    @Column(length = 50)
    private String context;

    protected MagicLink() {
    }

    public MagicLink(String token, Long fatherId, Instant expiresAt, 
                     String redirectPath, String context) {
        this.token = token;
        this.fatherId = fatherId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.redirectPath = redirectPath;
        this.context = context;
    }

    public UUID getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public String getContext() {
        return context;
    }

    /**
     * Checks if this token has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this token has already been used.
     */
    public boolean isConsumed() {
        return consumedAt != null;
    }

    /**
     * Checks if this token is valid (not expired and not consumed).
     */
    public boolean isValid() {
        return !isExpired() && !isConsumed();
    }

    /**
     * Marks this token as consumed.
     */
    public void consume() {
        if (consumedAt == null) {
            consumedAt = Instant.now();
        }
    }
}
