package com.dadcoach.onboarding.provisioning;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a father's language preference.
 * Maps to the "language_preferences" table.
 */
@Entity
@Table(name = "language_preferences", indexes = {
    @Index(name = "idx_lang_pref_father", columnList = "father_id", unique = true)
})
public class LanguagePreference {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "preference_id")
    private UUID preferenceId;

    @Column(name = "father_id", nullable = false, unique = true)
    private UUID fatherId;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode = "he";

    @Column(name = "date_format", length = 20, nullable = false)
    private String dateFormat = "dd/MM/yyyy";

    @Column(name = "time_format", length = 20, nullable = false)
    private String timeFormat = "HH:mm";

    @Column(name = "text_direction", length = 3, nullable = false)
    private String textDirection = "RTL";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LanguagePreference() {
        // JPA requires a no-arg constructor
    }

    public LanguagePreference(UUID fatherId, String languageCode) {
        this.fatherId = fatherId;
        this.languageCode = languageCode;
        this.textDirection = "he".equals(languageCode) ? "RTL" : "LTR";
        this.updatedAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getPreferenceId() { return preferenceId; }
    public void setPreferenceId(UUID preferenceId) { this.preferenceId = preferenceId; }

    public UUID getFatherId() { return fatherId; }
    public void setFatherId(UUID fatherId) { this.fatherId = fatherId; }

    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }

    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

    public String getTimeFormat() { return timeFormat; }
    public void setTimeFormat(String timeFormat) { this.timeFormat = timeFormat; }

    public String getTextDirection() { return textDirection; }
    public void setTextDirection(String textDirection) { this.textDirection = textDirection; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
