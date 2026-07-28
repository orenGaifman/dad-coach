package com.dadcoach.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Provides per-father mutual exclusion using PostgreSQL transaction-scoped advisory locks.
 *
 * <p>Uses {@code pg_advisory_xact_lock} which automatically releases on transaction
 * commit or rollback — no explicit unlock is needed.
 *
 * <p>Different fathers use different lock keys (derived from UUID most significant bits),
 * so they can be processed concurrently by separate threads.
 *
 * <p>The lock timeout is configurable via {@code conversation.session-lock.timeout-seconds}
 * (default: 45 seconds). If the lock cannot be acquired within the timeout, a
 * {@link SessionLockTimeoutException} is thrown, signaling the message should be queued for retry.
 */
@Service
public class SessionLockService {

    private static final Logger log = LoggerFactory.getLogger(SessionLockService.class);

    private final JdbcTemplate jdbcTemplate;
    private final long timeoutSeconds;

    public SessionLockService(
            JdbcTemplate jdbcTemplate,
            @Value("${conversation.session-lock.timeout-seconds:45}") long timeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Acquires a PostgreSQL advisory lock scoped to the current transaction for the given father.
     *
     * <p>The lock key is derived from the father's UUID most significant bits, ensuring
     * different fathers get different lock keys and can be processed concurrently.
     *
     * <p>This method sets the PostgreSQL {@code lock_timeout} for the current transaction,
     * then attempts to acquire the advisory lock. If another transaction already holds the
     * lock for this father, this call blocks until the lock is released or the timeout expires.
     *
     * <p>The lock is automatically released when the enclosing transaction commits or rolls back.
     *
     * @param fatherId the father's unique identifier
     * @throws SessionLockTimeoutException if the lock cannot be acquired within the configured timeout
     */
    public void acquireLock(UUID fatherId) {
        long lockKey = fatherId.getMostSignificantBits();

        log.debug("Acquiring session lock for father {} with key {} (timeout: {}s)",
                fatherId, lockKey, timeoutSeconds);

        try {
            // Set lock_timeout for this transaction so pg_advisory_xact_lock will fail
            // rather than block indefinitely if another transaction holds the lock.
            jdbcTemplate.execute("SET LOCAL lock_timeout = '" + timeoutSeconds + "s'");

            // Acquire the transaction-scoped advisory lock.
            // This blocks until the lock is available or lock_timeout is reached.
            // Auto-releases on COMMIT or ROLLBACK — no explicit unlock needed.
            jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");

            log.debug("Session lock acquired for father {} with key {}", fatherId, lockKey);
        } catch (Exception e) {
            if (isLockTimeoutException(e)) {
                log.warn("Session lock timeout for father {} after {}s. Message will be queued for retry.",
                        fatherId, timeoutSeconds);
                throw new SessionLockTimeoutException(fatherId, timeoutSeconds);
            }
            throw e;
        }
    }

    /**
     * Returns the configured lock timeout in seconds.
     */
    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Derives the lock key from a father ID. Exposed for testing purposes.
     */
    static long deriveLockKey(UUID fatherId) {
        return fatherId.getMostSignificantBits();
    }

    private boolean isLockTimeoutException(Exception e) {
        // PostgreSQL raises a "canceling statement due to lock timeout" error
        // with SQL state 55P03 when lock_timeout is exceeded.
        String message = e.getMessage();
        if (message != null && message.contains("lock timeout")) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains("lock timeout")) {
                return true;
            }
            // Check for PostgreSQL SQL state 55P03 (lock_not_available)
            if (cause instanceof java.sql.SQLException sqlEx) {
                if ("55P03".equals(sqlEx.getSQLState())) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
