package com.dadcoach.scheduler;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity for logging mission reminders sent to fathers.
 * Used for tracking and debugging reminder delivery.
 * Maps to the mission_reminder_log table from V19 migration.
 */
@Entity
@Table(name = "mission_reminder_log")
public class MissionReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "father_id", nullable = false)
    private Long fatherId;

    @Column(name = "reminder_type", length = 30, nullable = false)
    private String reminderType; // SCHEDULED, OVERDUE, FOLLOW_UP

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "channel", length = 20, nullable = false)
    private String channel = "WHATSAPP"; // WHATSAPP, PUSH, EMAIL

    @Column(name = "response_received_at")
    private Instant responseReceivedAt;

    @Column(name = "response_type", length = 20)
    private String responseType; // ACCEPTED, RESCHEDULED, SKIPPED, NO_RESPONSE

    protected MissionReminderLog() {}

    public MissionReminderLog(Long fatherId, Long missionId, String reminderType) {
        this.fatherId = fatherId;
        this.missionId = missionId;
        this.reminderType = reminderType;
        this.sentAt = Instant.now();
        this.channel = "WHATSAPP";
    }

    // ─── Static Factory Methods ──────────────────────────────────────────

    public static MissionReminderLog create(Long fatherId, Long missionId, String reminderType) {
        return new MissionReminderLog(fatherId, missionId, reminderType);
    }

    public static MissionReminderLog create(Long fatherId, Long missionId, String reminderType, String channel) {
        MissionReminderLog log = new MissionReminderLog(fatherId, missionId, reminderType);
        log.setChannel(channel);
        return log;
    }

    // ─── Response Handling ───────────────────────────────────────────────

    public void recordResponse(String responseType) {
        this.responseReceivedAt = Instant.now();
        this.responseType = responseType;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }

    public Long getFatherId() { return fatherId; }
    public void setFatherId(Long fatherId) { this.fatherId = fatherId; }

    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Instant getResponseReceivedAt() { return responseReceivedAt; }
    public void setResponseReceivedAt(Instant responseReceivedAt) { this.responseReceivedAt = responseReceivedAt; }

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }
}
