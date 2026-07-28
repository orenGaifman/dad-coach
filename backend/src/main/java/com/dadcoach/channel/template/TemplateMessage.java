package com.dadcoach.channel.template;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a pre-approved WhatsApp message template.
 * Templates are required for sending messages when the 24-hour session window is closed.
 *
 * Each template has a unique name, a language (default: Spanish), a category,
 * a body with numbered placeholders ({{1}}, {{2}}), and a status indicating
 * whether it is approved for sending.
 */
@Entity
@Table(name = "template_messages")
public class TemplateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "template_name", nullable = false, unique = true, length = 100)
    private String templateName;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "max_variables", nullable = false)
    private int maxVariables;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TemplateMessage() {
        // JPA requires no-arg constructor
    }

    public TemplateMessage(String templateName, String language, String category,
                           String body, String status, int maxVariables) {
        this.templateName = templateName;
        this.language = language;
        this.category = category;
        this.body = body;
        this.status = status;
        this.maxVariables = maxVariables;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public String getTemplateName() { return templateName; }

    public String getLanguage() { return language; }

    public String getCategory() { return category; }

    public String getBody() { return body; }

    public String getStatus() { return status; }

    public int getMaxVariables() { return maxVariables; }

    public Instant getCreatedAt() { return createdAt; }

    // ─── Setters for mutable fields ──────────────────────────────────────

    public void setBody(String body) { this.body = body; }

    public void setStatus(String status) { this.status = status; }

    public void setMaxVariables(int maxVariables) { this.maxVariables = maxVariables; }

    public void setCategory(String category) { this.category = category; }

    // ─── Status helpers ──────────────────────────────────────────────────

    public boolean isApproved() {
        return "APPROVED".equals(this.status);
    }

    @Override
    public String toString() {
        return "TemplateMessage{" +
                "id=" + id +
                ", templateName='" + templateName + '\'' +
                ", language='" + language + '\'' +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", maxVariables=" + maxVariables +
                '}';
    }
}
