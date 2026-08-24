package com.dadcoach.memory.audit;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for transactional behavior of memory audit operations.
 *
 * <p>These tests verify the SPEC-004 Design correctness property:
 * "Audit log is append-only and written synchronously with memory operations
 * (rollback on audit failure)"
 *
 * <p>The tests validate:
 * <ul>
 *   <li>Strict audit methods require an existing transaction (MANDATORY propagation)</li>
 *   <li>Audit failures in strict mode throw MemoryAuditException</li>
 *   <li>MemoryAuditException is a RuntimeException that triggers rollback</li>
 *   <li>The exception contains diagnostic information</li>
 * </ul>
 *
 * <p><strong>Validates: SPEC-004 Task 10 - Written synchronously with memory operations
 * (rollback on audit failure)</strong>
 *
 * @see MemoryAuditService
 * @see MemoryAuditException
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Memory Audit Transactional Integration Tests")
class MemoryAuditTransactionalIntegrationTest {

    @Mock
    private MemoryAuditRepository auditRepository;

    private ObjectMapper objectMapper;
    private MemoryAuditService auditService;

    private static final UUID TEST_FATHER_ID = UUID.randomUUID();
    private static final UUID TEST_MEMORY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditService = new MemoryAuditService(auditRepository, objectMapper);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Strict Mode Throws Exception on Audit Failure
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strict Mode Exception Behavior Tests")
    class StrictModeExceptionBehaviorTests {

        /**
         * Test: Strict mode throws MemoryAuditException when repository save fails.
         *
         * <p>This verifies that audit failures in strict mode propagate as exceptions,
         * which will cause the outer transaction to roll back.
         *
         * <p><strong>Validates: SPEC-004 Design - "rollback on audit failure"</strong>
         */
        @Test
        @DisplayName("Should throw MemoryAuditException when repository save fails in strict mode")
        void shouldThrowMemoryAuditExceptionWhenRepositorySaveFails() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            // Simulate database failure
            when(auditRepository.save(any(MemoryAuditLog.class)))
                    .thenThrow(new RuntimeException("Database connection lost"));

