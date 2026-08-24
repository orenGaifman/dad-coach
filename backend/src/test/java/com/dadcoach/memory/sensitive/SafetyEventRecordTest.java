package com.dadcoach.memory.sensitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SafetyEventRecord} entity.
 *
 * <p>Validates: SPEC-004 Task 12 - Safety events stored in separate table
 * <p>Validates: Task 12.2 - Records include: event_type, summary (≤100 chars), requires_review flag
 * <p>Validates: Task 12.3 - Expiration enforced on safety records
 */
@DisplayName("SafetyEventRecord Tests")
class SafetyEventRecordTest {

    // ─── Construction Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should create record with required fields including summary")
        void shouldCreateRecordWithRequiredFields() {
            UUID fatherId = UUID.randomUUID();
            SafetyEventType eventType = SafetyEventType.SAFETY_CONCERN_DETECTED;
            SafetyEventSeverity severity = SafetyEventSeverity.HIGH;
            String summary = "User mentioned feeling overwhelmed";

            SafetyEventRecord record = new SafetyEventRecord(fatherId, eventType, severity, summary);

            assertThat(record.getFatherId()).isEqualTo(fatherId);
            assertThat(record.getEventType()).isEqualTo(eventType);
            assertThat(record.getSeverity()).isEqualTo(severity);
            assertThat(record.getSummary()).isEqualTo(summary);
            assertThat(record.getDescription()).isNull(); // No description provided
            assertThat(record.getRequiresReview()).isTrue();
            assertThat(record.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should create record with summary and description")
        void shouldCreateRecordWithSummaryAndDescription() {
            UUID fatherId = UUID.randomUUID();
            SafetyEventType eventType = SafetyEventType.SAFETY_CONCERN_DETECTED;
            SafetyEventSeverity severity = SafetyEventSeverity.HIGH;
            String summary = "Safety concern detected";
            String description = "User mentioned feeling overwhelmed during the conversation";

            SafetyEventRecord record = new SafetyEventRecord(fatherId, eventType, severity, summary, description);

            assertThat(record.getFatherId()).isEqualTo(fatherId);
            assertThat(record.getEventType()).isEqualTo(eventType);
            assertThat(record.getSeverity()).isEqualTo(severity);
            assertThat(record.getSummary()).isEqualTo(summary);
            assertThat(record.getDescription()).isEqualTo(description);
            assertThat(record.getRequiresReview()).isTrue();
        }

        @Test
        @DisplayName("should truncate summary exceeding 100 char limit")
        void shouldTruncateSummaryExceedingMaxLength() {
            UUID fatherId = UUID.randomUUID();
            String longSummary = "x".repeat(150); // Exceeds 100 char limit

            SafetyEventRecord record = new SafetyEventRecord(
                    fatherId,
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    longSummary
            );

            assertThat(record.getSummary()).hasSize(SafetyEventRecord.MAX_SUMMARY_LENGTH);
            assertThat(record.getSummary()).endsWith("...");
        }

        @Test
        @DisplayName("should truncate description exceeding maximum length")
        void shouldTruncateDescriptionExceedingMaxLength() {
            UUID fatherId = UUID.randomUUID();
            String summary = "Brief summary";
            String longDescription = "x".repeat(600); // Exceeds 500 char limit

            SafetyEventRecord record = new SafetyEventRecord(
                    fatherId,
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    summary,
                    longDescription
            );

            assertThat(record.getDescription()).hasSize(SafetyEventRecord.MAX_DESCRIPTION_LENGTH);
            assertThat(record.getDescription()).endsWith("...");
        }

        @Test
        @DisplayName("should enforce summary field is 100 chars max as per SPEC-004")
        void shouldEnforceSummaryMaxLengthConstraint() {
            // Validates Task 12.2: summary (≤100 chars)
            assertThat(SafetyEventRecord.MAX_SUMMARY_LENGTH).isEqualTo(100);
        }

        @Test
        @DisplayName("should default requiresReview to true")
        void shouldDefaultRequiresReviewToTrue() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.ESCALATION_TRIGGERED,
                    SafetyEventSeverity.CRITICAL,
                    "Escalation triggered"
            );

            assertThat(record.getRequiresReview()).isTrue();
            assertThat(record.getReviewedBy()).isNull();
            assertThat(record.getReviewedAt()).isNull();
        }
    }

    // ─── Expiration/Retention Tests ──────────────────────────────────────

    @Nested
    @DisplayName("Expiration and Retention")
    class ExpirationRetentionTests {

        @Test
        @DisplayName("should set default retention period of 7 years")
        void shouldSetDefaultRetentionPeriod() {
            // Validates Task 12.3: 7 years retention for legal compliance
            assertThat(SafetyEventRecord.DEFAULT_RETENTION_YEARS).isEqualTo(7);
        }

        @Test
        @DisplayName("should set expiresAt to 7 years from creation by default")
        void shouldSetExpiresAtTo7YearsFromCreation() {
            Instant beforeCreation = Instant.now();

            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Test event"
            );

            Instant afterCreation = Instant.now();

            assertThat(record.getExpiresAt()).isNotNull();

            // expiresAt should be approximately 7 years (2555 days) from creation
            long daysDiff = ChronoUnit.DAYS.between(record.getCreatedAt(), record.getExpiresAt());
            assertThat(daysDiff).isEqualTo(7 * 365L);

            // expiresAt should be in the future
            assertThat(record.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("should allow custom retention period")
        void shouldAllowCustomRetentionPeriod() {
            int customRetentionYears = 10;

            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.CRISIS_DETECTED,
                    SafetyEventSeverity.CRITICAL,
                    "Critical event",
                    customRetentionYears
            );

            long daysDiff = ChronoUnit.DAYS.between(record.getCreatedAt(), record.getExpiresAt());
            assertThat(daysDiff).isEqualTo(customRetentionYears * 365L);
        }

        @Test
        @DisplayName("isExpired should return false for new records")
        void isExpiredShouldReturnFalseForNewRecords() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Test event"
            );

            assertThat(record.isExpired()).isFalse();
        }

        @Test
        @DisplayName("expiresWithinDays should correctly check future expiration window")
        void expiresWithinDaysShouldCheckFutureWindow() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Test event"
            );

            // Should NOT expire within 30 days (it's set for 7 years)
            assertThat(record.expiresWithinDays(30)).isFalse();

            // Should NOT expire within 365 days
            assertThat(record.expiresWithinDays(365)).isFalse();

            // Should expire within 10 years (3650 days)
            assertThat(record.expiresWithinDays(3650)).isTrue();
        }

        @Test
        @DisplayName("toString should include expiresAt")
        void toStringShouldIncludeExpiresAt() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Test event"
            );

            String result = record.toString();

            assertThat(result).contains("expiresAt=");
        }
    }

    // ─── Review Workflow Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Review Workflow")
    class ReviewWorkflowTests {

        @Test
        @DisplayName("should mark event as reviewed")
        void shouldMarkEventAsReviewed() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Test event"
            );
            UUID reviewerId = UUID.randomUUID();
            String notes = "Reviewed and addressed";

            record.markReviewed(reviewerId, notes);

            assertThat(record.getRequiresReview()).isFalse();
            assertThat(record.getReviewedBy()).isEqualTo(reviewerId);
            assertThat(record.getReviewedAt()).isNotNull();
            assertThat(record.getReviewNotes()).isEqualTo(notes);
            assertThat(record.isReviewed()).isTrue();
        }

        @Test
        @DisplayName("should flag event for re-review")
        void shouldFlagEventForReReview() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.CRISIS_DETECTED,
                    SafetyEventSeverity.CRITICAL,
                    "Crisis event"
            );
            record.markReviewed(UUID.randomUUID(), "Initial review");

            record.flagForReview();

            assertThat(record.getRequiresReview()).isTrue();
            // Previous review info should be preserved
            assertThat(record.getReviewedBy()).isNotNull();
            assertThat(record.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("isReviewed should return false when requiresReview is true")
        void isReviewedShouldReturnFalseWhenRequiresReviewIsTrue() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Test event"
            );

            assertThat(record.isReviewed()).isFalse();
        }
    }

    // ─── Priority Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Priority Checking")
    class PriorityTests {

        @Test
        @DisplayName("should identify HIGH severity as high priority")
        void shouldIdentifyHighSeverityAsHighPriority() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.HIGH,
                    "High severity event"
            );

            assertThat(record.isHighPriority()).isTrue();
        }

        @Test
        @DisplayName("should identify CRITICAL severity as high priority")
        void shouldIdentifyCriticalSeverityAsHighPriority() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.ESCALATION_TRIGGERED,
                    SafetyEventSeverity.CRITICAL,
                    "Critical event"
            );

            assertThat(record.isHighPriority()).isTrue();
        }

        @Test
        @DisplayName("should not identify LOW severity as high priority")
        void shouldNotIdentifyLowSeverityAsHighPriority() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.LOW,
                    "Low severity event"
            );

            assertThat(record.isHighPriority()).isFalse();
        }

        @Test
        @DisplayName("should not identify MEDIUM severity as high priority")
        void shouldNotIdentifyMediumSeverityAsHighPriority() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Medium severity event"
            );

            assertThat(record.isHighPriority()).isFalse();
        }
    }

    // ─── Context Setting Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Context Setting")
    class ContextSettingTests {

        @Test
        @DisplayName("should set conversation context")
        void shouldSetConversationContext() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Test event"
            );
            UUID conversationId = UUID.randomUUID();

            record.setConversationId(conversationId);

            assertThat(record.getConversationId()).isEqualTo(conversationId);
        }

        @Test
        @DisplayName("should set memory context")
        void shouldSetMemoryContext() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Test event"
            );
            UUID memoryId = UUID.randomUUID();

            record.setMemoryId(memoryId);

            assertThat(record.getMemoryId()).isEqualTo(memoryId);
        }

        @Test
        @DisplayName("should set metadata")
        void shouldSetMetadata() {
            SafetyEventRecord record = new SafetyEventRecord(
                    UUID.randomUUID(),
                    SafetyEventType.SAFETY_CONCERN_DETECTED,
                    SafetyEventSeverity.MEDIUM,
                    "Test event"
            );
            Map<String, Object> metadata = Map.of(
                    "trigger_keywords", "harm, danger",
                    "ai_confidence", 0.85
            );

            record.setMetadata(metadata);

            assertThat(record.getMetadata()).isEqualTo(metadata);
            assertThat(record.getMetadata()).containsEntry("trigger_keywords", "harm, danger");
            assertThat(record.getMetadata()).containsEntry("ai_confidence", 0.85);
        }
    }

    // ─── toString Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should include key fields including summary in toString")
        void shouldIncludeKeyFieldsInToString() {
            UUID fatherId = UUID.randomUUID();
            String summary = "Test event";
            SafetyEventRecord record = new SafetyEventRecord(
                    fatherId,
                    SafetyEventType.CRISIS_DETECTED,
                    SafetyEventSeverity.CRITICAL,
                    summary
            );

            String result = record.toString();

            assertThat(result).contains("SafetyEventRecord");
            assertThat(result).contains("fatherId=" + fatherId);
            assertThat(result).contains("eventType=CRISIS_DETECTED");
            assertThat(result).contains("severity=CRITICAL");
            assertThat(result).contains("summary='" + summary + "'");
            assertThat(result).contains("requiresReview=true");
        }
    }
}
