package com.dadcoach.calendar;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity for logging calendar synchronization events.
 * Tracks creation, updates, and deletions of calendar events.
 * Maps to the calendar_sync_log table from V19 migration.
 */
@Entity
@Table(name = "calendar_sync_log")
public class CalendarSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "father_id", nullable = false)
    private Long fatherId;

    @Column(name = "mission_id")
    private Long missionId;

    @Column(name = "action", length = 30, nullable = false)
    private String action; // CREATE, UPDATE, DELETE

    @Column(name = "calendar_event_id", length = 255)
    private String calendarEventId;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected CalendarSyncLog() {}

    public CalendarSyncLog(Long fatherId, Long missionId, String action) {
        this.fatherId = fatherId;
        this.missionId = missionId;
        this.action = action;
        this.syncedAt = Instant.now();
        this.success = true;
    }

    // ─── Static Factory Methods ──────────────────────────────────────────

    public static CalendarSyncLog success(Long fatherId, Long missionId, String action, String eventId) {
        CalendarSyncLog log = new CalendarSyncLog(fatherId, missionId, action);
        log.setCalendarEventId(eventId);
        log.setSuccess(true);
        return log;
    }

    public static CalendarSyncLog failure(Long fatherId, Long missionId, String action, String errorMessage) {
        CalendarSyncLog log = new CalendarSyncLog(fatherId, missionId, action);
        log.setSuccess(false);
        log.setErrorMessage(errorMessage);
        return log;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFatherId() { return fatherId; }
    public void setFatherId(Long fatherId) { this.fatherId = fatherId; }

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getCalendarEventId() { return calendarEventId; }
    public void setCalendarEventId(String calendarEventId) { this.calendarEventId = calendarEventId; }

    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
