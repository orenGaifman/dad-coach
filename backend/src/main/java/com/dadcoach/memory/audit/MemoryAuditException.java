package com.dadcoach.memory.audit;

/**
 * Exception thrown when a memory audit operation fails.
 *
 * <p>This exception is used to signal audit logging failures in strict mode,
 * where audit failures should cause the entire memory operation transaction
 * to roll back. This ensures memory operations and audit entries are always
 * consistent.
 *
 * <p>From SPEC-004 Design - Correctness Properties:
 * "Audit log is append-only and written synchronously with memory operations
 * (rollback on audit failure)"
 *
 * @see MemoryAuditService#createAuditEntryStrict
 */
public class MemoryAuditException extends RuntimeException {

    /**
     * Creates a new MemoryAuditException with the specified message.
     *
     * @param message the detail message
     */
    public MemoryAuditException(String message) {
        super(message);
    }

    /**
     * Creates a new MemoryAuditException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause of the failure
     */
    public MemoryAuditException(String message, Throwable cause) {
        super(message, cause);
    }
}
