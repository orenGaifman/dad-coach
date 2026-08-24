package com.dadcoach.memory.sensitive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SafetyEventService}.
 *
 * <p>Validates: SPEC-004 Task 12 - SafetyEventService for recording events
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyEventService Tests")
class SafetyEventServiceTest {

    @Mock
    private SafetyEventRepository safetyEventRepository;

    @Captor
    private ArgumentCaptor<SafetyEventRecord> eventCaptor;

    private SafetyEventService safetyEventService;

    @BeforeEach
    void setUp() {
        safetyEventService = new SafetyEventService(safetyEventRepository);
    }

    // ─── Record Operations Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Record Operations")
    class RecordOperationsTests {

        @Test
        @DisplayName("should record basic safety event with summary")
        void shouldRecordBasicSafetyEvent() {
            UUID fatherId = UUID.randomUUID();
            SafetyEventType eventType = SafetyEventType.SAFETY_CONCERN_DETECTED;
            SafetyEventSeverity severity = SafetyEventSeverity.MEDIUM;
            String summary = "Test safety concern";

            when(safetyEventRepository.save(any(SafetyEventRecord.class)))
                    .thenAnswer(invocation -> {
                        SafetyEventRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID());
                        return record;
                    });

            SafetyEventRecord result = safetyEventService.recordEvent(
                    fatherId, eventType, severity, summary);

            verify(safetyEventRepository).save(eventCaptor.capture());
            SafetyEventRecord captured = eventCaptor.getValue();

            assertThat(captured.getFatherId()).isEqualTo(fatherId);
            assertThat(captured.getEventType()).isEqualTo(eventType);
            assertThat(captured.getSeverity()).isEqualTo(severity);
            assertThat(captured.getSummary()).isEqualTo(summary);
            assertThat(captured.getDescription()).isNull(); // No description in basic call
            assertThat(captured.getRequiresReview()).isTrue();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should record event from conversation with summary")
        void shouldRecordEventFromConversation() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            SafetyEventType eventType = SafetyEventType.CRISIS_DETECTED;
            SafetyEventSeverity severity = SafetyEventSeverity.CRITICAL;
            String summary = "Crisis detected in conversation";

            when(safetyEventRepository.save(any(SafetyEventRecord.class)))
                    .thenAnswer(invocation -> {
                        SafetyEventRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID());
                        return record;
                    });

            SafetyEventRecord result = safetyEventService.recordEventFromConversation(
                    fatherId, eventType, severity, summary, conversationId);

            verify(safetyEventRepository).save(eventCaptor.capture());
            SafetyEventRecord captured = eventCaptor.getValue();

