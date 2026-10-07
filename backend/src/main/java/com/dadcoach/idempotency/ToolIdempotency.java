package com.dadcoach.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** reserve → execute → persist (playbook §11.2): one row per (scope, key); a replay returns the stored response. */
@Entity
@Table(name = "tool_idempotency")
public class ToolIdempotency {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String scope;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(nullable = false)
    private String status = "IN_PROGRESS";

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    /** Who made the call ("whatsapp:+E164"): a stored response is replayed only to the same actor. */
    @Column(name = "actor_ref")
    private String actorRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ToolIdempotency() {}

    public ToolIdempotency(String scope, String key, String payloadHash, String actorRef, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.scope = scope;
        this.idempotencyKey = key;
        this.payloadHash = payloadHash;
        this.actorRef = actorRef;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public String getScope() { return scope; }
    public String getPayloadHash() { return payloadHash; }
    public String getStatus() { return status; }
    public String getResponseBody() { return responseBody; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getActorRef() { return actorRef; }

    public void complete(boolean succeeded, int status, String body, Instant now) {
        this.status = succeeded ? "SUCCEEDED" : "FAILED";
        this.responseStatus = status;
        this.responseBody = body;
        this.completedAt = now;
    }
}
