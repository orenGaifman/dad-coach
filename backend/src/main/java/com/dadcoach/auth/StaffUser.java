package com.dadcoach.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A member of the internal Dad Coach team: may use the admin area. Created only through the ops API. */
@Entity
@Table(name = "staff_user")
public class StaffUser {

    @Id
    private UUID id;

    @Column(name = "phone", nullable = false, unique = true, length = 32)
    private String phone;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffUser() {
    }

    public StaffUser(String phone, String displayName, Instant now) {
        this.id = UUID.randomUUID();
        this.phone = phone;
        this.displayName = displayName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }

    public void confirm(String displayName, Instant now) {
        this.displayName = displayName;
        this.active = true;
        this.updatedAt = now;
    }
}
