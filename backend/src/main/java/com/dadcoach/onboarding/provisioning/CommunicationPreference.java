package com.dadcoach.onboarding.provisioning;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA entity representing a father's communication preferences.
 * Maps to the "communication_preferences" table.
 */
@Entity
@Table(name = "communication_preferences", indexes = {
    @Index(name = "idx_comm_pref_father", columnList = "father_id", unique = true)
})
public class CommunicationPreference {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "preference_id")
    private UUID preferenceId;

    @Column(name = "father_id", nullable = false, unique = true)
    private UUID fatherId;

    @Column(name = "preferred_coaching_time", nullable = false)
    private LocalTime preferredCoachingTime = LocalTime.of(8, 0);

    @Column(name = "notification_frequency", length = 20, nullable = false)
    private String notificationFrequency = "DAILY";

    @Column(name = "quiet_hours_start", nullable = false)
    private LocalTime quietHoursStart = LocalTime.of(21, 0);

    @Column(name = "quiet_hours_end", nullable = false)
    private LocalTime quietHoursEnd = LocalTime.of(7, 0);

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CommunicationPreference() {
        // JPA requires a no-arg constructor
    }

    public CommunicationPreference(UUID fatherId) {
        this.fatherId = fatherId;
        this.updatedAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getPreferenceId() { return preferenceId; }
    public void setPreferenceId(UUID preferenceId) { this.preferenceId = preferenceId; }

    public UUID getFatherId() { return fatherId; }
    public void setFatherId(UUID fatherId) { this.fatherId = fatherId; }

    public LocalTime getPreferredCoachingTime() { return preferredCoachingTime; }
    public void setPreferredCoachingTime(LocalTime preferredCoachingTime) { this.preferredCoachingTime = preferredCoachingTime; }

    public String getNotificationFrequency() { return notificationFrequency; }
    public void setNotificationFrequency(String notificationFrequency) { this.notificationFrequency = notificationFrequency; }

    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }

    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }

    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
