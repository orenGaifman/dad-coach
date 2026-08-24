package com.dadcoach.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Memory entity state transition methods.
 *
 * <p>Validates: REQ-7 (Memory Lifecycle States)
 * Verifies that Memory entity methods enforce valid state transitions
 * and throw IllegalStateException for invalid transitions.
 */
@DisplayName("Memory Entity State Transition Tests")
class MemoryStateTransitionTest {

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final String CONTENT = "Test memory content";
    private static final int IMPORTANCE_SCORE = 5;
    private static final BigDecimal CONFIDENCE_SCORE = new BigDecimal("0.80");

    private Memory memory;

    @BeforeEach
    void setUp() {
        memory = new Memory(
                FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                CONTENT,
                IMPORTANCE_SCORE,
                CONFIDENCE_SCORE,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
    }

    // ─── confirm() Method Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("confirm() method")
    class ConfirmMethodTests {

        @Test
        @DisplayName("confirm() succeeds when memory is ACTIVE")
        void confirmSucceedsWhenActive() {
            // Given
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // When
            memory.confirm();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);
            assertThat(memory.getConfirmationCount()).isEqualTo(1);
            assertThat(memory.getLastConfirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("confirm() throws IllegalStateException when memory is CONFIRMED")
        void confirmThrowsWhenConfirmed() {
            // Given
            memory.confirm(); // First confirm
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);

            // When/Then
            assertThatThrownBy(() -> memory.confirm())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CONFIRMED to CONFIRMED");
        }

        @Test
        @DisplayName("confirm() throws IllegalStateException when memory is SUPERSEDED")
        void confirmThrowsWhenSuperseded() {
            // Given
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);

            // When/Then
            assertThatThrownBy(() -> memory.confirm())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to CONFIRMED");
        }

        @Test
        @DisplayName("confirm() throws IllegalStateException when memory is ARCHIVED")
        void confirmThrowsWhenArchived() {
            // Given
            memory.archive();
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);

            // When/Then
            assertThatThrownBy(() -> memory.confirm())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to CONFIRMED");
        }

        @Test
        @DisplayName("confirm() throws IllegalStateException when memory is EXPIRED")
        void confirmThrowsWhenExpired() {
            // Given
            memory.expire();
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);

