package com.dadcoach.workflow.message;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a message template in the workflow engine.
 * Maps to the "message_templates" table.
 * 
 * Message templates store fallback text content for each message type,
 * supporting placeholder substitution with {placeholder_name} syntax.
 * 
 * Requirements: 10.4 - Every message type SHALL have a corresponding fallback template
 */
@Entity
@Table(name = "message_templates")
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Unique identifier for the message type (e.g., WELCOME_GREETING, SCHEDULE_CONFIRM).
     */
    @Column(name = "message_type", length = 50, nullable = false, unique = true)
    private String messageType;

    /**
     * The template text with {placeholder} syntax for variable substitution.
     */
    @Column(name = "template_text", columnDefinition = "TEXT", nullable = false)
    private String templateText;

    /**
     * ISO language code. Supported values: 'en' (English) or 'he' (Hebrew).
     * The language is determined by the father's language preference.
     */
    @Column(name = "language", length = 10, nullable = false)
    private String language;

    /**
     * Whether this template is currently active for use.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Timestamp when this template was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when this template was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA-required no-arg constructor. Not for application use.
     */
    protected MessageTemplate() {
    }

    /**
     * Creates a new message template.
     * 
     * @param messageType the unique message type identifier
     * @param templateText the template text with placeholders
     */
    public MessageTemplate(String messageType, String templateText) {
        this.messageType = messageType;
        this.templateText = templateText;
    }

    /**
     * Creates a new message template with specified language.
     * 
     * @param messageType the unique message type identifier
     * @param templateText the template text with placeholders
     * @param language the ISO language code
     */
    public MessageTemplate(String messageType, String templateText, String language) {
        this.messageType = messageType;
        this.templateText = templateText;
        this.language = language;
    }

    // ─── JPA Lifecycle Callbacks ─────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTemplateText() {
        return templateText;
    }

    public void setTemplateText(String templateText) {
        this.templateText = templateText;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "MessageTemplate{" +
                "id=" + id +
                ", messageType='" + messageType + '\'' +
                ", language='" + language + '\'' +
                ", active=" + active +
                '}';
    }
}
