package com.dadcoach.onboarding.provisioning;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a family unit in the coaching system.
 * Created during provisioning to group a father and their children.
 */
@Entity
@Table(name = "families", indexes = {
    @Index(name = "idx_families_father", columnList = "father_id", unique = true)
})
public class Family {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "father_id", nullable = false, unique = true)
    private UUID fatherId;

    @Column(name = "family_name", length = 120)
    private String familyName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Family() {
        // JPA requires a no-arg constructor
    }

    public Family(UUID fatherId, String familyName) {
        this.fatherId = fatherId;
        this.familyName = familyName;
        this.createdAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }

    public UUID getFatherId() { return fatherId; }
    public void setFatherId(UUID fatherId) { this.fatherId = fatherId; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
