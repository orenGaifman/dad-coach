package com.dadcoach.systemstate;

import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SystemStateCache.
 * Validates: Requirement 2.4 (Request-scoped state caching)
 */
@DisplayName("SystemStateCache")
class SystemStateCacheTest {

    private UUID fatherId;
    private SystemState testState;

    @BeforeEach
    void setUp() {
        fatherId = UUID.randomUUID();
        testState = createTestSystemState(fatherId);
    }

    @AfterEach
    void tearDown() {
        // Always clear the cache to prevent test pollution
        SystemStateCache.clear();
    }

    @Nested
    @DisplayName("set")
    class SetTest {

        @Test
        @DisplayName("stores state in cache")
        void storesStateInCache() {
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.isCached(fatherId)).isTrue();
            assertThat(SystemStateCache.get(fatherId)).isEqualTo(testState);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null fatherId")
        void throwsForNullFatherId() {
            assertThatThrownBy(() -> SystemStateCache.set(null, testState))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fatherId must not be null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null state")
        void throwsForNullState() {
            assertThatThrownBy(() -> SystemStateCache.set(fatherId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("state must not be null");
        }

        @Test
        @DisplayName("replaces existing state for same father")
        void replacesExistingStateForSameFather() {
            SystemState state1 = createTestSystemState(fatherId, WorkflowState.WELCOME);
            SystemState state2 = createTestSystemState(fatherId, WorkflowState.WAITING);

            SystemStateCache.set(fatherId, state1);
            assertThat(SystemStateCache.get(fatherId).workflowState()).isEqualTo(WorkflowState.WELCOME);

            SystemStateCache.set(fatherId, state2);
            assertThat(SystemStateCache.get(fatherId).workflowState()).isEqualTo(WorkflowState.WAITING);
        }

        @Test
        @DisplayName("replaces state when different father id is set (with warning)")
        void replacesStateForDifferentFather() {
            UUID fatherId2 = UUID.randomUUID();
            SystemState state2 = createTestSystemState(fatherId2);

            SystemStateCache.set(fatherId, testState);
            assertThat(SystemStateCache.get(fatherId)).isNotNull();

            // Set state for different father - this should replace (and log warning)
            SystemStateCache.set(fatherId2, state2);
            assertThat(SystemStateCache.get(fatherId)).isNull(); // old father no longer cached
            assertThat(SystemStateCache.get(fatherId2)).isNotNull(); // new father is cached
        }
    }

    @Nested
    @DisplayName("get")
    class GetTest {

        @Test
        @DisplayName("returns cached state for matching fatherId")
        void returnsCachedState() {
            SystemStateCache.set(fatherId, testState);

            SystemState retrieved = SystemStateCache.get(fatherId);

            assertThat(retrieved).isNotNull();
            assertThat(retrieved).isSameAs(testState);
        }

        @Test
        @DisplayName("returns null when nothing cached")
        void returnsNullWhenNotCached() {
            assertThat(SystemStateCache.get(fatherId)).isNull();
        }

        @Test
        @DisplayName("returns null for null fatherId")
        void returnsNullForNullFatherId() {
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.get(null)).isNull();
        }

        @Test
        @DisplayName("returns null when cached state is for different father")
        void returnsNullForDifferentFather() {
            UUID differentFatherId = UUID.randomUUID();
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.get(differentFatherId)).isNull();
        }
    }

    @Nested
    @DisplayName("current")
    class CurrentTest {

        @Test
        @DisplayName("returns cached state regardless of fatherId")
        void returnsCachedState() {
            SystemStateCache.set(fatherId, testState);

            SystemState retrieved = SystemStateCache.current();

            assertThat(retrieved).isNotNull();
            assertThat(retrieved).isSameAs(testState);
        }

        @Test
        @DisplayName("returns null when nothing cached")
        void returnsNullWhenNotCached() {
            assertThat(SystemStateCache.current()).isNull();
        }
    }

    @Nested
    @DisplayName("isCached")
    class IsCachedTest {

        @Test
        @DisplayName("returns true when state is cached")
        void returnsTrueWhenCached() {
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.isCached()).isTrue();
        }

        @Test
        @DisplayName("returns false when nothing cached")
        void returnsFalseWhenNotCached() {
            assertThat(SystemStateCache.isCached()).isFalse();
        }