            // When/Then
            assertThatThrownBy(() -> memory.confirm())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from EXPIRED to CONFIRMED");
        }

        @Test
        @DisplayName("confirm() throws IllegalStateException when memory is DELETED")
        void confirmThrowsWhenDeleted() {
            // Given
            memory.delete();
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);

            // When/Then
            assertThatThrownBy(() -> memory.confirm())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to CONFIRMED");
        }
    }

    // ─── markSuperseded() Method Tests ───────────────────────────────────

    @Nested
    @DisplayName("markSuperseded() method")
    class MarkSupersededMethodTests {

        private final UUID SUPERSEDING_MEMORY_ID = UUID.randomUUID();

        @Test
        @DisplayName("markSuperseded() succeeds when memory is ACTIVE")
        void markSupersededSucceedsWhenActive() {
            // Given
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // When
            memory.markSuperseded(SUPERSEDING_MEMORY_ID);

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);
            assertThat(memory.getSupersededBy()).isEqualTo(SUPERSEDING_MEMORY_ID);
        }

        @Test
        @DisplayName("markSuperseded() succeeds when memory is CONFIRMED")
        void markSupersededSucceedsWhenConfirmed() {
            // Given
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);

            // When
            memory.markSuperseded(SUPERSEDING_MEMORY_ID);

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);
            assertThat(memory.getSupersededBy()).isEqualTo(SUPERSEDING_MEMORY_ID);
        }

        @Test
        @DisplayName("markSuperseded() throws IllegalStateException when memory is SUPERSEDED")
        void markSupersededThrowsWhenSuperseded() {
            // Given
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);

            // When/Then
            assertThatThrownBy(() -> memory.markSuperseded(SUPERSEDING_MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to SUPERSEDED");
        }

        @Test
        @DisplayName("markSuperseded() throws IllegalStateException when memory is ARCHIVED")
        void markSupersededThrowsWhenArchived() {
            // Given
            memory.archive();
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);

            // When/Then
            assertThatThrownBy(() -> memory.markSuperseded(SUPERSEDING_MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to SUPERSEDED");
        }

        @Test
        @DisplayName("markSuperseded() throws IllegalStateException when memory is EXPIRED")
        void markSupersededThrowsWhenExpired() {
            // Given
            memory.expire();
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);

            // When/Then
            assertThatThrownBy(() -> memory.markSuperseded(SUPERSEDING_MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from EXPIRED to SUPERSEDED");
        }

        @Test
        @DisplayName("markSuperseded() throws IllegalStateException when memory is DELETED")
        void markSupersededThrowsWhenDeleted() {
            // Given
            memory.delete();
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);

            // When/Then
            assertThatThrownBy(() -> memory.markSuperseded(SUPERSEDING_MEMORY_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to SUPERSEDED");
        }
    }

    // ─── archive() Method Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("archive() method")
    class ArchiveMethodTests {

        @Test
        @DisplayName("archive() succeeds when memory is ACTIVE")
        void archiveSucceedsWhenActive() {
            // Given
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // When
            memory.archive();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archive() succeeds when memory is CONFIRMED")
        void archiveSucceedsWhenConfirmed() {
            // Given
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);

            // When
            memory.archive();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archive() succeeds when memory is SUPERSEDED")
        void archiveSucceedsWhenSuperseded() {
            // Given
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);

            // When
            memory.archive();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archive() succeeds when memory is EXPIRED")
        void archiveSucceedsWhenExpired() {
            // Given
            memory.expire();
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);

            // When
            memory.archive();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);
        }

        @Test
        @DisplayName("archive() throws IllegalStateException when memory is ARCHIVED")
        void archiveThrowsWhenArchived() {
            // Given
            memory.archive();
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);

            // When/Then
            assertThatThrownBy(() -> memory.archive())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to ARCHIVED");
        }

        @Test
        @DisplayName("archive() throws IllegalStateException when memory is DELETED")
        void archiveThrowsWhenDeleted() {
            // Given
            memory.delete();
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);

            // When/Then
            assertThatThrownBy(() -> memory.archive())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to ARCHIVED");
        }
    }

    // ─── expire() Method Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("expire() method")
    class ExpireMethodTests {

        @Test
        @DisplayName("expire() succeeds when memory is ACTIVE")
        void expireSucceedsWhenActive() {
            // Given
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // When
            memory.expire();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);
        }

        @Test
        @DisplayName("expire() throws IllegalStateException when memory is CONFIRMED")
        void expireThrowsWhenConfirmed() {
            // Given
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);

            // When/Then
            assertThatThrownBy(() -> memory.expire())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CONFIRMED to EXPIRED");
        }

        @Test
        @DisplayName("expire() throws IllegalStateException when memory is SUPERSEDED")
        void expireThrowsWhenSuperseded() {
            // Given
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);

            // When/Then
            assertThatThrownBy(() -> memory.expire())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to EXPIRED");
        }

        @Test
        @DisplayName("expire() throws IllegalStateException when memory is ARCHIVED")
        void expireThrowsWhenArchived() {
            // Given
            memory.archive();
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);

            // When/Then
            assertThatThrownBy(() -> memory.expire())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ARCHIVED to EXPIRED");
        }

        @Test
        @DisplayName("expire() throws IllegalStateException when memory is EXPIRED")
        void expireThrowsWhenExpired() {
            // Given
            memory.expire();
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);

            // When/Then
            assertThatThrownBy(() -> memory.expire())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from EXPIRED to EXPIRED");
        }

        @Test
        @DisplayName("expire() throws IllegalStateException when memory is DELETED")
        void expireThrowsWhenDeleted() {
            // Given
            memory.delete();
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);

            // When/Then
            assertThatThrownBy(() -> memory.expire())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to EXPIRED");
        }
    }

    // ─── delete() Method Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("delete() method")
    class DeleteMethodTests {

        @Test
        @DisplayName("delete() succeeds when memory is ACTIVE")
        void deleteSucceedsWhenActive() {
            // Given
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // When
            memory.delete();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("delete() succeeds when memory is CONFIRMED")
        void deleteSucceedsWhenConfirmed() {
            // Given
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);

            // When
            memory.delete();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("delete() succeeds when memory is SUPERSEDED")
        void deleteSucceedsWhenSuperseded() {
            // Given
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);

            // When
            memory.delete();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("delete() succeeds when memory is ARCHIVED")
        void deleteSucceedsWhenArchived() {
            // Given
            memory.archive();
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);

            // When
            memory.delete();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("delete() succeeds when memory is EXPIRED")
        void deleteSucceedsWhenExpired() {
            // Given
            memory.expire();
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);

            // When
            memory.delete();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);
        }

        @Test
        @DisplayName("delete() throws IllegalStateException when memory is DELETED")
        void deleteThrowsWhenDeleted() {
            // Given
            memory.delete();
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);

            // When/Then
            assertThatThrownBy(() -> memory.delete())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to DELETED");
        }
    }

    // ─── reactivate() Method Tests ───────────────────────────────────────

    @Nested
    @DisplayName("reactivate() method")
    class ReactivateMethodTests {

        @Test
        @DisplayName("reactivate() succeeds when memory is ARCHIVED")
        void reactivateSucceedsWhenArchived() {
            // Given
            memory.archive();
            assertThat(memory.getState()).isEqualTo(MemoryState.ARCHIVED);

            // When
            memory.reactivate();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("reactivate() succeeds when memory is EXPIRED")
        void reactivateSucceedsWhenExpired() {
            // Given
            memory.expire();
            assertThat(memory.getState()).isEqualTo(MemoryState.EXPIRED);

            // When
            memory.reactivate();

            // Then
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);
        }

        @Test
        @DisplayName("reactivate() throws IllegalStateException when memory is ACTIVE")
        void reactivateThrowsWhenActive() {
            // Given
            assertThat(memory.getState()).isEqualTo(MemoryState.ACTIVE);

            // When/Then
            assertThatThrownBy(() -> memory.reactivate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from ACTIVE to ACTIVE");
        }

        @Test
        @DisplayName("reactivate() throws IllegalStateException when memory is CONFIRMED")
        void reactivateThrowsWhenConfirmed() {
            // Given
            memory.confirm();
            assertThat(memory.getState()).isEqualTo(MemoryState.CONFIRMED);

            // When/Then
            assertThatThrownBy(() -> memory.reactivate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CONFIRMED to ACTIVE");
        }

        @Test
        @DisplayName("reactivate() throws IllegalStateException when memory is SUPERSEDED")
        void reactivateThrowsWhenSuperseded() {
            // Given
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getState()).isEqualTo(MemoryState.SUPERSEDED);

            // When/Then
            assertThatThrownBy(() -> memory.reactivate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUPERSEDED to ACTIVE");
        }

        @Test
        @DisplayName("reactivate() throws IllegalStateException when memory is DELETED")
        void reactivateThrowsWhenDeleted() {
            // Given
            memory.delete();
            assertThat(memory.getState()).isEqualTo(MemoryState.DELETED);

            // When/Then
            assertThatThrownBy(() -> memory.reactivate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from DELETED to ACTIVE");
        }
    }

    // ─── Timestamp Update Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Timestamp updates on state transitions")
    class TimestampUpdateTests {

        @Test
        @DisplayName("All state transitions update lastUpdatedAt")
        void stateTransitionsUpdateLastUpdatedAt() {
            // Given - initial state
            var initialUpdatedAt = memory.getLastUpdatedAt();

            // When/Then - each transition updates lastUpdatedAt
            memory.confirm();
            assertThat(memory.getLastUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);

            var afterConfirm = memory.getLastUpdatedAt();
            memory.markSuperseded(UUID.randomUUID());
            assertThat(memory.getLastUpdatedAt()).isAfterOrEqualTo(afterConfirm);
        }
    }
}