            // Act & Assert
            assertThatThrownBy(() -> 
                    auditService.createAuditEntryStrict(memory, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(MemoryAuditException.class)
                    .hasMessageContaining("Failed to create audit entry")
                    .hasMessageContaining(TEST_MEMORY_ID.toString())
                    .hasCauseInstanceOf(RuntimeException.class);

            // Verify save was attempted
            verify(auditRepository).save(any(MemoryAuditLog.class));
        }

        /**
         * Test: MemoryAuditException is a RuntimeException (triggers @Transactional rollback).
         *
         * <p>Spring's @Transactional annotation by default only rolls back on
         * RuntimeException and its subclasses. This test verifies MemoryAuditException
         * is correctly designed to trigger rollback.
         */
        @Test
        @DisplayName("MemoryAuditException should be RuntimeException for automatic rollback")
        void memoryAuditExceptionShouldBeRuntimeException() {
            // Arrange
            MemoryAuditException exception = new MemoryAuditException("Test message");

            // Assert
            assertThat(exception)
                    .isInstanceOf(RuntimeException.class)
                    .as("MemoryAuditException must be RuntimeException for @Transactional rollback");
        }

        /**
         * Test: Strict mode throws exception when audit repository is null.
         *
         * <p>This ensures that strict mode enforces audit logging requirement.
         */
        @Test
        @DisplayName("Should throw MemoryAuditException when repository is null in strict mode")
        void shouldThrowExceptionWhenRepositoryIsNullInStrictMode() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            // Act & Assert
            assertThatThrownBy(() ->
                    serviceWithoutRepo.createAuditEntryStrict(memory, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(MemoryAuditException.class)
                    .hasMessageContaining("AuditRepository not available");
        }

        /**
         * Test: Strict mode throws IllegalArgumentException for null memory.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for null memory in strict mode")
        void shouldThrowIllegalArgumentExceptionForNullMemory() {
            // Act & Assert
            assertThatThrownBy(() ->
                    auditService.createAuditEntryStrict(null, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null memory");
        }

        /**
         * Test: Strict mode throws IllegalArgumentException for memory without ID.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for memory without ID in strict mode")
        void shouldThrowIllegalArgumentExceptionForMemoryWithoutId() {
            // Arrange
            Memory memory = createTestMemory();
            // ID not set

            // Act & Assert
            assertThatThrownBy(() ->
                    auditService.createAuditEntryStrict(memory, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("without ID");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Strict Mode Success Path
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strict Mode Success Path Tests")
    class StrictModeSuccessPathTests {

        /**
         * Test: Strict mode returns audit entry on successful save.
         */
        @Test
        @DisplayName("Should return audit entry on successful save in strict mode")
        void shouldReturnAuditEntryOnSuccessfulSave() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            MemoryAuditLog savedEntry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");
            UUID auditId = UUID.randomUUID();
            savedEntry.setId(auditId);

            when(auditRepository.save(any(MemoryAuditLog.class))).thenReturn(savedEntry);

            // Act
            MemoryAuditLog result = auditService.createAuditEntryStrict(
                    memory, EventType.CREATE, ActorType.AI, null);

            // Assert
            assertThat(result)
                    .isNotNull()
                    .extracting(MemoryAuditLog::getId, MemoryAuditLog::getEventType)
                    .containsExactly(auditId, EventType.CREATE);
        }

        /**
         * Test: All strict convenience methods work correctly.
         */
        @Test
        @DisplayName("All strict convenience methods should create correct audit entries")
        void allStrictConvenienceMethodsShouldWork() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);
            String stateBefore = "{\"state\":\"ACTIVE\"}";

            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act & Assert - test each strict method
            assertThatCode(() -> auditService.createAuditEntryForCreateStrict(memory, ActorType.AI))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForUpdateStrict(memory, ActorType.USER, stateBefore))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForConfirmStrict(memory, stateBefore))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForArchiveStrict(memory, ActorType.SYSTEM, stateBefore))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForSupersedeStrict(memory, ActorType.USER, stateBefore))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForExpireStrict(memory, stateBefore))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForDeleteStrict(memory, ActorType.USER, stateBefore))
                    .doesNotThrowAnyException();

            assertThatCode(() -> auditService.createAuditEntryForReactivateStrict(memory, ActorType.USER, stateBefore))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Backward Compatibility (Graceful Degradation Mode)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backward Compatibility Tests (Graceful Degradation)")
    class BackwardCompatibilityTests {

        /**
         * Test: Deprecated methods still work with graceful degradation.
         */
        @Test
        @DisplayName("Deprecated methods should return Optional.empty on failure (graceful)")
        void deprecatedMethodsShouldReturnEmptyOnFailure() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            when(auditRepository.save(any(MemoryAuditLog.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // Act
            @SuppressWarnings("deprecation")
            Optional<MemoryAuditLog> result = auditService.createAuditEntry(
                    memory, EventType.CREATE, ActorType.AI, null);

            // Assert - graceful degradation returns empty, no exception
            assertThat(result).isEmpty();
        }

        /**
         * Test: Deprecated methods return Optional.empty when repository is null.
         */
        @Test
        @DisplayName("Deprecated methods should return Optional.empty when repository is null")
        void deprecatedMethodsShouldReturnEmptyWhenRepositoryNull() {
            // Arrange
            MemoryAuditService serviceWithoutRepo = new MemoryAuditService(null, objectMapper);
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            // Act
            @SuppressWarnings("deprecation")
            Optional<MemoryAuditLog> result = serviceWithoutRepo.createAuditEntry(
                    memory, EventType.CREATE, ActorType.AI, null);

            // Assert
            assertThat(result).isEmpty();
        }

        /**
         * Test: Deprecated convenience methods work correctly on success.
         */
        @Test
        @DisplayName("Deprecated convenience methods should return Optional with entry on success")
        void deprecatedConvenienceMethodsShouldReturnEntryOnSuccess() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            when(auditRepository.save(any(MemoryAuditLog.class))).thenAnswer(inv -> {
                MemoryAuditLog entry = inv.getArgument(0);
                entry.setId(UUID.randomUUID());
                return entry;
            });

            // Act
            @SuppressWarnings("deprecation")
            Optional<MemoryAuditLog> result = auditService.createAuditEntryForCreate(memory, ActorType.AI);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getEventType()).isEqualTo(EventType.CREATE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Exception Details for Debugging
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exception Details Tests")
    class ExceptionDetailsTests {

        /**
         * Test: MemoryAuditException includes memory ID for debugging.
         */
        @Test
        @DisplayName("MemoryAuditException should include memory ID in message")
        void exceptionShouldIncludeMemoryId() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            when(auditRepository.save(any(MemoryAuditLog.class)))
                    .thenThrow(new RuntimeException("Constraint violation"));

            // Act & Assert
            assertThatThrownBy(() ->
                    auditService.createAuditEntryStrict(memory, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(MemoryAuditException.class)
                    .hasMessageContaining(TEST_MEMORY_ID.toString());
        }

        /**
         * Test: MemoryAuditException preserves original cause.
         */
        @Test
        @DisplayName("MemoryAuditException should preserve original cause")
        void exceptionShouldPreserveOriginalCause() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            RuntimeException originalCause = new RuntimeException("Original database error");
            when(auditRepository.save(any(MemoryAuditLog.class))).thenThrow(originalCause);

            // Act & Assert
            assertThatThrownBy(() ->
                    auditService.createAuditEntryStrict(memory, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(MemoryAuditException.class)
                    .hasCause(originalCause);
        }

        /**
         * Test: MemoryAuditException includes original error message.
         */
        @Test
        @DisplayName("MemoryAuditException should include original error message")
        void exceptionShouldIncludeOriginalErrorMessage() {
            // Arrange
            Memory memory = createTestMemory();
            memory.setId(TEST_MEMORY_ID);

            String originalMessage = "Foreign key constraint violated";
            when(auditRepository.save(any(MemoryAuditLog.class)))
                    .thenThrow(new RuntimeException(originalMessage));

            // Act & Assert
            assertThatThrownBy(() ->
                    auditService.createAuditEntryStrict(memory, EventType.CREATE, ActorType.AI, null))
                    .isInstanceOf(MemoryAuditException.class)
                    .hasMessageContaining(originalMessage);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Transactional Propagation Behavior
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transactional Propagation Tests")
    class TransactionalPropagationTests {

        /**
         * Test: Verify strict methods use MANDATORY propagation annotation.
         *
         * <p>This test documents the expected behavior: strict methods require
         * an existing transaction. In a real Spring context, calling these methods
         * without a transaction would throw IllegalTransactionStateException.
         *
         * <p>Note: This unit test can't fully test the propagation behavior since
         * it requires a Spring context. The annotation-based behavior is tested
         * through the @Transactional annotation on the methods.
         */
        @Test
        @DisplayName("Strict method annotations should specify MANDATORY propagation")
        void strictMethodsShouldUseMandatoryPropagation() throws NoSuchMethodException {
            // Verify createAuditEntryStrict has @Transactional(propagation = MANDATORY)
            Transactional annotation = MemoryAuditService.class
                    .getMethod("createAuditEntryStrict", Memory.class, EventType.class, 
                            ActorType.class, String.class)
                    .getAnnotation(Transactional.class);

            assertThat(annotation)
                    .as("createAuditEntryStrict should have @Transactional annotation")
                    .isNotNull();
            assertThat(annotation.propagation())
                    .as("Strict methods should use MANDATORY propagation")
                    .isEqualTo(Propagation.MANDATORY);
        }

        /**
         * Test: Verify deprecated methods use REQUIRED propagation annotation.
         */
        @Test
        @DisplayName("Deprecated method annotations should specify REQUIRED propagation")
        void deprecatedMethodsShouldUseRequiredPropagation() throws NoSuchMethodException {
            // Verify createAuditEntry has @Transactional(propagation = REQUIRED)
            Transactional annotation = MemoryAuditService.class
                    .getMethod("createAuditEntry", Memory.class, EventType.class, 
                            ActorType.class, String.class)
                    .getAnnotation(Transactional.class);

            assertThat(annotation)
                    .as("createAuditEntry should have @Transactional annotation")
                    .isNotNull();
            assertThat(annotation.propagation())
                    .as("Deprecated methods should use REQUIRED propagation for backward compatibility")
                    .isEqualTo(Propagation.REQUIRED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private Memory createTestMemory() {
        return new Memory(
                TEST_FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                "Test memory content",
                5,
                new BigDecimal("0.70"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
    }
}
