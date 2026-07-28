package com.dadcoach.workspace.growth.celebration;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a celebration event in the growth system.
 *
 * <p>Celebration events are generated when a father achieves notable progress
 * (belt level-up, achievement earned, milestone reached, streak milestone).
 * They remain undisplayed until the client marks them as seen.</p>
 *
 * <p>Maps to the {@code celebration_events} table (V8.006 migration).</p>
 */
@Entity
@Table(name = "celebration_events")
public class CelebrationEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private CelebrationEventType eventType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "related_growth_signal_points")
    private Integer relatedGrowthSignalPoints;

    @Column(name = "celebration_message")
    private String celebrationMessage;

    @Column(name = "motivational_prompt")
    private String motivationalPrompt;

    @Column(name = "displayed", nullable = false)
    private boolean displayed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CelebrationEvent() {
        // JPA
    }

    public CelebrationEvent(UUID fatherId, CelebrationEventType eventType,
                            String title, String description, Integer relatedGrowthSignalPoints) {
        this.eventId = UUID.randomUUID();
        this.fatherId = fatherId;
        this.eventType = eventType;
        this.title = title;
        this.description = description;
        this.relatedGrowthSignalPoints = relatedGrowthSignalPoints;
        this.displayed = false;
        this.createdAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public CelebrationEventType getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getRelatedGrowthSignalPoints() {
        return relatedGrowthSignalPoints;
    }

    public String getCelebrationMessage() {
        return celebrationMessage;
    }

    public void setCelebrationMessage(String celebrationMessage) {
        this.celebrationMessage = celebrationMessage;
    }

    public String getMotivationalPrompt() {
        return motivationalPrompt;
    }

    public void setMotivationalPrompt(String motivationalPrompt) {
        this.motivationalPrompt = motivationalPrompt;
    }

    public boolean isDisplayed() {
        return displayed;
    }

    public void setDisplayed(boolean displayed) {
        this.displayed = displayed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
