package com.dadcoach.onboarding.security;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a rate limit window entry.
 * Maps to the "rate_limit_entries" table (V13 migration).
 */
@Entity
@Table(name = "rate_limit_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uq_rate_limit_window", columnNames = {"key_type", "key_value", "window_start"})
})
public class RateLimitEntry {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "key_type", nullable = false, length = 10)
    private String keyType;

    @Column(name = "key_value", nullable = false, length = 255)
    private String keyValue;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RateLimitEntry() {
    }

    public RateLimitEntry(String keyType, String keyValue, Instant windowStart) {
        this.keyType = keyType;
        this.keyValue = keyValue;
        this.windowStart = windowStart;
        this.attemptCount = 1;
        this.createdAt = Instant.now();
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public void incrementAttempts() {
        this.attemptCount++;
    }
}
