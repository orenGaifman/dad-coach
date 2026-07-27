package com.dadcoach.domain.notification;

import com.dadcoach.domain.father.Father;
import com.dadcoach.notification.NotificationType;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity representing a scheduled or delivered notification to a father.
 * Maps to the "notification" table.
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>type: What kind of notification (daily coaching, mission reminder, etc.)</li>
 *   <li>channel: Delivery channel (default: WHATSAPP)</li>
 *   <li>status: Current lifecycle state (SCHEDULED, DISPATCHED, DELIVERED, FAILED, CANCELLED)</li>
 *   <li>priority: 1-10 where 1 is highest priority</li>
 *   <li>scheduled_for: When the notification should be delivered</li>
 * </ul>
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20, nullable = false)
    private NotificationChannel channel = NotificationChannel.WHATSAPP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private NotificationStatus status = NotificationStatus.SCHEDULED;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "priority", nullable = false)
    private int priority = 5;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA requires a no-arg constructor
    }

    public Notification(Father father, NotificationType type, String content,
                        int priority, Instant scheduledFor) {
        this.father = father;
        this.type = type;
        this.content = content;
        this.priority = priority;
        this.scheduledFor = scheduledFor;
        this.createdAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Father getFather() {
        return father;
    }

    public void setFather(Father father) {
        this.father = father;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(Instant scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
