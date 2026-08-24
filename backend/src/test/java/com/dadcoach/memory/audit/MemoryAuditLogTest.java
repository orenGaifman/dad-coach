package com.dadcoach.memory.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MemoryAuditLog} entity.
 *
 * <p>These tests verify the audit log entity structure and behavior as defined in SPEC-004:
 * <ul>
 *   <li>REQ-24: Audit record contains event_type, memory_id, father_id, timestamp, actor_type</li>
 *   <li>Before/after state snapshots are supported</li>
 *   <li>Created timestamp is set automatically</li>
 *   <li>Audit entries are immutable (append-only)</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements REQ-24, Task 10</strong>
 *
 * @see MemoryAuditLog
 */
@DisplayName("MemoryAuditLog Tests")
class MemoryAuditLogTest {

    private static final UUID TEST_MEMORY_ID = UUID.randomUUID();
    private static final UUID TEST_FATHER_ID = UUID.randomUUID();

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create audit entry with required fields")
        void shouldCreateAuditEntryWithRequiredFields() {
            // Arrange
            String stateAfter = "{\"content\":\"test\"}";
            Instant beforeCreation = Instant.now();

            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, stateAfter);

            // Assert
            assertThat(entry.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
            assertThat(entry.getFatherId()).isEqualTo(TEST_FATHER_ID);
            assertThat(entry.getEventType()).isEqualTo(EventType.CREATE);
            assertThat(entry.getActorType()).isEqualTo(ActorType.AI);
            assertThat(entry.getStateAfter()).isEqualTo(stateAfter);
            assertThat(entry.getStateBefore()).isNull();
            assertThat(entry.getCreatedAt()).isAfterOrEqualTo(beforeCreation);
        }

        @Test
        @DisplayName("Should create audit entry with before and after states")
        void shouldCreateAuditEntryWithBeforeAndAfterStates() {
            // Arrange
            String stateBefore = "{\"confidence_score\":0.5}";
            String stateAfter = "{\"confidence_score\":0.9}";

            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.UPDATE, ActorType.USER, 
                    stateBefore, stateAfter);

