package com.dadcoach.memory.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryAuditContentErasureService}.
 *
 * <p>Tests cover SPEC-004 Requirement 2 Criteria 7:
 * <ul>
 *   <li>Version history erasure from audit log state_before/state_after fields</li>
 *   <li>Preservation of audit metadata (memory_id, father_id, timestamps, etc.)</li>
 *   <li>Bulk erasure for GDPR compliance</li>
 *   <li>Skip logic for already erased entries</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 2.7, 17 (SPEC-004)</strong>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryAuditContentErasureService Tests")
class MemoryAuditContentErasureServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MemoryAuditContentErasureService erasureService;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    private UUID memoryId;
    private UUID fatherId;

    @BeforeEach
    void setUp() {
        erasureService = new MemoryAuditContentErasureService(jdbcTemplate);
        memoryId = UUID.randomUUID();
        fatherId = UUID.randomUUID();
    }

    // ─── Memory-Level Erasure Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Memory-Level Audit Content Erasure")
    class MemoryLevelErasureTests {

        @Test
        @DisplayName("Should erase audit content for a specific memory")
        void shouldEraseAuditContentForMemory() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(5);

            // When
            int result = erasureService.eraseAuditContentForMemory(memoryId);

            // Then
            assertThat(result).isEqualTo(5);
            verify(jdbcTemplate).update(
                    contains("UPDATE memory_audit_log"),
                    eq(MemoryAuditContentErasureService.ERASED_PLACEHOLDER),
                    eq(MemoryAuditContentErasureService.ERASED_PLACEHOLDER),
                    eq(memoryId)
            );
        }

        @Test
        @DisplayName("Should return 0 when no audit entries exist")
        void shouldReturnZeroWhenNoAuditEntriesExist() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);

            // When
            int result = erasureService.eraseAuditContentForMemory(memoryId);

            // Then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("Should use GDPR-compliant erasure placeholder")
        void shouldUseGdprCompliantErasurePlaceholder() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

            // When
            erasureService.eraseAuditContentForMemory(memoryId);

            // Then
            verify(jdbcTemplate).update(
                    anyString(),
                    eq(MemoryAuditContentErasureService.ERASED_PLACEHOLDER),
                    eq(MemoryAuditContentErasureService.ERASED_PLACEHOLDER),
                    any()
            );
            
            // Verify placeholder content indicates erasure reason
            assertThat(MemoryAuditContentErasureService.ERASED_PLACEHOLDER)
                    .contains("erased")
                    .contains("GDPR");
        }
    }

    // ─── Father-Level Erasure Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Father-Level Audit Content Erasure (GDPR)")
    class FatherLevelErasureTests {

        @Test
        @DisplayName("Should erase all audit content for a father")
        void shouldEraseAllAuditContentForFather() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(25);

            // When
            int result = erasureService.eraseAuditContentForFather(fatherId);

            // Then
            assertThat(result).isEqualTo(25);
            verify(jdbcTemplate).update(
                    contains("UPDATE memory_audit_log"),
                    eq(MemoryAuditContentErasureService.ERASED_PLACEHOLDER),
                    eq(MemoryAuditContentErasureService.ERASED_PLACEHOLDER),
                    eq(fatherId)
            );
        }

        @Test
        @DisplayName("Should return 0 when father has no audit entries")
        void shouldReturnZeroWhenFatherHasNoAuditEntries() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);

            // When
            int result = erasureService.eraseAuditContentForFather(fatherId);

            // Then
            assertThat(result).isEqualTo(0);
        }
    }

    // ─── Erasure Check Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Erasure Check")
    class ErasureCheckTests {

        @Test
        @DisplayName("Should return true when audit content is already erased")
        void shouldReturnTrueWhenAuditContentIsErased() {
            // Given: No non-erased entries found
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(memoryId)))
                    .thenReturn(0);

            // When
            boolean result = erasureService.isAuditContentErased(memoryId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when audit content is not erased")
        void shouldReturnFalseWhenAuditContentIsNotErased() {
            // Given: Some non-erased entries exist
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(memoryId)))
                    .thenReturn(3);

            // When
            boolean result = erasureService.isAuditContentErased(memoryId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true when query returns null")
        void shouldReturnTrueWhenQueryReturnsNull() {
            // Given
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(memoryId)))
                    .thenReturn(null);

            // When
            boolean result = erasureService.isAuditContentErased(memoryId);

            // Then
            assertThat(result).isTrue();
        }
    }

    // ─── SQL Query Tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("SQL Query Verification")
    class SqlQueryTests {

        @Test
        @DisplayName("Should exclude already erased entries from update")
        void shouldExcludeAlreadyErasedEntriesFromUpdate() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

            // When
            erasureService.eraseAuditContentForMemory(memoryId);

            // Then - verify the SQL excludes already erased entries
            verify(jdbcTemplate).update(
                    argThat(sql -> sql.contains("NOT LIKE") && sql.contains("erased")),
                    any(), any(), any()
            );
        }

        @Test
        @DisplayName("Should only update entries with non-null state fields")
        void shouldOnlyUpdateEntriesWithNonNullStateFields() {
            // Given
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

            // When
            erasureService.eraseAuditContentForMemory(memoryId);

            // Then - verify SQL checks for non-null fields
            verify(jdbcTemplate).update(
                    argThat(sql -> sql.contains("IS NOT NULL")),
                    any(), any(), any()
            );
        }
    }
}
