package com.dadcoach.channel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a communication endpoint for a father.
 * Maps a father to their channel identity (e.g., WhatsApp phone number),
 * tracking session state and primary endpoint status.
 *
 * A father may have multiple endpoints (e.g., WhatsApp + SMS).
 * Exactly one endpoint per father is marked as primary at any time.
 */
@Entity
@Table(
    name = "communication_endpoints",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_channel_channel_identity",
        columnNames = {"channel", "channel_identity"}
    )
)
public class CommunicationEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "channel_identity", nullable = false, length = 50)
    private String channelIdentity;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "session_opens_at")
    private Instant sessionOpensAt;

    @Column(name = "session_closes_at")
    private Instant sessionClosesAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    protected CommunicationEndpoint() {
        // JPA requires no-arg constructor
    }

    public CommunicationEndpoint(UUID fatherId, String channel, String channelIdentity) {
        this.fatherId = fatherId;
        this.channel = channel;
        this.channelIdentity = channelIdentity;
        this.primary = true;
        this.registeredAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getFatherId() { return fatherId; }

    public String getChannel() { return channel; }

    public String getChannelIdentity() { return channelIdentity; }

    public boolean isPrimary() { return primary; }

    public Instant getSessionOpensAt() { return sessionOpensAt; }

    public Instant getSessionClosesAt() { return sessionClosesAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }

    public Instant getRegisteredAt() { return registeredAt; }

    // ─── Setters for mutable fields ──────────────────────────────────────

    public void setPrimary(boolean primary) { this.primary = primary; }

    public void setSessionOpensAt(Instant sessionOpensAt) { this.sessionOpensAt = sessionOpensAt; }

    public void setSessionClosesAt(Instant sessionClosesAt) { this.sessionClosesAt = sessionClosesAt; }

    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    @Override
    public String toString() {
        return "CommunicationEndpoint{" +
                "id=" + id +
                ", fatherId=" + fatherId +
                ", channel='" + channel + '\'' +
                ", channelIdentity='" + channelIdentity + '\'' +
                ", primary=" + primary +
                ", registeredAt=" + registeredAt +
                '}';
    }
}