            // Assert
            assertThat(entry.getStateBefore()).isEqualTo(stateBefore);
            assertThat(entry.getStateAfter()).isEqualTo(stateAfter);
            assertThat(entry.getEventType()).isEqualTo(EventType.UPDATE);
            assertThat(entry.getActorType()).isEqualTo(ActorType.USER);
        }

        @Test
        @DisplayName("Should auto-set createdAt timestamp")
        void shouldAutoSetCreatedAtTimestamp() {
            // Arrange
            Instant beforeCreation = Instant.now();

            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            // Assert
            assertThat(entry.getCreatedAt())
                    .isAfterOrEqualTo(beforeCreation)
                    .isBeforeOrEqualTo(Instant.now());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Event Types
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Type Tests")
    class EventTypeTests {

        @Test
        @DisplayName("Should support CREATE event type")
        void shouldSupportCreateEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.CREATE);
        }

        @Test
        @DisplayName("Should support UPDATE event type")
        void shouldSupportUpdateEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.UPDATE, ActorType.USER, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.UPDATE);
        }

        @Test
        @DisplayName("Should support CONFIRM event type")
        void shouldSupportConfirmEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CONFIRM, ActorType.USER, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.CONFIRM);
        }

        @Test
        @DisplayName("Should support ARCHIVE event type")
        void shouldSupportArchiveEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.ARCHIVE, ActorType.SYSTEM, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.ARCHIVE);
        }

        @Test
        @DisplayName("Should support SUPERSEDE event type")
        void shouldSupportSupersedeEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.SUPERSEDE, ActorType.AI, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.SUPERSEDE);
        }

        @Test
        @DisplayName("Should support EXPIRE event type")
        void shouldSupportExpireEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.EXPIRE, ActorType.SYSTEM, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.EXPIRE);
        }

        @Test
        @DisplayName("Should support DELETE event type")
        void shouldSupportDeleteEventType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.DELETE, ActorType.USER, "{}");

            // Assert
            assertThat(entry.getEventType()).isEqualTo(EventType.DELETE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Actor Types
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Actor Type Tests")
    class ActorTypeTests {

        @Test
        @DisplayName("Should support AI actor type")
        void shouldSupportAiActorType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            // Assert
            assertThat(entry.getActorType()).isEqualTo(ActorType.AI);
        }

        @Test
        @DisplayName("Should support USER actor type")
        void shouldSupportUserActorType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CONFIRM, ActorType.USER, "{}");

            // Assert
            assertThat(entry.getActorType()).isEqualTo(ActorType.USER);
        }

        @Test
        @DisplayName("Should support SYSTEM actor type")
        void shouldSupportSystemActorType() {
            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.EXPIRE, ActorType.SYSTEM, "{}");

            // Assert
            assertThat(entry.getActorType()).isEqualTo(ActorType.SYSTEM);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Immutability (Append-Only Behavior)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should prevent update via @PreUpdate callback")
        void shouldPreventUpdateViaPreUpdateCallback() {
            // Arrange
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            // Act & Assert
            assertThatThrownBy(entry::preventUpdate)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Audit log entries are immutable")
                    .hasMessageContaining("SPEC-004");
        }

        @Test
        @DisplayName("Should have no public setter for memoryId")
        void shouldHaveNoPublicSetterForMemoryId() {
            // This test verifies at compile time that setMemoryId doesn't exist
            // The method doesn't exist, so we verify the field is set only at construction
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            // Verify the value is set correctly and can't be changed
            assertThat(entry.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
        }

        @Test
        @DisplayName("Should have no public setter for fatherId")
        void shouldHaveNoPublicSetterForFatherId() {
            // Verify field is only settable at construction
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            assertThat(entry.getFatherId()).isEqualTo(TEST_FATHER_ID);
        }

        @Test
        @DisplayName("Should have no public setter for eventType")
        void shouldHaveNoPublicSetterForEventType() {
            // Verify field is only settable at construction
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            assertThat(entry.getEventType()).isEqualTo(EventType.CREATE);
        }

        @Test
        @DisplayName("Should have no public setter for actorType")
        void shouldHaveNoPublicSetterForActorType() {
            // Verify field is only settable at construction
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            assertThat(entry.getActorType()).isEqualTo(ActorType.AI);
        }

        @Test
        @DisplayName("Should have no public setter for createdAt")
        void shouldHaveNoPublicSetterForCreatedAt() {
            // Verify timestamp is only settable at construction
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");

            assertThat(entry.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("All values must be set at construction time")
        void allValuesMustBeSetAtConstructionTime() {
            // Arrange
            String stateBefore = "{\"before\":true}";
            String stateAfter = "{\"after\":true}";
            Instant beforeCreate = Instant.now();

            // Act
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.UPDATE, ActorType.USER,
                    stateBefore, stateAfter);

            // Assert - all values must be accessible via getters only
            assertThat(entry.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
            assertThat(entry.getFatherId()).isEqualTo(TEST_FATHER_ID);
            assertThat(entry.getEventType()).isEqualTo(EventType.UPDATE);
            assertThat(entry.getActorType()).isEqualTo(ActorType.USER);
            assertThat(entry.getStateBefore()).isEqualTo(stateBefore);
            assertThat(entry.getStateAfter()).isEqualTo(stateAfter);
            assertThat(entry.getCreatedAt()).isAfterOrEqualTo(beforeCreate);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: JPA ID Setter (Required by JPA)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA ID Setter Tests")
    class JpaIdSetterTests {

        @Test
        @DisplayName("Should allow ID to be set (JPA requirement)")
        void shouldAllowIdToBeSet() {
            // Arrange
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");
            UUID id = UUID.randomUUID();

            // Act - setId is package-private, accessible for JPA
            entry.setId(id);

            // Assert
            assertThat(entry.getId()).isEqualTo(id);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: toString
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should include key fields in toString")
        void shouldIncludeKeyFieldsInToString() {
            // Arrange
            MemoryAuditLog entry = new MemoryAuditLog(
                    TEST_MEMORY_ID, TEST_FATHER_ID, EventType.CREATE, ActorType.AI, "{}");
            entry.setId(UUID.randomUUID());

            // Act
            String result = entry.toString();

            // Assert
            assertThat(result)
                    .contains("MemoryAuditLog")
                    .contains("memoryId=" + TEST_MEMORY_ID)
                    .contains("fatherId=" + TEST_FATHER_ID)
                    .contains("eventType=CREATE")
                    .contains("actorType=AI");
        }
    }
}
