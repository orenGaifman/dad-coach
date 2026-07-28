package com.dadcoach.channel.media;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a downloaded media asset (image, audio, video, document).
 * Media is stored as BYTEA in PostgreSQL at launch scale; future migration to object storage
 * requires only changing the storage layer, not this entity.
 *
 * Retention: 90 days from download. A daily cleanup job deletes expired assets.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset {

    private static final int RETENTION_DAYS = 90;

    @Id
    private UUID id;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private int fileSize;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private Instant downloadedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected MediaAsset() {
        // JPA requires no-arg constructor
    }

    public MediaAsset(UUID fatherId, UUID messageId, String mimeType, byte[] content) {
        this.id = UUID.randomUUID();
        this.fatherId = fatherId;
        this.messageId = messageId;
        this.mimeType = mimeType;
        this.content = content;
        this.fileSize = content.length;
        this.downloadedAt = Instant.now();
        this.expiresAt = this.downloadedAt.plus(java.time.Duration.ofDays(RETENTION_DAYS));
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getFatherId() { return fatherId; }

    public UUID getMessageId() { return messageId; }

    public String getMimeType() { return mimeType; }

    public int getFileSize() { return fileSize; }

    public byte[] getContent() { return content; }

    public Instant getDownloadedAt() { return downloadedAt; }

    public Instant getExpiresAt() { return expiresAt; }

    public static int getRetentionDays() { return RETENTION_DAYS; }

    @Override
    public String toString() {
        return "MediaAsset{" +
                "id=" + id +
                ", fatherId=" + fatherId +
                ", messageId=" + messageId +
                ", mimeType='" + mimeType + '\'' +
                ", fileSize=" + fileSize +
                ", downloadedAt=" + downloadedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