            assertThat(captured.getFatherId()).isEqualTo(fatherId);
            assertThat(captured.getConversationId()).isEqualTo(conversationId);
            assertThat(captured.getEventType()).isEqualTo(eventType);
            assertThat(captured.getSeverity()).isEqualTo(severity);
            assertThat(captured.getSummary()).isEqualTo(summary);
        }

        @Test
        @DisplayName("should record event with full context using summary")
        void shouldRecordEventWithFullContext() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            UUID memoryId = UUID.randomUUID();
            Map<String, Object> metadata = Map.of("trigger", "keywords", "confidence", 0.9);
            String summary = "Child safety concern detected";

            when(safetyEventRepository.save(any(SafetyEventRecord.class)))
                    .thenAnswer(invocation -> {
                        SafetyEventRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID());
                        return record;
                    });

            SafetyEventRecord result = safetyEventService.recordEventWithContext(
                    fatherId,
                    SafetyEventType.CHILD_SAFETY_CONCERN,
                    SafetyEventSeverity.HIGH,
                    summary,
                    conversationId,
                    memoryId,
                    metadata
            );

            verify(safetyEventRepository).save(eventCaptor.capture());
            SafetyEventRecord captured = eventCaptor.getValue();

            assertThat(captured.getFatherId()).isEqualTo(fatherId);
            assertThat(captured.getConversationId()).isEqualTo(conversationId);
            assertThat(captured.getMemoryId()).isEqualTo(memoryId);
            assertThat(captured.getMetadata()).isEqualTo(metadata);
            assertThat(captured.getSummary()).isEqualTo(summary);
        }
    }

    // ─── Review Workflow Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Review Workflow")
    class ReviewWorkflowTests {

        @Test
        @DisplayName("should mark event as reviewed")
        void shouldMarkEventAsReviewed() {
            UUID eventId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            String notes = "Reviewed and addressed";

            SafetyEventRecord existingEvent = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Test event"
            );
            existingEvent.setId(eventId);

            when(safetyEventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
            when(safetyEventRepository.save(any(SafetyEventRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<SafetyEventRecord> result = safetyEventService.markAsReviewed(eventId, reviewerId, notes);

            assertThat(result).isPresent();
            assertThat(result.get().getRequiresReview()).isFalse();
            assertThat(result.get().getReviewedBy()).isEqualTo(reviewerId);
            assertThat(result.get().getReviewNotes()).isEqualTo(notes);
        }

        @Test
        @DisplayName("should return empty when marking non-existent event as reviewed")
        void shouldReturnEmptyWhenMarkingNonExistentEventAsReviewed() {
            UUID eventId = UUID.randomUUID();
            when(safetyEventRepository.findById(eventId)).thenReturn(Optional.empty());

            Optional<SafetyEventRecord> result = safetyEventService.markAsReviewed(
                    eventId, UUID.randomUUID(), "notes");

            assertThat(result).isEmpty();
            verify(safetyEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("should flag event for re-review")
        void shouldFlagEventForReReview() {
            UUID eventId = UUID.randomUUID();

            SafetyEventRecord existingEvent = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.HIGH,
                    "Test event"
            );
            existingEvent.setId(eventId);
            existingEvent.markReviewed(UUID.randomUUID(), "Initial review");

            when(safetyEventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
            when(safetyEventRepository.save(any(SafetyEventRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<SafetyEventRecord> result = safetyEventService.flagForReview(eventId);

            assertThat(result).isPresent();
            assertThat(result.get().getRequiresReview()).isTrue();
        }
    }

    // ─── Query Operations Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Query Operations")
    class QueryOperationsTests {

        @Test
        @DisplayName("should get event by ID")
        void shouldGetEventById() {
            UUID eventId = UUID.randomUUID();
            SafetyEventRecord event = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Test event"
            );
            event.setId(eventId);

            when(safetyEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            Optional<SafetyEventRecord> result = safetyEventService.getById(eventId);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(eventId);
        }

        @Test
        @DisplayName("should get events by father ID")
        void shouldGetEventsByFatherId() {
            UUID fatherId = UUID.randomUUID();
            List<SafetyEventRecord> events = List.of(
                    new SafetyEventRecord(fatherId, SafetyEventType.SAFETY_CONCERN_DETECTED,
                            SafetyEventSeverity.LOW, "Event 1"),
                    new SafetyEventRecord(fatherId, SafetyEventType.ESCALATION_TRIGGERED,
                            SafetyEventSeverity.HIGH, "Event 2")
            );

            when(safetyEventRepository.findByFatherIdOrderByCreatedAtDesc(fatherId))
                    .thenReturn(events);

            List<SafetyEventRecord> result = safetyEventService.getByFatherId(fatherId);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(e -> e.getFatherId().equals(fatherId));
        }

        @Test
        @DisplayName("should get all events requiring review")
        void shouldGetAllEventsRequiringReview() {
            List<SafetyEventRecord> events = List.of(
                    new SafetyEventRecord(UUID.randomUUID(), SafetyEventType.CRISIS_DETECTED,
                            SafetyEventSeverity.CRITICAL, "Critical event"),
                    new SafetyEventRecord(UUID.randomUUID(), SafetyEventType.SAFETY_CONCERN_DETECTED,
                            SafetyEventSeverity.HIGH, "High event")
            );

            when(safetyEventRepository.findAllRequiringReview()).thenReturn(events);

            List<SafetyEventRecord> result = safetyEventService.getAllRequiringReview();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should get high priority events requiring review")
        void shouldGetHighPriorityEventsRequiringReview() {
            List<SafetyEventRecord> events = List.of(
                    new SafetyEventRecord(UUID.randomUUID(), SafetyEventType.CRISIS_DETECTED,
                            SafetyEventSeverity.CRITICAL, "Critical event")
            );

            when(safetyEventRepository.findRequiringReviewBySeverityAtLeast(SafetyEventSeverity.HIGH))
                    .thenReturn(events);

            List<SafetyEventRecord> result = safetyEventService.getHighPriorityRequiringReview();

            assertThat(result).hasSize(1);
            verify(safetyEventRepository).findRequiringReviewBySeverityAtLeast(SafetyEventSeverity.HIGH);
        }

        @Test
        @DisplayName("should get events by conversation ID")
        void shouldGetEventsByConversationId() {
            UUID conversationId = UUID.randomUUID();
            List<SafetyEventRecord> events = List.of(
                    new SafetyEventRecord(UUID.randomUUID(), SafetyEventType.SAFETY_CONCERN_DETECTED,
                            SafetyEventSeverity.MEDIUM, "Event from conversation")
            );

            when(safetyEventRepository.findByConversationIdOrderByCreatedAtDesc(conversationId))
                    .thenReturn(events);

            List<SafetyEventRecord> result = safetyEventService.getByConversationId(conversationId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should get events by time range")
        void shouldGetEventsByTimeRange() {
            UUID fatherId = UUID.randomUUID();
            Instant startTime = Instant.now().minusSeconds(86400); // 1 day ago
            Instant endTime = Instant.now();
            List<SafetyEventRecord> events = List.of(
                    new SafetyEventRecord(fatherId, SafetyEventType.SAFETY_CONCERN_DETECTED,
                            SafetyEventSeverity.LOW, "Recent event")
            );

            when(safetyEventRepository.findByFatherIdAndTimeRange(fatherId, startTime, endTime))
                    .thenReturn(events);

            List<SafetyEventRecord> result = safetyEventService.getByFatherIdAndTimeRange(
                    fatherId, startTime, endTime);

            assertThat(result).hasSize(1);
            verify(safetyEventRepository).findByFatherIdAndTimeRange(fatherId, startTime, endTime);
        }
    }

    // ─── Statistics Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("should count events by father ID")
        void shouldCountEventsByFatherId() {
            UUID fatherId = UUID.randomUUID();
            when(safetyEventRepository.countByFatherId(fatherId)).thenReturn(5L);

            long result = safetyEventService.countByFatherId(fatherId);

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("should count events requiring review")
        void shouldCountEventsRequiringReview() {
            when(safetyEventRepository.countByRequiresReviewTrue()).thenReturn(10L);

            long result = safetyEventService.countRequiringReview();

            assertThat(result).isEqualTo(10L);
        }

        @Test
        @DisplayName("should count high priority events requiring review")
        void shouldCountHighPriorityEventsRequiringReview() {
            when(safetyEventRepository.countRequiringReviewBySeverityAtLeast(SafetyEventSeverity.HIGH))
                    .thenReturn(3L);

            long result = safetyEventService.countHighPriorityRequiringReview();

            assertThat(result).isEqualTo(3L);
        }

        @Test
        @DisplayName("should check if father has safety events")
        void shouldCheckIfFatherHasSafetyEvents() {
            UUID fatherId = UUID.randomUUID();
            when(safetyEventRepository.existsByFatherId(fatherId)).thenReturn(true);

            boolean result = safetyEventService.hasSafetyEvents(fatherId);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should check if father has unreviewed events")
        void shouldCheckIfFatherHasUnreviewedEvents() {
            UUID fatherId = UUID.randomUUID();
            when(safetyEventRepository.existsByFatherIdAndRequiresReviewTrue(fatherId)).thenReturn(true);

            boolean result = safetyEventService.hasUnreviewedEvents(fatherId);

            assertThat(result).isTrue();
        }
    }
}