        @Test
        @DisplayName("returns true for matching fatherId")
        void returnsTrueForMatchingFatherId() {
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.isCached(fatherId)).isTrue();
        }

        @Test
        @DisplayName("returns false for different fatherId")
        void returnsFalseForDifferentFatherId() {
            UUID differentFatherId = UUID.randomUUID();
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.isCached(differentFatherId)).isFalse();
        }

        @Test
        @DisplayName("returns false for null fatherId")
        void returnsFalseForNullFatherId() {
            SystemStateCache.set(fatherId, testState);

            assertThat(SystemStateCache.isCached(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTest {

        @Test
        @DisplayName("removes cached state")
        void removesCachedState() {
            SystemStateCache.set(fatherId, testState);
            assertThat(SystemStateCache.isCached()).isTrue();

            SystemStateCache.clear();

            assertThat(SystemStateCache.isCached()).isFalse();
            assertThat(SystemStateCache.get(fatherId)).isNull();
            assertThat(SystemStateCache.current()).isNull();
        }

        @Test
        @DisplayName("is idempotent - can be called multiple times safely")
        void isIdempotent() {
            SystemStateCache.set(fatherId, testState);

            SystemStateCache.clear();
            SystemStateCache.clear();
            SystemStateCache.clear();

            assertThat(SystemStateCache.isCached()).isFalse();
        }

        @Test
        @DisplayName("can be called when nothing is cached")
        void canBeCAlledWhenNotCached() {
            // Should not throw
            SystemStateCache.clear();

            assertThat(SystemStateCache.isCached()).isFalse();
        }
    }

    @Nested
    @DisplayName("Thread isolation")
    class ThreadIsolationTest {

        @Test
        @DisplayName("cache is isolated per thread")
        void cacheIsIsolatedPerThread() throws InterruptedException {
            UUID fatherId1 = UUID.randomUUID();
            UUID fatherId2 = UUID.randomUUID();
            SystemState state1 = createTestSystemState(fatherId1, WorkflowState.WELCOME);
            SystemState state2 = createTestSystemState(fatherId2, WorkflowState.WAITING);

            // Set in main thread
            SystemStateCache.set(fatherId1, state1);

            // Set in different thread
            Thread otherThread = new Thread(() -> {
                SystemStateCache.set(fatherId2, state2);
                assertThat(SystemStateCache.get(fatherId2)).isNotNull();
                assertThat(SystemStateCache.get(fatherId1)).isNull(); // Should not see main thread's cache
                SystemStateCache.clear();
            });
            otherThread.start();
            otherThread.join();

            // Main thread should still see its own cache
            assertThat(SystemStateCache.get(fatherId1)).isNotNull();
            assertThat(SystemStateCache.get(fatherId2)).isNull(); // Should not see other thread's cache
        }

        @Test
        @DisplayName("clearing one thread does not affect other threads")
        void clearingDoesNotAffectOtherThreads() throws InterruptedException {
            SystemStateCache.set(fatherId, testState);

            Thread otherThread = new Thread(() -> {
                SystemStateCache.clear(); // Clear in other thread
            });
            otherThread.start();
            otherThread.join();

            // Main thread's cache should still exist
            assertThat(SystemStateCache.get(fatherId)).isNotNull();
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private SystemState createTestSystemState(UUID fatherId) {
        return createTestSystemState(fatherId, WorkflowState.SCHEDULE_QUALITY_TIME);
    }

    private SystemState createTestSystemState(UUID fatherId, WorkflowState state) {
        SystemState.FatherProfile profile = new SystemState.FatherProfile(
                fatherId.getMostSignificantBits(),
                "Test Father",
                "+1234567890",
                List.of(new SystemState.ChildInfo(1L, "Test Child", null, 5, "Male", List.of())),
                "en",
                "America/New_York",
                null,
                true
        );

        SystemState.DashboardMetrics metrics = new SystemState.DashboardMetrics(
                Belt.WHITE,
                0,
                0,
                0,
                List.of(),
                0,
                3
        );

        return new SystemState(
                profile,
                state,
                List.of(),
                List.of(),
                metrics,
                List.of()
        );
    }
}
