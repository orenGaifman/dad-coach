package com.dadcoach.workspace.event;

import com.dadcoach.workspace.growth.signal.GrowthSignalProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DomainEventListener}.
 *
 * <p>Verifies that external domain events are correctly delegated to the
 * GrowthSignalProcessor and that processing errors are caught without propagating.</p>
 */
@ExtendWith(MockitoExtension.class)
class DomainEventListenerTest {

    @Mock
    private GrowthSignalProcessor growthSignalProcessor;

    private DomainEventListener domainEventListener;

    @BeforeEach
    void setUp() {
        domainEventListener = new DomainEventListener(growthSignalProcessor);
    }

    @Nested
    @DisplayName("onMissionCompleted")
    class OnMissionCompletedTests {

        @Test
        @DisplayName("should delegate to GrowthSignalProcessor")
        void shouldDelegateToProcessor() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionCompletedEvent event = new MissionCompletedEvent(
                    fatherId, missionId, null, Instant.now());

            domainEventListener.onMissionCompleted(event);

            verify(growthSignalProcessor).onMissionCompleted(event);
        }

        @Test
        @DisplayName("should not propagate processor exceptions")
        void shouldNotPropagateExceptions() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionCompletedEvent event = new MissionCompletedEvent(
                    fatherId, missionId, null, Instant.now());

            doThrow(new RuntimeException("Processing failed"))
                    .when(growthSignalProcessor).onMissionCompleted(event);

            // Should not throw
            domainEventListener.onMissionCompleted(event);

            verify(growthSignalProcessor).onMissionCompleted(event);
        }
    }

    @Nested
    @DisplayName("onMissionReflected")
    class OnMissionReflectedTests {

        @Test
        @DisplayName("should delegate to GrowthSignalProcessor")
        void shouldDelegateToProcessor() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionReflectedEvent event = new MissionReflectedEvent(
                    fatherId, missionId, null, Instant.now());

            domainEventListener.onMissionReflected(event);

            verify(growthSignalProcessor).onMissionReflected(event);
        }

        @Test
        @DisplayName("should not propagate processor exceptions")
        void shouldNotPropagateExceptions() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionReflectedEvent event = new MissionReflectedEvent(
                    fatherId, missionId, null, Instant.now());

            doThrow(new RuntimeException("Processing failed"))
                    .when(growthSignalProcessor).onMissionReflected(event);

            domainEventListener.onMissionReflected(event);

            verify(growthSignalProcessor).onMissionReflected(event);
        }
    }

    @Nested
    @DisplayName("onGoalProgress")
    class OnGoalProgressTests {

        @Test
        @DisplayName("should delegate to GrowthSignalProcessor")
        void shouldDelegateToProcessor() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalProgressEvent event = new GoalProgressEvent(
                    fatherId, goalId, 20, 35, Instant.now());

            domainEventListener.onGoalProgress(event);

            verify(growthSignalProcessor).onGoalProgress(event);
        }

        @Test
        @DisplayName("should not propagate processor exceptions")
        void shouldNotPropagateExceptions() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalProgressEvent event = new GoalProgressEvent(
                    fatherId, goalId, 20, 50, Instant.now());

            doThrow(new RuntimeException("Processing failed"))
                    .when(growthSignalProcessor).onGoalProgress(event);

            domainEventListener.onGoalProgress(event);

            verify(growthSignalProcessor).onGoalProgress(event);
        }
    }

    @Nested
    @DisplayName("onGoalCompleted")
    class OnGoalCompletedTests {

        @Test
        @DisplayName("should delegate to GrowthSignalProcessor")
        void shouldDelegateToProcessor() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalCompletedEvent event = new GoalCompletedEvent(
                    fatherId, goalId, Instant.now());

            domainEventListener.onGoalCompleted(event);

            verify(growthSignalProcessor).onGoalCompleted(event);
        }

        @Test
        @DisplayName("should not propagate processor exceptions")
        void shouldNotPropagateExceptions() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalCompletedEvent event = new GoalCompletedEvent(
                    fatherId, goalId, Instant.now());

            doThrow(new RuntimeException("Processing failed"))
                    .when(growthSignalProcessor).onGoalCompleted(event);

            domainEventListener.onGoalCompleted(event);

            verify(growthSignalProcessor).onGoalCompleted(event);
        }
    }

    @Nested
    @DisplayName("onConversationCompleted")
    class OnConversationCompletedTests {

        @Test
        @DisplayName("should delegate to GrowthSignalProcessor")
        void shouldDelegateToProcessor() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "DAILY_COACHING", 10, 0.85, Instant.now());

            domainEventListener.onConversationCompleted(event);

            verify(growthSignalProcessor).onConversationCompleted(event);
        }

        @Test
        @DisplayName("should not propagate processor exceptions")
        void shouldNotPropagateExceptions() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "DAILY_COACHING", 10, 0.85, Instant.now());

            doThrow(new RuntimeException("Processing failed"))
                    .when(growthSignalProcessor).onConversationCompleted(event);

            domainEventListener.onConversationCompleted(event);

            verify(growthSignalProcessor).onConversationCompleted(event);
        }
    }
}
