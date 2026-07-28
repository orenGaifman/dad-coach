package com.dadcoach.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a single audit entry for a memory's lifecycle.
 * <p>
 * Tracks all state transitions, modifications, and access events
 * including who performed the action and what changed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryAuditEntryDto {

    private UUID id;

    @JsonProperty("memory_id")
    private UUID memoryId;

    private String operation;

    @JsonProperty("actor_type")
    private String actorType;

    @JsonProperty("actor_id")
    private UUID actorId;

    @JsonProperty("previous_state")
    private String previousState;

    @JsonProperty("new_state")
    private String newState;

    private String details;

    @JsonProperty("created_at")
    private Instant createdAt;

    public MemoryAuditEntryDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(UUID memoryId) {
        this.memoryId = memoryId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getPreviousState() {
        return previousState;
    }

    public void setPreviousState(String previousState) {
        this.previousState = previousState;
    }

    public String getNewState() {
        return newState;
    }

    public void setNewState(String newState) {
        this.newState = newState;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
