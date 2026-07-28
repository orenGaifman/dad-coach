package com.dadcoach.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single API audit log entry.
 * <p>
 * Mapped to the {@code api_audit_log} table. Entries are append-only —
 * once written, they are never updated or deleted. This ensures an immutable
 * audit trail for all mutating API operations and admin reads on father data.
 * <p>
 * The {@code changes} field stores before/after state as JSONB, enabling
 * detailed change tracking without schema coupling to specific resources.
 */
@Entity
@Table(name = "api_audit_log")
public class ApiAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Unique request identifier for correlation with other logs and error responses.
     */
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    /**
     * Type of actor performing the operation (FATHER, ADMIN, SERVICE).
     */
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private String actorType;

    /**
     * Unique identifier of the actor performing the operation.
     */
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    /**
     * The operation performed, in the format "HTTP_METHOD endpoint"
     * (e.g., "POST /api/v1/fathers/me/children").
     */
    @Column(name = "operation", nullable = false, updatable = false, length = 50)
    private String operation;

    /**
     * The type of resource being operated on (e.g., "Father", "Child", "Goal").
     */
    @Column(name = "resource_type", nullable = false, updatable = false, length = 30)
    private String resourceType;

    /**
     * The specific resource identifier, if applicable. May be null for list/create operations.
     */
    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    /**
     * The result of the operation: SUCCESS or FAILURE.
     */
    @Column(name = "result", nullable = false, updatable = false, length = 20)
    private String result;

    /**
     * Error code if the operation failed, null on success.
     */
    @Column(name = "error_code", updatable = false, length = 50)
    private String errorCode;

    /**
     * JSONB field capturing before/after state for mutations.
     * Structure: {"before": {...}, "after": {...}}
     * Null for read operations or when state capture is not applicable.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", updatable = false, columnDefinition = "jsonb")
    private String changes;

    /**
     * Timestamp when the audit entry was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApiAuditEntry() {
        // JPA requires a no-arg constructor
    }

    private ApiAuditEntry(Builder builder) {
        this.requestId = builder.requestId;
        this.actorType = builder.actorType;
        this.actorId = builder.actorId;
        this.operation = builder.operation;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.result = builder.result;
        this.errorCode = builder.errorCode;
        this.changes = builder.changes;
        this.createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters only — no setters to enforce append-only semantics

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getOperation() {
        return operation;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getResult() {
        return result;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getChanges() {
        return changes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Builder for constructing immutable ApiAuditEntry instances.
     */
    public static final class Builder {
        private UUID requestId;
        private String actorType;
        private UUID actorId;
        private String operation;
        private String resourceType;
        private UUID resourceId;
        private String result;
        private String errorCode;
        private String changes;

        private Builder() {
        }

        public Builder requestId(UUID requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder actorType(String actorType) {
            this.actorType = actorType;
            return this;
        }

        public Builder actorId(UUID actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(UUID resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder changes(String changes) {
            this.changes = changes;
            return this;
        }

        public ApiAuditEntry build() {
            if (requestId == null) {
                throw new IllegalStateException("requestId is required");
            }
            if (actorType == null) {
                throw new IllegalStateException("actorType is required");
            }
            if (actorId == null) {
                throw new IllegalStateException("actorId is required");
            }
            if (operation == null) {
                throw new IllegalStateException("operation is required");
            }
            if (resourceType == null) {
                throw new IllegalStateException("resourceType is required");
            }
            if (result == null) {
                throw new IllegalStateException("result is required");
            }
            return new ApiAuditEntry(this);
        }
    }
}
