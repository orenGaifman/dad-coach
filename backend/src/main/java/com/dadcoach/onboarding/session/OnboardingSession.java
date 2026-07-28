package com.dadcoach.onboarding.session;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a server-side onboarding wizard session.
 * Maps to the "onboarding_sessions" table.
 *
 * <p>Sessions are created when a father begins the registration flow after invitation validation.
 * Each session tracks the current wizard step, accumulated wizard data (encrypted at rest),
 * and has a 72-hour TTL from creation.
 *
 * <p>The session cookie (ONBOARDING_SESSION) contains a 256-bit random ID that maps to this record.
 * The wizard_data field is encrypted with AES-256-GCM via {@link WizardDataEncryptor}.
 *
 * @see WizardStep
 * @see SessionStatus
 * @see WizardData
 */
@Entity
@Table(name = "onboarding_sessions", indexes = {
    @Index(name = "idx_sessions_invitation", columnList = "invitation_id"),
    @Index(name = "idx_sessions_father", columnList = "father_id"),
    @Index(name = "idx_sessions_status_expires", columnList = "status, expires_at")
})
public class OnboardingSession {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "invitation_id", nullable = false)
    private UUID invitationId;

    @Column(name = "father_id")
    private UUID fatherId;  // Nullable until provisioning

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 20)
    private WizardStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private SessionStatus status;

    @Convert(converter = WizardDataEncryptor.class)
    @Column(name = "wizard_data", columnDefinition = "bytea")
    private WizardData wizardData;

    @Column(name = "language", length = 5)
    private String language;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    protected OnboardingSession() {
        // JPA requires a no-arg constructor
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getInvitationId() {
        return invitationId;
    }

    public void setInvitationId(UUID invitationId) {
        this.invitationId = invitationId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public void setFatherId(UUID fatherId) {
        this.fatherId = fatherId;
    }

    public WizardStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(WizardStep currentStep) {
        this.currentStep = currentStep;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public WizardData getWizardData() {
        return wizardData;
    }

    public void setWizardData(WizardData wizardData) {
        this.wizardData = wizardData;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Returns true if this session has expired based on the current time.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns true if this session is still active (IN_PROGRESS and not expired).
     */
    public boolean isActive() {
        return status == SessionStatus.IN_PROGRESS && !isExpired();
    }

    /**
     * Updates the last activity timestamp to now.
     */
    public void touch() {
        this.lastActivityAt = Instant.now();
    }
}
