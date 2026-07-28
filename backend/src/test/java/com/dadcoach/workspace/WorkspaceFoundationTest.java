package com.dadcoach.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.workspace.dto.response.PartialResponse;
import com.dadcoach.workspace.event.WorkspaceDomainEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for workspace foundation classes.
 */
class WorkspaceFoundationTest {

    @Nested
    @DisplayName("WorkspaceDomainEvent")
    class DomainEventTests {

        @Test
        void shouldGenerateUniqueEventId() {
            UUID fatherId = UUID.randomUUID();
            TestEvent event = new TestEvent(fatherId);

            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getFatherId()).isEqualTo(fatherId);
            assertThat(event.getOccurredAt()).isNotNull();
        }

        @Test
        void shouldGenerateDifferentEventIdsForDifferentInstances() {
            UUID fatherId = UUID.randomUUID();
            TestEvent event1 = new TestEvent(fatherId);
            TestEvent event2 = new TestEvent(fatherId);

            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }

        private static class TestEvent extends WorkspaceDomainEvent {
            TestEvent(UUID fatherId) {
                super(fatherId);
            }
        }
    }

    @Nested
    @DisplayName("WorkspaceErrorCode")
    class ErrorCodeTests {

        @Test
        void shouldHaveCorrectCodeStrings() {
            assertThat(WorkspaceErrorCode.FATHER_NOT_FOUND.getCode()).isEqualTo("WORKSPACE_001");
            assertThat(WorkspaceErrorCode.CHILD_NOT_FOUND.getCode()).isEqualTo("WORKSPACE_002");
            assertThat(WorkspaceErrorCode.GOAL_NOT_FOUND.getCode()).isEqualTo("WORKSPACE_003");
            assertThat(WorkspaceErrorCode.RESOURCE_NOT_FOUND.getCode()).isEqualTo("WORKSPACE_004");
            assertThat(WorkspaceErrorCode.GROWTH_SIGNAL_DUPLICATE.getCode()).isEqualTo("WORKSPACE_005");
            assertThat(WorkspaceErrorCode.RATE_LIMIT_EXCEEDED.getCode()).isEqualTo("WORKSPACE_006");
            assertThat(WorkspaceErrorCode.VALIDATION_ERROR.getCode()).isEqualTo("WORKSPACE_007");
            assertThat(WorkspaceErrorCode.SERVICE_UNAVAILABLE.getCode()).isEqualTo("WORKSPACE_008");
            assertThat(WorkspaceErrorCode.INTERNAL_ERROR.getCode()).isEqualTo("WORKSPACE_009");
        }

        @Test
        void shouldFormatMessageWithArguments() {
            String message = WorkspaceErrorCode.FATHER_NOT_FOUND.formatMessage("abc-123");
            assertThat(message).isEqualTo("Father not found with identifier: abc-123");
        }

        @Test
        void shouldReturnTemplateWhenNoArgs() {
            String message = WorkspaceErrorCode.INTERNAL_ERROR.formatMessage();
            assertThat(message).isEqualTo("An unexpected error occurred. Please try again later.");
        }
    }

    @Nested
    @DisplayName("WorkspaceException hierarchy")
    class ExceptionTests {

        @Test
        void resourceNotFoundShouldResolveCorrectErrorCode() {
            var ex = new ResourceNotFoundException("Father", UUID.randomUUID());
            assertThat(ex.getErrorCode()).isEqualTo(WorkspaceErrorCode.FATHER_NOT_FOUND);
            assertThat(ex.getEntityType()).isEqualTo("Father");
        }

        @Test
        void resourceNotFoundForChildShouldResolveCorrectCode() {
            var ex = new ResourceNotFoundException("Child", UUID.randomUUID());
            assertThat(ex.getErrorCode()).isEqualTo(WorkspaceErrorCode.CHILD_NOT_FOUND);
        }

        @Test
        void resourceNotFoundForGoalShouldResolveCorrectCode() {
            var ex = new ResourceNotFoundException("Goal", UUID.randomUUID());
            assertThat(ex.getErrorCode()).isEqualTo(WorkspaceErrorCode.GOAL_NOT_FOUND);
        }

        @Test
        void resourceNotFoundForUnknownTypeShouldUseGenericCode() {
            var ex = new ResourceNotFoundException("Widget", "xyz");
            assertThat(ex.getErrorCode()).isEqualTo(WorkspaceErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void duplicateSignalExceptionShouldCarrySourceEntityId() {
            var ex = new DuplicateSignalException("mission-123");
            assertThat(ex.getErrorCode()).isEqualTo(WorkspaceErrorCode.GROWTH_SIGNAL_DUPLICATE);
            assertThat(ex.getSourceEntityId()).isEqualTo("mission-123");
            assertThat(ex.getMessage()).contains("mission-123");
        }

        @Test
        void rateLimitExceptionShouldCarryRetryAfter() {
            var ex = new RateLimitExceededException(60);
            assertThat(ex.getErrorCode()).isEqualTo(WorkspaceErrorCode.RATE_LIMIT_EXCEEDED);
            assertThat(ex.getRetryAfterSeconds()).isEqualTo(60);
            assertThat(ex.getMessage()).contains("60");
        }
    }

    @Nested
    @DisplayName("PartialResponse")
    class PartialResponseTests {

        @Test
        void completeShouldHaveCorrectStatus() {
            PartialResponse<String> response = PartialResponse.complete("hello");

            assertThat(response.getData()).isEqualTo("hello");
            assertThat(response.getResponseStatus()).isEqualTo("complete");
            assertThat(response.getDegradedSections()).isNull();
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        void partialShouldListDegradedSections() {
            List<String> degraded = List.of("notifications", "streak");
            PartialResponse<String> response = PartialResponse.partial("partial data", degraded);

            assertThat(response.getData()).isEqualTo("partial data");
            assertThat(response.getResponseStatus()).isEqualTo("partial");
            assertThat(response.getDegradedSections()).containsExactly("notifications", "streak");
            assertThat(response.getTimestamp()).isNotNull();
        }
    }
}
