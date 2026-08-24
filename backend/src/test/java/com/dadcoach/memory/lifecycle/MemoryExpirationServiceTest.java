package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Unit tests for {@link MemoryExpirationService}.
 *
 * <p>Tests cover SPEC-004 Requirements 2 (lifecycle states) and 6 (decay/aging):
 * <ul>
 *   <li>Time-based expiration (expires_at check)</li>
 *   <li>Confidence-based expiration (low confidence + no access)</li>
 *   <li>Long-term tier exemption</li>
 *   <li>Category exemptions (IDENTITY, FAMILY, GOAL)</li>
 *   <li>Race condition protection</li>
 *   <li>Audit logging</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryExpirationServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryAuditService auditService;

    @InjectMocks
    private MemoryExpirationService expirationService;

    @Captor
    private ArgumentCaptor<Memory> memoryCaptor;

    private UUID fatherId;
    private Instant now;

    @BeforeEach
    void setUp() {
        fatherId = UUID.randomUUID();
        now = Instant.now();
        when(auditService.serializeMemoryState(any(Memory.class))).thenReturn("{}");
    }

    // ─── Time-Based Expiration Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Time-Based Expiration")
    class TimeBasedExpirationTests {

        @Test
        @DisplayName("Should expire ACTIVE memory past its expires_at timestamp")
        void shouldExpireMemoryPastExpiresAt() {
            // Given: A short-term memory (importance 3) that expired 5 days ago
            Memory memory = createMemory(3, new BigDecimal("0.80"));
            memory.setExpiresAt(now.minus(5, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS)); // Before job start

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerTimeBasedExpiration();

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            Memory savedMemory = memoryCaptor.getValue();
            assertThat(savedMemory.getState()).isEqualTo(MemoryState.EXPIRED);
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesExpired()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(0);

            // Verify audit was created
            verify(auditService).createAuditEntry(eq(memory), eq(EventType.UPDATE), eq(ActorType.SYSTEM), any());
        }

        @Test
        @DisplayName("Should expire medium-term memory (importance 4-6) past expires_at")
        void shouldExpireMediumTermMemoryPastExpiresAt() {
            // Given: A medium-term memory (importance 5) that expired
            Memory memory = createMemory(5, new BigDecimal("0.70"));
            memory.setExpiresAt(now.minus(1, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerTimeBasedExpiration();

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
            assertThat(result.memoriesExpired()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should skip Long-term tier memory even if has expires_at (data inconsistency)")
        void shouldSkipLongTermMemoryWithExpiresAt() {
            // Given: A long-term memory (importance 8) that unexpectedly has expires_at set
            // This is a data inconsistency - long-term memories should have null expires_at
            Memory memory = createMemory(8, new BigDecimal("0.80"));
            memory.setExpiresAt(now.minus(1, ChronoUnit.DAYS)); // Unexpected: should be null
            memory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerTimeBasedExpiration();

            // Then: Memory should be skipped, not expired
            verify(memoryRepository, never()).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesExpired()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip memory that changed state since job start (race condition)")
        void shouldSkipMemoryChangedSinceJobStart() {
            // Given: A memory that was updated after job start time
            Memory memory = createMemory(3, new BigDecimal("0.80"));
            memory.setExpiresAt(now.minus(1, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.plus(1, ChronoUnit.SECONDS)); // After job start

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerTimeBasedExpiration();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesExpired()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle no expired memories")
        void shouldHandleNoExpiredMemories() {
            // Given: No expired memories
            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(Collections.emptyList());

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerTimeBasedExpiration();

            // Then
            verify(memoryRepository, never()).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(0);
            assertThat(result.memoriesExpired()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should process multiple expired memories")
        void shouldProcessMultipleExpiredMemories() {
            // Given: Multiple expired memories
            Memory memory1 = createMemory(2, new BigDecimal("0.70"));
            memory1.setExpiresAt(now.minus(5, ChronoUnit.DAYS));
            memory1.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            Memory memory2 = createMemory(4, new BigDecimal("0.60"));
            memory2.setExpiresAt(now.minus(3, ChronoUnit.DAYS));
            memory2.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            Memory memory3 = createMemory(6, new BigDecimal("0.50"));
            memory3.setExpiresAt(now.minus(1, ChronoUnit.DAYS));
            memory3.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory1, memory2, memory3));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerTimeBasedExpiration();

            // Then
            verify(memoryRepository, times(3)).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(3);
            assertThat(result.memoriesExpired()).isEqualTo(3);
        }
    }

    // ─── Confidence-Based Expiration Tests ───────────────────────────────────

    @Nested
    @DisplayName("Confidence-Based Expiration")
    class ConfidenceBasedExpirationTests {

        @Test
        @DisplayName("Should expire memory with low confidence and no recent access")
        void shouldExpireMemoryWithLowConfidenceAndNoRecentAccess() {
            // Given: A short-term memory with confidence < 0.5 and not accessed in 60+ days
            Memory memory = createMemory(3, new BigDecimal("0.40"));
            memory.setLastAccessedAt(now.minus(70, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(90, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
            assertThat(result.memoriesExpired()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should expire medium-term memory with low confidence")
        void shouldExpireMediumTermWithLowConfidence() {
            // Given: A medium-term memory (importance 5) with low confidence
            Memory memory = createMemory(5, new BigDecimal("0.45"));
            memory.setLastAccessedAt(now.minus(65, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(90, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("Should skip Long-term tier memory with low confidence")
        void shouldSkipLongTermWithLowConfidence() {
            // Given: A long-term memory (importance 8) with low confidence
            // Per Req 6 criteria 7, long-term memories are exempt from confidence-based expiration
            Memory memory = createMemory(8, new BigDecimal("0.40"));
            memory.setLastAccessedAt(now.minus(100, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesExpired()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip IDENTITY memory with confidence 1.0")
        void shouldSkipIdentityMemoryWithFullConfidence() {
            // Given: An IDENTITY memory with confidence 1.0 (exempt per Req 6 criteria 7)
            Memory memory = createMemory(5, BigDecimal.ONE);
            memory.setCategory(MemoryCategory.IDENTITY);
            memory.setLastAccessedAt(now.minus(100, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should skip FAMILY memory with high confidence")
        void shouldSkipFamilyMemoryWithHighConfidence() {
            // Given: A FAMILY memory with confidence >= 0.9 (exempt per Req 6 criteria 7)
            Memory memory = createMemory(5, new BigDecimal("0.95"));
            memory.setCategory(MemoryCategory.FAMILY);
            memory.setLastAccessedAt(now.minus(100, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should expire FAMILY memory with low confidence (below 0.9 threshold)")
        void shouldExpireFamilyMemoryWithLowConfidence() {
            // Given: A FAMILY memory with confidence < 0.5 (below exempt threshold)
            Memory memory = createMemory(5, new BigDecimal("0.40"));
            memory.setCategory(MemoryCategory.FAMILY);
            memory.setLastAccessedAt(now.minus(100, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be expired (confidence < 0.5 is well below 0.9 exempt threshold)
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("Should skip GOAL memory linked to active goal")
        void shouldSkipGoalMemoryLinkedToActiveGoal() {
            // Given: A GOAL memory linked to an active goal (exempt per Req 6 criteria 7)
            Memory memory = createMemory(5, new BigDecimal("0.40"));
            memory.setCategory(MemoryCategory.GOAL);
            memory.setGoalId(UUID.randomUUID()); // Has associated goal
            memory.setLastAccessedAt(now.minus(100, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
        }

        @Test
        @DisplayName("Should expire GOAL memory not linked to any goal")
        void shouldExpireGoalMemoryWithoutGoal() {
            // Given: A GOAL memory NOT linked to any goal
            Memory memory = createMemory(5, new BigDecimal("0.40"));
            memory.setCategory(MemoryCategory.GOAL);
            memory.setGoalId(null); // No associated goal
            memory.setLastAccessedAt(now.minus(100, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be expired
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("Should skip memory with null lastAccessedAt (never accessed)")
        void shouldHandleNullLastAccessedAt() {
            // Given: A memory that has never been accessed (lastAccessedAt is null)
            // Repository query includes this case: "m.lastAccessedAt IS NULL OR m.lastAccessedAt < :accessThreshold"
            Memory memory = createMemory(3, new BigDecimal("0.40"));
            memory.setLastAccessedAt(null); // Never accessed
            memory.setLastUpdatedAt(now.minus(120, ChronoUnit.DAYS));

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be expired (null lastAccessedAt treated as never accessed)
            verify(memoryRepository).save(memoryCaptor.capture());
            assertThat(memoryCaptor.getValue().getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("Should skip memory that changed state since job start (race condition)")
        void shouldSkipMemoryChangedSinceJobStartConfidenceBased() {
            // Given: A memory that was updated after job start time
            Memory memory = createMemory(3, new BigDecimal("0.40"));
            memory.setLastAccessedAt(now.minus(70, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.plus(1, ChronoUnit.SECONDS)); // After job start

            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(memory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerConfidenceBasedExpiration();

            // Then: Memory should be skipped
            verify(memoryRepository, never()).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(1);
            assertThat(result.memoriesExpired()).isEqualTo(0);
        }
    }

    // ─── Full Expiration Job Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Full Expiration Job")
    class FullExpirationJobTests {

        @Test
        @DisplayName("Should combine time-based and confidence-based expiration")
        void shouldCombineBothExpirationTypes() {
            // Given: One time-based expired memory and one confidence-based expired memory
            Memory timeBasedMemory = createMemory(3, new BigDecimal("0.80"));
            timeBasedMemory.setExpiresAt(now.minus(5, ChronoUnit.DAYS));
            timeBasedMemory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            Memory confidenceBasedMemory = createMemory(4, new BigDecimal("0.40"));
            confidenceBasedMemory.setLastAccessedAt(now.minus(70, ChronoUnit.DAYS));
            confidenceBasedMemory.setLastUpdatedAt(now.minus(90, ChronoUnit.DAYS));

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(timeBasedMemory));
            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(List.of(confidenceBasedMemory));

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerFullExpiration();

            // Then
            verify(memoryRepository, times(2)).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesExpired()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle errors in individual memories gracefully")
        void shouldHandleErrorsGracefully() {
            // Given: A memory and one that throws an exception
            Memory goodMemory = createMemory(3, new BigDecimal("0.80"));
            goodMemory.setExpiresAt(now.minus(5, ChronoUnit.DAYS));
            goodMemory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            Memory badMemory = createMemory(4, new BigDecimal("0.70"));
            badMemory.setExpiresAt(now.minus(3, ChronoUnit.DAYS));
            badMemory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));
            // Make the state transition throw an exception (memory already in terminal state)
            badMemory.setState(MemoryState.DELETED);

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(goodMemory, badMemory));
            when(memoryRepository.findLowConfidenceUnaccessed(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            MemoryExpirationService.ExpirationResult result = expirationService.triggerFullExpiration();

            // Then: Should process good memory and record error for bad memory
            verify(memoryRepository, times(1)).save(any(Memory.class));
            assertThat(result.memoriesProcessed()).isEqualTo(2);
            assertThat(result.memoriesExpired()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(1);
        }
    }

    // ─── Audit Logging Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Audit Logging")
    class AuditLoggingTests {

        @Test
        @DisplayName("Should create audit entry for expired memory")
        void shouldCreateAuditEntryForExpiredMemory() {
            // Given: A memory to expire
            Memory memory = createMemory(3, new BigDecimal("0.80"));
            memory.setExpiresAt(now.minus(5, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory));

            // When
            expirationService.triggerTimeBasedExpiration();

            // Then
            verify(auditService).serializeMemoryState(memory);
            verify(auditService).createAuditEntry(eq(memory), eq(EventType.UPDATE), eq(ActorType.SYSTEM), any());
        }

        @Test
        @DisplayName("Should serialize state before expiration for audit")
        void shouldSerializeStateBeforeExpiration() {
            // Given: A memory to expire
            Memory memory = createMemory(3, new BigDecimal("0.80"));
            memory.setExpiresAt(now.minus(5, ChronoUnit.DAYS));
            memory.setLastUpdatedAt(now.minus(10, ChronoUnit.DAYS));

            String expectedStateBefore = "{\"state\":\"ACTIVE\"}";
            when(auditService.serializeMemoryState(any(Memory.class))).thenReturn(expectedStateBefore);
            when(memoryRepository.findExpiredMemories(any(Instant.class), eq(MemoryState.ACTIVE)))
                    .thenReturn(List.of(memory));

            // When
            expirationService.triggerTimeBasedExpiration();

            // Then
            verify(auditService).createAuditEntry(eq(memory), eq(EventType.UPDATE), eq(ActorType.SYSTEM), eq(expectedStateBefore));
        }
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    /**
     * Creates a test memory with the specified importance and confidence scores.
     */
    private Memory createMemory(int importanceScore, BigDecimal confidenceScore) {
        Memory memory = new Memory(
                fatherId,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.FATHER,
                "Test memory content",
                importanceScore,
                confidenceScore,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
        memory.setCreatedAt(now.minus(100, ChronoUnit.DAYS));
        memory.setLastUpdatedAt(now.minus(100, ChronoUnit.DAYS));
        return memory;
    }
}
