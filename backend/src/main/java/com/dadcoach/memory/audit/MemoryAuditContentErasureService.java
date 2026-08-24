package com.dadcoach.memory.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for erasing content snapshots from memory audit log entries.
 *
 * <p>From SPEC-004 Requirement 2 Criteria 7:
 * When performing content erasure on DELETED memories, THE Memory_System SHALL
 * erase all version history content_snapshots (from audit log state_before/state_after fields).
 * Only audit metadata (memory_id, father_id, category, operation timestamps, state transitions)
 * SHALL be retained.
 *
 * <p>This service performs bulk updates to audit log entries to null out the
 * state_before and state_after JSON fields that contain the full memory content.
 * This is a special operation that uses direct JDBC to bypass JPA's @PreUpdate
 * protection on the immutable audit log entity.
 *
 * <p><strong>Important:</strong> This service is specifically designed for GDPR-compliant
 * content erasure. Normal audit operations should use {@link MemoryAuditService}.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Uses direct JDBC update to bypass JPA @PreUpdate callback (intentional for erasure)</li>
 *   <li>Only nullifies state_before/state_after, preserving all metadata</li>
 *   <li>Operates transactionally with the memory erasure</li>
 *   <li>Logs all erasure operations for compliance tracking</li>
 * </ul>
 *
 * @see MemoryAuditLog
 * @see com.dadcoach.memory.lifecycle.MemoryErasureJob
 */
@Service
public class MemoryAuditContentErasureService {

    private static final Logger log = LoggerFactory.getLogger(MemoryAuditContentErasureService.class);

    /**
     * Placeholder value to indicate content was erased.
     * This preserves the audit trail while removing actual content.
     */
    public static final String ERASED_PLACEHOLDER = "{\"erased\":true,\"reason\":\"GDPR_COMPLIANT_ERASURE\"}";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a MemoryAuditContentErasureService.
     *
     * @param jdbcTemplate the JDBC template for direct database updates
     */
    public MemoryAuditContentErasureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Erases content snapshots (state_before, state_after) from all audit entries
     * for a specific memory.
     *
     * <p>This operation:
     * <ul>
     *   <li>Sets state_before to a placeholder JSON indicating erasure</li>
     *   <li>Sets state_after to a placeholder JSON indicating erasure</li>
     *   <li>Preserves all other audit metadata (memory_id, father_id, operation_type, etc.)</li>
     * </ul>
     *
     * <p>The update is performed directly via JDBC to bypass the @PreUpdate callback
     * on MemoryAuditLog that prevents normal updates. This is intentional for erasure
     * operations.
     *
     * @param memoryId the ID of the memory whose audit content should be erased
     * @return the number of audit entries that were updated
     */
    @Transactional
    public int eraseAuditContentForMemory(UUID memoryId) {
        log.debug("MemoryAuditContentErasureService: Erasing audit content for memory. memoryId={}",
                memoryId);

        String sql = """
                UPDATE memory_audit_log 
                SET state_before = CAST(? AS JSONB),
                    state_after = CAST(? AS JSONB)
                WHERE memory_id = ?
                  AND (state_before IS NOT NULL OR state_after IS NOT NULL)
                  AND state_after::text NOT LIKE '%"erased":true%'
                """;

        int updatedCount = jdbcTemplate.update(
                sql,
                ERASED_PLACEHOLDER,
                ERASED_PLACEHOLDER,
                memoryId
        );

        log.info("MemoryAuditContentErasureService: Erased audit content for memory. " +
                        "memoryId={}, auditEntriesUpdated={}",
                memoryId, updatedCount);

        return updatedCount;
    }

    /**
     * Erases content snapshots from all audit entries for a father.
     *
     * <p>This is used for bulk GDPR erasure when a father's entire account
     * and data is being deleted.
     *
     * @param fatherId the ID of the father whose audit content should be erased
     * @return the number of audit entries that were updated
     */
    @Transactional
    public int eraseAuditContentForFather(UUID fatherId) {
        log.debug("MemoryAuditContentErasureService: Erasing audit content for father. fatherId={}",
                fatherId);

        String sql = """
                UPDATE memory_audit_log 
                SET state_before = CAST(? AS JSONB),
                    state_after = CAST(? AS JSONB)
                WHERE father_id = ?
                  AND (state_before IS NOT NULL OR state_after IS NOT NULL)
                  AND state_after::text NOT LIKE '%"erased":true%'
                """;

        int updatedCount = jdbcTemplate.update(
                sql,
                ERASED_PLACEHOLDER,
                ERASED_PLACEHOLDER,
                fatherId
        );

        log.info("MemoryAuditContentErasureService: Erased audit content for father. " +
                        "fatherId={}, auditEntriesUpdated={}",
                fatherId, updatedCount);

        return updatedCount;
    }

    /**
     * Checks if audit content has already been erased for a memory.
     *
     * @param memoryId the ID of the memory to check
     * @return true if content has been erased (or was never present)
     */
    public boolean isAuditContentErased(UUID memoryId) {
        String sql = """
                SELECT COUNT(*) 
                FROM memory_audit_log 
                WHERE memory_id = ?
                  AND state_after IS NOT NULL
                  AND state_after::text NOT LIKE '%"erased":true%'
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, memoryId);
        return count == null || count == 0;
    }
}
