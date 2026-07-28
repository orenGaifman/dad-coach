package com.dadcoach.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionLockService Unit Tests")
class SessionLockServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SessionLockService sessionLockService;

    @BeforeEach
    void setUp() {
        sessionLockService = new SessionLockService(jdbcTemplate, 45);
    }

    @Test
    @DisplayName("acquireLock sets lock_timeout and calls pg_advisory_xact_lock with father_id most significant bits")
    void acquireLock_setsTimeoutAndAcquiresLock() {
        UUID fatherId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        long expectedLockKey = fatherId.getMostSignificantBits();

        sessionLockService.acquireLock(fatherId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(sqlCaptor.capture());

        var executedSql = sqlCaptor.getAllValues();
        assertThat(executedSql.get(0)).isEqualTo("SET LOCAL lock_timeout = '45s'");
        assertThat(executedSql.get(1)).isEqualTo("SELECT pg_advisory_xact_lock(" + expectedLockKey + ")");
    }

    @Test
    @DisplayName("acquireLock uses configurable timeout value")
    void acquireLock_usesConfigurableTimeout() {
        SessionLockService customTimeoutService = new SessionLockService(jdbcTemplate, 60);
        UUID fatherId = UUID.randomUUID();

        customTimeoutService.acquireLock(fatherId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(0)).isEqualTo("SET LOCAL lock_timeout = '60s'");
    }

    @Test
    @DisplayName("acquireLock throws SessionLockTimeoutException on lock timeout (SQL state 55P03)")
    void acquireLock_throwsTimeoutException_onLockTimeout() {
        UUID fatherId = UUID.randomUUID();
        SQLException sqlException = new SQLException("canceling statement due to lock timeout", "55P03");
        DataAccessResourceFailureException wrappedException =
                new DataAccessResourceFailureException("lock timeout", sqlException);

        // First call (SET LOCAL) succeeds, second call (pg_advisory_xact_lock) fails
        doNothing().when(jdbcTemplate).execute("SET LOCAL lock_timeout = '45s'");
        doThrow(wrappedException).when(jdbcTemplate).execute(
                "SELECT pg_advisory_xact_lock(" + fatherId.getMostSignificantBits() + ")");

        assertThatThrownBy(() -> sessionLockService.acquireLock(fatherId))
                .isInstanceOf(SessionLockTimeoutException.class)
                .hasMessageContaining(fatherId.toString())
                .hasMessageContaining("45 seconds");

        SessionLockTimeoutException ex = catchThrowableOfType(
                () -> sessionLockService.acquireLock(fatherId),
                SessionLockTimeoutException.class);
        assertThat(ex.getFatherId()).isEqualTo(fatherId);
        assertThat(ex.getTimeoutSeconds()).isEqualTo(45);
    }

    @Test
    @DisplayName("acquireLock rethrows non-timeout exceptions as-is")
    void acquireLock_rethrowsNonTimeoutExceptions() {
        UUID fatherId = UUID.randomUUID();
        RuntimeException otherException = new RuntimeException("connection lost");

        doNothing().when(jdbcTemplate).execute("SET LOCAL lock_timeout = '45s'");
        doThrow(otherException).when(jdbcTemplate).execute(
                "SELECT pg_advisory_xact_lock(" + fatherId.getMostSignificantBits() + ")");

        assertThatThrownBy(() -> sessionLockService.acquireLock(fatherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection lost");
    }

    @Test
    @DisplayName("different father IDs produce different lock keys enabling concurrent processing")
    void differentFathers_produceDifferentLockKeys() {
        UUID father1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID father2 = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

        long key1 = SessionLockService.deriveLockKey(father1);
        long key2 = SessionLockService.deriveLockKey(father2);

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("same father ID always produces the same lock key")
    void sameFather_producesSameLockKey() {
        UUID fatherId = UUID.randomUUID();

        long key1 = SessionLockService.deriveLockKey(fatherId);
        long key2 = SessionLockService.deriveLockKey(fatherId);

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    @DisplayName("getTimeoutSeconds returns configured value")
    void getTimeoutSeconds_returnsConfiguredValue() {
        assertThat(sessionLockService.getTimeoutSeconds()).isEqualTo(45);

        SessionLockService customService = new SessionLockService(jdbcTemplate, 120);
        assertThat(customService.getTimeoutSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("lock key uses UUID most significant bits")
    void lockKey_usesUuidMostSignificantBits() {
        UUID fatherId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        long expected = fatherId.getMostSignificantBits();

        assertThat(SessionLockService.deriveLockKey(fatherId)).isEqualTo(expected);
    }

    @Test
    @DisplayName("acquireLock detects timeout from exception message containing 'lock timeout'")
    void acquireLock_detectsTimeoutFromMessage() {
        UUID fatherId = UUID.randomUUID();
        RuntimeException lockTimeoutEx = new RuntimeException("ERROR: canceling statement due to lock timeout");

        doNothing().when(jdbcTemplate).execute("SET LOCAL lock_timeout = '45s'");
        doThrow(lockTimeoutEx).when(jdbcTemplate).execute(
                "SELECT pg_advisory_xact_lock(" + fatherId.getMostSignificantBits() + ")");

        assertThatThrownBy(() -> sessionLockService.acquireLock(fatherId))
                .isInstanceOf(SessionLockTimeoutException.class);
    }
}
